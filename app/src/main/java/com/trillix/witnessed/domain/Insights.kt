package com.trillix.witnessed.domain

import com.trillix.witnessed.data.AttemptReceipt
import com.trillix.witnessed.data.Outcome
import com.trillix.witnessed.data.reasonName
import java.util.Calendar

data class DayStats(
    val total: Int,
    val backed: Int,
    val cont: Int,
    val topReason: String?,
)

data class WeekStats(
    val total: Int,
    val backed: Int,
    val cont: Int,
    val keptPct: Int,
    val topReason: String?,
    val dangerWindow: String,
    val workHours: Int,
    val hist: List<Int>,
    val reasonCounts: Map<String, Int>,
    val earliest: String,
    val latest: String,
    val worstStart: Int,
)

data class MonthStats(
    val total: Int,
    val backed: Int,
    val cont: Int,
    val keptPct: Int,
)

data class Stats(
    val today: DayStats,
    val week: WeekStats,
    val month: MonthStats,
    val streakDays: Int,
    val latest: AttemptReceipt?,
)

/** Pure stat functions over the receipt ledger — ported from computeStats(). */
object Insights {
    fun compute(
        receipts: List<AttemptReceipt>,
        now: Long = System.currentTimeMillis(),
        createdAt: Long = now,
    ): Stats {
        val dayMs = 86_400_000L
        fun midnight(x: Long): Long = Calendar.getInstance().apply {
            timeInMillis = x
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val startToday = midnight(now)
        val weekAgo = now - 7L * dayMs
        val monthAgo = now - 30L * dayMs

        val week = receipts.filter { it.timestamp >= weekAgo }
        val today = receipts.filter { it.timestamp >= startToday }
        val month = receipts.filter { it.timestamp >= monthAgo }
        fun count(list: List<AttemptReceipt>, o: Outcome) = list.count { it.outcome == o }

        val wBacked = count(week, Outcome.BACKED_OUT)
        val wCont = count(week, Outcome.CONTINUED)
        val wTotal = week.size
        val keptPct = if (wTotal > 0) Math.round(wBacked * 100f / wTotal) else 0

        val reasonCounts = HashMap<String, Int>()
        week.forEach { r -> r.reasonCategory?.let { reasonCounts[it] = (reasonCounts[it] ?: 0) + 1 } }
        val topReason = reasonName(reasonCounts.entries.maxByOrNull { it.value }?.key)

        val hist = IntArray(24)
        week.forEach { if (it.hour in 0..23) hist[it.hour] = hist[it.hour] + 1 }
        var worstStart = 0
        var worstSum = -1
        for (h in 0 until 24) {
            val sum = hist[h] + hist[(h + 1) % 24] + hist[(h + 2) % 24]
            if (sum > worstSum) { worstSum = sum; worstStart = h }
        }
        fun fmtH(h: Int): String {
            val hh = ((h % 24) + 24) % 24
            val ap = if (hh < 12) "am" else "pm"
            var v = hh % 12; if (v == 0) v = 12
            return "$v$ap"
        }
        val dangerWindow = if (wTotal > 0) "${fmtH(worstStart)}–${fmtH(worstStart + 3)}" else "—"
        val workHours = week.count { it.hour in 9..16 }

        fun fmtTime(r: AttemptReceipt): String {
            val c = Calendar.getInstance().apply { timeInMillis = r.timestamp }
            val h = c.get(Calendar.HOUR_OF_DAY)
            val m = c.get(Calendar.MINUTE)
            val ap = if (h < 12) "AM" else "PM"
            var hh = h % 12; if (hh == 0) hh = 12
            return String.format("%d:%02d %s", hh, m, ap)
        }
        val byHour = week.sortedBy { it.hour }
        val earliest = byHour.firstOrNull()?.let { fmtTime(it) } ?: "—"
        val latest = byHour.lastOrNull()?.let { fmtTime(it) } ?: "—"

        val tReasonCounts = HashMap<String, Int>()
        today.forEach { r -> r.reasonCategory?.let { tReasonCounts[it] = (tReasonCounts[it] ?: 0) + 1 } }
        val tTop = reasonName(tReasonCounts.entries.maxByOrNull { it.value }?.key)

        // ---- This month (rolling 30 days) ----
        val mBacked = count(month, Outcome.BACKED_OUT)
        val mCont = count(month, Outcome.CONTINUED)
        val mTotal = month.size
        val mKept = if (mTotal > 0) Math.round(mBacked * 100f / mTotal) else 0

        // ---- Forgiving "kept" streak ----
        // Consecutive days back from today with NO recorded CONTINUE. Backing out
        // (or logging nothing) keeps the day; a logged continue breaks it. Honest
        // for manual mode — it only counts what the ledger knows. The objective
        // version (real minutes) waits on the Usage tier. Capped at days since the
        // promise was made so a brand-new install can't show a fake long streak.
        val contDays = HashSet<Long>()
        receipts.forEach { r -> if (r.outcome == Outcome.CONTINUED) contDays.add(midnight(r.timestamp)) }
        val maxDays = (((startToday - midnight(createdAt)) / dayMs).toInt() + 1).coerceIn(0, 366)
        var streakDays = 0
        var d = startToday
        while (streakDays < maxDays) {
            if (contDays.contains(d)) break
            streakDays++
            d -= dayMs
        }

        return Stats(
            today = DayStats(today.size, count(today, Outcome.BACKED_OUT), count(today, Outcome.CONTINUED), tTop),
            week = WeekStats(
                wTotal, wBacked, wCont, keptPct, topReason, dangerWindow, workHours,
                hist.toList(), reasonCounts, earliest, latest, worstStart
            ),
            month = MonthStats(mTotal, mBacked, mCont, mKept),
            streakDays = streakDays,
            latest = receipts.firstOrNull(),
        )
    }

    /** True if [now]'s hour falls inside the 3-hour danger window starting at [worstStart]. */
    fun inWindow(worstStart: Int, now: Long = System.currentTimeMillis()): Boolean {
        val h = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY)
        val a = ((worstStart % 24) + 24) % 24
        return (0..2).any { (a + it) % 24 == h }
    }

    /** Minutes until the next start of the danger window (0 if already inside this hour's start). */
    fun minutesToWindow(worstStart: Int, now: Long = System.currentTimeMillis()): Int {
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        val cur = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val start = (((worstStart % 24) + 24) % 24) * 60
        var diff = start - cur
        if (diff < 0) diff += 24 * 60
        return diff
    }
}
