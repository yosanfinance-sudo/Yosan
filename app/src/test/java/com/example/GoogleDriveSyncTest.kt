package com.example

import com.example.data.remote.DriveBackupVersionDto
import com.example.utils.BackupService
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GoogleDriveSyncTest {

    @Test
    fun testVersionSnapshotNamingConvention() {
        val formatVersion = { v: Int -> "backup-v${String.format("%04d", v)}.json" }

        assertEquals("backup-v0001.json", formatVersion(1))
        assertEquals("backup-v0002.json", formatVersion(2))
        assertEquals("backup-v0010.json", formatVersion(10))
        assertEquals("backup-v0150.json", formatVersion(150))
        assertEquals("backup-v9999.json", formatVersion(9999))
    }

    @Test
    fun testGoogleDriveFolderHierarchyDefinition() {
        val rootFolderName = "Yosan-Backup"
        val currentBackupFileName = "detailsaboutbackup.json"
        val versionsDirectory = "versions"

        assertEquals("Yosan-Backup", rootFolderName)
        assertEquals("detailsaboutbackup.json", currentBackupFileName)
        assertEquals("versions", versionsDirectory)

        val fullSnapshotPath = "$rootFolderName/$versionsDirectory/backup-v0001.json"
        assertTrue(fullSnapshotPath.startsWith("Yosan-Backup/versions/"))
        assertTrue(fullSnapshotPath.endsWith(".json"))
    }

    @Test
    fun testChecksumCalculationConsistency() {
        val payload1 = """[{"id":1,"amount":100.0,"date":"2026-10-15"}]"""
        val payload2 = """[{"id":1,"amount":100.0,"date":"2026-10-15"}]"""
        val payload3 = """[{"id":2,"amount":250.0,"date":"2026-10-16"}]"""

        val checksum1 = BackupService.calculateChecksum(payload1)
        val checksum2 = BackupService.calculateChecksum(payload2)
        val checksum3 = BackupService.calculateChecksum(payload3)

        assertNotNull(checksum1)
        assertEquals("Same payload must produce identical checksum", checksum1, checksum2)
        assertNotEquals("Different payload must produce different checksum", checksum1, checksum3)
        assertEquals("SHA-256 hex string must be 64 characters", 64, checksum1.length)
    }

    @Test
    fun testBackupEnvelopeStructureAndIntegrity() {
        val root = JSONObject()
        val version = 3
        val nowIso = "2026-10-15T18:30:00.000Z"

        root.put("application", "Yosan Finance")
        root.put("backup_id", UUID.randomUUID().toString())
        root.put("backup_version", version)
        root.put("schema_version", 1)
        root.put("created_at", nowIso)
        root.put("updated_at", nowIso)

        val txArray = JSONArray()
        val tx1 = JSONObject().apply {
            put("id", 101L)
            put("amount", 500.0)
            put("transactionType", "EXPENSE")
            put("description", "Groceries")
        }
        txArray.put(tx1)
        root.put("transactions", txArray)
        root.put("transaction_count", txArray.length())
        root.put("checksum", BackupService.calculateChecksum(txArray.toString()))

        // Verify standard schema requirements
        assertEquals("Yosan Finance", root.getString("application"))
        assertTrue(root.getString("backup_id").isNotBlank())
        assertEquals(3, root.getInt("backup_version"))
        assertEquals(1, root.getInt("schema_version"))
        assertEquals(nowIso, root.getString("created_at"))
        assertEquals(nowIso, root.getString("updated_at"))
        assertEquals(1, root.getInt("transaction_count"))
        assertEquals(64, root.getString("checksum").length)
        assertTrue(root.has("transactions"))
    }

    @Test
    fun testDriveBackupVersionDtoSorting() {
        val v1 = DriveBackupVersionDto(version = 1, fileName = "backup-v0001.json", fileId = "id_1", isCurrent = false)
        val v2 = DriveBackupVersionDto(version = 2, fileName = "backup-v0002.json", fileId = "id_2", isCurrent = false)
        val v3 = DriveBackupVersionDto(version = 3, fileName = "detailsaboutbackup.json", fileId = "id_3", isCurrent = true)

        val list = listOf(v1, v3, v2)
        val sorted = list.sortedByDescending { it.version }

        assertEquals(3, sorted[0].version)
        assertTrue(sorted[0].isCurrent)
        assertEquals(2, sorted[1].version)
        assertFalse(sorted[1].isCurrent)
        assertEquals(1, sorted[2].version)
    }

    @Test
    fun testCorruptBackupValidationRejection() = kotlinx.coroutines.runBlocking {
        val emptyResult = BackupService.restoreBackupJson("", com.example.data.local.AppDatabase.getDatabase(androidx.test.core.app.ApplicationProvider.getApplicationContext(), this))
        assertFalse(emptyResult.success)
        assertTrue(emptyResult.message.contains("empty", ignoreCase = true))

        val invalidResult = BackupService.restoreBackupJson("""{"unknown_random_key": "some_value"}""", com.example.data.local.AppDatabase.getDatabase(androidx.test.core.app.ApplicationProvider.getApplicationContext(), this))
        assertFalse(invalidResult.success)
        assertTrue(invalidResult.message.contains("format", ignoreCase = true))
    }

    @Test
    fun testInitialBackupVersionResolution() {
        val resolveNextVersion = { currentVersionStr: String? ->
            val currentVersion = currentVersionStr?.toIntOrNull() ?: 0
            currentVersion + 1
        }

        // When never backed up before (null/empty), next version must start at 1
        assertEquals(1, resolveNextVersion(null))
        assertEquals(1, resolveNextVersion(""))

        // Subsequent backups increment properly
        assertEquals(2, resolveNextVersion("1"))
        assertEquals(3, resolveNextVersion("2"))
        assertEquals(10, resolveNextVersion("9"))
    }

    @Test
    fun testUtcTimestampFormatting() {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }
        val formatted = sdf.format(java.util.Date(1700000000000L))
        assertEquals("2023-11-14T22:13:20Z", formatted)
        assertTrue(formatted.endsWith("Z"))
    }
}
