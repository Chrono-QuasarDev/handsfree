package com.handsfree.core.engine

import com.handsfree.core.model.EngineResult
import com.handsfree.core.model.HaltReason
import com.handsfree.core.model.UssdScreen

class FlowEngine(private val flow: FlowDefinition) {

    private var cursor: Int = 0
    private var terminal: EngineResult? = null

    fun onScreen(screen: UssdScreen): EngineResult {
        terminal?.let { return it }

        if (cursor >= flow.steps.size) {
            return EngineResult.Done.also { terminal = it }
        }

        val step = flow.steps[cursor]
        return if (screen.messageText.contains(step.expectText, ignoreCase = true)) {
            cursor++
            EngineResult.Action.TapOption(step.tapLabel)
        } else {
            EngineResult.Halted(HaltReason.NoRule).also { terminal = it }
        }
    }
}
