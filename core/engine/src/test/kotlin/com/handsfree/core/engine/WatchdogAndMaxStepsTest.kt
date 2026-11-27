package com.handsfree.core.engine

import com.handsfree.core.model.EngineResult
import com.handsfree.core.model.HaltReason
import com.handsfree.core.model.InputField
import com.handsfree.core.model.UssdScreen
import org.junit.Assert.assertEquals
import org.junit.Test

class WatchdogAndMaxStepsTest {

    private var now: Long = 0L

    private fun screen(text: String) =
        UssdScreen(sourcePackage = "com.android.phone", messageText = text)

    private fun pinScreen() = UssdScreen(
        sourcePackage = "com.android.phone",
        messageText = "Enter PIN",
        inputField = InputField(isPassword = true, hint = null)
    )

    private fun engine(vararg steps: Step) =
        FlowEngine(FlowDefinition(steps.toList()), clock = { now }, timeoutMs = 8_000L, maxActions = 12)

    private fun manySteps(count: Int) =
        Array(count) { i -> Step(expectText = "step$i", tapLabel = "T$i") }

    @Test
    fun `screen just inside the timeout still matches`() {
        val e = engine(Step(expectText = "main menu", tapLabel = "MoMo"))
        now = 7_999L
        assertEquals(EngineResult.Action.TapOption("MoMo"), e.onScreen(screen("Main Menu")))
    }

    @Test
    fun `exactly at the timeout is not a timeout`() {
        val e = engine(Step(expectText = "main menu", tapLabel = "MoMo"))
        now = 8_000L
        assertEquals(EngineResult.Action.TapOption("MoMo"), e.onScreen(screen("Main Menu")))
    }

    @Test
    fun `past the timeout halts with timeout and taps nothing`() {
        val e = engine(Step(expectText = "main menu", tapLabel = "MoMo"))
        now = 8_001L
        assertEquals(EngineResult.Halted(HaltReason.Timeout), e.onScreen(screen("Main Menu")))
    }

    @Test
    fun `timeout is terminal`() {
        val e = engine(Step(expectText = "main menu", tapLabel = "MoMo"))
        now = 9_000L
        assertEquals(EngineResult.Halted(HaltReason.Timeout), e.onScreen(screen("Main Menu")))
        now = 0L
        assertEquals(EngineResult.Halted(HaltReason.Timeout), e.onScreen(screen("Main Menu")))
    }

    @Test
    fun `timer resets after each accepted step`() {
        val e = engine(
            Step(expectText = "one", tapLabel = "A"),
            Step(expectText = "two", tapLabel = "B")
        )
        now = 7_000L
        assertEquals(EngineResult.Action.TapOption("A"), e.onScreen(screen("one")))
        now = 14_000L
        assertEquals(EngineResult.Action.TapOption("B"), e.onScreen(screen("two")))
    }

    @Test
    fun `twelve taps allowed and the thirteenth is refused`() {
        val e = engine(*manySteps(13))
        for (i in 0 until 12) {
            now = i.toLong() * 1_000L
            assertEquals(EngineResult.Action.TapOption("T$i"), e.onScreen(screen("step$i")))
        }
        now = 12_000L
        assertEquals(EngineResult.Halted(HaltReason.MaxSteps), e.onScreen(screen("step12")))
    }

    @Test
    fun `max steps is terminal`() {
        val e = engine(*manySteps(13))
        for (i in 0 until 12) {
            now = i.toLong() * 1_000L
            e.onScreen(screen("step$i"))
        }
        assertEquals(EngineResult.Halted(HaltReason.MaxSteps), e.onScreen(screen("step12")))
        assertEquals(EngineResult.Halted(HaltReason.MaxSteps), e.onScreen(screen("step0")))
    }

    @Test
    fun `pin gate wins over an elapsed timeout`() {
        val e = engine(Step(expectText = "main menu", tapLabel = "MoMo"))
        now = 60_000L
        assertEquals(EngineResult.Halted(HaltReason.PinGate), e.onScreen(pinScreen()))
    }

    @Test
    fun `pin gate wins when the action cap is exhausted`() {
        val e = engine(*manySteps(13))
        for (i in 0 until 12) {
            now = i.toLong() * 1_000L
            e.onScreen(screen("step$i"))
        }
        now = 12_000L
        assertEquals(EngineResult.Halted(HaltReason.PinGate), e.onScreen(pinScreen()))
    }

    @Test
    fun `default constructor never times out`() {
        val e = FlowEngine(FlowDefinition(listOf(Step(expectText = "main menu", tapLabel = "MoMo"))))
        assertEquals(EngineResult.Action.TapOption("MoMo"), e.onScreen(screen("Main Menu")))
    }
}
