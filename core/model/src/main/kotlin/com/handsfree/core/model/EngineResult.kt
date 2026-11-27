package com.handsfree.core.model

sealed interface EngineResult {
    sealed interface Action : EngineResult {
        data class TapOption(val label: String) : Action
    }
    data class Halted(val reason: HaltReason) : EngineResult
    data object Done : EngineResult
}
