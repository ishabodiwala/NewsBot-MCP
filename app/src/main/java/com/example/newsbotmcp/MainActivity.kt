package com.example.newsbotmcp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.MessageParam
import com.anthropic.models.messages.Model
import com.anthropic.models.messages.Tool
import com.anthropic.models.messages.ToolUnion
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StdioClientTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlinx.serialization.json.JsonObject
import java.io.PipedInputStream
import java.io.PipedOutputStream
import kotlin.jvm.optionals.getOrNull

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Scaffold(topBar = {
                    TopAppBar(
                        title =
                        { Text("News Bot App") },
                        colors = TopAppBarDefaults.topAppBarColors().copy(
                            containerColor = MaterialTheme.colorScheme.primary,
                            titleContentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }) { padding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                    ) {
                        NewsBotApp()
                    }

                }
            }
        }


    }
}


@Composable
fun NewsBotApp() {
    val newsServer = NewsMCPServer()
    var newsItems by remember { mutableStateOf<List<String?>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val topic = remember { mutableStateOf("") }
    var mcpConfig by remember { mutableStateOf<MCPConfig?>(null) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> {
                    scope.launch {
                        mcpConfig = setupMCPServerAndClient(newsServer)
                    }
                }

                Lifecycle.Event.ON_DESTROY -> {
                    scope.launch {
                        mcpConfig?.let { cleanupResources(it) }
                    }
                }

                else -> Unit
            }
        }

        lifecycle.addObserver(observer)

        onDispose {
            lifecycle.removeObserver(observer)
        }
    }

    Column(modifier = Modifier.padding(16.dp)) {
        Box(
            modifier = Modifier
                .weight(2f)
                .align(Alignment.CenterHorizontally)
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator()
                }

                error != null -> {
                    Text(
                        text = "Error: $error",
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }

                newsItems.isEmpty() -> {
                    Text("No news to display")
                }

                else -> {
                    NewsList(newsItems = newsItems)
                }
            }
        }
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                maxLines = 1,
                value = topic.value,
                onValueChange = { topic.value = it },
                placeholder = { Text(text = "Search") },
                modifier =
                Modifier
                    .weight(1f)
                    .border(
                        BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(8.dp)
                    )
            )
            Spacer(modifier = Modifier.width(3.dp))
            Button(
                onClick = {
                    scope.launch {
                        isLoading = true
                        error = null
                        try {
                            withContext(Dispatchers.IO) {
                                val result = searchNews(topic.value, mcpConfig = mcpConfig!!)
                                withContext(Dispatchers.Main) {
                                    newsItems = result
                                }
                            }
                        } catch (e: Exception) {
                            error = e.message
                            Log.e("TAG", "Error fetching news", e)
                        } finally {
                            isLoading = false
                        }
                    }
                },
                enabled = topic.value.isNotBlank() && mcpConfig != null,
            ) {
                Text(text = "Search")
            }
        }
    }
}

/**
 * Searches for news articles based on the provided query using the MCP server.
 */
private suspend fun searchNews(query: String, mcpConfig: MCPConfig): List<String> {
    try {
        // Get the list of available tools
        val tools = getAvailableTools(mcpConfig.client)

        // Create the Anthropic client and process the user query
        return processUserQuery(query, tools, mcpConfig)
    } catch (e: Exception) {
        Log.e("TAG", "Error during search", e)
        return emptyList()
    }
}

/**
 * Sets up the MCP server and client with pipes for communication.
 */
private suspend fun setupMCPServerAndClient(newsServer: NewsMCPServer): MCPConfig {
    // Create pipes for communication
    val serverInput = PipedInputStream()
    val serverOutput = PipedOutputStream()
    val clientInput = PipedInputStream()
    val clientOutput = PipedOutputStream()

    // Connect the pipes
    withContext(Dispatchers.IO) {
        serverInput.connect(clientOutput)
        clientInput.connect(serverOutput)
    }

    // Start the server in a separate coroutine
    val serverJob = CoroutineScope(Dispatchers.IO).launch {
        try {
            newsServer.start(serverInput, serverOutput)
        } catch (e: Exception) {
            Log.e("TAG", "Server error", e)
        }
    }

    // Create client transport
    val transport = StdioClientTransport(
        input = clientInput.asSource().buffered(),
        output = clientOutput.asSink().buffered()
    )

    val client = Client(
        clientInfo = Implementation(
            name = "news-client",
            version = "1.0.0"
        ),
    )

    client.connect(transport)

    val anthropicClient: AnthropicClient = AnthropicOkHttpClient.builder()
        .apiKey(Config.ANTHROPIC_API_KEY)
        .build()

    val messageParamsBuilder: MessageCreateParams.Builder = MessageCreateParams.builder()
        .model(Model.CLAUDE_3_5_SONNET_20241022)
        .maxTokens(1024)


    // Return the setup components
    return MCPConfig(
        client = client,
        transport = transport,
        serverJob = serverJob,
        pipes = listOf(serverInput, serverOutput, clientInput, clientOutput),
        anthropicClient = anthropicClient,
        messageParamsBuilder = messageParamsBuilder
    )
}

/**
 * Data class holding the client and server setup components.
 */
private data class MCPConfig(
    val client: Client,
    val transport: StdioClientTransport,
    val serverJob: Job,
    val pipes: List<Any>,
    val anthropicClient: AnthropicClient,
    val messageParamsBuilder: MessageCreateParams.Builder
)

/**
 * Gets the list of available tools from the MCP client.
 */
private suspend fun getAvailableTools(client: Client): List<ToolUnion> {
    val toolsResult = client.listTools()
    return toolsResult?.tools?.map { tool ->
        ToolUnion.ofTool(
            Tool.builder()
                .name(tool.name)
                .description(tool.description ?: "")
                .inputSchema(
                    Tool.InputSchema.builder()
                        .type(JsonValue.from(tool.inputSchema.type))
                        .properties(tool.inputSchema.properties.toJsonValue())
                        .putAdditionalProperty(
                            "required",
                            JsonValue.from(tool.inputSchema.required)
                        )
                        .build()
                )
                .build()
        )
    } ?: emptyList()
}

/**
 * Processes the user query using Anthropic API and MCP tools.
 */
private suspend fun processUserQuery(
    query: String,
    tools: List<ToolUnion>,
    mcpConfig: MCPConfig
): List<String> {

    val messages = mutableListOf(
        MessageParam.builder()
            .role(MessageParam.Role.USER)
            .content(query)
            .build()
    )

    // Store extracted news items
    val newsItems = mutableListOf<String>()
    // Store raw news data for summarization
    val rawNewsData = mutableListOf<String>()

    // Send the query to the Anthropic model and get the response
    val response = mcpConfig.anthropicClient.messages().create(
        mcpConfig.messageParamsBuilder
            .messages(messages)
            .tools(tools)
            .build()
    )

    response.content().forEach { content ->
        when {
            // If the response indicates a tool use, process it further
            content.isToolUse() -> {
                val toolName = content.toolUse().get().name()
                val toolArgs = content.toolUse().get()._input()
                    .convert(object : TypeReference<Map<String, JsonValue>>() {})

                // Call the tool with provided arguments
                val result = mcpConfig.client.callTool(
                    name = toolName,
                    arguments = toolArgs ?: emptyMap()
                )

                // Extract raw news items for further processing
                if (toolName == "get_news" && result != null) {
                    val rawItems = result.content
                        .filterIsInstance<TextContent>()
                        .map { it.text ?: "" }

                    rawNewsData.addAll(rawItems)
                }

                // Add the tool result message to the conversation
                messages.add(
                    MessageParam.builder()
                        .role(MessageParam.Role.USER)
                        .content(
                            """
                            "type": "tool_result",
                            "tool_name": $toolName,
                            "result": ${result?.content?.joinToString("\n") { (it as TextContent).text ?: "" }}
                        """.trimIndent()
                        )
                        .build()
                )
            }
        }
    }

    // Generate summaries from the raw news data using Claude
    if (rawNewsData.isNotEmpty()) {
        val summarizationPrompt = """
            For each of the following news articles, please provide a more concise summary.
            Keep the exact same format with Title, Summary, URL, and Published At fields.
            Make sure to preserve the original title exactly as shown.
            Format each article as:
            
            Title: [original title]
            Summary: [your concise summary]
            URL: [original URL]
            Published At: [original date]
            
            Here are the articles:
            ${rawNewsData.joinToString("\n\n")}
        """.trimIndent()

        // Add the summarization request
        messages.add(
            MessageParam.builder()
                .role(MessageParam.Role.USER)
                .content(summarizationPrompt)
                .build()
        )

        // Get improved summaries from Claude
        val summaryResponse = mcpConfig.anthropicClient.messages().create(
            mcpConfig.messageParamsBuilder
                .messages(messages)
                .build()
        )

        // Extract the generated summaries
        val summaryText = summaryResponse.content().firstOrNull()?.text()?.getOrNull()?.text() ?: ""

        // Split the summary text into individual articles and ensure proper formatting
        val improvedArticles = summaryText
            .split("\n\n")
            .filter { it.contains("Title:") && it.contains("Summary:") }
            .map { it.trim() }

        if (improvedArticles.isNotEmpty()) {
            return improvedArticles
        }
    }

    // Extract news items from tool results in messages if not already extracted
    if (rawNewsData.isNotEmpty()) {
        newsItems.addAll(rawNewsData)
    }

    // If no news items were found anywhere, display a message
    return newsItems.ifEmpty {
        listOf("No news articles found.")
    }
}

/**
 * Cleans up resources after the search operation.
 */
private suspend fun cleanupResources(mcpConfig: MCPConfig) {
    try {
        mcpConfig.serverJob.cancel()
    } catch (_: Exception) {
    }

    // Close all pipes
    mcpConfig.pipes.forEach { pipe ->
        try {
            when (pipe) {
                is PipedInputStream -> pipe.close()
                is PipedOutputStream -> pipe.close()
            }
        } catch (_: Exception) {
        }
    }

    try {
        mcpConfig.client.close()
    } catch (_: Exception) {
    }

    try {
        mcpConfig.transport.close()
    } catch (_: Exception) {
    }

    try {
        mcpConfig.anthropicClient.close()
    } catch (_: Exception) {
    }

}

/**
 * Extension function to convert JsonObject to JsonValue for Anthropic API.
 */
private fun JsonObject.toJsonValue(): JsonValue {
    val mapper = ObjectMapper()
    val node = mapper.readTree(this.toString())
    return JsonValue.fromJsonNode(node)
}

@Composable
fun NewsList(newsItems: List<String?>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        items(newsItems) { newsItem ->
            if (newsItem != null) {
                NewsCard(newsItem = newsItem)
            }
        }
    }
}

@Composable
fun NewsCard(newsItem: String) {
    val articleInfo = parseNewsItem(newsItem)
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Title
            Text(
                text = articleInfo.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 8.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // Summary
            if (!articleInfo.summary.isNullOrBlank()) {
                Text(
                    text = articleInfo.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp),
                    maxLines = 4,  // Show more lines for the summary
                    overflow = TextOverflow.Ellipsis
                )
            }

            // URL and Published Date
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // URL Button
                TextButton(
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(articleInfo.url))
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Log.e("NewsCard", "Error opening URL", e)
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("Read More")
                }

                // Published Date
                Text(
                    text = formatPublishedDate(articleInfo.publishedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun formatPublishedDate(dateStr: String): String {
    return try {
        dateStr.substringBefore('T').replace('-', '/')
    } catch (e: Exception) {
        dateStr
    }
}

private data class ArticleInfo(
    val title: String,
    val summary: String?,
    val url: String,
    val publishedAt: String
)

private fun parseNewsItem(newsItem: String): ArticleInfo {
    // Split the input into lines and create a map of field names to values
    val fieldMap = newsItem.lines()
        .filter { it.contains(":") }
        .associate { line ->
            val parts = line.split(":", limit = 2)
            val key = parts[0].trim()
            val value = if (parts.size > 1) parts[1].trim() else ""
            key to value
        }

    val title = fieldMap["Title"] ?: ""
    val summary = fieldMap["Summary"] ?: ""
    val url = fieldMap["URL"] ?: ""
    val publishedAt = fieldMap["Published At"] ?: ""

    return ArticleInfo(
        title = title,
        summary = summary,
        url = url,
        publishedAt = publishedAt
    )
}

