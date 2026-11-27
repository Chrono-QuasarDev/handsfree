package com.handsfree.core.engine

import com.handsfree.core.model.EngineResult
import com.handsfree.core.model.HaltReason
import com.handsfree.core.model.InputField
import com.handsfree.core.model.UssdScreen
import org.junit.Assert.assertEquals
import org.junit.Test

class PinGateTest {

    private fun plainScreen(text: String) =
        UssdScreen(sourcePackage = "com.android.phone", messageText = text)

    private fun screenWithField(text: String, field: InputField) =
        UssdScreen(sourcePackage = "com.android.phone", messageText = text, inputField = field)

    private fun flow(vararg steps: Step) = FlowEngine(FlowDefinition(steps.toList()))

    @Test
    fun `gate trips on password field`() {
        val engine = flow(Step(expectText = "main menu", tapLabel = "MoMo"))
        assertEquals(
            EngineResult.Halted(HaltReason.PinGate),
            engine.onScreen(screenWithField("Enter your PIN", InputField(isPassword = true, hint = null)))
        )
    }

    @Test
    fun `gate wins over a matching step and emits no tap`() {
        // ATTACK TEST: the flow is demanding a tap, and the screen even satisfies it.
        // The gate must still win. If this ever returns TapOption, the product's promise is broken.
        val engine = flow(Step(expectText = "enter your pin", tapLabel = "Continue"))
        val result = engine.onScreen(
            screenWithField("Enter your PIN", InputField(isPassword = true, hint = "Enter PIN"))
        )
        assertEquals(EngineResult.Halted(HaltReason.PinGate), result)
    }

    @Test
    fun `gate is terminal and never taps afterwards`() {
        val engine = flow(Step(expectText = "main menu", tapLabel = "MoMo"))
        assertEquals(
            EngineResult.Halted(HaltReason.PinGate),
            engine.onScreen(screenWithField("PIN", InputField(isPassword = true, hint = null)))
        )
        assertEquals(EngineResult.Halted(HaltReason.PinGate), engine.onScreen(plainScreen("Main Menu")))
        assertEquals(
            EngineResult.Halted(HaltReason.PinGate),
            engine.onScreen(screenWithField("Main Menu", InputField(isPassword = true, hint = null)))
        )
    }

    @Test
    fun `hint containing pin trips the gate even when isPassword is false`() {
        val engine = flow(Step(expectText = "main menu", tapLabel = "MoMo"))
        assertEquals(
            EngineResult.Halted(HaltReason.PinGate),
            engine.onScreen(screenWithField("Verify", InputField(isPassword = false, hint = "Enter PIN")))
        )
    }

    @Test
    fun `hint containing password trips the gate case-insensitively`() {
        val engine = flow(Step(expectText = "main menu", tapLabel = "MoMo"))
        assertEquals(
            EngineResult.Halted(HaltReason.PinGate),
            engine.onScreen(screenWithField("Verify", InputField(isPassword = false, hint = "Wallet PassWord")))
        )
    }

    @Test
    fun `gate trips mid flow after earlier steps tapped`() {
        val engine = flow(
            Step(expectText = "main menu", tapLabel = "MoMo"),
            Step(expectText = "enter pin", tapLabel = "Continue")
        )
        assertEquals(EngineResult.Action.TapOption("MoMo"), engine.onScreen(plainScreen("Main Menu")))
        assertEquals(
            EngineResult.Halted(HaltReason.PinGate),
            engine.onScreen(screenWithField("Enter PIN", InputField(isPassword = true, hint = null)))
        )
        assertEquals(EngineResult.Halted(HaltReason.PinGate), engine.onScreen(plainScreen("Main Menu")))
    }

    @Test
    fun `gate trips even on the first screen before any step`() {
        val engine = flow(Step(expectText = "welcome", tapLabel = "Start"))
        assertEquals(
            EngineResult.Halted(HaltReason.PinGate),
            engine.onScreen(screenWithField("Welcome", InputField(isPassword = true, hint = null)))
        )
    }

    @Test
    fun `non password input field does not trip the gate`() {
        val engine = flow(Step(expectText = "enter amount", tapLabel = "Confirm"))
        assertEquals(
            EngineResult.Action.TapOption("Confirm"),
            engine.onScreen(screenWithField("Enter amount", InputField(isPassword = false, hint = "Amount in GHS")))
        )
    }

    @Test
    fun `null input field does not trip the gate`() {
        val engine = flow(Step(expectText = "main menu", tapLabel = "MoMo"))
        assertEquals(EngineResult.Action.TapOption("MoMo"), engine.onScreen(plainScreen("Main Menu")))
    }

    @Test
    fun `unrelated hint does not trip the gate`() {
        val engine = flow(Step(expectText = "enter recipient", tapLabel = "Next"))
        assertEquals(
            EngineResult.Action.TapOption("Next"),
            engine.onScreen(screenWithField("Enter recipient", InputField(isPassword = false, hint = "Phone number")))
        )
    }
}
