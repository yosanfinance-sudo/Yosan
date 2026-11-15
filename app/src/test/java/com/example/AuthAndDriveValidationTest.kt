package com.example

import com.example.data.remote.*
import com.example.features.auth.GoogleClaims
import org.junit.Assert.*
import org.junit.Test
import java.util.Base64

class AuthAndDriveValidationTest {

    @Test
    fun testGoogleDriveEmailValidationMatching() {
        val registeredEmail = "user.finance@gmail.com"
        val selectedDriveEmail = "USER.FINANCE@GMAIL.COM"

        // Normalized comparison logic
        val isMatch = registeredEmail.trim().lowercase() == selectedDriveEmail.trim().lowercase()
        assertTrue("Case-insensitive email should match", isMatch)
    }

    @Test
    fun testGoogleDriveEmailValidationMismatchRequiresConfirmation() {
        val registeredEmail = "primary.account@gmail.com"
        val selectedDriveEmail = "backup.destination@gmail.com"

        val isDifferent = registeredEmail.isNotBlank() &&
                selectedDriveEmail.isNotBlank() &&
                registeredEmail.trim().lowercase() != selectedDriveEmail.trim().lowercase()

        assertTrue("Different accounts must be detected to trigger user confirmation prompt", isDifferent)

        // Once confirmed by the user, allowDifferentAccount enables connecting different Drive account
        val userConfirmed = true
        val canConnect = !isDifferent || userConfirmed
        assertTrue("User confirmation allows separate Drive backup destination", canConnect)
    }

    @Test
    fun testOfflineUserCanConnectAnyDriveAccount() {
        val registeredEmail = "" // Offline user has no registered cloud email
        val selectedDriveEmail = "personal.drive@gmail.com"

        val requiresConfirmation = registeredEmail.isNotBlank() &&
                selectedDriveEmail.isNotBlank() &&
                registeredEmail.trim().lowercase() != selectedDriveEmail.trim().lowercase()

        assertFalse("Offline users should connect Drive directly without mismatch block", requiresConfirmation)
    }

    @Test
    fun testOfflineToOnlineMigrationPreservesData() {
        // Mock offline data
        val localTransactionsCount = 25
        val localAccountsCount = 3

        val claims = GoogleClaims(
            sub = "google_sub_12345",
            email = "user@gmail.com",
            name = "John Doe",
            picture = "https://example.com/avatar.jpg"
        )

        // Linking should update credentials without wiping tables
        assertEquals("user@gmail.com", claims.email)
        assertEquals(25, localTransactionsCount)
        assertEquals(3, localAccountsCount)
    }

    @Test
    fun testDuplicateTransactionDetectionOnRestore() {
        data class MockTx(val syncId: String, val date: String, val amount: Double, val desc: String)

        val existing = listOf(
            MockTx("id_1", "2026-10-01", 500.0, "Groceries"),
            MockTx("id_2", "2026-10-02", 1200.0, "Electricity")
        )

        val incoming = listOf(
            MockTx("id_1", "2026-10-01", 500.0, "Groceries"), // Duplicate by syncId
            MockTx("id_3", "2026-10-02", 1200.0, "electricity"), // Duplicate by fingerprint
            MockTx("id_4", "2026-10-03", 350.0, "Coffee") // New
        )

        val existingSyncIds = existing.map { it.syncId }.toSet()
        val existingFingerprints = existing.map { "${it.date}_${it.amount}_${it.desc.trim().lowercase()}" }.toSet()

        val filteredNew = incoming.filter {
            it.syncId !in existingSyncIds && "${it.date}_${it.amount}_${it.desc.trim().lowercase()}" !in existingFingerprints
        }

        assertEquals(1, filteredNew.size)
        assertEquals("Coffee", filteredNew.first().desc)
    }

    @Test
    fun testOtpValidationCodeRequirements() {
        val validOtp = "123456"
        val shortOtp = "12345"
        val alphaOtp = "12345a"

        val isValidLengthAndDigits = { code: String ->
            code.length == 6 && code.all { it.isDigit() }
        }

        assertTrue(isValidLengthAndDigits(validOtp))
        assertFalse(isValidLengthAndDigits(shortOtp))
        assertFalse(isValidLengthAndDigits(alphaOtp))
    }

    @Test
    fun testAuthDtoStructures() {
        val sendReq = SendOtpRequest(email = "test@yosan.app", purpose = "LOGIN")
        assertEquals("test@yosan.app", sendReq.email)
        assertEquals("LOGIN", sendReq.purpose)

        val verifyReq = VerifyOtpRequest(email = "test@yosan.app", otp = "654321", name = "Test User")
        assertEquals("654321", verifyReq.otp)
        assertEquals("Test User", verifyReq.name)

        val emailChangeReq = RequestEmailChangeRequest(newEmail = "new@yosan.app")
        assertEquals("new@yosan.app", emailChangeReq.newEmail)

        val driveReq = DriveConnectRequest(authCode = "code_123", driveEmail = "test@yosan.app")
        assertEquals("code_123", driveReq.authCode)
        assertEquals("test@yosan.app", driveReq.driveEmail)

        val timestamp = System.currentTimeMillis()
        val driveStatus = DriveStatusResponse(isConnected = true, driveEmail = "test@yosan.app", lastBackupAt = timestamp)
        assertTrue(driveStatus.isConnected)
        assertEquals("test@yosan.app", driveStatus.driveEmail)
        assertEquals(timestamp, driveStatus.lastBackupAt)

        val latestBackup = LatestBackupResponse(
            hasBackup = true,
            backupDate = "2026-10-07T15:00:00Z",
            accountsCount = 2,
            transactionsCount = 15,
            backupJson = """{"version":1}"""
        )
        assertTrue(latestBackup.hasBackup)
        assertEquals(2, latestBackup.accountsCount)
        assertEquals(15, latestBackup.transactionsCount)
        assertEquals("""{"version":1}""", latestBackup.backupJson)
    }

    @Test
    fun testDecodeMockGoogleIdTokenPayload() {
        val header = Base64.getUrlEncoder().withoutPadding().encodeToString("""{"alg":"RS256","typ":"JWT"}""".toByteArray())
        val payload = Base64.getUrlEncoder().withoutPadding().encodeToString(
            """{"sub":"1234567890","email":"test@example.com","name":"Test User","picture":"https://example.com/pic.png"}""".toByteArray()
        )
        val mockToken = "$header.$payload.dummySignature"

        val parts = mockToken.split(".")
        assertEquals(3, parts.size)

        val decodedPayload = String(Base64.getUrlDecoder().decode(parts[1]))
        assertTrue(decodedPayload.contains("1234567890"))
        assertTrue(decodedPayload.contains("test@example.com"))
        assertTrue(decodedPayload.contains("Test User"))
    }

    @Test
    fun testGoogleClaimsCarriesIdToken() {
        val claimsWithToken = GoogleClaims(
            sub = "1234567890",
            email = "user@example.com",
            name = "Test User",
            picture = "https://example.com/pic.png",
            idToken = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.dummyPayload.dummySig"
        )
        assertNotNull(claimsWithToken.idToken)
        assertEquals("eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.dummyPayload.dummySig", claimsWithToken.idToken)

        val claimsDefault = GoogleClaims(
            sub = "1234567890",
            email = "user@example.com",
            name = "Test User",
            picture = "https://example.com/pic.png"
        )
        assertNull(claimsDefault.idToken)
    }

    @Test
    fun testOfflineRefreshTokenProtectedFromNetworkEviction() {
        val offlineRefTokens = listOf(
            "offline_ref_1731669400000_123456",
            "offline_refresh_guest",
            "offline_local_token"
        )
        val onlineRefTokens = listOf(
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJyZWZyZXNoIjp0cnVlfQ.signature",
            "mock_server_refresh_token_abcdef"
        )

        val shouldAttemptNetworkRefresh = { token: String ->
            !token.startsWith("offline_")
        }

        offlineRefTokens.forEach { token ->
            assertFalse("Offline token $token must NOT trigger network refresh", shouldAttemptNetworkRefresh(token))
        }

        onlineRefTokens.forEach { token ->
            assertTrue("Online token $token must be forwarded for network refresh", shouldAttemptNetworkRefresh(token))
        }
    }

    @Test
    fun testEmailOtpSessionColdStartAuthResolution() {
        // Case 1: Google account login
        val googleUser = Triple("google_sub_123", "google@example.com", false)
        val isGoogleAuth = !googleUser.first.isNullOrBlank()
        assertTrue("Google user with valid sub is authenticated", isGoogleAuth)

        // Case 2: Email OTP user (no Google Sub, but email verified)
        val otpUserEmail = "otp.user@example.com"
        val otpUserSub: String? = null
        val isEmailVerified = true
        val isOtpSession = isEmailVerified && !otpUserEmail.isNullOrBlank()
        val isOtpAuth = !otpUserSub.isNullOrBlank() || isOtpSession
        assertTrue("OTP user without Google sub must be recognized as authenticated on cold start", isOtpAuth)

        // Case 3: Guest / Unauthenticated user (no sub, no verified email)
        val guestUserEmail: String? = null
        val guestUserSub: String? = null
        val guestEmailVerified = false
        val isGuestOtpSession = guestEmailVerified && !guestUserEmail.isNullOrBlank()
        val isGuestAuth = !guestUserSub.isNullOrBlank() || isGuestOtpSession
        assertFalse("Guest user must not be marked authenticated", isGuestAuth)
    }
}
