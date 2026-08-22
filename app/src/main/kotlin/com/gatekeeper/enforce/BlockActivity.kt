package com.gatekeeper.enforce

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gatekeeper.GatekeeperApp
import com.gatekeeper.model.DenialReason
import com.gatekeeper.ui.GatekeeperTheme
import com.gatekeeper.ui.NegotiationScreen
import com.gatekeeper.ui.NegotiationViewModel

class BlockActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val appId = intent.getStringExtra(EXTRA_APP_ID) ?: ""
        val denialReasonName = intent.getStringExtra(EXTRA_DENIAL_REASON)
        val denialReason = denialReasonName?.let { DenialReason.valueOf(it) }
        val retryAfterMinutes = intent.getIntExtra(EXTRA_RETRY_AFTER_MINUTES, -1).takeIf { it >= 0 }

        val appContainer = (application as GatekeeperApp).container

        // targetSdk 36 dispatches Back through OnBackPressedDispatcher; overriding the
        // deprecated onBackPressed() never runs, which let Back silently bypass the blocker.
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = returnToHomeScreen()
        })

        setContent {
            GatekeeperTheme {
                val viewModel: NegotiationViewModel = viewModel(
                    factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                            return NegotiationViewModel(appContainer) as T
                        }
                    }
                )

                val uiState by viewModel.uiState.collectAsState()

                androidx.compose.runtime.LaunchedEffect(appId) {
                    viewModel.initialize(appId, denialReason, retryAfterMinutes)
                }

                NegotiationScreen(
                    uiState = uiState,
                    onSendPlea = { plea -> viewModel.sendPlea(plea) },
                    onAcceptOffer = {
                        viewModel.acceptOffer {
                            finish()
                        }
                    },
                    onKeepNegotiating = { viewModel.keepNegotiating() },
                    onNeverMind = {
                        returnToHomeScreen()
                    }
                )
            }
        }
    }

    private fun returnToHomeScreen() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
        finish()
    }

    companion object {
        const val EXTRA_APP_ID = "extra_app_id"
        const val EXTRA_DENIAL_REASON = "extra_denial_reason"
        const val EXTRA_RETRY_AFTER_MINUTES = "extra_retry_after_minutes"

        fun launch(
            context: Context,
            appId: String,
            denialReason: DenialReason? = null,
            retryAfterMinutes: Int? = null
        ) {
            val intent = Intent(context, BlockActivity::class.java).apply {
                putExtra(EXTRA_APP_ID, appId)
                if (denialReason != null) {
                    putExtra(EXTRA_DENIAL_REASON, denialReason.name)
                }
                if (retryAfterMinutes != null) {
                    putExtra(EXTRA_RETRY_AFTER_MINUTES, retryAfterMinutes)
                }
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TASK or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            }
            context.startActivity(intent)
        }
    }
}
