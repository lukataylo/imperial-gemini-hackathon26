package com.crusty.llm

import android.content.Context
import android.util.Log
import com.crusty.data.LedgerRepository
import com.crusty.data.SettingsRepository
import com.crusty.model.AccessMode
import com.crusty.model.LedgerSnapshot
import com.crusty.model.Proposal
import com.crusty.model.Rules
import com.crusty.model.Verdict
import com.crusty.policy.PolicyEngine
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.tool
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed interface EngineState {
    data object Uninitialized : EngineState
    data object Initializing : EngineState
    data class Ready(val backend: String, val modelPath: String) : EngineState
    data object NoModel : EngineState
    data class Error(val message: String) : EngineState
}

sealed interface NegotiationEvent {
    data class Token(val text: String) : NegotiationEvent
    data class Proposed(val proposal: Proposal) : NegotiationEvent
    data class Error(val message: String) : NegotiationEvent
    data object Finished : NegotiationEvent
}

interface NegotiationSession : AutoCloseable {
    fun sendPlea(plea: String): Flow<NegotiationEvent>
}

class InferenceManager(
    private val context: Context,
    private val modelProvider: ModelProvider,
    private val policyEngine: PolicyEngine,
    private val ledgerRepository: LedgerRepository,
    private val settingsRepository: SettingsRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) {

    private val tag = "InferenceManager"
    private val mutex = Mutex()
    private var engine: Engine? = null

    private val _engineState = MutableStateFlow<EngineState>(EngineState.Uninitialized)
    val engineState: StateFlow<EngineState> = _engineState.asStateFlow()

    suspend fun initialize() = withContext(dispatcher) {
        mutex.withLock {
            if (_engineState.value is EngineState.Ready || _engineState.value is EngineState.Initializing) {
                return@withContext
            }

            _engineState.value = EngineState.Initializing
            val modelFile = modelProvider.getModelFile()

            if (modelFile == null || !modelFile.exists()) {
                Log.w(tag, "No LiteRT model file found. Entering simulated mode.")
                _engineState.value = EngineState.NoModel
                return@withContext
            }

            try {
                Log.i(tag, "Initializing Engine with model: ${modelFile.absolutePath}")
                var loadedBackend = "GPU"
                val engineInstance = try {
                    val config = EngineConfig(
                        modelPath = modelFile.absolutePath,
                        backend = Backend.GPU(),
                        cacheDir = context.cacheDir.path
                    )
                    Engine(config).also { it.initialize() }
                } catch (e: Exception) {
                    Log.w(tag, "GPU initialization failed, falling back to CPU: ${e.message}")
                    loadedBackend = "CPU"
                    val cpuConfig = EngineConfig(
                        modelPath = modelFile.absolutePath,
                        backend = Backend.CPU(),
                        cacheDir = context.cacheDir.path
                    )
                    Engine(cpuConfig).also { it.initialize() }
                }

                engine = engineInstance
                _engineState.value = EngineState.Ready(backend = loadedBackend, modelPath = modelFile.absolutePath)
                Log.i(tag, "Inference engine initialized successfully on $loadedBackend")
            } catch (e: Exception) {
                Log.e(tag, "Failed to initialize engine: ${e.message}", e)
                _engineState.value = EngineState.Error(e.message ?: "Model load failed")
            }
        }
    }

    suspend fun createNegotiationSession(
        appId: String,
        appName: String,
        now: Long
    ): NegotiationSession = withContext(dispatcher) {
        val snapshot = ledgerRepository.getSnapshot(appId, now)
        val rules = settingsRepository.getRules()
        val systemPrompt = PromptBuilder.buildSystemPrompt(appName, snapshot, rules, now)

        val currentEngine = engine
        if (currentEngine != null && _engineState.value is EngineState.Ready) {
            try {
                val conversationConfig = ConversationConfig(
                    systemInstruction = Contents.of(systemPrompt),
                    samplerConfig = SamplerConfig(topK = 40, topP = 0.9, temperature = 0.35),
                    tools = listOf(tool(CrustyTools())),
                    automaticToolCalling = false
                )
                val conversation = currentEngine.createConversation(conversationConfig)
                return@withContext RealNegotiationSession(conversation)
            } catch (e: Exception) {
                Log.e(tag, "Failed to create real conversation, falling back to simulated: ${e.message}")
            }
        }

        // Simulated / Fallback Session for when weights are downloading or running without GPU
        SimulatedNegotiationSession(appName, snapshot, rules)
    }

    private class RealNegotiationSession(
        private val conversation: Conversation
    ) : NegotiationSession {
        override fun sendPlea(plea: String): Flow<NegotiationEvent> = flow {
            var emittedProposal = false
            try {
                conversation.sendMessageAsync(Contents.of(plea)).collect { responseMessage ->
                    // Prose arrives in `contents`. Note Message.toString() renders ONLY contents —
                    // the tool call lives in `toolCalls` and is invisible to it.
                    val text = responseMessage.contents.toString()
                    // isNotEmpty, not isNotBlank: a whitespace-only delta is a real space and
                    // dropping it runs words together on screen.
                    if (text.isNotEmpty()) {
                        emit(NegotiationEvent.Token(text))
                    }

                    // The actual proposal. automaticToolCalling = false means the library hands
                    // us the call instead of executing it — typed arguments, no regex needed.
                    if (!emittedProposal) {
                        val call = responseMessage.toolCalls.firstOrNull {
                            it.name == "propose_access" || it.name == "proposeAccess"
                        }
                        if (call != null) {
                            emittedProposal = true
                            emit(NegotiationEvent.Proposed(proposalFromToolCall(call.arguments)))
                        }
                    }
                }
            } catch (e: Exception) {
                // Never fake a model reply — a native failure must not look like a negotiation.
                Log.e("InferenceManager", "Inference failed", e)
                emit(NegotiationEvent.Error(e.message ?: "on-device model failed"))
            }

            emit(NegotiationEvent.Finished)
        }.flowOn(Dispatchers.Default)

        override fun close() {
            try {
                conversation.close()
            } catch (_: Exception) {}
        }
    }

    private class SimulatedNegotiationSession(
        private val appName: String,
        private val snapshot: LedgerSnapshot,
        private val rules: Rules
    ) : NegotiationSession {
        private var turn = 0

        override fun sendPlea(plea: String): Flow<NegotiationEvent> = flow {
            turn++
            val lower = plea.lowercase().trim()

            val isAdversarial = lower.contains("system:") || lower.contains("ignore") ||
                    lower.contains("developer") || lower.contains("override")

            if (isAdversarial) {
                streamTokens("Not a reason. Why do you want to open $appName?")
                emit(NegotiationEvent.Finished)
                return@flow
            }

            // Judge the plea on what it actually says. Previously turn 1 was forced to be
            // "vague", so a specific reason on the first message got a nonsense brush-off.
            val vagueMarkers = listOf(
                "just check", "just look", "just a", "just want", "quick", "bored",
                "a sec", "a minute", "nothing", "dunno", "idk", "see what", "scroll"
            )
            val specificMarkers = listOf(
                "msg", "message", "reply", "text", "dm", "send", "tell", "ask",
                "friend", "mum", "mom", "dad", "sister", "brother", "urgent",
                "address", "order", "meeting", "meet", "tomorrow", "tonight",
                "post", "event", "work", "email", "call", "photo", "booking"
            )
            val looksVague = vagueMarkers.any { lower.contains(it) }
            val looksSpecific = specificMarkers.any { lower.contains(it) }
            // Too short to contain a real reason, unless it names something concrete.
            val tooShort = lower.split(" ").size < 3 && !looksSpecific

            // Push back once on the opening move unless they were already concrete.
            val pushBack = (looksVague || tooShort || (turn == 1 && !looksSpecific))

            if (pushBack) {
                val reply = when {
                    snapshot.lastGrant?.honoured == false ->
                        "Last time you said you'd be quick and stayed the full " +
                        "${snapshot.lastGrant.grantedMinutes} minutes. What's different now?"
                    lower.contains("msg") || lower.contains("message") ||
                        lower.contains("text") || lower.contains("dm") ->
                        "Message who, about what?"
                    lower.contains("check") || lower.contains("look") || lower.contains("see") ->
                        "Check what, specifically?"
                    else ->
                        "That could mean anything. What exactly do you need to do in $appName?"
                }
                streamTokens(reply)
                emit(NegotiationEvent.Finished)
                return@flow
            }

            val brokePromise = snapshot.lastGrant?.honoured == false
            val proposedMinutes = if (brokePromise) 8 else 10
            val rationaleText = if (brokePromise) {
                "$proposedMinutes minutes, grayscale — less than last time, since you overran."
            } else {
                "$proposedMinutes minutes in grayscale. Enough to do what you came for."
            }

            streamTokens(rationaleText)

            val proposal = Proposal(
                verdict = Verdict.COUNTER,
                minutes = proposedMinutes,
                mode = AccessMode.GRAYSCALE,
                rationale = rationaleText,
                promise = plea.take(100)
            )
            emit(NegotiationEvent.Proposed(proposal))
            emit(NegotiationEvent.Finished)
        }.flowOn(Dispatchers.Default)

        private suspend fun kotlinx.coroutines.flow.FlowCollector<NegotiationEvent>.streamTokens(text: String) {
            val words = text.split(" ")
            for (i in words.indices) {
                val word = if (i == words.size - 1) words[i] else "${words[i]} "
                emit(NegotiationEvent.Token(word))
                delay(35)
            }
        }

        override fun close() {}
    }

    companion object {
        /** Build a Proposal from LiteRT-LM's typed tool arguments. */
        fun proposalFromToolCall(args: Map<String, Any?>): Proposal {
            fun str(k: String) = args[k]?.toString()?.trim().orEmpty()

            // The model can genuinely deny; the old text parser hardcoded COUNTER, which meant
            // PolicyEngine's "never upgrade a verdict" guard could never fire from the LLM path.
            val verdict = when (str("verdict").lowercase()) {
                "grant" -> Verdict.GRANT
                "deny" -> Verdict.DENY
                "conditional" -> Verdict.CONDITIONAL
                else -> Verdict.COUNTER
            }
            val minutes = when (val m = args["minutes"]) {
                is Number -> m.toInt()
                else -> m?.toString()?.trim()?.toIntOrNull() ?: 0
            }
            val mode = when (str("mode").lowercase()) {
                "full" -> AccessMode.FULL
                "delayed" -> AccessMode.DELAYED
                "no_feed" -> AccessMode.NO_FEED
                "search_only" -> AccessMode.SEARCH_ONLY
                else -> AccessMode.GRAYSCALE
            }
            return Proposal(
                verdict = verdict,
                minutes = minutes,
                mode = mode,
                rationale = str("rationale"),
                promise = str("promise"),
            )
        }

        fun parseProposalFromText(text: String): Proposal? {
            val regex = Regex("""propose_access\s*\((.*?)\)""", RegexOption.DOT_MATCHES_ALL)
            val match = regex.find(text) ?: return null
            val content = match.groupValues[1]

            var minutes = 10
            var mode = AccessMode.GRAYSCALE
            var verdict = Verdict.COUNTER
            var rationale = "Grant proposed"
            var promise = ""

            val minMatch = Regex("""minutes\s*=\s*(\d+)""").find(content)
            if (minMatch != null) {
                minutes = minMatch.groupValues[1].toIntOrNull() ?: 10
            }

            if (content.contains("mode=full") || content.contains("mode=\"full\"")) {
                mode = AccessMode.FULL
            } else if (content.contains("mode=delayed")) {
                mode = AccessMode.DELAYED
            }

            val ratMatch = Regex("""rationale\s*=\s*["']([^"']+)["']""").find(content)
            if (ratMatch != null) {
                rationale = ratMatch.groupValues[1]
            }

            val promMatch = Regex("""promise\s*=\s*["']([^"']+)["']""").find(content)
            if (promMatch != null) {
                promise = promMatch.groupValues[1]
            }

            return Proposal(
                verdict = verdict,
                minutes = minutes,
                mode = mode,
                rationale = rationale,
                promise = promise
            )
        }
    }
}
