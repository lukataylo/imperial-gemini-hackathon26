package com.gatekeeper.di

import android.content.Context
import com.gatekeeper.data.JsonLedgerRepository
import com.gatekeeper.data.JsonSettingsRepository
import com.gatekeeper.data.LedgerRepository
import com.gatekeeper.data.SettingsRepository
import com.gatekeeper.enforce.DefaultGrantManager
import com.gatekeeper.enforce.GrantManager
import com.gatekeeper.enforce.GrayscaleOverlayManager
import com.gatekeeper.llm.DefaultModelProvider
import com.gatekeeper.llm.InferenceManager
import com.gatekeeper.llm.ModelDownloader
import com.gatekeeper.llm.ModelProvider
import com.gatekeeper.policy.DefaultPolicyEngine
import com.gatekeeper.policy.PolicyEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

interface AppContainer {
    val context: Context
    val applicationScope: CoroutineScope
    val policyEngine: PolicyEngine
    val settingsRepository: SettingsRepository
    val ledgerRepository: LedgerRepository
    val modelProvider: ModelProvider
    val inferenceManager: InferenceManager
    val modelDownloader: ModelDownloader
    val grantManager: GrantManager
    val grayscaleOverlayManager: GrayscaleOverlayManager
}

class DefaultAppContainer(override val context: Context) : AppContainer {
    override val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    override val policyEngine: PolicyEngine by lazy { DefaultPolicyEngine() }
    override val settingsRepository: SettingsRepository by lazy { JsonSettingsRepository(context) }
    override val ledgerRepository: LedgerRepository by lazy { JsonLedgerRepository(context, settingsRepository) }
    override val modelProvider: ModelProvider by lazy { DefaultModelProvider(context, settingsRepository) }
    override val inferenceManager: InferenceManager by lazy { InferenceManager(context, modelProvider, policyEngine, ledgerRepository, settingsRepository) }
    override val modelDownloader: ModelDownloader by lazy { ModelDownloader(context, modelProvider) }
    override val grayscaleOverlayManager: GrayscaleOverlayManager by lazy { GrayscaleOverlayManager(context) }
    override val grantManager: GrantManager by lazy {
        DefaultGrantManager(
            context = context,
            ledgerRepository = ledgerRepository,
            policyEngine = policyEngine,
            settingsRepository = settingsRepository,
            grayscaleOverlayManager = grayscaleOverlayManager,
            scope = applicationScope
        )
    }
}
