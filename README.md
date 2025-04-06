# NewsBot-MCP

## Overview

**NewsBot-MCP** is a Kotlin-based conversational chatbot that delivers real-time news content using the **Model Context Protocol (MCP)**. 

## Features

- Uses MCP for managing conversation context.
- Fetches current news based on user input.
- Automatic news summary generation.

## Setup Instructions

### 1. Clone the Repository

```bash
git clone https://github.com/ishabodiwala/NewsBot-MCP.git
cd NewsBot-MCP
```
### 2. Open the project in Android Studio

### 3. Add Your API Keys

- Obtain a **News API key** from https://newsapi.org
- Obtain an **Anthropic API key** from https://www.anthropic.com/api.
- Open the `Config` file and locate the API key variables.
- Replace the placeholders with your actual keys:
```
const val NEWS_API_KEY = "your_news_api_key" 
const val ANTHROPIC_API_KEY = "your-anthropic-api-key"
```

### 4. Run the Application

## Demo

https://github.com/user-attachments/assets/6b323b20-55c7-4ed3-828f-87947643e836

## Reference

https://github.com/modelcontextprotocol/kotlin-sdk/tree/main/samples/kotlin-mcp-server
