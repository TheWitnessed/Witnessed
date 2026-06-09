package com.trillix.witnessed.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trillix.witnessed.data.AttemptReceipt
import com.trillix.witnessed.data.DetectionTier
import com.trillix.witnessed.data.MODE_INFO
import com.trillix.witnessed.data.Mode
import com.trillix.witnessed.data.Outcome
import com.trillix.witnessed.data.WatchedApp
import com.trillix.witnessed.data.reasonName
import com.trillix.witnessed.domain.Insights
import com.trillix.witnessed.ui.theme.Accent
import com.trillix.witnessed.ui.theme.AccentLine
import com.trillix.witnessed.ui.theme.AccentSoft
import com.trillix.witnessed.ui.theme.AccentText
import com.trillix.witnessed.ui.theme.Bg
import com.trillix.witnessed.ui.theme.Bg2
import com.trillix.witnessed.ui.theme.Dim
import com.trillix.witnessed.ui.theme.Faint
import com.trillix.witnessed.ui.theme.Kept
import com.trillix.witnessed.ui.theme.Line
import com.trillix.witnessed.ui.theme.LineStrong
import com.trillix.witnessed.ui.theme.Mono
import com.trillix.witnessed.ui.theme.Serif
import com.trillix.witnessed.ui.theme.Surface1
import com.trillix.witnessed.ui.theme.Surface3
import com.trillix.witnessed.ui.theme.TextMain

private fun lvl(mode: Mode) = when (mode) { Mode.LIGHT -> 0; Mode.SERIOUS -> 1; Mode.NOBULL -> 2 }

// Bump this each build so we can confirm on-device which APK is actually running.
private const val BUILD_TAG = "BUILD F2"

@Composable
fun HomeScreen(state: UiState, vm: AppViewModel, onTrigger: () -> Unit) {
    var tab by remember { mutableStateOf("mirror") }
    val titles = mapOf("mirror" to "Mirror", "receipts" to "Receipts", "settings" to "Settings")

    Column(Modifier.fillMaxSize().background(Bg)) {
        // app bar
        Row(
            Modifier.fillMaxWidth().border(1.dp, Line, RoundedCornerShape(0.dp))
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(titles[tab] ?: "", color = TextMain, fontFamily = Serif, fontWeight = FontWeight.Medium,
                fontSize = 19.sp, modifier = Modifier.weight(1f))
            Text(BUILD_TAG, color = Faint, fontFamily = Mono, fontSize = 9.5.sp, letterSpacing = 1.sp)
            Spacer(Modifier.width(10.dp))
            Seal(size = 26.dp)
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                "mirror" -> Mirror(state, onTrigger)
                "receipts" -> Receipts(state)
                else -> SettingsScreen(state, vm)
            }
        }

        // bottom nav
        Row(Modifier.fillMaxWidth().background(Bg2).border(1.dp, Line, RoundedCornerShape(0.dp))) {
            listOf(
                Triple("mirror", "mirror", "Mirror"),
                Triple("receipts", "receipt", "Receipts"),
                Triple("settings", "gear", "Settings"),
            ).forEach { (id, icon, label) ->
                val on = tab == id
                Column(
                    Modifier.weight(1f).clickable { tab = id }.padding(top = 11.dp, bottom = 13.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    VIcon(icon, size = 21.dp, color = if (on) AccentText else Faint)
                    Spacer(Modifier.height(5.dp))
                    Text(label, color = if (on) AccentText else Faint, fontFamily = com.trillix.witnessed.ui.theme.Sans,
                        fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun Mirror(state: UiState, onTrigger: () -> Unit) {
    val app = state.app ?: return
    val stats = state.stats ?: return
    val l = lvl(app.mode)
    val w = stats.week
    val t = stats.today
    val m = stats.month
    val hasData = state.receipts.isNotEmpty()
    val now = remember(state.receipts.size) { System.currentTimeMillis() }

    val reasonLc = w.topReason?.lowercase()
    val summary = when {
        !hasData -> ""
        app.mode == Mode.LIGHT ->
            "You had ${w.total} temptation ${if (w.total == 1) "moment" else "moments"} this week. You backed out ${w.backed} of them."
        else ->
            "You reached for ${app.appName} ${w.total} times this week. ${w.workHours} happened during work hours. " +
                (reasonLc?.let { "Your most common reason was $it. " } ?: "") +
                "You kept your promise ${w.keptPct}% of the time."
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {

        if (!hasData) {
            Column(Modifier.padding(horizontal = 22.dp).padding(top = 16.dp)) {
                PromiseBanner(app)
            }
            Column(Modifier.padding(22.dp)) {
                Column(Modifier.fillMaxWidth().padding(vertical = 30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(54.dp).clip(RoundedCornerShape(14.dp)).border(1.dp, LineStrong, RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center,
                    ) { VIcon("receipt", size = 24.dp, color = Dim) }
                    Spacer(Modifier.height(14.dp))
                    Text("Nothing on the record yet.", color = TextMain, fontFamily = Serif, fontWeight = FontWeight.Medium, fontSize = 22.sp)
                    Spacer(Modifier.height(6.dp))
                    Lead("The first time you feel the pull, log it. Your standing builds from there.")
                }
                Spacer(Modifier.height(8.dp))
                PrimaryButton("I’m being pulled — log it", icon = "hand", onClick = onTrigger)
                Spacer(Modifier.height(10.dp))
                DetectionLine(state.settings.detection, app.appName)
            }
            return
        }

        // ---- Standing hero (live, present-tense, at stake) ----
        Column(Modifier.padding(horizontal = 22.dp).padding(top = 18.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Kicker("Standing")
                    Spacer(Modifier.height(9.dp))
                    Text(
                        "DAY ${stats.streakDays}",
                        color = if (stats.streakDays > 0) Kept else AccentText,
                        fontFamily = Mono, fontWeight = FontWeight.SemiBold, fontSize = 40.sp, letterSpacing = (-1).sp,
                    )
                    Spacer(Modifier.height(7.dp))
                    Text(standingSub(app.mode, stats.streakDays), color = TextMain, fontFamily = Serif, fontSize = 15.sp, lineHeight = 20.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(fmtClock(now), color = TextMain, fontFamily = Mono, fontSize = 13.sp)
                    Spacer(Modifier.height(5.dp))
                    Text(
                        if (t.cont == 0) "NO CONTINUE TODAY" else "${t.cont} CONTINUED TODAY",
                        color = if (t.cont == 0) Kept else AccentText, fontFamily = Mono, fontSize = 9.5.sp, letterSpacing = 0.6.sp,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
            Spacer(Modifier.height(16.dp))
            PromiseBanner(app, echo = if (l >= 1) promiseEcho(app.mode, t.cont) else null)
        }

        Column(Modifier.padding(22.dp)) {

            // ---- Danger window: counts down, escalates when you're inside it ----
            if (l >= 1 && w.total >= 4) {
                val inWin = Insights.inWindow(w.worstStart, now)
                val mins = Insights.minutesToWindow(w.worstStart, now)
                Card(accent = inWin) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Kicker(if (inWin) "Danger window · live now" else "Next danger window", modifier = Modifier.weight(1f))
                        Tag(w.dangerWindow)
                    }
                    Spacer(Modifier.height(12.dp))
                    if (inWin) {
                        Text(dangerLive(app.mode, app.appName), color = TextMain, fontFamily = Serif, fontSize = 18.sp, lineHeight = 25.sp)
                    } else {
                        Text(fmtCountdown(mins), color = AccentText, fontFamily = Mono, fontWeight = FontWeight.SemiBold, fontSize = 28.sp)
                        Spacer(Modifier.height(9.dp))
                        Tiny("Your heaviest pull lands ${w.dangerWindow}. You don’t open it at random — it opens you.")
                    }
                }
                Spacer(Modifier.height(18.dp))
            }

            // ---- Today ----
            Kicker("Today")
            Spacer(Modifier.height(11.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                StatTile(t.total.toString(), "Attempts", modifier = Modifier.weight(1f))
                StatTile(t.backed.toString(), "Backed out", accent = true, modifier = Modifier.weight(1f))
                if (l >= 1) StatTile(t.cont.toString(), "Continued", modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(22.dp))

            // ---- This month: the standing cost (Serious+) ----
            if (l >= 1) {
                Kicker("This month")
                Spacer(Modifier.height(11.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    StatTile(m.total.toString(), "Moments", modifier = Modifier.weight(1f))
                    StatTile(m.cont.toString(), "Continued", accent = true, modifier = Modifier.weight(1f))
                    StatTile("${m.keptPct}%", "Kept", modifier = Modifier.weight(1f))
                }
                if (state.settings.detection == DetectionTier.MANUAL) {
                    Spacer(Modifier.height(10.dp))
                    Tiny("Minutes lost isn’t tracked yet — turn on Usage access in Settings to put real time spent on the record.")
                }
                Spacer(Modifier.height(22.dp))
            }

            // ---- This week ----
            Kicker("This week")
            Spacer(Modifier.height(11.dp))
            Card {
                Text(summary, color = TextMain, fontFamily = Serif, fontSize = 17.sp, lineHeight = 24.sp)
            }
            Spacer(Modifier.height(14.dp))

            if (l >= 1) {
                Card {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Kicker("Promise kept · 7d", modifier = Modifier.weight(1f))
                        Text("${w.keptPct}%", color = Kept, fontFamily = Serif, fontSize = 22.sp)
                    }
                    Spacer(Modifier.height(10.dp))
                    Meter(w.keptPct)
                    Spacer(Modifier.height(9.dp))
                    Tiny("${w.backed} backed out · ${w.cont} continued · ${w.total} total moments")
                }
                Spacer(Modifier.height(14.dp))
            }

            if (l >= 1 && w.reasonCounts.isNotEmpty()) {
                Card {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Kicker("Why you continued", modifier = Modifier.weight(1f))
                        w.topReason?.let { Tag("Top · $it") }
                    }
                    Spacer(Modifier.height(14.dp))
                    ReasonBars(w.reasonCounts)
                }
                Spacer(Modifier.height(14.dp))
            }

            if (l >= 2) {
                Card {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Kicker("Pattern", modifier = Modifier.weight(1f))
                        Tag(w.dangerWindow)
                    }
                    Spacer(Modifier.height(14.dp))
                    DangerSpark(w.hist, w.worstStart)
                    Spacer(Modifier.height(12.dp))
                    Tiny("Earliest ${w.earliest} · latest ${w.latest}. Your heaviest pull clusters ${w.dangerWindow}.")
                }
                Spacer(Modifier.height(14.dp))
            }

            // ---- Last entry: evidence, on the record ----
            stats.latest?.let { r ->
                LatestReceipt(r, now)
                Spacer(Modifier.height(16.dp))
            }

            PrimaryButton("I’m being pulled — log it", icon = "hand", onClick = onTrigger)
            Spacer(Modifier.height(10.dp))
            DetectionLine(state.settings.detection, app.appName)
        }
    }
}

@Composable
private fun PromiseBanner(app: WatchedApp, echo: String? = null) {
    Card(accent = true) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(26.dp).clip(RoundedCornerShape(7.dp)).background(Color(app.appColor)),
                contentAlignment = Alignment.Center,
            ) { Text(app.appGlyph, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.width(9.dp))
            Text("YOUR PROMISE · ${MODE_INFO[app.mode]?.name} MODE", color = Dim, fontFamily = Mono,
                fontSize = 10.5.sp, letterSpacing = 1.4.sp)
        }
        Spacer(Modifier.height(9.dp))
        PromiseQuote(app.promiseText, size = 18)
        if (echo != null) {
            Spacer(Modifier.height(9.dp))
            Text(echo, color = Dim, fontFamily = Serif, fontStyle = FontStyle.Italic, fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
}

@Composable
private fun DetectionLine(detection: DetectionTier, appName: String) {
    Tiny(
        when (detection) {
            DetectionTier.MANUAL -> "Manual mode: you keep the receipts yourself."
            DetectionTier.USAGE -> "Usage access on — minutes are recorded objectively."
            else -> "Full effect on — the wall appears the moment you open $appName."
        },
        modifier = Modifier.fillMaxWidth(), align = TextAlign.Center,
    )
}

@Composable
private fun LatestReceipt(r: AttemptReceipt, now: Long) {
    val label = when (r.outcome) {
        Outcome.BACKED_OUT -> "Backed out"
        Outcome.CONTINUED -> "Continued"
        else -> "Dismissed"
    }
    val reason = r.reasonText?.takeIf { it.isNotBlank() } ?: reasonName(r.reasonCategory)
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(Surface1)
            .border(1.dp, Line, RoundedCornerShape(13.dp)).padding(14.dp),
    ) {
        Column {
            Text("LAST ENTRY · ${relDay(r.timestamp, now)} ${fmtClock(r.timestamp)}",
                color = Faint, fontFamily = Mono, fontSize = 10.5.sp, letterSpacing = 0.8.sp)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, color = if (r.outcome == Outcome.BACKED_OUT) Kept else AccentText, fontFamily = Serif, fontSize = 16.sp)
                reason?.let {
                    Text("  ·  “$it”", color = Dim, fontFamily = Serif, fontStyle = FontStyle.Italic, fontSize = 15.sp)
                }
            }
            Spacer(Modifier.height(5.dp))
            Text("ON THE RECORD", color = Faint, fontFamily = Mono, fontSize = 8.5.sp, letterSpacing = 1.sp)
        }
    }
}

private fun standingSub(mode: Mode, streak: Int): String = when (mode) {
    Mode.LIGHT -> if (streak > 0) "$streak ${dayWord(streak)} showing up." else "Day one starts now."
    Mode.SERIOUS -> if (streak > 0) "$streak ${dayWord(streak)}, no continue on the record." else "Today is day one. Face the first pull."
    Mode.NOBULL -> if (streak > 0) "$streak ${dayWord(streak)}. One continue resets it." else "Day zero. Earn day one."
}

private fun dayWord(n: Int) = if (n == 1) "day" else "days"

private fun promiseEcho(mode: Mode, contToday: Int): String = when {
    contToday > 0 -> "You’ve already continued today. The promise still stands."
    mode == Mode.NOBULL -> "It’s still true. Nothing’s happened yet."
    else -> "Still true today."
}

private fun dangerLive(mode: Mode, appName: String): String = when (mode) {
    Mode.NOBULL -> "This is the hour $appName runs your evening. You opened this — good. Read your own words before you scroll."
    else -> "$appName usually pulls you in around now. You know the pattern."
}

private fun fmtClock(ts: Long): String {
    val c = java.util.Calendar.getInstance().apply { timeInMillis = ts }
    val h = c.get(java.util.Calendar.HOUR_OF_DAY)
    val mn = c.get(java.util.Calendar.MINUTE)
    val ap = if (h < 12) "AM" else "PM"
    var hh = h % 12; if (hh == 0) hh = 12
    return String.format("%d:%02d %s", hh, mn, ap)
}

private fun fmtCountdown(mins: Int): String {
    if (mins <= 0) return "now"
    val h = mins / 60
    val mn = mins % 60
    return if (h == 0) "${mn}m" else "${h}h ${mn}m"
}

private fun relDay(ts: Long, now: Long): String {
    fun midnight(x: Long) = java.util.Calendar.getInstance().apply {
        timeInMillis = x
        set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis
    val days = ((midnight(now) - midnight(ts)) / 86_400_000L).toInt()
    return when (days) {
        0 -> "Today"
        1 -> "Yesterday"
        else -> {
            val c = java.util.Calendar.getInstance().apply { timeInMillis = ts }
            val mo = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")[c.get(java.util.Calendar.MONTH)]
            "${c.get(java.util.Calendar.DAY_OF_MONTH)} $mo"
        }
    }
}

@Composable
private fun Tag(text: String) {
    Box(
        Modifier.clip(RoundedCornerShape(6.dp)).background(AccentSoft)
            .border(1.dp, AccentLine, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 4.dp),
    ) { Text(text.uppercase(), color = AccentText, fontFamily = Mono, fontSize = 10.5.sp, letterSpacing = 0.8.sp) }
}

@Composable
private fun ReasonBars(counts: Map<String, Int>) {
    val entries = counts.entries.sortedByDescending { it.value }
    val max = (entries.maxOfOrNull { it.value } ?: 1).coerceAtLeast(1)
    Column {
        entries.forEach { (id, n) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(reasonName(id) ?: id, color = Dim, fontSize = 13.sp, modifier = Modifier.width(82.dp))
                Spacer(Modifier.width(12.dp))
                Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(99.dp)).background(Surface3)) {
                    Box(Modifier.fillMaxWidth(fraction = n.toFloat() / max).height(8.dp).clip(RoundedCornerShape(99.dp)).background(Accent))
                }
                Spacer(Modifier.width(12.dp))
                Text(n.toString(), color = Faint, fontFamily = Mono, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun DangerSpark(hist: List<Int>, worstStart: Int) {
    val max = (hist.maxOrNull() ?: 1).coerceAtLeast(1)
    val window = setOf(worstStart, (worstStart + 1) % 24, (worstStart + 2) % 24)
    Row(Modifier.fillMaxWidth().height(46.dp), verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        hist.forEachIndexed { h, v ->
            val barH: Dp = ((v.toFloat() / max) * 46f).dp.coerceAtLeast(2.dp)
            val c = if (h in window) Accent else if (v > 0) AccentLine else Surface3
            Box(Modifier.weight(1f).height(barH).clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp)).background(c))
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 7.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        listOf("12a", "6a", "12p", "6p").forEach {
            Text(it, color = Faint, fontFamily = Mono, fontSize = 9.5.sp)
        }
    }
}
