package com.handsfree.core.engine

import com.handsfree.core.model.EngineResult
import com.handsfree.core.model.HaltReason
import com.handsfree.core.model.InputField
import com.handsfree.core.model.Option
import com.handsfree.core.model.UssdScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GoldenFlowTest {

    private fun parseGolden(): ParsedFlow {
        val raw = checkNotNull(this::class.java.getResource("/flows/golden_flow.json")).readText()
        val result = FlowParser().parse(raw)
        assertTrue("Expected Success but got $result", result is FlowParseResult.Success)
        return (result as FlowParseResult.Success).flow
    }

    @Test
    fun `golden flow parses and completes option taps and typed input to done`() {
        val parsed = parseGolden()
        var now = 0L
        val engine = FlowEngine(
            flow = parsed.definition,
            clock = { now },
            timeoutMs = parsed.timeoutMs,
            maxActions = parsed.maxActions,
        )

        val menu1 = UssdScreen("com.android.phone", "Main Menu", listOf(Option(1, "1. Option A"), Option(2, "2. Other")))
        val menu2 = UssdScreen("com.android.phone", "Sub Menu", listOf(Option(1, "1. Other"), Option(2, "2. Option B")))
        val amountScreen = UssdScreen("com.android.phone", "Enter amount", inputField = InputField(false, "Amount"))
        val closingScreen = UssdScreen("com.android.phone", "Request submitted")

        now = 2_000L
        assertEquals(EngineResult.Action.TapOption("1. Option A"), engine.onScreen(menu1))
        now = 6_000L
        assertEquals(EngineResult.Action.TapOption("2. Option B"), engine.onScreen(menu2))
        now = 10_000L
        assertEquals(EngineResult.Action.TypeText("150"), engine.onScreen(amountScreen))
        now = 12_000L
        assertEquals(EngineResult.Done, engine.onScreen(closingScreen))
    }

    @Test
    fun `golden flow halts permanently at pin gate mid-flow`() {
        val parsed = parseGolden()
        var now = 0L
        val engine = FlowEngine(
            flow = parsed.definition,
            clock = { now },
            timeoutMs = parsed.timeoutMs,
            maxActions = parsed.maxActions,
        )

        val menu1 = UssdScreen("com.android.phone", "Main Menu", listOf(Option(1, "1. Option A")))
        val pinScreen = UssdScreen("com.android.phone", "Enter PIN", inputField = InputField(true, "Enter PIN"))
        val menu2 = UssdScreen("com.android.phone", "Sub Menu", listOf(Option(2, "2. Option B")))

        now = 1_000L
        assertEquals(EngineResult.Action.TapOption("1. Option A"), engine.onScreen(menu1))
        now = 2_000L
        assertEquals(EngineResult.Halted(HaltReason.PinGate), engine.onScreen(pinScreen))
        now = 3_000L
        assertEquals(EngineResult.Halted(HaltReason.PinGate), engine.onScreen(menu2))
    }

    @Test
    fun `blank expectText step never matches via empty message fallthrough`() {
        val engine = FlowEngine(FlowDefinition(listOf(Step(expectText = "", tapLabel = "Continue"))))
        val screen = UssdScreen("com.android.phone", "Any USSD dialog")
        assertEquals(EngineResult.Halted(HaltReason.NoRule), engine.onScreen(screen))
    }
}
