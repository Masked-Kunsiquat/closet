package com.closet.core.data.ai.tools

/** The set of [WardrobeTool]s offered to the model. */
class ToolRegistry(val tools: List<WardrobeTool>) {
    val specs: List<ToolSpec> = tools.map { it.spec }
    private val byName: Map<String, WardrobeTool> = tools.associateBy { it.spec.name }

    fun find(name: String): WardrobeTool? = byName[name]
}
