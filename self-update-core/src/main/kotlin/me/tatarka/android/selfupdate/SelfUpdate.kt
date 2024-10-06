@file:OptIn(ExperimentalSerializationApi::class)

package me.tatarka.android.selfupdate

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageInstaller.EXTRA_STATUS
import android.content.pm.PackageInstaller.EXTRA_STATUS_MESSAGE
import android.content.pm.PackageInstaller.SessionParams
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okio.use
import ru.gildor.coroutines.okhttp.await
import java.io.IOException
import kotlin.coroutines.resume

private val CommitFlow = MutableSharedFlow<Result<Unit>>(extraBufferCapacity = 1)

class SelfUpdate(
    private val context: Context,
    private val receiver: Class<out BroadcastReceiver> = SelfUpdateReceiver::class.java,
) {

    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }
    private var installingJob: Job? = null

    suspend fun check(
        manifestUrl: String,
        tags: Set<String>? = null,
        onlyUpgrades: Boolean = true,
    ): List<Release> {
        val url = manifestUrl.toHttpUrl()
        val manifestResponse = client.newCall(
            Request.Builder()
                .url(url)
                .build()
        ).await()

        return withContext(Dispatchers.IO) {
            val manifestBody = manifestResponse.ensureBody()
            val manifest = manifestBody.byteStream().use { stream ->
                json.decodeFromStream<me.tatarka.android.selfupdate.manifest.Manifest>(stream)
            }
            val versionCode = context.versionCode()

            filterReleases(
                manifestUrl = url,
                releases = manifest.releases,
                versionCode = versionCode,
                deviceInfo = DeviceInfo(context),
                tags = tags,
                onlyUpgrades = onlyUpgrades,
            )
        }
    }

    class Release internal constructor(
        val versionName: String,
        val versionCode: Long,
        internal val manifestUrl: HttpUrl,
        internal val artifacts: List<me.tatarka.android.selfupdate.manifest.Manifest.Artifact>,
    ) {

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Release) return false
            if (versionName != other.versionName) return false
            if (versionCode != other.versionCode) return false
            if (manifestUrl != other.manifestUrl) return false
            return true
        }

        override fun hashCode(): Int {
            var result = versionName.hashCode()
            result = 31 * result + versionCode.hashCode()
            result = 31 * result + manifestUrl.hashCode()
            return result
        }

        override fun toString(): String {
            return "Release(versionName='$versionName', versionCode=$versionCode)"
        }
    }

    suspend fun install(
        release: Release,
        onProgress: ((Float) -> Unit)? = null
    ) {
        // can only run one install at a time.
        installingJob?.cancel()

        val packageManager = context.packageManager
        val installer = packageManager.packageInstaller

        val response = download(release = release, client = client)
        val sessionId = installer.createSession(response.estimatedSize)

        coroutineScope {
            val progressJob = if (onProgress != null) {
                launch {
                    installer.watchSessionProgress(sessionId, onProgress)
                }
            } else {
                null
            }
            installingJob = launch {
                installer.openSession(sessionId).use { session ->
                    withContext(Dispatchers.IO) {
                        response.write(onProgress = session::setStagingProgress) { name, size ->
                            session.openWrite(name, 0, size)
                        }
                    }
                    session.commit(
                        PendingIntent.getBroadcast(
                            context,
                            sessionId,
                            Intent(context, receiver),
                            PendingIntent.FLAG_MUTABLE,
                        ).intentSender
                    )
                }
            }
            CommitFlow.first().getOrThrow()
            progressJob?.cancel()
        }
    }

    private suspend fun PackageInstaller.watchSessionProgress(
        sessionId: Int,
        onProgress: (Float) -> Unit
    ) {
        // deliver initial progress
        val sessionInfo = getSessionInfo(sessionId)
        if (sessionInfo != null) {
            onProgress(sessionInfo.progress)
        }
        suspendCancellableCoroutine { continuation ->
            val callback = object : PackageInstaller.SessionCallback() {
                override fun onCreated(sId: Int) {
                }

                override fun onBadgingChanged(sId: Int) {
                }

                override fun onActiveChanged(sId: Int, active: Boolean) {
                    if (sessionId == sId) {
                        Log.d("SelfUpdate", "onActiveChange: ${active}")
                    }
                }

                override fun onProgressChanged(sId: Int, progress: Float) {
                    if (sessionId == sId) {
                        onProgress(progress)
                    }
                }

                override fun onFinished(sId: Int, success: Boolean) {
                    if (sessionId == sId) {
                        continuation.resume(Unit)
                    }
                }
            }
            registerSessionCallback(callback)
            continuation.invokeOnCancellation { unregisterSessionCallback(callback) }
        }
    }

    private fun PackageInstaller.createSession(estimatedSize: Long = 0): Int {
        return createSession(SessionParams(SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            if (estimatedSize > 0) {
                setSize(estimatedSize)
            }
            if (Build.VERSION.SDK_INT >= 26) {
                setInstallReason(PackageManager.INSTALL_REASON_USER)
            }
            setInstallLocation(PackageInfo.INSTALL_LOCATION_AUTO)
            if (Build.VERSION.SDK_INT >= 31) {
                setInstallScenario(PackageManager.INSTALL_SCENARIO_FAST)
            }
            if (Build.VERSION.SDK_INT == 30) {
                setAutoRevokePermissionsMode(true)
            }
        })
    }
}

open class SelfUpdateReceiver : BroadcastReceiver() {
    final override fun onReceive(context: Context, intent: Intent?) {
        when (val status = intent?.extras?.getInt(EXTRA_STATUS)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val userIntent: Intent? =
                    intent.extras?.getParcelable(Intent.EXTRA_INTENT) as? Intent
                if (userIntent != null) {
                    userIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(userIntent)
                }
            }

            PackageInstaller.STATUS_SUCCESS -> {
                onUpdate(context)

            }

            else -> {
                val message = intent?.extras?.getString(EXTRA_STATUS_MESSAGE)
                CommitFlow.tryEmit(
                    Result.failure(
                        UpdateFailedException(
                            message ?: "Failed to install",
                            status ?: 1,
                        )
                    )
                )
            }
        }
    }

    open fun onUpdate(context: Context) {
        Log.d("SelfUpdate", "App Updated")
    }
}

class UpdateFailedException(message: String, val status: Int) : IOException(message)

internal fun Response.ensureBody(): ResponseBody {
    if (!isSuccessful) {
        throw IOException("${request.url} -> ${code}: $message")
    }
    return body ?: throw IOException("${request.url} missing body")
}

private fun Context.versionCode(): Long {
    val packageInfo = packageManager.getPackageInfo(packageName, 0)
    return if (Build.VERSION.SDK_INT >= 28) {
        packageInfo.longVersionCode
    } else {
        packageInfo.versionCode.toLong()
    }
}
