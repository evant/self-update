@file:OptIn(ExperimentalSerializationApi::class)

package me.tatarka.android.selfupdate

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller.SessionCallback
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PersistableBundle
import android.util.Log
import kotlinx.coroutines.CancellationException
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
import me.tatarka.android.selfupdate.PackageInstallerCompat.Companion.EXTRA_STATUS
import me.tatarka.android.selfupdate.PackageInstallerCompat.Companion.EXTRA_STATUS_MESSAGE
import me.tatarka.android.selfupdate.PackageInstallerCompat.Companion.STATUS_PENDING_USER_ACTION
import me.tatarka.android.selfupdate.PackageInstallerCompat.Companion.STATUS_SUCCESS
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
import kotlin.math.absoluteValue

private const val SelfUpdateReceiverName = "me.tatarka.android.selfupdate.SelfUpdateReceiver"
private val CommitFlow = MutableSharedFlow<Result<Unit>>(extraBufferCapacity = 1)

class SelfUpdate internal constructor(
    private val context: Context,
    private val client: OkHttpClient = OkHttpClient(),
) {

    constructor(context: Context) : this(context, OkHttpClient())

    private val receiverName = context.packageManager
        .getPackageInfo(context.packageName, PackageManager.GET_RECEIVERS or PackageManager.GET_META_DATA)
        .receivers.firstOrNull {
            it.metaData?.containsKey(SelfUpdateReceiverName) == true
        }?.name ?: SelfUpdateReceiverName

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
        val notes: String?,
        val tags: Set<String>,
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

    enum class DownloadState {
        None, Partial, Complete
    }

    suspend fun download(
        release: Release,
        onProgress: ((Float) -> Unit)? = null
    ) {
        val (session, _) = getOrDownloadSession(release, onProgress)
        session.close()
    }

    suspend fun currentDownloadState(release: Release): DownloadState {
        val installer = PackageInstallerCompat.getInstance(context)
        if (installer.mySessions.isEmpty()) {
            return DownloadState.None
        }
        // assuming a single session
        val sessionInfo = installer.mySessions.first()
        installer.openSession(sessionInfo.sessionId).use { session ->
            if (matchMetadata(session, release)) {
                // check all artifacts are downloaded
                val bytesWritten = readBytesWritten(session)
                if (bytesWritten.isEmpty()) {
                    return DownloadState.None
                }
                // not all artifacts have been fetched
                if (bytesWritten.size != release.artifacts.size) {
                    return DownloadState.Partial
                }
                return if (bytesWritten.all { it.value < 0 }) {
                    DownloadState.Complete
                } else {
                    return DownloadState.Partial
                }
            } else {
                return DownloadState.None
            }
        }
    }

    private suspend fun getOrDownloadSession(
        release: Release,
        onProgress: ((Float) -> Unit)? = null
    ): Pair<PackageInstallerCompat.Session, Int> {
        val installer = PackageInstallerCompat.getInstance(context)

        if (installer.mySessions.isNotEmpty()) {
            // may already have a session, see if it's for the same release
            val sessionInfo = installer.mySessions.first()
            val session = installer.openSession(sessionInfo.sessionId)
            if (matchMetadata(session, release)) {
                // report current progress
                if (onProgress != null) {
                    onProgress(sessionInfo.progress)
                }

                // filter completed downloads
                val skip = readBytesWritten(session).mapNotNull { (name, bytes) ->
                    // negative denotes complete
                    if (bytes < 0) name else null
                }.toSet()

                val response = download(release = release, client = client, skip = skip)

                watchProgress(
                    installer = installer,
                    sessionId = sessionInfo.sessionId,
                    onProgress = onProgress
                ) {
                    downloadArtifacts(session = session, response = response)
                }

                return session to sessionInfo.sessionId
            } else {
                // not a match, delete as we currently only handle 1 session
                session.use { it.abandon() }
            }
        }

        val response = download(release = release, client = client)
        val sessionId = installer.createSession(
            uri = Uri.parse(release.manifestUrl.toString()),
            estimatedSize = response.estimatedSize
        )
        val session = installer.openSession(sessionId)
        recordMetadata(session, release)

        watchProgress(
            installer = installer,
            sessionId = sessionId,
            onProgress = onProgress,
        ) {
            downloadArtifacts(session = session, response = response)
        }

        return session to sessionId
    }

    private suspend fun watchProgress(
        installer: PackageInstallerCompat,
        sessionId: Int,
        onProgress: ((Float) -> Unit)?,
        body: suspend () -> Unit
    ) {
        coroutineScope {
            val progressJob = if (onProgress != null) {
                launch {
                    installer.watchSessionProgress(sessionId, onProgress)
                }
            } else {
                null
            }
            body()
            progressJob?.cancel()
        }
    }

    private suspend fun downloadArtifacts(
        session: PackageInstallerCompat.Session,
        response: DownloadResponse,
    ) {
        try {
            response.write(
                onProgress = session::setStagingProgress,
                output = { name, size ->
                    session.openWrite(name, 0, size)
                },
                complete = { name, size ->
                    recordBytesWritten(session, name, size, complete = true)
                }
            )
        } catch (e: CancellationException) {
            session.close()
            throw e
        } catch (e: IOException) {
            session.close()
            throw e
        }
    }

    private fun recordMetadata(session: PackageInstallerCompat.Session, release: Release) {
        session.appMetadata = PersistableBundle().apply {
            putLong("versionCode", release.versionCode)
        }
    }

    private fun recordBytesWritten(
        session: PackageInstallerCompat.Session,
        name: String,
        size: Long,
        complete: Boolean,
    ) {
        val bundle = session.appMetadata
        val downloaded = bundle.getPersistableBundle("downloaded") ?: PersistableBundle()
        downloaded.putLong(name, if (complete) -size.absoluteValue else size)
        bundle.putPersistableBundle("downloaded", downloaded)
        session.appMetadata = bundle
    }

    private fun readBytesWritten(session: PackageInstallerCompat.Session): Map<String, Long> {
        val downloaded = session.appMetadata.getPersistableBundle("downloaded") ?: return emptyMap()
        return downloaded.keySet().associateWith { name -> downloaded.getLong(name) }
    }

    private fun matchMetadata(session: PackageInstallerCompat.Session, release: Release): Boolean {
        val metadata = session.appMetadata
        val versionCode = metadata.getLong("versionCode")
        return release.versionCode == versionCode
    }

    suspend fun delete(release: Release) {
        val packageManager = context.packageManager
        val installer = packageManager.packageInstaller
        // only 1 session supported currently, delete it
        val sessions = installer.mySessions
        if (sessions.isNotEmpty()) {
            val sessionId = sessions.first().sessionId
            installer.abandonSession(sessionId)
        }
    }

    suspend fun install(
        release: Release,
        onProgress: ((Float) -> Unit)? = null
    ) {
        // can only have one install going at a time
        installingJob?.cancel()

        val (session, sessionId) = getOrDownloadSession(release, onProgress)

        val installer = PackageInstallerCompat.getInstance(context)

        coroutineScope {
            val progressJob = if (onProgress != null) {
                launch {
                    installer.watchSessionProgress(sessionId, onProgress)
                }
            } else {
                null
            }
            installingJob = launch {
                session.commit(
                    PendingIntent.getBroadcast(
                        context,
                        sessionId,
                        Intent().setComponent(ComponentName(context, receiverName)),
                        PendingIntent.FLAG_MUTABLE,
                    ).intentSender
                )
            }
            CommitFlow.first().getOrThrow()
            progressJob?.cancel()
        }
    }

    private suspend fun PackageInstallerCompat.watchSessionProgress(
        sessionId: Int,
        onProgress: (Float) -> Unit
    ) {
        withContext(Dispatchers.Main.immediate) {
            // deliver initial progress
            val sessionInfo = getSessionInfo(sessionId)
            if (sessionInfo != null) {
                onProgress(sessionInfo.progress)
            }
            suspendCancellableCoroutine { continuation ->
                val callback = object : SessionCallback() {
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
    }

    @SuppressLint("InlinedApi")
    private fun PackageInstallerCompat.createSession(
        uri: Uri,
        estimatedSize: Long = 0
    ): Int {
        return createSession(
            PackageInstallerCompat.SessionParams(PackageInstallerCompat.SessionParams.MODE_FULL_INSTALL)
                .apply {
                    setAppPackageName(context.packageName)
                    setOriginatingUri(uri)
                    setSize(estimatedSize)
                    setInstallReason(PackageManager.INSTALL_REASON_USER)
                    setInstallLocation(PackageInfo.INSTALL_LOCATION_AUTO)
                    setInstallScenario(PackageManager.INSTALL_SCENARIO_FAST)
                    setRequireUserAction(PackageInstallerCompat.SessionParams.USER_ACTION_NOT_REQUIRED)
                }
        )
    }
}

open class SelfUpdateReceiver : BroadcastReceiver() {
    final override fun onReceive(context: Context, intent: Intent?) {
        when (val status = intent?.extras?.getInt(EXTRA_STATUS)) {
            STATUS_PENDING_USER_ACTION -> {
                val userIntent: Intent? =
                    intent.extras?.getParcelable(Intent.EXTRA_INTENT) as? Intent
                if (userIntent != null) {
                    userIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(userIntent)
                }
            }

            STATUS_SUCCESS -> {
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
