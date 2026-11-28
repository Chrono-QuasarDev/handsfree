package com.handsfree.core.engine

import com.handsfree.core.model.EngineResult
import com.handsfree.core.model.HaltReason
import com.handsfree.core.model.UssdScreen

class FlowEngine(
    private val flow: FlowDefinition,
    private val clock: () -> Long = { 0L },
    private val timeoutMs: Long = 8_000L,
    private val maxActions: Int = 12,
) {

    private var cursor: Int = 0
    private var terminal: EngineResult? = null
    private var lastActivityMs: Long = clock()
    private var actionCount: Int = 0

    fun onScreen(screen: UssdScreen): EngineResult {
        // Severity order: 1) terminal latch -> 2) PIN gate -> 3) timeout -> 4) action cap -> 5) step matching.
        terminal?.let { return it }

        // PIN gate MUST be evaluated before any flow rule so a password screen always halts and never taps.
        if (isPinGateTriggered(screen)) {
            return EngineResult.Halted(HaltReason.PinGate).also { terminal = it }
        }

        val now = clock()
        if (now - lastActivityMs > timeoutMs) {
            return EngineResult.Halted(HaltReason.Timeout).also { terminal = it }
        }

        if (actionCount >= maxActions) {
            return EngineResult.Halted(HaltReason.MaxSteps).also { terminal = it }
        }

        if (cursor >= flow.steps.size) {
            return EngineResult.Done.also { terminal = it }
        }

        val step = flow.steps[cursor]
        val expectedOption = step.expectOption
        if (expectedOption != null) {
            val matched = matchOptionTiered(screen.options, expectedOption)
            return if (matched != null) {
                cursor++
                actionCount++
                lastActivityMs = now
                EngineResult.Action.TapOption(matched.label)
            } else {
                EngineResult.Halted(HaltReason.NoRule).also { terminal = it }
            }
        }

        val typeValue = step.typeValue
        if (typeValue != null) {
            val field = screen.inputField
            if (field == null || typeValue.isBlank()) {
                return EngineResult.Halted(HaltReason.NoRule).also { terminal = it }
            }
            // Belt-and-braces: typing path must also refuse password fields even if gate above were bypassed.
            if (isPinGateTriggered(screen)) {
                return EngineResult.Halted(HaltReason.PinGate).also { terminal = it }
            }
            cursor++
            actionCount++
            lastActivityMs = now
            return EngineResult.Action.TypeText(typeValue)
        }

        return if (step.expectText.isNotBlank() && screen.messageText.contains(step.expectText, ignoreCase = true)) {
            cursor++
            actionCount++
            lastActivityMs = now
            EngineResult.Action.TapOption(step.tapLabel)
        } else {
            EngineResult.Halted(HaltReason.NoRule).also { terminal = it }
        }
    }

    private fun matchOptionTiered(
        options: List<com.handsfree.core.model.Option>,
        rawExpectation: String,
    ): com.handsfree.core.model.Option? {
        val expected = rawExpectation.trim()
        if (expected.isEmpty()) return null

        val strippedExpected = stripNumericPrefix(expected)
        val predicates: List<(com.handsfree.core.model.Option) -> Boolean> = listOf(
            // Tier 0: raw equality (case-insensitive, trimmed)
            { it.label.trim().equals(expected, ignoreCase = true) },
            // Tier 1: prefix-adjusted equality
            { stripNumericPrefix(it.label).equals(strippedExpected, ignoreCase = true) },
            // Tier 2: substring match
            { it.label.contains(expected, ignoreCase = true) },
        )

        for (predicate in predicates) {
            val matches = options.filter(predicate)
            if (matches.isNotEmpty()) {
                return if (matches.size == 1) matches[0] else null
            }
        }
        return null
    }

    private fun stripNumericPrefix(text: String): String {
        val s = text.trim()
        var i = 0
        while (i < s.length && s[i].isDigit() && i < 3) {
            i++
        }
        if (i == 0 || (i < s.length && s[i].isDigit())) return s

        while (i < s.length && s[i].isWhitespace()) {
            i++
        }
        if (i >= s.length || (s[i] != '.' && s[i] != ')' && s[i] != ':' && s[i] != '-')) {
            return s
        }
        i++

        while (i < s.length && s[i].isWhitespace()) {
            i++
        }
        if (i >= s.length || s[i].isDigit()) {
            return s
        }
        return s.substring(i).trim()
    }

    private fun isPinGateTriggered(screen: UssdScreen): Boolean {
        val field = screen.inputField ?: return false
        if (field.isPassword) return true
        val hint = field.hint ?: return false
        return hint.contains("pin", ignoreCase = true) || hint.contains("password", ignoreCase = true)
    }
}
