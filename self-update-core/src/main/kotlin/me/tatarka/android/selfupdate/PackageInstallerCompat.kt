package me.tatarka.android.selfupdate

import android.annotation.SuppressLint
import android.content.Context
import android.content.IntentSender
import android.content.pm.PackageInstaller
import android.content.pm.PackageInstaller.SessionCallback
import android.net.Uri
import android.os.Build
import android.os.PersistableBundle
import androidx.annotation.RequiresApi
import androidx.annotation.WorkerThread
import okio.withLock
import java.io.File
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

    val mySessions: List<SessionInfo> get() = installer.mySessions.map { SessionInfo(it) }

    fun openSession(sessionId: Int): Session =
        createSession(sessionId, installer.openSession(sessionId))

    fun createSession(params: SessionParams): Int {
        //TODO: SessionParams compat
        return installer.createSession(params.params)
    }

    fun getSessionInfo(sessionId: Int): SessionInfo? =
        installer.getSessionInfo(sessionId)?.let { SessionInfo(it) }

    fun registerSessionCallback(callback: SessionCallback) {
        installer.registerSessionCallback(callback)
    }

    fun unregisterSessionCallback(callback: SessionCallback) {
        installer.unregisterSessionCallback(callback)
    }

    class SessionParams(mode: Int) {
        internal val params = PackageInstaller.SessionParams(mode)

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

    class SessionInfo(private val info: PackageInstaller.SessionInfo) {
        val sessionId: Int get() = info.sessionId
        val progress: Float get() = info.progress
    }

    private fun createSession(
        sessionId: Int,
        session: PackageInstaller.Session,
    ): Session = if (Build.VERSION.SDK_INT >= 34) {
        SessionApi34(session)
    } else {
        SessionApi(context, sessionId, session)
    }

    sealed class Session(protected val session: PackageInstaller.Session) : AutoCloseable {

        abstract var appMetadata: PersistableBundle

        fun setStagingProgress(progress: Float) {
            session.setStagingProgress(progress)
        }

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
                val bundle = state.getBundle()
                if (cleanupStaleSessionMetadata(bundle)) {
                    state.setBundle(bundle)
                }
                return bundle.getPersistableBundle(sessionId.toString()) ?: PersistableBundle.EMPTY
            }
            set(value) {
                val bundle = state.getBundle()
                bundle.putPersistableBundle(sessionId.toString(), value)
                state.setBundle(bundle)
            }

        private fun cleanupStaleSessionMetadata(bundle: PersistableBundle): Boolean {
            var changed = false
            val sessionIds = installer.mySessions.map { it.sessionId.toString() }
            for (sessionId in bundle.keySet()) {
                if (sessionId !in sessionIds) {
                    bundle.remove(sessionId)
                    changed = true
                }
            }
            return changed
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
                bundle = readBundle()
            }
        }
    }

    fun getBundle(): PersistableBundle {
        return lock.withLock { bundle!! }
    }

    fun setBundle(value: PersistableBundle) {
        lock.withLock {
            bundle = value
        }
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