package com.handsfree.core.engine

data class ValidationError(val path: String, val reason: String)

data class VerifiedMetadata(val by: String, val date: String, val device: String)

data class ParsedFlow(
    val id: String,
    val network: String,
    val name: String,
    val entry: String,
    val verified: VerifiedMetadata,
    val timeoutMs: Long,
    val maxActions: Int,
    val definition: FlowDefinition,
)

sealed interface FlowParseResult {
    data class Success(val flow: ParsedFlow) : FlowParseResult
    data class Failure(val errors: List<ValidationError>) : FlowParseResult
}
