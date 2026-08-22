package com.gatekeeper.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gatekeeper.data.LedgerRepository
import com.gatekeeper.data.SettingsRepository
import com.gatekeeper.di.AppContainer
import com.gatekeeper.enforce.GrantManager
import com.gatekeeper.llm.InferenceManager
import com.gatekeeper.llm.NegotiationEvent
import com.gatekeeper.llm.NegotiationSession
import com.gatekeeper.model.DenialReason
import com.gatekeeper.model.Grant
import com.gatekeeper.model.LedgerSnapshot
import com.gatekeeper.model.Proposal
import com.gatekeeper.policy.PolicyEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val isUser: Boolean,
    val text: String
)

data class NegotiationUiState(
    val appId: String = "",
    val appName: String = "",
    val todayUsedMinutes: Int = 0,
    val todayBudgetMinutes: Int = 60,
    val messages: List<ChatMessage> = emptyList(),
    val isStreaming: Boolean = false,
    val currentProposal: Proposal? = null,
    val clampedGrant: Grant? = null,
    val denialReason: DenialReason? = null,
    val retryAfterMinutes: Int? = null,
    val lastPlea: String = "",
    val errorMessage: String? = null,
    val isReady: Boolean = false,
)

class NegotiationViewModel(
    private val appContainer: AppContainer
) : ViewModel() {

    private val inferenceManager: InferenceManager = appContainer.inferenceManager
    private val policyEngine: PolicyEngine = appContainer.policyEngine
    private val ledgerRepository: LedgerRepository = appContainer.ledgerRepository
    private val settingsRepository: SettingsRepository = appContainer.settingsRepository
    private val grantManager: GrantManager = appContainer.grantManager

    private val _uiState = MutableStateFlow(NegotiationUiState())
    val uiState: StateFlow<NegotiationUiState> = _uiState.asStateFlow()

    private var negotiationSession: NegotiationSession? = null
    private var streamingJob: Job? = null

    fun initialize(appId: String, denialReason: DenialReason? = null, retryAfterMinutes: Int? = null) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val snapshot = ledgerRepository.getSnapshot(appId, now)
            val appName = settingsRepository.settingsData.value.watchedApps
                .firstOrNull { it.packageName == appId }?.appName ?: appId.substringAfterLast('.')

            val rules = settingsRepository.getRules()
            val dailyBudget = rules.dailyBudgetMinutes[appId] ?: snapshot.todayDailyBudgetMinutes

            _uiState.value = NegotiationUiState(
                appId = appId,
                appName = appName,
                todayUsedMinutes = snapshot.todayUsageMinutes,
                todayBudgetMinutes = dailyBudget,
                denialReason = denialReason,
                retryAfterMinutes = retryAfterMinutes
            )

            if (denialReason == null) {
                try {
                    // Close any previous session first — LaunchedEffect re-runs on rotation and
                    // silently leaked a native Conversation (and its KV cache) each time.
                    negotiationSession?.close()
                    negotiationSession = inferenceManager.createNegotiationSession(appId, appName, now)
                    _uiState.value = _uiState.value.copy(isReady = true)
                } catch (e: Exception) {
                    _uiState.value = _uiState.value.copy(
                        errorMessage = e.message ?: "Could not start the on-device model"
                    )
                }
            }
        }
    }

    fun sendPlea(plea: String) {
        val trimmed = plea.trim()
        if (trimmed.isBlank() || _uiState.value.isStreaming) return

        val userMessage = ChatMessage(isUser = true, text = trimmed)
        val currentMessages = _uiState.value.messages + userMessage

        _uiState.value = _uiState.value.copy(
            messages = currentMessages,
            isStreaming = true,
            currentProposal = null,
            clampedGrant = null,
            lastPlea = trimmed
        )

        streamingJob?.cancel()
        streamingJob = viewModelScope.launch {
            val session = negotiationSession
            if (session == null) {
                // Model still warming. Never leave isStreaming stuck true — that permanently
                // disables Send with no error and no way back except Never mind.
                _uiState.value = _uiState.value.copy(
                    isStreaming = false,
                    errorMessage = "Still waking the model up — try again in a second."
                )
                return@launch
            }
            var streamingAgentMessage: ChatMessage? = null

            session.sendPlea(trimmed).collect { event ->
                when (event) {
                    is NegotiationEvent.Token -> {
                        val currentText = streamingAgentMessage?.text ?: ""
                        val updatedText = currentText + event.text
                        streamingAgentMessage = (streamingAgentMessage
                            ?: ChatMessage(isUser = false, text = "")).copy(text = updatedText)

                        val updatedList = if (_uiState.value.messages.lastOrNull()?.isUser == false) {
                            _uiState.value.messages.dropLast(1) + streamingAgentMessage!!
                        } else {
                            _uiState.value.messages + streamingAgentMessage!!
                        }
                        _uiState.value = _uiState.value.copy(messages = updatedList)
                    }

                    is NegotiationEvent.Proposed -> {
                        val now = System.currentTimeMillis()
                        val snapshot = ledgerRepository.getSnapshot(_uiState.value.appId, now)
                        val rules = settingsRepository.getRules()

                        // Run through deterministic policy clamp
                        val grant = policyEngine.clamp(event.proposal, _uiState.value.appId, now, snapshot, rules)

                        _uiState.value = _uiState.value.copy(
                            currentProposal = event.proposal,
                            clampedGrant = grant
                        )
                    }

                    is NegotiationEvent.Error -> {
                        _uiState.value = _uiState.value.copy(
                            isStreaming = false,
                            errorMessage = event.message
                        )
                    }

                    is NegotiationEvent.Finished -> {
                        _uiState.value = _uiState.value.copy(isStreaming = false)
                    }
                }
            }
        }
    }

    fun acceptOffer(onGranted: () -> Unit) {
        val grant = _uiState.value.clampedGrant ?: return
        viewModelScope.launch {
            grantManager.startGrant(
                grant = grant,
                plea = _uiState.value.lastPlea,
                proposedMinutes = _uiState.value.currentProposal?.minutes ?: grant.minutes
            )
            onGranted()
        }
    }

    fun keepNegotiating() {
        _uiState.value = _uiState.value.copy(
            currentProposal = null,
            clampedGrant = null
        )
    }

    override fun onCleared() {
        super.onCleared()
        negotiationSession?.close()
        streamingJob?.cancel()
    }
}
