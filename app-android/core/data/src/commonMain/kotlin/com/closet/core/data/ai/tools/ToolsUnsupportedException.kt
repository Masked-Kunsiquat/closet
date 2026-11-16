package com.closet.core.data.ai.tools

/**
 * The selected provider's server rejected tool calling. Callers retry on the RAG path; the message
 * is for logs only and is never shown to the user.
 */
class ToolsUnsupportedException(message: String) : Exception(message)
