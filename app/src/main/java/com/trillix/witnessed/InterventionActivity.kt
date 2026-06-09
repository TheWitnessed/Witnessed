package com.trillix.witnessed

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.trillix.witnessed.data.Outcome
import com.trillix.witnessed.service.InterventionGate
import com.trillix.witnessed.ui.AppViewModel
import com.trillix.witnessed.ui.InterventionWall
import com.trillix.witnessed.ui.theme.Bg
import com.trillix.witnessed.ui.theme.WitnessedTheme
import kotlinx.coroutines.launch

/** The wall, as its own full-screen Activity so it feels total. */
class InterventionActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Hardware back logs a DISMISSED receipt rather than silently vanishing.
        onBackPressedDispatcher.addCallback(this) {
            resolve(Outcome.DISMISSED, null, null)
        }

        setContent {
            WitnessedTheme {
                val state by vm.state.collectAsState()
                val app = state.app
                androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().background(Bg)) {
                    if (app != null) {
                        InterventionWall(
                            mode = app.mode,
                            tone = app.tone,
                            promise = app.promiseText,
                        ) { outcome, reasonCategory, reasonText ->
                            resolve(outcome, reasonCategory, reasonText)
                        }
                    }
                }
            }
        }
    }

    private fun resolve(outcome: Outcome, reasonCategory: String?, reasonText: String?) {
        // Mute detection briefly so the watched app returning to the foreground when
        // this wall closes doesn't immediately re-trigger it (the Continue loop).
        InterventionGate.mute()
        // Write on the application scope so it survives finish().
        Graph.applicationScope.launch {
            Graph.repository.logReceipt(outcome, reasonCategory, reasonText)
        }
        setResult(RESULT_OK, Intent().putExtra(EXTRA_OUTCOME, outcome.name))
        // When the wall was triggered automatically by detection, "Back out"
        // should actually leave the watched app — send the user home.
        val fromDetection = intent.getBooleanExtra(EXTRA_FROM_DETECTION, false)
        if (outcome == Outcome.BACKED_OUT && fromDetection) {
            startActivity(
                Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
        finish()
    }

    companion object {
        const val EXTRA_OUTCOME = "outcome"
        const val EXTRA_FROM_DETECTION = "from_detection"
    }
}
