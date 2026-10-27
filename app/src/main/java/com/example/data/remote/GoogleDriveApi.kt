package com.example.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

@JsonClass(generateAdapter = true)
data class GoogleDriveFileDto(
    @field:Json(name = "id") val id: String = "",
    @field:Json(name = "name") val name: String = "",
    @field:Json(name = "mimeType") val mimeType: String = "",
    @field:Json(name = "parents") val parents: List<String>? = null,
    @field:Json(name = "size") val size: Long? = null,
    @field:Json(name = "modifiedTime") val modifiedTime: String? = null,
    @field:Json(name = "createdTime") val createdTime: String? = null,
    @field:Json(name = "trashed") val trashed: Boolean? = false
)

@JsonClass(generateAdapter = true)
data class GoogleDriveCreateFolderRequest(
    @field:Json(name = "name") val name: String,
    @field:Json(name = "mimeType") val mimeType: String = "application/vnd.google-apps.folder",
    @field:Json(name = "parents") val parents: List<String>? = null
)

@JsonClass(generateAdapter = true)
data class GoogleDriveFileListResponse(
    @field:Json(name = "files") val files: List<GoogleDriveFileDto> = emptyList(),
    @field:Json(name = "nextPageToken") val nextPageToken: String? = null
)

@JsonClass(generateAdapter = true)
data class GoogleDriveAboutUserDto(
    @field:Json(name = "displayName") val displayName: String? = null,
    @field:Json(name = "emailAddress") val emailAddress: String? = null,
    @field:Json(name = "photoLink") val photoLink: String? = null
)

@JsonClass(generateAdapter = true)
data class GoogleDriveAboutResponse(
    @field:Json(name = "user") val user: GoogleDriveAboutUserDto? = null
)

interface GoogleDriveApi {

    @GET("drive/v3/files")
    suspend fun listFiles(
        @Header("Authorization") bearerToken: String,
        @Query("q") query: String,
        @Query("fields") fields: String = "files(id, name, mimeType, parents, size, modifiedTime, createdTime, trashed)",
        @Query("spaces") spaces: String = "drive",
        @Query("pageSize") pageSize: Int = 100
    ): Response<GoogleDriveFileListResponse>

    @POST("drive/v3/files")
    suspend fun createFolder(
        @Header("Authorization") bearerToken: String,
        @Body folder: GoogleDriveCreateFolderRequest
    ): Response<GoogleDriveFileDto>

    @POST("drive/v3/files")
    suspend fun createMetadata(
        @Header("Authorization") bearerToken: String,
        @Body metadata: GoogleDriveFileDto
    ): Response<GoogleDriveFileDto>

    @POST("upload/drive/v3/files?uploadType=multipart")
    suspend fun uploadMultipart(
        @Header("Authorization") bearerToken: String,
        @Body multipartBody: RequestBody
    ): Response<GoogleDriveFileDto>

    @PATCH("upload/drive/v3/files/{fileId}?uploadType=media")
    suspend fun updateMedia(
        @Header("Authorization") bearerToken: String,
        @Path("fileId") fileId: String,
        @Body body: RequestBody
    ): Response<GoogleDriveFileDto>

    @Streaming
    @GET("drive/v3/files/{fileId}?alt=media")
    suspend fun downloadMedia(
        @Header("Authorization") bearerToken: String,
        @Path("fileId") fileId: String
    ): Response<ResponseBody>

    @DELETE("drive/v3/files/{fileId}")
    suspend fun deleteFile(
        @Header("Authorization") bearerToken: String,
        @Path("fileId") fileId: String
    ): Response<Unit>

    @GET("drive/v3/about?fields=user")
    suspend fun getAbout(
        @Header("Authorization") bearerToken: String
    ): Response<GoogleDriveAboutResponse>
}
