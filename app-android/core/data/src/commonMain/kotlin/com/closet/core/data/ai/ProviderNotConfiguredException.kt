package com.closet.core.data.ai

/** The selected AI provider isn't set up (missing key, Nano not ready). [message] is safe to show to the user. */
class ProviderNotConfiguredException(message: String) : Exception(message)
