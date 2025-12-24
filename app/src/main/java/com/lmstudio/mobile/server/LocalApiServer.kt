package com.lmstudio.mobile.server

import android.util.Log
import com.lmstudio.mobile.llm.inference.InferenceManager
import com.lmstudio.mobile.data.repository.ModelRepository
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

@Serializable
data class ChatCompletionRequest(
    val messages: List<ChatMessage>,
    val model: String,
    val stream: Boolean = false,
    val temperature: Double = 0.7,
    val max_tokens: Int? = null
)

@Serializable
data class ChatMessage(
    val role: String,
    val content: String
)

@Serializable
data class ChatCompletionResponse(
    val id: String,
    val choices: List<Choice>,
    val model: String,
    val created: Long
)

@Serializable
data class Choice(
    val index: Int,
    val message: ChatMessage,
    val finish_reason: String? = null
)

@Serializable
data class ModelsResponse(
    val data: List<ModelInfo>
)

@Serializable
data class ModelInfo(
    val id: String,
    val name: String
)

class LocalApiServer(
    private val inferenceManager: InferenceManager,
    private val modelRepository: ModelRepository,
    private val port: Int = 8080
) : NanoHTTPD(port) {

    private val json = Json { ignoreUnknownKeys = true }

    override fun serve(session: IHTTPSession): Response {
        return try {
            when (session.uri) {
                "/v1/chat/completions" -> handleChatCompletion(session)
                "/v1/completions" -> handleCompletion(session)
                "/v1/models" -> handleListModels()
                "/v1/embeddings" -> handleEmbeddings(session)
                else -> newFixedLengthResponse(
                    Response.Status.NOT_FOUND,
                    "application/json",
                    """{"error": "Not found"}"""
                )
            }
        } catch (e: Exception) {
            Log.e("LocalApiServer", "Error handling request", e)
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                "application/json",
                """{"error": "${e.message}"}"""
            )
        }
    }

    private fun handleChatCompletion(session: IHTTPSession): Response {
        if (!inferenceManager.isModelLoaded()) {
            return newFixedLengthResponse(
                Response.Status.BAD_REQUEST,
                "application/json",
                """{"error": "No model loaded"}"""
            )
        }

        val body = readRequestBody(session)
        val request = json.decodeFromString<ChatCompletionRequest>(body)

        if (request.stream) {
            return handleStreamingCompletion(request)
        }

        // Non-streaming response
        val response = runBlocking {
            val messages = request.messages.map {
                com.lmstudio.mobile.domain.model.Message(
                    id = java.util.UUID.randomUUID().toString(),
                    chatId = "",
                    role = when (it.role.lowercase()) {
                        "user" -> com.lmstudio.mobile.domain.model.MessageRole.USER
                        "assistant" -> com.lmstudio.mobile.domain.model.MessageRole.ASSISTANT
                        "system" -> com.lmstudio.mobile.domain.model.MessageRole.SYSTEM
                        else -> com.lmstudio.mobile.domain.model.MessageRole.USER
                    },
                    content = it.content,
                    timestamp = System.currentTimeMillis()
                )
            }

            var assistantContent = ""
            inferenceManager.generateCompletion(
                messages = messages,
                onToken = { token -> assistantContent += token },
                onComplete = {}
            )

            // Wait for completion (simplified - in production use proper synchronization)
            Thread.sleep(1000)

            ChatCompletionResponse(
                id = java.util.UUID.randomUUID().toString(),
                choices = listOf(
                    Choice(
                        index = 0,
                        message = ChatMessage(
                            role = "assistant",
                            content = assistantContent
                        ),
                        finish_reason = "stop"
                    )
                ),
                model = request.model,
                created = System.currentTimeMillis() / 1000
            )
        }

        return newFixedLengthResponse(
            Response.Status.OK,
            "application/json",
            json.encodeToString(response)
        )
    }

    private fun handleStreamingCompletion(request: ChatCompletionRequest): Response {
        // Streaming implementation would use Server-Sent Events (SSE)
        // For now, return a simple non-streaming response
        val messages = request.messages.map {
            com.lmstudio.mobile.domain.model.Message(
                id = java.util.UUID.randomUUID().toString(),
                chatId = "",
                role = when (it.role.lowercase()) {
                    "user" -> com.lmstudio.mobile.domain.model.MessageRole.USER
                    "assistant" -> com.lmstudio.mobile.domain.model.MessageRole.ASSISTANT
                    "system" -> com.lmstudio.mobile.domain.model.MessageRole.SYSTEM
                    else -> com.lmstudio.mobile.domain.model.MessageRole.USER
                },
                content = it.content,
                timestamp = System.currentTimeMillis()
            )
        }

        var assistantContent = ""
        runBlocking {
            inferenceManager.generateCompletion(
                messages = messages,
                onToken = { token -> assistantContent += token },
                onComplete = {}
            )
            Thread.sleep(1000) // Wait for completion
        }

        val response = ChatCompletionResponse(
            id = java.util.UUID.randomUUID().toString(),
            choices = listOf(
                Choice(
                    index = 0,
                    message = ChatMessage(
                        role = "assistant",
                        content = assistantContent
                    ),
                    finish_reason = "stop"
                )
            ),
            model = request.model,
            created = System.currentTimeMillis() / 1000
        )

        return newFixedLengthResponse(
            Response.Status.OK,
            "application/json",
            json.encodeToString(response)
        )
    }

    private fun handleListModels(): Response {
        val models = runBlocking {
            var result: List<ModelInfo> = emptyList()
            modelRepository.getAllModels().collect { modelList ->
                result = modelList.map { ModelInfo(it.id, it.name) }
            }
            result
        }

        val response = ModelsResponse(data = models)
        return newFixedLengthResponse(
            Response.Status.OK,
            "application/json",
            json.encodeToString(response)
        )
    }

    private fun handleCompletion(session: IHTTPSession): Response {
        // Similar to chat completion but for text completion
        return handleChatCompletion(session)
    }

    private fun handleEmbeddings(session: IHTTPSession): Response {
        return newFixedLengthResponse(
            Response.Status.NOT_IMPLEMENTED,
            "application/json",
            """{"error": "Embeddings not yet implemented"}"""
        )
    }

    private fun readRequestBody(session: IHTTPSession): String {
        val contentLength = session.headers["content-length"]?.toInt() ?: 0
        val buffer = ByteArray(contentLength)
        session.inputStream.read(buffer)
        return String(buffer)
    }

    fun getServerUrl(): String = "http://localhost:$port"
}

