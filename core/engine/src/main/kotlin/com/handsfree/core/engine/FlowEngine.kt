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
            val matched = screen.options.firstOrNull {
                it.label.contains(expectedOption, ignoreCase = true)
            }
            return if (matched != null) {
                cursor++
                actionCount++
                lastActivityMs = now
                EngineResult.Action.TapOption(matched.label)
            } else {
                EngineResult.Halted(HaltReason.NoRule).also { terminal = it }
            }
        }

        return if (screen.messageText.contains(step.expectText, ignoreCase = true)) {
            cursor++
            actionCount++
            lastActivityMs = now
            EngineResult.Action.TapOption(step.tapLabel)
        } else {
            EngineResult.Halted(HaltReason.NoRule).also { terminal = it }
        }
    }

    private fun isPinGateTriggered(screen: UssdScreen): Boolean {
        val field = screen.inputField ?: return false
        if (field.isPassword) return true
        val hint = field.hint ?: return false
        return hint.contains("pin", ignoreCase = true) || hint.contains("password", ignoreCase = true)
    }
}
