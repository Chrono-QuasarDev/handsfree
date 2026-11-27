package com.handsfree.core.engine

import com.handsfree.core.model.EngineResult
import com.handsfree.core.model.HaltReason
import com.handsfree.core.model.UssdScreen

class FlowEngine(private val flow: FlowDefinition) {

    private var cursor: Int = 0
    private var terminal: EngineResult? = null

    fun onScreen(screen: UssdScreen): EngineResult {
        terminal?.let { return it }

        // PIN gate MUST be evaluated before any flow rule so a password screen always halts and never taps.
        if (isPinGateTriggered(screen)) {
            return EngineResult.Halted(HaltReason.PinGate).also { terminal = it }
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
                EngineResult.Action.TapOption(matched.label)
            } else {
                EngineResult.Halted(HaltReason.NoRule).also { terminal = it }
            }
        }

        return if (screen.messageText.contains(step.expectText, ignoreCase = true)) {
            cursor++
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
