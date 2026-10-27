package com.example.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.Response
import retrofit2.http.*

@JsonClass(generateAdapter = true)
data class GoogleAuthRequest(
    @field:Json(name = "idToken") val idToken: String
)

@JsonClass(generateAdapter = true)
data class RefreshTokenRequest(
    @field:Json(name = "refreshToken") val refreshToken: String
)

@JsonClass(generateAdapter = true)
data class LogoutRequest(
    @field:Json(name = "refreshToken") val refreshToken: String?
)

@JsonClass(generateAdapter = true)
data class UserDto(
    @field:Json(name = "id") val id: Long,
    @field:Json(name = "googleSub") val googleSub: String,
    @field:Json(name = "email") val email: String,
    @field:Json(name = "name") val name: String,
    @field:Json(name = "profilePictureUrl") val profilePictureUrl: String? = null,
    @field:Json(name = "emailVerified") val emailVerified: Boolean = true,
    @field:Json(name = "currencySymbol") val currencySymbol: String = "₹",
    @field:Json(name = "timezone") val timezone: String = "Asia/Kolkata",
    @field:Json(name = "theme") val theme: String = "SYSTEM",
    @field:Json(name = "isDriveConnected") val isDriveConnected: Boolean = false,
    @field:Json(name = "driveEmail") val driveEmail: String? = null,
    @field:Json(name = "lastBackupAt") val lastBackupAt: Long? = null
)

@JsonClass(generateAdapter = true)
data class AuthResponse(
    @field:Json(name = "isNewUser") val isNewUser: Boolean = false,
    @field:Json(name = "user") val user: UserDto,
    @field:Json(name = "accessToken") val accessToken: String,
    @field:Json(name = "refreshToken") val refreshToken: String
)

@JsonClass(generateAdapter = true)
data class UserResponse(
    @field:Json(name = "user") val user: UserDto
)

@JsonClass(generateAdapter = true)
data class SendOtpRequest(
    @field:Json(name = "email") val email: String,
    @field:Json(name = "purpose") val purpose: String = "LOGIN"
)

@JsonClass(generateAdapter = true)
data class SendOtpResponse(
    @field:Json(name = "success") val success: Boolean = true,
    @field:Json(name = "message") val message: String = "",
    @field:Json(name = "cooldownSeconds") val cooldownSeconds: Int = 60,
    @field:Json(name = "devOtp") val devOtp: String? = null
)

@JsonClass(generateAdapter = true)
data class VerifyOtpRequest(
    @field:Json(name = "email") val email: String,
    @field:Json(name = "otp") val otp: String,
    @field:Json(name = "purpose") val purpose: String = "LOGIN",
    @field:Json(name = "name") val name: String? = null
)

@JsonClass(generateAdapter = true)
data class RequestEmailChangeRequest(
    @field:Json(name = "newEmail") val newEmail: String
)

@JsonClass(generateAdapter = true)
data class VerifyEmailChangeRequest(
    @field:Json(name = "newEmail") val newEmail: String,
    @field:Json(name = "otp") val otp: String
)

@JsonClass(generateAdapter = true)
data class DriveConnectRequest(
    @field:Json(name = "authCode") val authCode: String? = null,
    @field:Json(name = "refreshToken") val refreshToken: String? = null,
    @field:Json(name = "googleSub") val googleSub: String? = null,
    @field:Json(name = "driveEmail") val driveEmail: String? = null
)

@JsonClass(generateAdapter = true)
data class DriveStatusResponse(
    @field:Json(name = "isConnected") val isConnected: Boolean,
    @field:Json(name = "googleAccountSub") val googleAccountSub: String? = null,
    @field:Json(name = "driveEmail") val driveEmail: String? = null,
    @field:Json(name = "lastBackupAt") val lastBackupAt: Long? = null,
    @field:Json(name = "currentBackupVersion") val currentBackupVersion: Int? = null,
    @field:Json(name = "driveFolderId") val driveFolderId: String? = null
)

@JsonClass(generateAdapter = true)
data class BackupNowRequest(
    @field:Json(name = "payload") val payload: Map<String, Any?>? = null
)

@JsonClass(generateAdapter = true)
data class BackupNowResponse(
    @field:Json(name = "success") val success: Boolean = true,
    @field:Json(name = "fileId") val fileId: String? = null,
    @field:Json(name = "fileName") val fileName: String? = null,
    @field:Json(name = "version") val version: Int = 1,
    @field:Json(name = "backupDate") val backupDate: String? = null,
    @field:Json(name = "transactionCount") val transactionCount: Int = 0,
    @field:Json(name = "message") val message: String? = null
)

@JsonClass(generateAdapter = true)
data class DriveBackupVersionDto(
    @field:Json(name = "version") val version: Int = 1,
    @field:Json(name = "fileName") val fileName: String = "",
    @field:Json(name = "fileId") val fileId: String = "",
    @field:Json(name = "createdTime") val createdTime: String? = null,
    @field:Json(name = "transactionCount") val transactionCount: Int = 0,
    @field:Json(name = "isCurrent") val isCurrent: Boolean = false,
    @field:Json(name = "checksum") val checksum: String? = null
)

@JsonClass(generateAdapter = true)
data class DriveBackupHistoryResponse(
    @field:Json(name = "backups") val backups: List<DriveBackupVersionDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class BackupVersionResponse(
    @field:Json(name = "success") val success: Boolean = true,
    @field:Json(name = "version") val version: Int = 1,
    @field:Json(name = "backupJson") val backupJson: String = ""
)

@JsonClass(generateAdapter = true)
data class RestoreVersionRequest(
    @field:Json(name = "version") val version: Int,
    @field:Json(name = "confirm") val confirm: Boolean = true
)

@JsonClass(generateAdapter = true)
data class RestoreBackupRequest(
    @field:Json(name = "confirm") val confirm: Boolean = true
)

@JsonClass(generateAdapter = true)
data class LatestBackupResponse(
    @field:Json(name = "hasBackup") val hasBackup: Boolean = false,
    @field:Json(name = "backupDate") val backupDate: String? = null,
    @field:Json(name = "accountsCount") val accountsCount: Int = 0,
    @field:Json(name = "transactionsCount") val transactionsCount: Int = 0,
    @field:Json(name = "backupJson") val backupJson: String? = null
)

@JsonClass(generateAdapter = true)
data class ApiResponse(
    @field:Json(name = "success") val success: Boolean = true,
    @field:Json(name = "message") val message: String = ""
)

@JsonClass(generateAdapter = true)
data class ChatMessageDto(
    @field:Json(name = "role") val role: String,
    @field:Json(name = "text") val text: String
)

@JsonClass(generateAdapter = true)
data class CategoryShareDto(
    @field:Json(name = "category") val category: String,
    @field:Json(name = "amount") val amount: Double,
    @field:Json(name = "share") val share: String
)

@JsonClass(generateAdapter = true)
data class ExpenseItemDto(
    @field:Json(name = "date") val date: String,
    @field:Json(name = "description") val description: String,
    @field:Json(name = "category") val category: String,
    @field:Json(name = "amount") val amount: Double
)

@JsonClass(generateAdapter = true)
data class TransactionItemDto(
    @field:Json(name = "date") val date: String,
    @field:Json(name = "description") val description: String,
    @field:Json(name = "type") val type: String,
    @field:Json(name = "amount") val amount: Double,
    @field:Json(name = "category") val category: String
)

@JsonClass(generateAdapter = true)
data class AccountItemDto(
    @field:Json(name = "name") val name: String,
    @field:Json(name = "type") val type: String,
    @field:Json(name = "balance") val balance: Double
)

@JsonClass(generateAdapter = true)
data class LoanItemDto(
    @field:Json(name = "name") val name: String,
    @field:Json(name = "type") val type: String,
    @field:Json(name = "totalAmount") val totalAmount: Double,
    @field:Json(name = "remainingAmount") val remainingAmount: Double,
    @field:Json(name = "dueDate") val dueDate: String,
    @field:Json(name = "status") val status: String
)

@JsonClass(generateAdapter = true)
data class FinancialClientContextDto(
    @field:Json(name = "userName") val userName: String? = null,
    @field:Json(name = "currencySymbol") val currencySymbol: String? = null,
    @field:Json(name = "totalBalance") val totalBalance: Double? = null,
    @field:Json(name = "monthlyIncome") val monthlyIncome: Double? = null,
    @field:Json(name = "monthlyExpense") val monthlyExpense: Double? = null,
    @field:Json(name = "accountsSummary") val accountsSummary: List<AccountItemDto>? = null,
    @field:Json(name = "categoryDistribution") val categoryDistribution: List<CategoryShareDto>? = null,
    @field:Json(name = "highestExpenses") val highestExpenses: List<ExpenseItemDto>? = null,
    @field:Json(name = "recentTransactions") val recentTransactions: List<TransactionItemDto>? = null,
    @field:Json(name = "loansSummary") val loansSummary: List<LoanItemDto>? = null
)

@JsonClass(generateAdapter = true)
data class ChatRequest(
    @field:Json(name = "message") val message: String,
    @field:Json(name = "history") val history: List<ChatMessageDto>? = null,
    @field:Json(name = "clientContext") val clientContext: FinancialClientContextDto? = null
)

@JsonClass(generateAdapter = true)
data class ChatResponse(
    @field:Json(name = "success") val success: Boolean = true,
    @field:Json(name = "reply") val reply: String = "",
    @field:Json(name = "source") val source: String = "gemini"
)

@JsonClass(generateAdapter = true)
data class FeedbackRequest(
    @field:Json(name = "category") val category: String,
    @field:Json(name = "rating") val rating: Int,
    @field:Json(name = "subject") val subject: String,
    @field:Json(name = "description") val description: String,
    @field:Json(name = "appVersion") val appVersion: String = "v1.1.2",
    @field:Json(name = "deviceModel") val deviceModel: String = "",
    @field:Json(name = "androidVersion") val androidVersion: String = "",
    @field:Json(name = "syncId") val syncId: String? = null
)

@JsonClass(generateAdapter = true)
data class FeedbackResponse(
    @field:Json(name = "success") val success: Boolean = true,
    @field:Json(name = "feedbackId") val feedbackId: Long? = null,
    @field:Json(name = "isDuplicate") val isDuplicate: Boolean = false,
    @field:Json(name = "message") val message: String = ""
)

@JsonClass(generateAdapter = true)
data class StatementAiParseRequest(
    @field:Json(name = "textContent") val textContent: String,
    @field:Json(name = "fileName") val fileName: String? = null
)

@JsonClass(generateAdapter = true)
data class StatementAiTransactionDto(
    @field:Json(name = "transactionDate") val transactionDate: String = "",
    @field:Json(name = "description") val description: String = "",
    @field:Json(name = "debitAmount") val debitAmount: Double = 0.0,
    @field:Json(name = "creditAmount") val creditAmount: Double = 0.0,
    @field:Json(name = "amount") val amount: Double = 0.0,
    @field:Json(name = "transactionType") val transactionType: String = "EXPENSE",
    @field:Json(name = "balanceAfterTransaction") val balanceAfterTransaction: Double? = null,
    @field:Json(name = "suggestedCategory") val suggestedCategory: String = "General",
    @field:Json(name = "referenceNumber") val referenceNumber: String = ""
)

@JsonClass(generateAdapter = true)
data class StatementAiParseResponse(
    @field:Json(name = "success") val success: Boolean = true,
    @field:Json(name = "transactions") val transactions: List<StatementAiTransactionDto> = emptyList(),
    @field:Json(name = "totalParsed") val totalParsed: Int = 0,
    @field:Json(name = "source") val source: String = "local_ai_cleaner"
)

interface AuthApi {

    @POST("api/auth/google")
    suspend fun authenticateGoogle(@Body req: GoogleAuthRequest): Response<AuthResponse>

    @POST("api/auth/send-otp")
    suspend fun sendOtp(@Body req: SendOtpRequest): Response<SendOtpResponse>

    @POST("api/auth/verify-otp")
    suspend fun verifyOtp(@Body req: VerifyOtpRequest): Response<AuthResponse>

    @POST("api/auth/request-email-change")
    suspend fun requestEmailChange(@Body req: RequestEmailChangeRequest): Response<ApiResponse>

    @POST("api/auth/verify-email-change")
    suspend fun verifyEmailChange(@Body req: VerifyEmailChangeRequest): Response<ApiResponse>

    @POST("api/auth/refresh")
    suspend fun refreshSession(@Body req: RefreshTokenRequest): Response<AuthResponse>

    @POST("api/auth/logout")
    suspend fun logout(@Body req: LogoutRequest): Response<ApiResponse>

    @GET("api/auth/me")
    suspend fun getCurrentUser(): Response<UserResponse>

    @DELETE("api/auth/account")
    suspend fun deleteAccount(): Response<ApiResponse>

    @POST("api/drive/connect")
    suspend fun connectDrive(@Body req: DriveConnectRequest): Response<ApiResponse>

    @GET("api/drive/status")
    suspend fun getDriveStatus(): Response<DriveStatusResponse>

    @POST("api/drive/disconnect")
    suspend fun disconnectDrive(): Response<ApiResponse>

    @POST("api/drive/backup/now")
    suspend fun backupNow(@Body req: BackupNowRequest = BackupNowRequest()): Response<BackupNowResponse>

    @GET("api/drive/backups")
    suspend fun getBackupHistory(): Response<DriveBackupHistoryResponse>

    @GET("api/drive/backup/version/{version}")
    suspend fun getBackupVersion(@retrofit2.http.Path("version") version: Int): Response<BackupVersionResponse>

    @POST("api/drive/backup/restore-version")
    suspend fun restoreBackupVersion(@Body req: RestoreVersionRequest): Response<ApiResponse>

    @GET("api/drive/backup/latest")
    suspend fun getLatestBackup(): Response<LatestBackupResponse>

    @POST("api/drive/backup/restore")
    suspend fun restoreBackup(@Body req: RestoreBackupRequest): Response<ApiResponse>

    @POST("api/chat")
    suspend fun sendChatMessage(@Body req: ChatRequest): Response<ChatResponse>

    @POST("api/feedback")
    suspend fun submitFeedback(@Body req: FeedbackRequest): Response<FeedbackResponse>

    @POST("api/statement/parse-ai")
    suspend fun parseStatementAi(@Body req: StatementAiParseRequest): Response<StatementAiParseResponse>
}
