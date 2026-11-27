package com.handsfree.core.engine

import com.handsfree.core.model.EngineResult
import com.handsfree.core.model.HaltReason
import com.handsfree.core.model.UssdScreen
import org.junit.Assert.assertEquals
import org.junit.Test

class FlowEngineTest {

    private fun screen(text: String) =
        UssdScreen(sourcePackage = "com.android.phone", messageText = text)

    @Test
    fun `empty flow is done immediately`() {
        val engine = FlowEngine(FlowDefinition(steps = emptyList()))
        assertEquals(EngineResult.Done, engine.onScreen(screen("anything at all")))
    }

    @Test
    fun `matches contains ignoring case and emits tap`() {
        val engine = FlowEngine(
            FlowDefinition(listOf(Step(expectText = "welcome to mtn", tapLabel = "Airtime")))
        )
        assertEquals(EngineResult.Action.TapOption("Airtime"), engine.onScreen(screen("WELCOME TO MTN Ghana")))
    }

    @Test
    fun `tap label is passed through verbatim`() {
        val engine = FlowEngine(
            FlowDefinition(listOf(Step(expectText = "menu", tapLabel = "Send Money (MoMo)")))
        )
        assertEquals(
            EngineResult.Action.TapOption("Send Money (MoMo)"),
            engine.onScreen(screen("MAIN MENU"))
        )
    }

    @Test
    fun `advances through steps in order`() {
        val engine = FlowEngine(
            FlowDefinition(
                listOf(
                    Step(expectText = "main menu", tapLabel = "MoMo"),
                    Step(expectText = "momo menu", tapLabel = "Send")
                )
            )
        )
        assertEquals(EngineResult.Action.TapOption("MoMo"), engine.onScreen(screen("Main Menu")))
        assertEquals(EngineResult.Action.TapOption("Send"), engine.onScreen(screen("MoMo Menu")))
        assertEquals(EngineResult.Done, engine.onScreen(screen("Enter recipient number")))
    }

    @Test
    fun `unmatched screen halts with no rule`() {
        val engine = FlowEngine(
            FlowDefinition(listOf(Step(expectText = "main menu", tapLabel = "MoMo")))
        )
        assertEquals(EngineResult.Halted(HaltReason.NoRule), engine.onScreen(screen("Something unexpected")))
    }

    @Test
    fun `late step text does not satisfy an early step`() {
        val engine = FlowEngine(
            FlowDefinition(
                listOf(
                    Step(expectText = "step one", tapLabel = "A"),
                    Step(expectText = "step two", tapLabel = "B")
                )
            )
        )
        assertEquals(EngineResult.Halted(HaltReason.NoRule), engine.onScreen(screen("step two is here")))
    }

    @Test
    fun `matching ignores option labels in E3`() {
        val engine = FlowEngine(
            FlowDefinition(listOf(Step(expectText = "Airtime", tapLabel = "Airtime")))
        )
        val s = UssdScreen(
            sourcePackage = "com.android.phone",
            messageText = "Choose an option",
            options = listOf(com.handsfree.core.model.Option(1, "Airtime"))
        )
        assertEquals(EngineResult.Halted(HaltReason.NoRule), engine.onScreen(s))
    }

    @Test
    fun `halted is terminal and never taps again`() {
        val engine = FlowEngine(
            FlowDefinition(listOf(Step(expectText = "main menu", tapLabel = "MoMo")))
        )
        assertEquals(EngineResult.Halted(HaltReason.NoRule), engine.onScreen(screen("garbage")))
        assertEquals(EngineResult.Halted(HaltReason.NoRule), engine.onScreen(screen("Main Menu")))
        assertEquals(EngineResult.Halted(HaltReason.NoRule), engine.onScreen(screen("Main Menu")))
    }

    @Test
    fun `done is terminal`() {
        val engine = FlowEngine(FlowDefinition(steps = emptyList()))
        assertEquals(EngineResult.Done, engine.onScreen(screen("first")))
        assertEquals(EngineResult.Done, engine.onScreen(screen("second")))
        assertEquals(EngineResult.Done, engine.onScreen(screen("third")))
    }
}
