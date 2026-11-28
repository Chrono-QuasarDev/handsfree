package com.handsfree.core.engine

import com.handsfree.core.model.EngineResult
import com.handsfree.core.model.HaltReason
import com.handsfree.core.model.InputField
import com.handsfree.core.model.UssdScreen
import org.junit.Assert.assertEquals
import org.junit.Test

class TypingRefusalTest {

    private var now: Long = 0L

    private fun engine(vararg steps: Step) =
        FlowEngine(FlowDefinition(steps.toList()), clock = { now })

    private fun plainFieldScreen(text: String, hint: String? = "Amount") = UssdScreen(
        sourcePackage = "com.android.phone",
        messageText = text,
        inputField = InputField(isPassword = false, hint = hint)
    )

    private fun pinScreen() = UssdScreen(
        sourcePackage = "com.android.phone",
        messageText = "Enter PIN",
        inputField = InputField(isPassword = true, hint = null)
    )

    private fun noFieldScreen(text: String) =
        UssdScreen(sourcePackage = "com.android.phone", messageText = text)

    @Test
    fun `plain field receives the typed value`() {
        val e = engine(Step(typeValue = "100"))
        assertEquals(EngineResult.Action.TypeText("100"), e.onScreen(plainFieldScreen("Enter amount")))
    }

    @Test
    fun `password field refuses to type even when the step demands it`() {
        // ATTACK TEST: the flow wants 1234 typed. The screen is a PIN field.
        // The answer must be a PIN gate halt. TypeText here would be the end of the product.
        val e = engine(Step(typeValue = "1234"))
        assertEquals(EngineResult.Halted(HaltReason.PinGate), e.onScreen(pinScreen()))
    }

    @Test
    fun `refusal is terminal`() {
        val e = engine(Step(typeValue = "1234"))
        assertEquals(EngineResult.Halted(HaltReason.PinGate), e.onScreen(pinScreen()))
        assertEquals(EngineResult.Halted(HaltReason.PinGate), e.onScreen(plainFieldScreen("Enter amount")))
    }

    @Test
    fun `no input field halts rather than typing nowhere`() {
        val e = engine(Step(typeValue = "100"))
        assertEquals(EngineResult.Halted(HaltReason.NoRule), e.onScreen(noFieldScreen("Enter amount")))
    }

    @Test
    fun `blank type value halts and never emits empty typing`() {
        val e = engine(Step(typeValue = "   "))
        assertEquals(EngineResult.Halted(HaltReason.NoRule), e.onScreen(plainFieldScreen("Enter amount")))
    }

    @Test
    fun `typing advances the cursor one step per field`() {
        val e = engine(
            Step(typeValue = "0244000111"),
            Step(typeValue = "50")
        )
        assertEquals(EngineResult.Action.TypeText("0244000111"), e.onScreen(plainFieldScreen("Enter recipient")))
        assertEquals(EngineResult.Action.TypeText("50"), e.onScreen(plainFieldScreen("Enter amount")))
        assertEquals(EngineResult.Done, e.onScreen(noFieldScreen("Processing")))
    }

    @Test
    fun `typing counts toward the action cap`() {
        val steps = Array(13) { i -> Step(typeValue = "v$i") }
        val e = engine(*steps)
        for (i in 0 until 12) {
            assertEquals(EngineResult.Action.TypeText("v$i"), e.onScreen(plainFieldScreen("Field $i")))
        }
        assertEquals(EngineResult.Halted(HaltReason.MaxSteps), e.onScreen(plainFieldScreen("Field 12")))
    }

    @Test
    fun `typing resets the watchdog`() {
        val e = engine(
            Step(typeValue = "100"),
            Step(typeValue = "200")
        )
        now = 7_000L
        assertEquals(EngineResult.Action.TypeText("100"), e.onScreen(plainFieldScreen("Enter amount")))
        now = 14_000L
        assertEquals(EngineResult.Action.TypeText("200"), e.onScreen(plainFieldScreen("Enter amount")))
    }

    @Test
    fun `message steps are unaffected by the typing path`() {
        val e = engine(Step(expectText = "welcome", tapLabel = "Proceed"))
        assertEquals(EngineResult.Action.TapOption("Proceed"), e.onScreen(noFieldScreen("Welcome to MTN")))
    }

    @Test
    fun `pin gate still wins over an elapsed timeout on a typing step`() {
        val e = engine(Step(typeValue = "1234"))
        now = 60_000L
        assertEquals(EngineResult.Halted(HaltReason.PinGate), e.onScreen(pinScreen()))
    }
}
