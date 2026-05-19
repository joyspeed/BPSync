package com.bpsync.app

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.time.LocalDate
import java.time.LocalTime

class CsvParserTest {

    private fun parse(csv: String): List<BpReading> =
        CsvParser.parse(ByteArrayInputStream(csv.toByteArray()))

    // ── Basic parsing ──────────────────────────────────────────

    @Test
    fun `parses a single valid row`() {
        val csv = """
            "Date","Time","Sys","Dia","Pulse","Irregular Pulse","Source"
            "2025-04-10","07:30:00","120","80","72","","Omron HEM-7156T"
        """.trimIndent()

        val results = parse(csv)

        assertEquals(1, results.size)
        val r = results[0]
        assertEquals(LocalDate.of(2025, 4, 10), r.date)
        assertEquals(LocalTime.of(7, 30, 0), r.time)
        assertEquals(120.0, r.systolic, 0.01)
        assertEquals(80.0, r.diastolic, 0.01)
        assertEquals(72L, r.pulse)
        assertFalse(r.irregularPulse)
        assertEquals("Omron HEM-7156T", r.source)
    }

    @Test
    fun `parses multiple rows`() {
        val csv = """
            "Date","Time","Sys","Dia","Pulse","Irregular Pulse","Source"
            "2025-04-10","07:30:00","120","80","72","","Device A"
            "2025-04-11","08:00:00","130","85","68","","Device B"
            "2025-04-12","19:15:00","118","76","75","detected","Device A"
        """.trimIndent()

        val results = parse(csv)
        assertEquals(3, results.size)
    }

    @Test
    fun `detects irregular pulse`() {
        val csv = """
            "Date","Time","Sys","Dia","Pulse","Irregular Pulse","Source"
            "2025-04-10","07:30:00","120","80","72","detected","Device"
        """.trimIndent()

        assertTrue(parse(csv)[0].irregularPulse)
    }

    @Test
    fun `irregular pulse detection is case-insensitive`() {
        val csv = """
            "Date","Time","Sys","Dia","Pulse","Irregular Pulse","Source"
            "2025-04-10","07:30:00","120","80","72","Detected","Device"
        """.trimIndent()

        assertTrue(parse(csv)[0].irregularPulse)
    }

    // ── Edge cases ─────────────────────────────────────────────

    @Test
    fun `returns empty list for header-only CSV`() {
        val csv = "\"Date\",\"Time\",\"Sys\",\"Dia\",\"Pulse\",\"Irregular Pulse\",\"Source\""
        assertEquals(0, parse(csv).size)
    }

    @Test
    fun `returns empty list for empty input`() {
        assertEquals(0, parse("").size)
    }

    @Test
    fun `skips blank lines`() {
        val csv = """
            "Date","Time","Sys","Dia","Pulse","Irregular Pulse","Source"
            
            "2025-04-10","07:30:00","120","80","72","","Device"
            
        """.trimIndent()

        assertEquals(1, parse(csv).size)
    }

    @Test
    fun `skips rows with too few columns`() {
        val csv = """
            "Date","Time","Sys","Dia","Pulse","Irregular Pulse","Source"
            "2025-04-10","07:30:00","120","80"
            "2025-04-10","08:00:00","130","85","68","","Device"
        """.trimIndent()

        assertEquals(1, parse(csv).size)
    }

    @Test
    fun `skips rows with malformed date`() {
        val csv = """
            "Date","Time","Sys","Dia","Pulse","Irregular Pulse","Source"
            "not-a-date","07:30:00","120","80","72","","Device"
            "2025-04-10","08:00:00","130","85","68","","Device"
        """.trimIndent()

        assertEquals(1, parse(csv).size)
    }

    @Test
    fun `skips rows with non-numeric systolic`() {
        val csv = """
            "Date","Time","Sys","Dia","Pulse","Irregular Pulse","Source"
            "2025-04-10","07:30:00","high","80","72","","Device"
            "2025-04-10","08:00:00","130","85","68","","Device"
        """.trimIndent()

        assertEquals(1, parse(csv).size)
    }

    // ── CSV quoting ────────────────────────────────────────────

    @Test
    fun `handles unquoted fields`() {
        val csv = """
            Date,Time,Sys,Dia,Pulse,Irregular Pulse,Source
            2025-04-10,07:30:00,120,80,72,,Omron
        """.trimIndent()

        val results = parse(csv)
        assertEquals(1, results.size)
        assertEquals(120.0, results[0].systolic, 0.01)
    }

    @Test
    fun `handles comma inside quoted field`() {
        val csv = """
            "Date","Time","Sys","Dia","Pulse","Irregular Pulse","Source"
            "2025-04-10","07:30:00","120","80","72","","Omron, Inc."
        """.trimIndent()

        val results = parse(csv)
        assertEquals(1, results.size)
        assertEquals("Omron, Inc.", results[0].source)
    }

    // ── Deduplication ──────────────────────────────────────────

    @Test
    fun `deduplicates identical rows`() {
        val csv = """
            "Date","Time","Sys","Dia","Pulse","Irregular Pulse","Source"
            "2025-04-10","07:30:00","120","80","72","","Device"
            "2025-04-10","07:30:00","120","80","72","","Device"
        """.trimIndent()

        assertEquals(1, parse(csv).size)
    }

    @Test
    fun `deduplicates by measurement identity ignoring source`() {
        val csv = """
            "Date","Time","Sys","Dia","Pulse","Irregular Pulse","Source"
            "2025-04-10","07:30:00","120","80","72","","Device A"
            "2025-04-10","07:30:00","120","80","72","","Device B"
        """.trimIndent()

        // Same date/time/sys/dia/pulse → deduplicated
        assertEquals(1, parse(csv).size)
    }

    @Test
    fun `keeps rows with different measurements at same time`() {
        val csv = """
            "Date","Time","Sys","Dia","Pulse","Irregular Pulse","Source"
            "2025-04-10","07:30:00","120","80","72","","Device"
            "2025-04-10","07:30:00","125","82","70","","Device"
        """.trimIndent()

        // Different systolic/diastolic → not duplicates
        assertEquals(2, parse(csv).size)
    }

    @Test
    fun `keeps rows with same measurements at different times`() {
        val csv = """
            "Date","Time","Sys","Dia","Pulse","Irregular Pulse","Source"
            "2025-04-10","07:30:00","120","80","72","","Device"
            "2025-04-10","19:30:00","120","80","72","","Device"
        """.trimIndent()

        assertEquals(2, parse(csv).size)
    }
}
