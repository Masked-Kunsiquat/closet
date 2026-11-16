package com.closet.core.data.ai.tools

/** The JSON type of a [ToolParam]. */
enum class ToolParamType { STRING, INTEGER, BOOLEAN, STRING_ARRAY }

/** One parameter of a [ToolSpec]. [enumValues], when set, lists the only accepted values. */
data class ToolParam(
    val name: String,
    val type: ToolParamType,
    val description: String,
    val required: Boolean = false,
    val enumValues: List<String>? = null,
)

/**
 * A provider-neutral tool declaration. [name] is `snake_case` and [description] is at most
 * 180 characters; providers map this to their own function-declaration format.
 */
data class ToolSpec(
    val name: String,
    val description: String,
    val params: List<ToolParam>,
)
