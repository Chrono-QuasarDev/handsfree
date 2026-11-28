package com.handsfree.core.engine

import com.handsfree.core.model.EngineResult
import com.handsfree.core.model.HaltReason
import com.handsfree.core.model.Option
import com.handsfree.core.model.UssdScreen
import org.junit.Assert.assertEquals
import org.junit.Test

class MatchingPrecisionTest {

    private fun menuScreen(vararg labels: String) = UssdScreen(
        sourcePackage = "com.android.phone",
        messageText = "Choose an option",
        options = labels.mapIndexed { index, label -> Option(index + 1, label) },
    )

    @Test
    fun `prefix-adjusted exact beats earlier substring`() {
        val screen = menuScreen("1. Cash Out to Agent", "2. Cash Out")
        val engine = FlowEngine(FlowDefinition(listOf(Step(expectOption = "Cash Out"))))
        assertEquals(EngineResult.Action.TapOption("2. Cash Out"), engine.onScreen(screen))
    }

    @Test
    fun `raw equality wins outright`() {
        val screen = menuScreen("1. Cash Out", "2. Cash Out", "2. Cash Out Extra")
        val engine = FlowEngine(FlowDefinition(listOf(Step(expectOption = "2. Cash Out"))))
        assertEquals(EngineResult.Action.TapOption("2. Cash Out"), engine.onScreen(screen))
    }

    @Test
    fun `prefix variety matches across dot paren colon and dash`() {
        for (label in listOf("3) Buy Airtime", "3: Buy Airtime", "3 - Buy Airtime")) {
            val screen = menuScreen("1. Send Money", label)
            val engine = FlowEngine(FlowDefinition(listOf(Step(expectOption = "Buy Airtime"))))
            assertEquals(EngineResult.Action.TapOption(label), engine.onScreen(screen))
        }
    }

    @Test
    fun `decimal labels are not mangled by prefix stripping`() {
        val screen = menuScreen("1.5 GB Bundle", "1.5 GB Bundle Extra")
        val exactEngine = FlowEngine(FlowDefinition(listOf(Step(expectOption = "1.5 GB Bundle"))))
        assertEquals(EngineResult.Action.TapOption("1.5 GB Bundle"), exactEngine.onScreen(screen))

        val nonDecimalEngine = FlowEngine(FlowDefinition(listOf(Step(expectOption = "5 GB Bundle"))))
        assertEquals(EngineResult.Halted(HaltReason.NoRule), nonDecimalEngine.onScreen(screen))
    }

    @Test
    fun `single substring still works`() {
        val screen = menuScreen("1. Send Money", "2. Buy Data Bundle")
        val engine = FlowEngine(FlowDefinition(listOf(Step(expectOption = "Data"))))
        assertEquals(EngineResult.Action.TapOption("2. Buy Data Bundle"), engine.onScreen(screen))
    }

    @Test
    fun `two substring matches halt`() {
        val screen = menuScreen("1. Cash Out to Agent", "2. Cash Out to Bank")
        val engine = FlowEngine(FlowDefinition(listOf(Step(expectOption = "Cash Out"))))
        assertEquals(EngineResult.Halted(HaltReason.NoRule), engine.onScreen(screen))
    }

    @Test
    fun `two exact matches halt`() {
        val screen = menuScreen("1. Cash Out", "2. Cash Out")
        val engine = FlowEngine(FlowDefinition(listOf(Step(expectOption = "Cash Out"))))
        assertEquals(EngineResult.Halted(HaltReason.NoRule), engine.onScreen(screen))
    }

    @Test
    fun `blank expectation halts`() {
        val screen = menuScreen("1. Send Money", "2. Cash Out")
        val engine = FlowEngine(FlowDefinition(listOf(Step(expectOption = "   "))))
        assertEquals(EngineResult.Halted(HaltReason.NoRule), engine.onScreen(screen))
    }

    @Test
    fun `verbatim label including prefix and spacing is preserved`() {
        val label = "2)  Cash Out  "
        val screen = menuScreen("1. Send Money", label)
        val engine = FlowEngine(FlowDefinition(listOf(Step(expectOption = "cash out"))))
        assertEquals(EngineResult.Action.TapOption(label), engine.onScreen(screen))
    }

    @Test
    fun `message-only steps unchanged`() {
        val screen = UssdScreen(sourcePackage = "com.android.phone", messageText = "Welcome to MTN")
        val engine = FlowEngine(FlowDefinition(listOf(Step(expectText = "welcome", tapLabel = "Proceed"))))
        assertEquals(EngineResult.Action.TapOption("Proceed"), engine.onScreen(screen))
    }
}
