package com.bpsync.app

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Pressure
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

data class WriteResult(val written: Int, val skipped: Int)

object HealthConnectWriter {

    val PERMISSIONS = setOf(
        HealthPermission.getWritePermission(BloodPressureRecord::class),
        HealthPermission.getWritePermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(BloodPressureRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
    )

    fun isAvailable(context: Context): Boolean {
        val status = HealthConnectClient.getSdkStatus(context)
        return status == HealthConnectClient.SDK_AVAILABLE
    }

    /**
     * Build a fingerprint for an existing BP record so we can detect duplicates.
     * Key = "epoch_millis|sys_mmHg|dia_mmHg"
     */
    private fun fingerprint(record: BloodPressureRecord): String {
        val t = record.time.toEpochMilli()
        val s = record.systolic.inMillimetersOfMercury.toLong()
        val d = record.diastolic.inMillimetersOfMercury.toLong()
        return "$t|$s|$d"
    }

    private fun fingerprint(reading: BpReading, zone: ZoneId): String {
        val t = ZonedDateTime.of(reading.date, reading.time, zone).toInstant().toEpochMilli()
        val s = reading.systolic.toLong()
        val d = reading.diastolic.toLong()
        return "$t|$s|$d"
    }

    /**
     * Read all existing BP records in the given time window from Health Connect.
     */
    private suspend fun readExistingFingerprints(
        client: HealthConnectClient,
        startTime: Instant,
        endTime: Instant
    ): Set<String> {
        val existing = mutableSetOf<String>()
        val request = ReadRecordsRequest(
            recordType = BloodPressureRecord::class,
            timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
        )
        val response = client.readRecords(request)
        for (record in response.records) {
            existing.add(fingerprint(record))
        }
        return existing
    }

    suspend fun writeReadings(
        context: Context,
        readings: List<BpReading>,
        onProgress: (current: Int, total: Int) -> Unit
    ): Result<WriteResult> {
        return try {
            val client = HealthConnectClient.getOrCreate(context)
            val zone = ZoneId.systemDefault()

            // Determine the time range covered by the CSV
            val instants = readings.map {
                ZonedDateTime.of(it.date, it.time, zone).toInstant()
            }
            val earliest = instants.min().minusSeconds(60)
            val latest = instants.max().plusSeconds(60)

            // Fetch existing records to detect duplicates
            val existingFingerprints = readExistingFingerprints(client, earliest, latest)

            // Filter out readings that already exist
            val newReadings = readings.filter { reading ->
                fingerprint(reading, zone) !in existingFingerprints
            }
            val skipped = readings.size - newReadings.size

            var written = 0

            // Batch in groups of 50
            val batches = newReadings.chunked(50)

            for (batch in batches) {
                val bpRecords = mutableListOf<BloodPressureRecord>()
                val hrRecords = mutableListOf<HeartRateRecord>()

                for (reading in batch) {
                    val zdt = ZonedDateTime.of(reading.date, reading.time, zone)
                    val instant = zdt.toInstant()
                    val zoneOffset = zdt.offset

                    val device = Device(
                        manufacturer = reading.source,
                        model = null,
                        type = Device.TYPE_UNKNOWN,
                    )
                    val metadata = Metadata.manualEntry(device = device)

                    bpRecords.add(
                        BloodPressureRecord(
                            time = instant,
                            zoneOffset = zoneOffset,
                            systolic = Pressure.millimetersOfMercury(reading.systolic),
                            diastolic = Pressure.millimetersOfMercury(reading.diastolic),
                            bodyPosition = BloodPressureRecord.BODY_POSITION_UNKNOWN,
                            measurementLocation = BloodPressureRecord.MEASUREMENT_LOCATION_LEFT_UPPER_ARM,
                            metadata = metadata,
                        )
                    )

                    hrRecords.add(
                        HeartRateRecord(
                            startTime = instant,
                            startZoneOffset = zoneOffset,
                            endTime = instant.plusSeconds(1),
                            endZoneOffset = zoneOffset,
                            samples = listOf(
                                HeartRateRecord.Sample(
                                    time = instant,
                                    beatsPerMinute = reading.pulse
                                )
                            ),
                            metadata = metadata,
                        )
                    )
                }

                client.insertRecords(bpRecords + hrRecords)
                written += batch.size
                onProgress(written + skipped, readings.size)
            }

            Result.success(WriteResult(written = written, skipped = skipped))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
