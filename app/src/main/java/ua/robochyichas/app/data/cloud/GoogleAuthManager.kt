package ua.robochyichas.app.data.cloud

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

data class GoogleUserProfile(
    val email: String?,
    val name: String?
)

sealed class AuthOutcome {
    data class Authorized(
        val accessToken: String,
        val email: String?,
        val name: String?
    ) : AuthOutcome()

    data class NeedsResolution(val pendingIntent: PendingIntent) : AuthOutcome()

    data class SetupRequired(
        val statusCode: Int,
        val message: String
    ) : AuthOutcome()

    data class Failed(val message: String) : AuthOutcome()
}

interface AccessTokenProvider {
    suspend fun getAccessTokenSilently(): AuthOutcome
}

class GoogleAuthManager(
    private val context: Context,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) : AccessTokenProvider {

    private val authClient by lazy { Identity.getAuthorizationClient(context) }

    private fun buildRequest(): AuthorizationRequest {
        val scopes = listOf(
            Scope(SCOPE_DRIVE_FILE),
            Scope(SCOPE_DRIVE_APPDATA),
            Scope("email"),
            Scope("profile")
        )
        return AuthorizationRequest.builder()
            .setRequestedScopes(scopes)
            .build()
    }

    suspend fun requestAuthorizationInteractive(): AuthOutcome = authorizeInternal()

    override suspend fun getAccessTokenSilently(): AuthOutcome = authorizeInternal()

    private suspend fun authorizeInternal(): AuthOutcome {
        val resultOrError = suspendCancellableCoroutine<Result<AuthorizationResult>> { cont ->
            authClient.authorize(buildRequest())
                .addOnSuccessListener { result ->
                    if (cont.isActive) cont.resume(Result.success(result))
                }
                .addOnFailureListener { exc ->
                    if (cont.isActive) cont.resume(Result.failure(exc))
                }
        }

        val result = resultOrError.getOrElse { exc ->
            return mapExceptionToOutcome(exc)
        }

        if (result.hasResolution()) {
            val pi = result.pendingIntent
            return if (pi != null) {
                AuthOutcome.NeedsResolution(pi)
            } else {
                AuthOutcome.Failed("Потрібне підтвердження облікового запису Google")
            }
        }

        val token = result.accessToken
        if (token.isNullOrBlank()) {
            return AuthOutcome.Failed("Не вдалося отримати токен доступу Google Drive")
        }

        val account = result.toGoogleSignInAccount()
        val fetched = if (account?.email.isNullOrBlank()) fetchUserProfile(token) else null
        return AuthOutcome.Authorized(
            accessToken = token,
            email = account?.email ?: fetched?.email,
            name = account?.displayName ?: fetched?.name
        )
    }

    suspend fun handleResolutionIntent(data: Intent?): AuthOutcome {
        if (data == null) {
            return AuthOutcome.Failed("Авторизацію скасовано")
        }
        val result = try {
            authClient.getAuthorizationResultFromIntent(data)
        } catch (exc: Exception) {
            return mapExceptionToOutcome(exc)
        }

        val token = result.accessToken
        if (token.isNullOrBlank()) {
            return AuthOutcome.Failed("Токен доступу не отримано після входу")
        }

        val account = result.toGoogleSignInAccount()
        val fetched = if (account?.email.isNullOrBlank()) fetchUserProfile(token) else null
        return AuthOutcome.Authorized(
            accessToken = token,
            email = account?.email ?: fetched?.email,
            name = account?.displayName ?: fetched?.name
        )
    }

    private fun mapExceptionToOutcome(exc: Throwable): AuthOutcome {
        if (exc is ApiException) {
            val statusCode = exc.statusCode
            val resolution = exc.status.resolution
            if (resolution != null) {
                return AuthOutcome.NeedsResolution(resolution)
            }
            return when (statusCode) {
                CommonStatusCodes.DEVELOPER_ERROR,
                12500, // SIGN_IN_FAILED
                28444, // OAuth client not configured in Cloud Console
                CommonStatusCodes.INTERNAL_ERROR -> AuthOutcome.SetupRequired(
                    statusCode = statusCode,
                    message = "Потрібно зареєструвати SHA-1 та package name у Google Cloud Console (код $statusCode)"
                )
                CommonStatusCodes.CANCELED, 12501 -> AuthOutcome.Failed("Вхід у Google скасовано")
                CommonStatusCodes.NETWORK_ERROR -> AuthOutcome.Failed("Немає з'єднання з серверами Google")
                else -> AuthOutcome.Failed("Помилка Google Play Services (код $statusCode)")
            }
        }
        return AuthOutcome.Failed(exc.message ?: "Помилка авторизації Google")
    }

    suspend fun fetchUserProfile(accessToken: String): GoogleUserProfile? = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url("https://www.googleapis.com/oauth2/v3/userinfo")
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching null
                val body = resp.body?.string().orEmpty()
                val obj = JsonParser.parseString(body).asJsonObject
                val email = obj.get("email")?.takeIf { !it.isJsonNull }?.asString
                val name = obj.get("name")?.takeIf { !it.isJsonNull }?.asString
                GoogleUserProfile(email = email, name = name)
            }
        }.getOrNull()
    }

    suspend fun revokeTokenBestEffort(token: String?) {
        if (token.isNullOrBlank()) return
        withContext(Dispatchers.IO) {
            runCatching {
                val req = Request.Builder()
                    .url("https://oauth2.googleapis.com/revoke?token=$token")
                    .post(ByteArray(0).toRequestBody(null))
                    .build()
                httpClient.newCall(req).execute().close()
            }
        }
        runCatching {
            Identity.getSignInClient(context).signOut()
        }
    }

    companion object {
        const val SCOPE_DRIVE_FILE = "https://www.googleapis.com/auth/drive.file"
        const val SCOPE_DRIVE_APPDATA = "https://www.googleapis.com/auth/drive.appdata"

        fun getAppSigningSha1(context: Context): String {
            return runCatching {
                val pm = context.packageManager
                val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val info = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                    val signingInfo = info.signingInfo
                    if (signingInfo != null) {
                        if (signingInfo.hasMultipleSigners()) signingInfo.apkContentsSigners
                        else signingInfo.signingCertificateHistory
                    } else {
                        emptyArray()
                    }
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures ?: emptyArray()
                }
                val certBytes = signatures.firstOrNull()?.toByteArray() ?: return "Невідомо"
                val digest = MessageDigest.getInstance("SHA-1").digest(certBytes)
                digest.joinToString(":") { "%02X".format(it) }
            }.getOrDefault("Невідомо")
        }
    }
}
