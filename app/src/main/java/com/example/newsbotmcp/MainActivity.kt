package com.example.newsbotmcp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.modelcontextprotocol.kotlin.sdk.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StdioClientTransport
import kotlinx.coroutines.*
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.PipedInputStream
import java.io.PipedOutputStream

class MainActivity : ComponentActivity() {
    private val newsApiKey = "Your_News_API_Key_Here" // Replace with your actual News API key
    private val openAIApiKey = "Your_OpenAI_API_Key_Here" // Replace with your actual OpenAI API key
    private val newsServer = NewsMCPServer(newsApiKey, openAIApiKey)

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
                        NewsApp(newsServer)
                    }

                }
            }
        }
    }
}

@Composable
fun NewsApp(newsServer: NewsMCPServer) {
    var newsItems by remember { mutableStateOf<List<String?>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val topic = remember { mutableStateOf("") }
    Column(modifier = Modifier.padding(16.dp)) {
        Box(modifier = Modifier
            .weight(2f)
            .align(Alignment.CenterHorizontally)) {
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
                                // Create pipes for communication
                                val serverInput = PipedInputStream()
                                val serverOutput = PipedOutputStream()
                                val clientInput = PipedInputStream()
                                val clientOutput = PipedOutputStream()

                                // Connect the pipes
                                serverInput.connect(clientOutput)
                                clientInput.connect(serverOutput)

                                // Start the server in a separate coroutine
                                val serverJob = launch {
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

                                try {
                                    client.connect(transport)

                                    val newsResult = client.callTool(
                                        CallToolRequest(
                                            name = "get_news",
                                            arguments = JsonObject(
                                                mapOf(
                                                    "query" to JsonPrimitive(
                                                        topic.value
                                                    )
                                                )
                                            )
                                        )
                                    )?.content?.map { if (it is TextContent) it.text else it.toString() }

                                    withContext(Dispatchers.Main) {
                                        newsItems = newsResult ?: emptyList()
                                    }
                                } finally {
                                    try {
                                        serverJob.cancel()
                                    } catch (_: Exception) {
                                    }
                                    try {
                                        serverInput.close()
                                    } catch (_: Exception) {
                                    }
                                    try {
                                        serverOutput.close()
                                    } catch (_: Exception) {
                                    }
                                    try {
                                        clientInput.close()
                                    } catch (_: Exception) {
                                    }
                                    try {
                                        clientOutput.close()
                                    } catch (_: Exception) {
                                    }
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
                enabled = topic.value.isNotBlank(),
            ) {
                Text(text = "Search")
            }
        }


    }


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
                    maxLines = 3,
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
            val (field, value) = line.split(":", limit = 2)
            field.trim() to value.trim()
        }
    
    return ArticleInfo(
        title = fieldMap["Title"] ?: "",
        summary = fieldMap["Summary"],
        url = fieldMap["URL"] ?: "",
        publishedAt = fieldMap["Published At"] ?: ""
    ).also {
        Log.d("TAG", "Parsed article info: $it")
    }
}

