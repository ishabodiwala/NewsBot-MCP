package com.example.newsbotmcp

import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.http.Timeout
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import com.aallam.openai.client.OpenAIConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.seconds

class ChatGPTService(private val apiKey: String) {
    private val config = OpenAIConfig(
        token = apiKey,
        timeout = Timeout(
            request = 60.seconds,
            connect = 60.seconds,
            socket = 60.seconds
        )
    )
    
    private val openAI = OpenAI(config)

    private val maxRetries = 3
    private val initialDelay = 1000L // 1 second

    suspend fun summarizeText(text: String): String = withContext(Dispatchers.IO) {
        var retryCount = 0
        var delayTime = initialDelay

        while (retryCount < maxRetries) {
            try {
                val prompt = """
                    Please provide a concise summary of the following news article in 2-3 sentences:
                    
                    ${text}
                """.trimIndent()

                val request = ChatCompletionRequest(
                    model = ModelId("gpt-3.5-turbo"),
                    messages = listOf(
                        ChatMessage(
                            role = ChatRole.User,
                            content = prompt
                        )
                    ),
                    maxCompletionTokens = 150
                )

                val completion = openAI.chatCompletion(request)
                return@withContext completion.choices.first().message.content ?: "Unable to generate summary"
            } catch (e: Exception) {
                retryCount++
                if (retryCount == maxRetries) {
                    return@withContext "Error generating summary: ${e.message}"
                }
                delay(delayTime)
                delayTime *= 2
            }
        }
        "Error generating summary after $maxRetries attempts"
    }
} 