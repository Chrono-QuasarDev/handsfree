package com.handsfree.core.engine

/** Extended in E5 with optional [expectOption] for on-screen option matching. */
data class Step(
    val expectText: String = "",
    val tapLabel: String = "",
    val expectOption: String? = null,
    val typeValue: String? = null,
)

data class FlowDefinition(val steps: List<Step>)
