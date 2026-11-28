package com.handsfree.core.engine

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

class FlowParser {

    private val json = Json {
        ignoreUnknownKeys = false
        isLenient = false
    }

    fun parse(rawJson: String): FlowParseResult {
        val dto = try {
            json.decodeFromString<FlowJsonDto>(rawJson)
        } catch (e: SerializationException) {
            return FlowParseResult.Failure(
                listOf(ValidationError(path = extractPath(e.message), reason = e.message ?: "Malformed JSON"))
            )
        } catch (e: IllegalArgumentException) {
            return FlowParseResult.Failure(
                listOf(ValidationError(path = "$", reason = e.message ?: "Invalid JSON input"))
            )
        }

        val errors = validate(dto)
        if (errors.isNotEmpty()) {
            return FlowParseResult.Failure(errors)
        }

        val definition = FlowDefinition(
            steps = dto.steps.map { step ->
                Step(
                    expectText = step.expectText ?: "",
                    tapLabel = step.tapLabel ?: "",
                    expectOption = step.expectOption,
                    typeValue = step.typeValue,
                )
            }
        )

        return FlowParseResult.Success(
            ParsedFlow(
                id = dto.id,
                network = dto.network,
                name = dto.name,
                entry = dto.entry,
                verified = VerifiedMetadata(
                    by = dto.verified.by,
                    date = dto.verified.date,
                    device = dto.verified.device,
                ),
                timeoutMs = dto.timeoutMs ?: DEFAULT_TIMEOUT_MS,
                maxActions = dto.maxActions ?: DEFAULT_MAX_ACTIONS,
                definition = definition,
            )
        )
    }

    private fun validate(dto: FlowJsonDto): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()

        if (dto.id.isBlank()) errors += ValidationError("id", "id must not be blank")
        if (dto.network.isBlank()) errors += ValidationError("network", "network must not be blank")
        if (dto.name.isBlank()) errors += ValidationError("name", "name must not be blank")
        if (!DIAL_CODE_REGEX.matches(dto.entry)) {
            errors += ValidationError("entry", "entry must match ^[*#][0-9*#]{1,23}#$")
        }

        if (dto.verified.by.isBlank()) errors += ValidationError("verified.by", "verified.by must not be blank")
        if (dto.verified.date.isBlank()) errors += ValidationError("verified.date", "verified.date must not be blank")
        if (dto.verified.device.isBlank()) errors += ValidationError("verified.device", "verified.device must not be blank")

        dto.timeoutMs?.let { timeout ->
            if (timeout !in MIN_TIMEOUT_MS..MAX_TIMEOUT_MS) {
                errors += ValidationError("timeoutMs", "timeoutMs must be between $MIN_TIMEOUT_MS and $MAX_TIMEOUT_MS")
            }
        }
        dto.maxActions?.let { max ->
            if (max !in MIN_ACTIONS..MAX_ACTIONS) {
                errors += ValidationError("maxActions", "maxActions must be between $MIN_ACTIONS and $MAX_ACTIONS")
            }
        }

        if (dto.steps.isEmpty()) {
            errors += ValidationError("steps", "steps must not be empty")
        } else if (dto.steps.size > MAX_STEPS) {
            errors += ValidationError("steps", "steps must not exceed $MAX_STEPS")
        }

        dto.steps.forEachIndexed { index, step ->
            val prefix = "steps[$index]"
            if (step.expectText != null && step.expectText.isBlank()) {
                errors += ValidationError("$prefix.expectText", "expectText must not be blank")
            }
            if (step.expectOption != null && step.expectOption.isBlank()) {
                errors += ValidationError("$prefix.expectOption", "expectOption must not be blank")
            }
            if (step.typeValue != null && step.typeValue.isBlank()) {
                errors += ValidationError("$prefix.typeValue", "typeValue must not be blank")
            }
            if (step.expectOption != null && step.typeValue != null) {
                errors += ValidationError(prefix, "step cannot set both expectOption and typeValue")
            }
            if (step.typeValue == null && step.expectOption == null && step.tapLabel.isNullOrBlank()) {
                errors += ValidationError("$prefix.tapLabel", "tapLabel must not be blank when typeValue is not set")
            } else if (step.typeValue == null && step.tapLabel != null && step.tapLabel.isBlank()) {
                errors += ValidationError("$prefix.tapLabel", "tapLabel must not be blank when typeValue is not set")
            }
            if (step.expectText.isNullOrBlank() && step.expectOption.isNullOrBlank() && step.typeValue.isNullOrBlank()) {
                errors += ValidationError(prefix, "step must set expectText, expectOption, or typeValue")
            }
        }

        return errors
    }

    private fun extractPath(message: String?): String {
        if (message == null) return "$"
        val marker = "at path: "
        val idx = message.indexOf(marker)
        if (idx == -1) return "$"
        return message.substring(idx + marker.length).lineSequence().first().trim()
    }

    @Serializable
    private data class FlowJsonDto(
        val id: String,
        val network: String,
        val name: String,
        val entry: String,
        val steps: List<StepJsonDto>,
        val verified: VerifiedJsonDto,
        val timeoutMs: Long? = null,
        val maxActions: Int? = null,
    )

    @Serializable
    private data class VerifiedJsonDto(
        val by: String,
        val date: String,
        val device: String,
    )

    @Serializable
    private data class StepJsonDto(
        val expectText: String? = null,
        val tapLabel: String? = null,
        val expectOption: String? = null,
        val typeValue: String? = null,
    )

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 8_000L
        const val DEFAULT_MAX_ACTIONS = 12
        const val MIN_TIMEOUT_MS = 1_000L
        const val MAX_TIMEOUT_MS = 30_000L
        const val MIN_ACTIONS = 1
        const val MAX_ACTIONS = 12
        const val MAX_STEPS = 12
        val DIAL_CODE_REGEX = Regex("^[*#][0-9*#]{1,23}#$")
    }
}
