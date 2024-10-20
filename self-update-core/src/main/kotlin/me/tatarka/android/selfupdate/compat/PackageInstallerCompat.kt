package me.tatarka.android.selfupdate.compat

import android.annotation.SuppressLint
import android.content.Context
import android.content.IntentSender
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.os.PersistableBundle
import androidx.annotation.RequiresApi
import androidx.annotation.WorkerThread
import okio.withLock
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.Executors
import java.util.concurrent.locks.ReentrantLock

internal class PackageInstallerCompat private constructor(private val context: Context) {

    private val installer = context.packageManager.packageInstaller

    init {
        if (Build.VERSION.SDK_INT < 34) {
            PersistableBundleState.getInstance(context).load()
        }
    }

    val mySessions: List<SessionInfo> get() = installer.mySessions.map { createSessionInfo(it) }

    fun openSession(sessionId: Int): Session =
        createSession(sessionId, installer.openSession(sessionId))

    fun createSession(params: SessionParams): Int {
        val sessionId = installer.createSession(params.params)
        if (Build.VERSION.SDK_INT < 27) {
            val state = PersistableBundleState.getInstance(context)
            // save size
            state.withBundle {
                it.putLong("${sessionId}_size", params.size)
            }
        }
        return sessionId
    }

    fun getSessionInfo(sessionId: Int): SessionInfo? =
        installer.getSessionInfo(sessionId)?.let { createSessionInfo(it) }

    fun registerSessionCallback(callback: PackageInstaller.SessionCallback) {
        installer.registerSessionCallback(callback)
    }

    fun unregisterSessionCallback(callback: PackageInstaller.SessionCallback) {
        installer.unregisterSessionCallback(callback)
    }

    class SessionParams(mode: Int) {
        internal val params = PackageInstaller.SessionParams(mode)
        internal var size: Long = 0
            private set

        init {
            if (Build.VERSION.SDK_INT == 30) {
                @Suppress("DEPRECATION")
                params.setAutoRevokePermissionsMode(true)
            }
        }

        fun setAppPackageName(appPackageName: String) {
            params.setAppPackageName(appPackageName)
        }

        fun setOriginatingUri(originatingUri: Uri) {
            params.setOriginatingUri(originatingUri)
        }

        fun setSize(sizeBytes: Long) {
            size = sizeBytes
            params.setSize(sizeBytes)
        }

        fun setInstallReason(installReason: Int) {
            if (Build.VERSION.SDK_INT >= 26) {
                params.setInstallReason(installReason)
            }
        }

        fun setInstallLocation(installLocation: Int) {
            params.setInstallLocation(installLocation)
        }

        fun setInstallScenario(installScenario: Int) {
            if (Build.VERSION.SDK_INT >= 31) {
                params.setInstallScenario(installScenario)
            }
        }

        fun setRequireUserAction(requireUserAction: Int) {
            if (Build.VERSION.SDK_INT >= 31) {
                params.setRequireUserAction(requireUserAction)
            }
        }

        @SuppressLint("InlinedApi")
        companion object {
            const val MODE_FULL_INSTALL = PackageInstaller.SessionParams.MODE_FULL_INSTALL
            const val MODE_INHERIT_EXISTING = PackageInstaller.SessionParams.MODE_INHERIT_EXISTING

            const val USER_ACTION_UNSPECIFIED =
                PackageInstaller.SessionParams.USER_ACTION_UNSPECIFIED
            const val USER_ACTION_REQUIRED = PackageInstaller.SessionParams.USER_ACTION_REQUIRED
            const val USER_ACTION_NOT_REQUIRED =
                PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED
        }
    }

    private fun createSessionInfo(
        info: PackageInstaller.SessionInfo
    ): SessionInfo = if (Build.VERSION.SDK_INT >= 27) {
        SessionInfoApi27(info)
    } else {
        SessionInfoApi(info)
    }

    sealed class SessionInfo(protected val info: PackageInstaller.SessionInfo) {
        val sessionId: Int get() = info.sessionId
        val progress: Float get() = info.progress
        abstract val size: Long
    }

    @RequiresApi(27)
    private class SessionInfoApi27(info: PackageInstaller.SessionInfo) : SessionInfo(info) {
        override val size: Long
            get() = info.size
    }

    private inner class SessionInfoApi(info: PackageInstaller.SessionInfo) : SessionInfo(info) {
        private val state = PersistableBundleState.getInstance(context)

        override val size: Long
            get() {
                return state.getBundle().getLong("${info.sessionId}_size", -1)
            }
    }

    private fun createSession(
        sessionId: Int,
        session: PackageInstaller.Session,
    ): Session = if (Build.VERSION.SDK_INT >= 34) {
        SessionApi34(session)
    } else {
        SessionApi(context, sessionId, session)
    }

    fun abandonSession(sessionId: Int) {
        installer.abandonSession(sessionId)
    }

    sealed class Session(protected val session: PackageInstaller.Session) : AutoCloseable {

        val names: Array<String>
            get() = session.names

        abstract var appMetadata: PersistableBundle

        fun setStagingProgress(progress: Float) {
            session.setStagingProgress(progress)
        }

        fun openRead(name: String): InputStream =
            session.openRead(name)

        fun openWrite(name: String, offsetBytes: Long, lengthBytes: Long): OutputStream =
            session.openWrite(name, offsetBytes, lengthBytes)

        override fun close() {
            session.close()
        }

        fun abandon() {
            session.abandon()
        }

        fun commit(statusReceiver: IntentSender) {
            session.commit(statusReceiver)
        }
    }

    @RequiresApi(34)
    private class SessionApi34(
        session: PackageInstaller.Session,
    ) : Session(session) {
        override var appMetadata: PersistableBundle
            get() = session.appMetadata
            set(value) {
                session.setAppMetadata(value)
            }
    }

    private inner class SessionApi(
        context: Context,
        private val sessionId: Int,
        session: PackageInstaller.Session
    ) : Session(session) {
        private val state = PersistableBundleState.getInstance(context)

        override var appMetadata: PersistableBundle
            get() {
                return state.withBundle {
                    cleanupStaleSessionMetadata(it)
                    it.getPersistableBundle(sessionId.toString())
                } ?: PersistableBundle.EMPTY
            }
            set(value) {
                state.withBundle {
                    it.putPersistableBundle(sessionId.toString(), value)
                }
            }

        private fun cleanupStaleSessionMetadata(bundle: PersistableBundle) {
            val sessionIds = installer.mySessions.map { it.sessionId.toString() }
            for (key: String? in bundle.keySet()) {
                if (sessionIds.none { sessionId -> key?.startsWith(sessionId) != false }) {
                    bundle.remove(key)
                }
            }
        }
    }

    companion object {
        const val STATUS_PENDING_USER_ACTION = PackageInstaller.STATUS_PENDING_USER_ACTION
        const val STATUS_SUCCESS = PackageInstaller.STATUS_SUCCESS

        const val EXTRA_STATUS = PackageInstaller.EXTRA_STATUS
        const val EXTRA_STATUS_MESSAGE = PackageInstaller.EXTRA_STATUS_MESSAGE

        fun getInstance(context: Context): PackageInstallerCompat = PackageInstallerCompat(context)
    }
}

private class PersistableBundleState private constructor(private val file: File) {
    private val executor = Executors.newSingleThreadExecutor()
    private var bundle: PersistableBundle? = null
    private val lock = ReentrantLock()

    fun load() {
        executor.submit {
            lock.withLock {
                if (bundle == null) {
                    bundle = readBundle()
                }
            }
        }
    }

    fun getBundle(): PersistableBundle {
        return lock.withLock { bundle!! }
    }

    fun <T> withBundle(body: (PersistableBundle) -> T): T {
        return lock.withLock {
            val result = body(bundle!!)
            submitWrite()
            result
        }
    }

    private fun submitWrite() {
        executor.submit {
            lock.withLock {
                writeBundle(bundle!!)
            }
        }
    }

    @WorkerThread
    private fun readBundle(): PersistableBundle {
        return if (file.exists()) {
            file.inputStream().buffered().use {
                PersistableBundleCompat.readFromStream(it)
            }
        } else {
            PersistableBundle()
        }
    }

    @WorkerThread
    private fun writeBundle(bundle: PersistableBundle) {
        file.outputStream().buffered().use {
            PersistableBundleCompat.writeToStream(bundle, it)
        }
    }

    companion object {
        private var instance: PersistableBundleState? = null

        fun getInstance(context: Context): PersistableBundleState {
            if (instance == null) {
                instance = PersistableBundleState(File(context.cacheDir, "selfupdate"))
            }
            return instance!!
        }
    }
}
