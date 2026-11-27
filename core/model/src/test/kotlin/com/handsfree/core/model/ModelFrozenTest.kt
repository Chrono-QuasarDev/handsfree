package com.handsfree.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ModelFrozenTest {

    @Test
    fun `ussd screen defaults to no options and no input`() {
        val s = UssdScreen(sourcePackage = "com.android.phone", messageText = "Welcome")
        assertEquals(emptyList<Option>(), s.options)
        assertEquals(null, s.inputField)
        assertEquals(emptyList<String>(), s.buttons)
    }

    @Test
    fun `option supports missing index`() {
        assertEquals(null, Option(index = null, label = "Continue").index)
        assertEquals(3, Option(index = 3, label = "Send money").index)
    }

    @Test
    fun `password input field is distinguishable`() {
        val pin = InputField(isPassword = true, hint = "Enter PIN")
        val amount = InputField(isPassword = false, hint = "Enter amount")
        assertEquals(true, pin.isPassword)
        assertEquals(false, amount.isPassword)
        assertNotEquals(pin, amount)
    }

    @Test
    fun `halt reasons are singletons`() {
        assertSame(HaltReason.PinGate, HaltReason.PinGate)
        assertSame(HaltReason.Timeout, HaltReason.Timeout)
        assertSame(HaltReason.NoRule, HaltReason.NoRule)
        assertSame(HaltReason.MaxSteps, HaltReason.MaxSteps)
        assertSame(HaltReason.UserStopped, HaltReason.UserStopped)
        assertSame(HaltReason.SessionClosed, HaltReason.SessionClosed)
    }

    @Test
    fun `engine results carry their payload`() {
        val action: EngineResult = EngineResult.Action.TapOption("Send money")
        assertEquals("Send money", (action as EngineResult.Action.TapOption).label)
        val halted: EngineResult = EngineResult.Halted(HaltReason.PinGate)
        assertEquals(HaltReason.PinGate, (halted as EngineResult.Halted).reason)
        assertEquals(EngineResult.Done, EngineResult.Done)
    }

    @Test
    fun `ussd screen equality is structural`() {
        val a = UssdScreen("pkg", "msg", listOf(Option(1, "Airtime")))
        val b = UssdScreen("pkg", "msg", listOf(Option(1, "Airtime")))
        assertEquals(a, b)
    }
}
