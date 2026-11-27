package com.handsfree.core.engine

import com.handsfree.core.model.EngineResult
import com.handsfree.core.model.HaltReason
import com.handsfree.core.model.InputField
import com.handsfree.core.model.Option
import com.handsfree.core.model.UssdScreen
import org.junit.Assert.assertEquals
import org.junit.Test

class OptionMatchingTest {

    private fun momoMenuScreen(message: String = "Welcome to MTN MoMo") = UssdScreen(
        sourcePackage = "com.android.phone",
        messageText = message,
        options = listOf(
            Option(1, "1. Send Money"),
            Option(2, "2. Cash Out"),
            Option(3, "3. Buy Airtime"),
        ),
    )

    @Test
    fun `option label match emits on-screen label verbatim`() {
        val engine = FlowEngine(FlowDefinition(listOf(Step(expectOption = "cash out"))))
        assertEquals(EngineResult.Action.TapOption("2. Cash Out"), engine.onScreen(momoMenuScreen()))
    }

    @Test
    fun `option label match is case-insensitive`() {
        val engine = FlowEngine(FlowDefinition(listOf(Step(expectOption = "CASH OUT"))))
        assertEquals(EngineResult.Action.TapOption("2. Cash Out"), engine.onScreen(momoMenuScreen()))
    }

    @Test
    fun `message text is irrelevant when option is specified`() {
        val engine = FlowEngine(
            FlowDefinition(listOf(Step(expectText = "nonexistent header", expectOption = "cash out")))
        )
        assertEquals(
            EngineResult.Action.TapOption("2. Cash Out"),
            engine.onScreen(momoMenuScreen(message = "Completely different text")),
        )
    }

    @Test
    fun `exact on-screen label including number prefix is emitted`() {
        val label = "2.  Cash Out (Agent)"
        val screen = UssdScreen(
            sourcePackage = "com.android.phone",
            messageText = "Menu",
            options = listOf(Option(2, label)),
        )
        val engine = FlowEngine(FlowDefinition(listOf(Step(expectOption = "cash out"))))
        val result = engine.onScreen(screen) as EngineResult.Action.TapOption
        assertEquals(label, result.label)
    }

    @Test
    fun `no matching option halts with no rule even when message text matches`() {
        val engine = FlowEngine(
            FlowDefinition(
                listOf(Step(expectText = "Welcome to MTN MoMo", tapLabel = "Fallback", expectOption = "Pay Bill"))
            )
        )
        assertEquals(EngineResult.Halted(HaltReason.NoRule), engine.onScreen(momoMenuScreen()))
    }

    @Test
    fun `message-only step still taps flow label on message match`() {
        val engine = FlowEngine(
            FlowDefinition(listOf(Step(expectText = "Welcome to MTN MoMo", tapLabel = "Continue")))
        )
        assertEquals(EngineResult.Action.TapOption("Continue"), engine.onScreen(momoMenuScreen()))
    }

    @Test
    fun `pin gate wins over a matching option`() {
        val screen = UssdScreen(
            sourcePackage = "com.android.phone",
            messageText = "Enter PIN",
            options = listOf(Option(1, "1. Cash Out")),
            inputField = InputField(isPassword = true, hint = "Enter PIN"),
        )
        val engine = FlowEngine(FlowDefinition(listOf(Step(expectOption = "cash out"))))
        assertEquals(EngineResult.Halted(HaltReason.PinGate), engine.onScreen(screen))
    }

    @Test
    fun `halted after option mismatch is terminal`() {
        val engine = FlowEngine(FlowDefinition(listOf(Step(expectOption = "Pay Bill"))))
        assertEquals(EngineResult.Halted(HaltReason.NoRule), engine.onScreen(momoMenuScreen()))
        assertEquals(
            EngineResult.Halted(HaltReason.NoRule),
            engine.onScreen(
                UssdScreen(
                    sourcePackage = "com.android.phone",
                    messageText = "Retry",
                    options = listOf(Option(1, "1. Pay Bill")),
                )
            ),
        )
    }

    @Test
    fun `multi-step flow combines message-matched and option-matched steps to done`() {
        val engine = FlowEngine(
            FlowDefinition(
                listOf(
                    Step(expectText = "welcome", tapLabel = "MoMo"),
                    Step(expectOption = "cash out"),
                )
            )
        )
        assertEquals(
            EngineResult.Action.TapOption("MoMo"),
            engine.onScreen(UssdScreen(sourcePackage = "com.android.phone", messageText = "Welcome")),
        )
        assertEquals(EngineResult.Action.TapOption("2. Cash Out"), engine.onScreen(momoMenuScreen()))
        assertEquals(
            EngineResult.Done,
            engine.onScreen(UssdScreen(sourcePackage = "com.android.phone", messageText = "Enter agent number")),
        )
    }
}
