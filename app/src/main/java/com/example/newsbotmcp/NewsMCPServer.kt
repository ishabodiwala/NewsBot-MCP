package com.example.newsbotmcp

import android.util.Log
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.streams.*
import io.modelcontextprotocol.kotlin.sdk.*
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import kotlinx.coroutines.*
import kotlinx.io.asSink
import kotlinx.io.buffered
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.io.InputStream
import java.io.OutputStream

class NewsMCPServer(private val newsApiKey: String) {
    // Base URL for the News API
    private val baseUrl = "https://newsapi.org"

    // Create an HTTP client with a default request configuration and JSON content negotiation
    private val httpClient = HttpClient {
        defaultRequest {
            url(baseUrl)
            headers {
                append("Accept", "application/json")
                append("User-Agent", "NewsApiClient/1.0")
            }
            contentType(ContentType.Application.Json)
        }
        // Install content negotiation plugin for JSON serialization/deserialization
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                prettyPrint = true
            })
        }
    }

    // Create the MCP Server instance with a basic implementation
    private val server = Server(
        Implementation(
            name = "news", // Tool name is "news"
            version = "1.0.0" // Version of the implementation
        ),
        ServerOptions(
            capabilities = ServerCapabilities(tools = ServerCapabilities.Tools(listChanged = true))
        )
    ).apply {
        // Register a tool to fetch news by query
        addTool(
            name = "get_news",
            description = """
                Get news articles for a specific query. Input is a search query string.
            """.trimIndent(),
            inputSchema = Tool.Input(
                properties = buildJsonObject {
                    putJsonObject("query") {
                        put("type", "string")
                        put("description", "Search query for news articles")
                    }
                },
            )
        ) { request ->
            try {
                val query = request.arguments["query"]?.jsonPrimitive?.content
                    ?: return@addTool CallToolResult(
                        content = listOf(TextContent("The 'query' parameter is required."))
                    )

                val news = runBlocking { getNews(query) }
                CallToolResult(content = news.map { TextContent(it) })
            } catch (e: Exception) {
                Log.e("TAG", "Error processing request", e)
                CallToolResult(
                    content = listOf(TextContent("Error processing request: ${e.message}"))
                )
            }
        }
    }


    private suspend fun getNews(query: String): List<String> {
        try {
            val response = httpClient.get("/v2/everything") {
                parameter("q", query)
                parameter("apiKey", newsApiKey)
                parameter("sortBy", "publishedAt")
                parameter("language", "en")
                parameter("pageSize", 3)
            }.bodyAsText()

            val newsResponse = Json.decodeFromString<NewsResponse>(response)
            return newsResponse.articles.map { article ->
                buildString {
                    appendLine("Title: ${article.title}")
                    appendLine("Description: ${article.description ?: "No description available"}")
                    appendLine("URL: ${article.url}")
                    appendLine("Published At: ${article.publishedAt}")
                }.trim()
            }
        } catch (e: Exception) {
            Log.e("TAG", "Error fetching news", e)
            return listOf("Error fetching news: ${e.message}")
        }
    }

    fun start(input: InputStream, output: OutputStream) {
        try {
            val transport = StdioServerTransport(
                inputStream = input.asInput().buffered(),
                outputStream = output.asSink().buffered()
            )

            runBlocking {
                server.connect(transport)
                val done = Job()
                server.onClose {
                    done.complete()
                }
                done.join()
            }
            Log.d("TAG", "Server completed")
        } catch (e: Exception) {
            Log.e("TAG", "Server error", e)
            throw e
        }
    }
}

@Serializable
data class NewsResponse(
    val status: String,
    val totalResults: Int,
    val articles: List<Article>
)

@Serializable
data class Article(
    val source: Source,
    val author: String? = null,
    val title: String,
    val description: String? = null,
    val url: String,
    val urlToImage: String? = null,
    val publishedAt: String,
    val content: String? = null
)

@Serializable
data class Source(
    val id: String? = null,
    val name: String
)