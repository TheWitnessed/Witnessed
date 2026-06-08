package com.trillix.witnessed.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.trillix.witnessed.Graph
import com.trillix.witnessed.data.AttemptReceipt
import com.trillix.witnessed.data.DetectionTier
import com.trillix.witnessed.data.Outcome
import com.trillix.witnessed.data.UserSettings
import com.trillix.witnessed.data.WatchedApp
import com.trillix.witnessed.domain.Insights
import com.trillix.witnessed.domain.Stats
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class UiState(
    val loading: Boolean = true,
    val onboardingComplete: Boolean = false,
    val app: WatchedApp? = null,
    val settings: UserSettings = UserSettings(),
    val receipts: List<AttemptReceipt> = emptyList(),
    val stats: Stats? = null,
)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = Graph.repository

    val state: StateFlow<UiState> =
        combine(repo.watchedApp, repo.userSettings, repo.receipts) { app, settings, receipts ->
            UiState(
                loading = false,
                onboardingComplete = settings.onboardingComplete && app != null,
                app = app,
                settings = settings,
                receipts = receipts,
                stats = Insights.compute(receipts, createdAt = app?.createdAt ?: System.currentTimeMillis()),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    fun completeOnboarding(app: WatchedApp, detection: DetectionTier) =
        viewModelScope.launch { repo.completeOnboarding(app, detection) }

    fun updateApp(transform: (WatchedApp) -> WatchedApp) =
        viewModelScope.launch { repo.updateApp(transform) }

    fun setDaily(v: Boolean) = viewModelScope.launch { repo.settings.setDaily(v) }
    fun setWeekly(v: Boolean) = viewModelScope.launch { repo.settings.setWeekly(v) }
    fun setDetection(v: DetectionTier) = viewModelScope.launch { repo.settings.setDetection(v) }

    fun clearReceipts() = viewModelScope.launch { repo.clearReceipts() }
    fun resetAll() = viewModelScope.launch { repo.resetAll() }
}
