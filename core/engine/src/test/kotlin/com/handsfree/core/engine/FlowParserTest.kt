package com.handsfree.core.engine

import com.handsfree.core.model.EngineResult
import com.handsfree.core.model.InputField
import com.handsfree.core.model.Option
import com.handsfree.core.model.UssdScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FlowParserTest {

    private val parser = FlowParser()

    private fun validJson(
        id: String = "test_flow",
        network: String = "TEST",
        name: String = "Test Flow",
        entry: String = "*123#",
        verifiedBlock: String = """{"by":"qa","date":"2026-11-28","device":"Pixel"}""",
        extraTopLevel: String = "",
        stepsJson: String = """[{"expectText":"Welcome","tapLabel":"Next"}]""",
    ): String = """
        {
          "id": "$id",
          "network": "$network",
          "name": "$name",
          "entry": "$entry",
          "verified": $verifiedBlock,
          $extraTopLevel
          "steps": $stepsJson
        }
    """.trimIndent()

    private fun assertFailsWith(raw: String, expectedPath: String, expectedReasonSubstring: String) {
        val result = parser.parse(raw)
        assertTrue("Expected Failure but got $result", result is FlowParseResult.Failure)
        val errors = (result as FlowParseResult.Failure).errors
        assertTrue(
            "Expected error at path '$expectedPath' containing '$expectedReasonSubstring', got $errors",
            errors.any { it.path == expectedPath && it.reason.contains(expectedReasonSubstring, ignoreCase = true) }
        )
    }

    @Test
    fun `valid fixture parses and runs through FlowEngine`() {
        val raw = checkNotNull(this::class.java.getResource("/flows/valid_flow.json")).readText()
        val result = parser.parse(raw)
        assertTrue("Expected Success but got $result", result is FlowParseResult.Success)
        val parsed = (result as FlowParseResult.Success).flow
        assertEquals("sample_two_step", parsed.id)
        assertEquals(10_000L, parsed.timeoutMs)
        assertEquals(6, parsed.maxActions)

        val engine = FlowEngine(parsed.definition, timeoutMs = parsed.timeoutMs, maxActions = parsed.maxActions)
        val screen1 = UssdScreen("com.android.phone", "Menu", listOf(Option(1, "1. Option A")))
        val screen2 = UssdScreen("com.android.phone", "Amount", inputField = InputField(false, "Amount"))
        val screen3 = UssdScreen("com.android.phone", "Done")

        assertEquals(EngineResult.Action.TapOption("1. Option A"), engine.onScreen(screen1))
        assertEquals(EngineResult.Action.TypeText("50"), engine.onScreen(screen2))
        assertEquals(EngineResult.Done, engine.onScreen(screen3))
    }

    @Test
    fun `blank metadata fields are rejected with field path`() {
        assertFailsWith(validJson(id = "  "), "id", "blank")
        assertFailsWith(validJson(network = ""), "network", "blank")
        assertFailsWith(validJson(name = "\t"), "name", "blank")
    }

    @Test
    fun `bad dial code is rejected`() {
        for (bad in listOf("123#", "*123", "*12 3#", "*abc#", "#")) {
            assertFailsWith(validJson(entry = bad), "entry", "entry must match")
        }
    }

    @Test
    fun `empty steps and more than 12 steps are rejected while 12 steps succeed`() {
        assertFailsWith(validJson(stepsJson = "[]"), "steps", "empty")
        val twelve = (1..12).joinToString(",") { """{"expectText":"s$it","tapLabel":"t$it"}""" }
        val thirteen = (1..13).joinToString(",") { """{"expectText":"s$it","tapLabel":"t$it"}""" }
        assertTrue(parser.parse(validJson(stepsJson = "[$twelve]")) is FlowParseResult.Success)
        assertFailsWith(validJson(stepsJson = "[$thirteen]"), "steps", "12")
    }

    @Test
    fun `dead step is rejected`() {
        assertFailsWith(validJson(stepsJson = """[{"tapLabel":"Next"}]"""), "steps[0]", "expectText")
    }

    @Test
    fun `blank expectText is rejected`() {
        assertFailsWith(
            validJson(stepsJson = """[{"expectText":"   ","tapLabel":"Next"}]"""),
            "steps[0].expectText",
            "blank",
        )
    }

    @Test
    fun `blank tapLabel without typeValue is rejected`() {
        assertFailsWith(
            validJson(stepsJson = """[{"expectText":"Menu","tapLabel":"  "}]"""),
            "steps[0].tapLabel",
            "blank",
        )
    }

    @Test
    fun `blank expectOption is rejected`() {
        assertFailsWith(
            validJson(stepsJson = """[{"expectOption":"  "}]"""),
            "steps[0].expectOption",
            "blank",
        )
    }

    @Test
    fun `conflicting step with both expectOption and typeValue is rejected`() {
        assertFailsWith(
            validJson(stepsJson = """[{"expectOption":"Pay","typeValue":"10"}]"""),
            "steps[0]",
            "both expectOption and typeValue",
        )
    }

    @Test
    fun `missing or blank verified metadata is rejected`() {
        val missingVerified = """{"id":"a","network":"N","name":"F","entry":"*1#","steps":[{"expectText":"x","tapLabel":"y"}]}"""
        assertTrue(parser.parse(missingVerified) is FlowParseResult.Failure)
        assertFailsWith(validJson(verifiedBlock = """{"by":" ","date":"2026-11-28","device":"P"}"""), "verified.by", "blank")
        assertFailsWith(validJson(verifiedBlock = """{"by":"qa","date":"","device":"P"}"""), "verified.date", "blank")
        assertFailsWith(validJson(verifiedBlock = """{"by":"qa","date":"2026-11-28","device":" "}"""), "verified.device", "blank")
    }

    @Test
    fun `unknown keys at flow and step level are rejected`() {
        val flowUnknown = parser.parse(validJson(extraTopLevel = """"surprise": true,"""))
        assertTrue(flowUnknown is FlowParseResult.Failure)
        assertTrue((flowUnknown as FlowParseResult.Failure).errors.any { it.reason.contains("unknown key", ignoreCase = true) })

        val stepUnknown = parser.parse(validJson(stepsJson = """[{"expectText":"a","tapLabel":"b","extra":1}]"""))
        assertTrue(stepUnknown is FlowParseResult.Failure)
        assertTrue((stepUnknown as FlowParseResult.Failure).errors.any { it.reason.contains("unknown key", ignoreCase = true) })
    }

    @Test
    fun `timeoutMs and maxActions boundaries are enforced`() {
        assertFailsWith(validJson(extraTopLevel = """"timeoutMs": 999,"""), "timeoutMs", "1000")
        assertTrue(parser.parse(validJson(extraTopLevel = """"timeoutMs": 1000,""")) is FlowParseResult.Success)
        assertFailsWith(validJson(extraTopLevel = """"timeoutMs": 30001,"""), "timeoutMs", "30000")

        assertFailsWith(validJson(extraTopLevel = """"maxActions": 0,"""), "maxActions", "12")
        assertTrue(parser.parse(validJson(extraTopLevel = """"maxActions": 12,""")) is FlowParseResult.Success)
        assertFailsWith(validJson(extraTopLevel = """"maxActions": 13,"""), "maxActions", "12")
    }

    @Test
    fun `malformed json returns failure without throwing`() {
        val truncated = parser.parse("""{"id": "broken"""")
        assertTrue(truncated is FlowParseResult.Failure)

        val wrongType = parser.parse(validJson(stepsJson = """"not-an-array""""))
        assertTrue(wrongType is FlowParseResult.Failure)
    }
}
