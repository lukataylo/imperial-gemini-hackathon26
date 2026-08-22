package com.crusty.di

import android.content.Context
import com.crusty.cloud.UsageInsights
import com.crusty.data.JsonLedgerRepository
import com.crusty.data.JsonSettingsRepository
import com.crusty.data.LedgerRepository
import com.crusty.data.SettingsRepository
import com.crusty.enforce.DefaultGrantManager
import com.crusty.enforce.GrantManager
import com.crusty.enforce.GrayscaleOverlayManager
import com.crusty.llm.DefaultModelProvider
import com.crusty.llm.InferenceManager
import com.crusty.llm.ModelDownloader
import com.crusty.llm.ModelProvider
import com.crusty.policy.DefaultPolicyEngine
import com.crusty.policy.PolicyEngine
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
    val usageInsights: UsageInsights
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
    override val usageInsights: UsageInsights by lazy { UsageInsights() }
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
