package com.handsfree.core.model

sealed interface HaltReason {
    data object PinGate : HaltReason
    data object Timeout : HaltReason
    data object NoRule : HaltReason
    data object MaxSteps : HaltReason
    data object UserStopped : HaltReason
    data object SessionClosed : HaltReason
}
