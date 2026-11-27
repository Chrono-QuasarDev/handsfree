package com.handsfree.core.engine

/** v0 shape. Extended by E5 (option matching) and E7 (params). Do not add fields now. */
data class Step(val expectText: String, val tapLabel: String)

data class FlowDefinition(val steps: List<Step>)
