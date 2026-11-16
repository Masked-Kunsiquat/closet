package com.closet.core.data.ai

/** The model replied, but [ChatResponseParser] couldn't turn the reply into a [ChatResponse]. */
class ChatResponseParseException(cause: Throwable) : Exception(cause.message, cause)
