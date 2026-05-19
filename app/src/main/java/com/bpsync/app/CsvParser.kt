package com.bpsync.app

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

data class BpReading(
    val date: LocalDate,
    val time: LocalTime,
    val systolic: Double,
    val diastolic: Double,
    val pulse: Long,
    val irregularPulse: Boolean,
    val source: String
)

object CsvParser {

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    fun parse(inputStream: InputStream): List<BpReading> {
        val readings = mutableListOf<BpReading>()
        BufferedReader(InputStreamReader(inputStream)).use { reader ->
            val header = reader.readLine() ?: return readings // skip header

            reader.lineSequence().forEach { rawLine ->
                val line = rawLine.trim()
                if (line.isEmpty()) return@forEach

                val fields = parseCsvLine(line)
                if (fields.size < 7) return@forEach

                try {
                    val reading = BpReading(
                        date = LocalDate.parse(fields[0].trim(), dateFormatter),
                        time = LocalTime.parse(fields[1].trim()),
                        systolic = fields[2].trim().toDouble(),
                        diastolic = fields[3].trim().toDouble(),
                        pulse = fields[4].trim().toLong(),
                        irregularPulse = fields[5].trim().equals("detected", ignoreCase = true),
                        source = fields[6].trim()
                    )
                    readings.add(reading)
                } catch (e: Exception) {
                    // skip malformed rows
                }
            }
        }
        // Deduplicate rows within the CSV by measurement identity
        return readings.distinctBy { r ->
            Triple(r.date, r.time, Triple(r.systolic.toLong(), r.diastolic.toLong(), r.pulse))
        }
    }

    private fun parseCsvLine(line: String): List<String> {
        val fields = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false

        for (ch in line) {
            when {
                ch == '"' -> inQuotes = !inQuotes
                ch == ',' && !inQuotes -> {
                    fields.add(current.toString())
                    current.clear()
                }
                else -> current.append(ch)
            }
        }
        fields.add(current.toString())
        return fields
    }
}
