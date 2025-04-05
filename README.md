# NewsBot-MCP

## Overview

**NewsBot-MCP** is a Kotlin-based conversational chatbot that delivers real-time news content using the **Model Context Protocol (MCP)**. 

## Features

- Uses MCP for managing conversation context.
- Fetches current news based on user input.
- Automatic news summary generation using OpenAI language model.

## Setup Instructions

### 1. Clone the Repository

```bash
git clone https://github.com/ishabodiwala/NewsBot-MCP.git
cd NewsBot-MCP
```
### 2. Open the project in Android Studio

### 3. Add Your API Keys

- Obtain a **News API key** from [https://newsapi.org](https://newsapi.org).
- Obtain an **OpenAI API key** from [https://platform.openai.com](https://platform.openai.com/account/api-keys).
- Open the `MainActivity.kt` file and locate the API key variables.
- Replace the placeholders with your actual keys:
```
  private val newsApiKey = "Your_Actual_News_API_Key"
  private val openAIApiKey = "Your_Actual_OpenAI_API_Key"
```

### 4.Run the Application

## Demo

https://github.com/user-attachments/assets/6b323b20-55c7-4ed3-828f-87947643e836

## Reference

https://github.com/modelcontextprotocol/kotlin-sdk/tree/main/samples/kotlin-mcp-server
