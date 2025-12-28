package com.lmstudio.mobile.server

import android.util.Log
import com.lmstudio.mobile.llm.inference.InferenceManager
import com.lmstudio.mobile.data.repository.ModelRepository
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.UUID

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
    val created: Long,
    val `object`: String = "chat.completion"
)

@Serializable
data class Choice(
    val index: Int,
    val message: ChatMessage? = null,
    val delta: ChatMessage? = null,
    val finish_reason: String? = null
)

@Serializable
data class ModelsResponse(
    val data: List<ModelInfo>
)

@Serializable
data class ModelInfo(
    val id: String,
    val name: String,
    val `object`: String = "model"
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
                "/v1/models" -> handleListModels()
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

        val domainMessages = request.messages.map {
            com.lmstudio.mobile.domain.model.Message(
                id = UUID.randomUUID().toString(),
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

        if (request.stream) {
            return handleStreamingResponse(domainMessages, request.model)
        }

        var assistantContent = ""
        val completionChannel = Channel<Unit>()
        
        inferenceManager.generateCompletion(
            chatId = "",
            assistantMessageId = "",
            messages = domainMessages,
            onToken = { token -> assistantContent += token },
            onComplete = { _ -> completionChannel.trySend(Unit) }
        )

        runBlocking { completionChannel.receive() }

        val response = ChatCompletionResponse(
            id = UUID.randomUUID().toString(),
            choices = listOf(
                Choice(
                    index = 0,
                    message = ChatMessage(role = "assistant", content = assistantContent),
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

    private fun handleStreamingResponse(messages: List<com.lmstudio.mobile.domain.model.Message>, modelId: String): Response {
        val pipedInput = PipedInputStream()
        val pipedOutput = PipedOutputStream(pipedInput)
        val requestId = UUID.randomUUID().toString()

        Thread {
            try {
                inferenceManager.generateCompletion(
                    chatId = "",
                    assistantMessageId = "",
                    messages = messages,
                    onToken = { token ->
                        val chunk = ChatCompletionResponse(
                            id = requestId,
                            choices = listOf(
                                Choice(
                                    index = 0,
                                    delta = ChatMessage(role = "assistant", content = token),
                                    finish_reason = null
                                )
                            ),
                            model = modelId,
                            created = System.currentTimeMillis() / 1000,
                            `object` = "chat.completion.chunk"
                        )
                        pipedOutput.write("data: ${json.encodeToString(chunk)}\n\n".toByteArray())
                        pipedOutput.flush()
                    },
                    onComplete = { _ ->
                        val finalChunk = ChatCompletionResponse(
                            id = requestId,
                            choices = listOf(
                                Choice(
                                    index = 0,
                                    delta = null,
                                    finish_reason = "stop"
                                )
                            ),
                            model = modelId,
                            created = System.currentTimeMillis() / 1000,
                            `object` = "chat.completion.chunk"
                        )
                        pipedOutput.write("data: ${json.encodeToString(finalChunk)}\n\n".toByteArray())
                        pipedOutput.write("data: [DONE]\n\n".toByteArray())
                        pipedOutput.flush()
                        pipedOutput.close()
                    }
                )
            } catch (e: Exception) {
                Log.e("LocalApiServer", "Streaming error", e)
                try { pipedOutput.close() } catch (ignored: Exception) {}
            }
        }.start()

        return newChunkedResponse(Response.Status.OK, "text/event-stream", pipedInput)
    }

    private fun handleListModels(): Response {
        // Simple list models implementation
        return newFixedLengthResponse(Response.Status.OK, "application/json", """{"data": []}""")
    }

    private fun readRequestBody(session: IHTTPSession): String {
        val map = HashMap<String, String>()
        session.parseBody(map)
        return map["postData"] ?: ""
    }
}
