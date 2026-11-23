package ua.robochyichas.app.data.cloud

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class DriveUnauthorizedException(message: String) : Exception(message)
class DriveNotFoundException(message: String) : Exception(message)
class DriveNetworkException(message: String, cause: Throwable? = null) : Exception(message, cause)
class DriveApiException(val code: Int, message: String) : Exception(message)

data class DriveFileMetadata(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("modifiedTime") val modifiedTime: String? = null,
    @SerializedName("mimeType") val mimeType: String? = null
)

private data class DriveFileListResponse(
    @SerializedName("files") val files: List<DriveFileMetadata>? = null
)

class GoogleDriveApiClient(
    private val baseUrl: String = "https://www.googleapis.com",
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .build()
) {
    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val normalizedBaseUrl = baseUrl.trimEnd('/')

    private fun executeRequest(request: Request): String {
        val response = try {
            httpClient.newCall(request).execute()
        } catch (e: IOException) {
            throw DriveNetworkException("Помилка мережі під час звернення до Google Диску", e)
        }

        response.use { resp ->
            val bodyStr = resp.body?.string().orEmpty()
            return when {
                resp.isSuccessful -> bodyStr
                resp.code == 401 -> throw DriveUnauthorizedException("Сесія Google застаріла (401)")
                resp.code == 404 -> throw DriveNotFoundException("Файл не знайдено на Google Диску (404)")
                else -> throw DriveApiException(resp.code, "Помилка Google Drive API (${resp.code}): ${bodyStr.take(240)}")
            }
        }
    }

    suspend fun findFileInAppData(accessToken: String, fileName: String): DriveFileMetadata? =
        withContext(Dispatchers.IO) {
            val url = "$normalizedBaseUrl/drive/v3/files".toHttpUrl().newBuilder()
                .addQueryParameter("spaces", "appDataFolder")
                .addQueryParameter("q", "name = '$fileName' and trashed = false")
                .addQueryParameter("fields", "files(id,name,modifiedTime,mimeType)")
                .addQueryParameter("orderBy", "modifiedTime desc")
                .addQueryParameter("pageSize", "5")
                .build()

            val req = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()

            val body = executeRequest(req)
            gson.fromJson(body, DriveFileListResponse::class.java)?.files?.firstOrNull()
        }

    suspend fun findVisibleFolder(accessToken: String, folderName: String): DriveFileMetadata? =
        withContext(Dispatchers.IO) {
            val url = "$normalizedBaseUrl/drive/v3/files".toHttpUrl().newBuilder()
                .addQueryParameter("spaces", "drive")
                .addQueryParameter(
                    "q",
                    "name = '$folderName' and mimeType = 'application/vnd.google-apps.folder' and trashed = false"
                )
                .addQueryParameter("fields", "files(id,name,modifiedTime,mimeType)")
                .addQueryParameter("pageSize", "5")
                .build()

            val req = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()

            val body = executeRequest(req)
            gson.fromJson(body, DriveFileListResponse::class.java)?.files?.firstOrNull()
        }

    suspend fun createVisibleFolder(accessToken: String, folderName: String): DriveFileMetadata =
        createFolderInParent(accessToken = accessToken, folderName = folderName, parentId = "root")

    suspend fun listFoldersInParent(
        accessToken: String,
        parentId: String = "root"
    ): List<DriveFileMetadata> = withContext(Dispatchers.IO) {
        val safeParent = parentId.ifBlank { "root" }
        val url = "$normalizedBaseUrl/drive/v3/files".toHttpUrl().newBuilder()
            .addQueryParameter("spaces", "drive")
            .addQueryParameter(
                "q",
                "'$safeParent' in parents and mimeType = 'application/vnd.google-apps.folder' and trashed = false"
            )
            .addQueryParameter("fields", "files(id,name,modifiedTime,mimeType)")
            .addQueryParameter("orderBy", "name")
            .addQueryParameter("pageSize", "100")
            .build()

        val req = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()

        val body = executeRequest(req)
        gson.fromJson(body, DriveFileListResponse::class.java)?.files.orEmpty()
    }

    suspend fun createFolderInParent(
        accessToken: String,
        folderName: String,
        parentId: String = "root"
    ): DriveFileMetadata = withContext(Dispatchers.IO) {
        val payload = buildMap<String, Any> {
            put("name", folderName)
            put("mimeType", "application/vnd.google-apps.folder")
            if (parentId.isNotBlank() && parentId != "root") {
                put("parents", listOf(parentId))
            }
        }
        val metaJson = gson.toJson(payload)
        val url = "$normalizedBaseUrl/drive/v3/files".toHttpUrl().newBuilder()
            .addQueryParameter("fields", "id,name,modifiedTime,mimeType")
            .build()

        val req = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $accessToken")
            .post(metaJson.toRequestBody(jsonMediaType))
            .build()

        val body = executeRequest(req)
        gson.fromJson(body, DriveFileMetadata::class.java)
            ?: throw DriveApiException(500, "Порожня відповідь при створенні папки Drive")
    }

    suspend fun ensureVisibleFolder(accessToken: String, folderName: String): DriveFileMetadata {
        return findVisibleFolder(accessToken, folderName) ?: createVisibleFolder(accessToken, folderName)
    }

    suspend fun findVisibleFileInFolder(
        accessToken: String,
        folderId: String,
        fileName: String
    ): DriveFileMetadata? = withContext(Dispatchers.IO) {
        val url = "$normalizedBaseUrl/drive/v3/files".toHttpUrl().newBuilder()
            .addQueryParameter("spaces", "drive")
            .addQueryParameter("q", "name = '$fileName' and '$folderId' in parents and trashed = false")
            .addQueryParameter("fields", "files(id,name,modifiedTime,mimeType)")
            .addQueryParameter("orderBy", "modifiedTime desc")
            .addQueryParameter("pageSize", "5")
            .build()

        val req = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()

        val body = executeRequest(req)
        gson.fromJson(body, DriveFileListResponse::class.java)?.files?.firstOrNull()
    }

    suspend fun downloadFile(accessToken: String, fileId: String): String =
        withContext(Dispatchers.IO) {
            val url = "$normalizedBaseUrl/drive/v3/files/$fileId".toHttpUrl().newBuilder()
                .addQueryParameter("alt", "media")
                .build()

            val req = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()

            executeRequest(req)
        }

    suspend fun uploadOrUpdateJsonFile(
        accessToken: String,
        existingFileId: String?,
        fileName: String,
        parentIds: List<String>,
        jsonContent: String
    ): DriveFileMetadata = withContext(Dispatchers.IO) {
        if (!existingFileId.isNullOrBlank()) {
            val updateUrl = "$normalizedBaseUrl/upload/drive/v3/files/$existingFileId".toHttpUrl().newBuilder()
                .addQueryParameter("uploadType", "media")
                .addQueryParameter("fields", "id,name,modifiedTime,mimeType")
                .build()

            val patchReq = Request.Builder()
                .url(updateUrl)
                .header("Authorization", "Bearer $accessToken")
                .patch(jsonContent.toRequestBody(jsonMediaType))
                .build()

            val body = executeRequest(patchReq)
            return@withContext gson.fromJson(body, DriveFileMetadata::class.java)
                ?: throw DriveApiException(500, "Порожня відповідь при оновленні файлу Drive")
        }

        val metadataMap = mapOf(
            "name" to fileName,
            "parents" to parentIds,
            "mimeType" to "application/json"
        )
        val metaJson = gson.toJson(metadataMap)

        val multipartBody = MultipartBody.Builder()
            .setType("multipart/related".toMediaType())
            .addPart(metaJson.toRequestBody(jsonMediaType))
            .addPart(jsonContent.toRequestBody(jsonMediaType))
            .build()

        val createUrl = "$normalizedBaseUrl/upload/drive/v3/files".toHttpUrl().newBuilder()
            .addQueryParameter("uploadType", "multipart")
            .addQueryParameter("fields", "id,name,modifiedTime,mimeType")
            .build()

        val postReq = Request.Builder()
            .url(createUrl)
            .header("Authorization", "Bearer $accessToken")
            .post(multipartBody)
            .build()

        val body = executeRequest(postReq)
        gson.fromJson(body, DriveFileMetadata::class.java)
            ?: throw DriveApiException(500, "Порожня відповідь при створенні файлу Drive")
    }
}
