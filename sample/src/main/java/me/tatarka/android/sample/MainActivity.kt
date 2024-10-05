@file:OptIn(ExperimentalMaterial3Api::class)

package me.tatarka.android.sample

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.TaskStackBuilder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import me.tatarka.android.selfupdate.SelfUpdate
import me.tatarka.android.selfupdate.SelfUpdateReceiver
import java.io.IOException

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val vm = viewModel<MainViewModel>()
            val releases by vm.releases.collectAsState(initial = emptyList())
            val updateProgress by vm.updateProgress.collectAsState()
            val updateFailed by vm.updateFailed.collectAsState()
            MaterialTheme {
                Scaffold(
                    topBar = {
                        TopAppBar(title = { Text("Self Update") })
                    }
                ) { padding ->
                    if (releases.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(padding),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else {
                        Box(modifier = Modifier.fillMaxSize()) {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = padding
                            ) {
                                itemsIndexed(releases) { index, release ->
                                    ListItem(
                                        headlineContent = { Text(release) },
                                        trailingContent = {
                                            Button(onClick = { vm.install(index) }) {
                                                Text("Install")
                                            }
                                        }
                                    )
                                }
                            }
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth(),
                                progress = { updateProgress }
                            )
                        }
                    }
                }
                if (updateFailed != null) {
                    AlertDialog(
                        title = {
                            Text("Update Failed")
                        },
                        text = {
                            Text(updateFailed ?: "")
                        },
                        onDismissRequest = { vm.acceptFailure() },
                        confirmButton = {
                            TextButton(onClick = { vm.acceptFailure() }) {
                                Text("Ok")
                            }
                        },
                    )
                }
            }
        }
    }

    class MainViewModel(app: Application) : AndroidViewModel(app) {
        private val selfUpdate = SelfUpdate(app, UpdateReceiver::class.java)

        private val _releases = flow {
            emit(
                selfUpdate.check(
//            manifestUrl = "http://10.0.2.2:8000/index.json",
                    manifestUrl = "http://10.0.0.18:8000/index.json",
                    onlyUpgrades = false
                )
            )
        }
            .catch { error ->
                Log.e("MainViewModel", error.message, error)
            }
            .shareIn(viewModelScope, SharingStarted.Lazily, replay = 1)

        val releases: Flow<List<String>> =
            _releases.map { it.map { release -> release.versionName } }

        private val _updateProgress = MutableStateFlow(0f)
        val updateProgress: StateFlow<Float> get() = _updateProgress

        private val _updateFailed = MutableStateFlow<String?>(null)
        val updateFailed: StateFlow<String?> get() = _updateFailed

        fun install(index: Int) {
            viewModelScope.launch {
                try {
                    val release = _releases.first()[index]
                    selfUpdate.install(release, onProgress = { _updateProgress.value = it })
                } catch (e: IOException) {
                    Log.e("MainViewModel", e.message, e)
                    _updateFailed.value = e.message
                }
            }
        }

        fun acceptFailure() {
            _updateFailed.value = null
        }
    }
}

class UpdateReceiver : SelfUpdateReceiver() {
    override fun onUpdate(context: Context) {
        val nm = NotificationManagerCompat.from(context)
        nm.createNotificationChannel(
            NotificationChannelCompat.Builder("update", NotificationManager.IMPORTANCE_HIGH)
                .setName("Update")
                .build()
        )

        val intent = TaskStackBuilder.create(context)
            .addNextIntent(Intent(context, MainActivity::class.java))
            .getPendingIntent(0, PendingIntent.FLAG_IMMUTABLE)

        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            nm.notify(
                0,
                NotificationCompat.Builder(context, "update")
                    .setContentTitle("App Updated")
                    .setSmallIcon(R.drawable.small_icon)
                    .setContentIntent(intent)
                    .setAutoCancel(true)
                    .build()
            )
        }
    }
}