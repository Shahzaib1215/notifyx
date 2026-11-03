package com.notifyx

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.database.Cursor
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class InstalledApp(
    val label: String,
    val packageName: String,
    val isSystem: Boolean
)

data class AudioChoice(val uri: Uri, val name: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { NotifyXApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotifyXApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("notifyx_audio_preferences", Context.MODE_PRIVATE) }
    var apps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<InstalledApp?>(null) }
    var showSystem by remember { mutableStateOf(false) }
    var audioSearch by remember { mutableStateOf("") }
    var audioChoices by remember { mutableStateOf<List<AudioChoice>>(emptyList()) }
    var currentAudio by remember { mutableStateOf<AudioChoice?>(null) }
    var statusMessage by remember { mutableStateOf("Choose an app to customize its notification sound.") }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }

    fun stopPlayback() {
        try { player?.stop() } catch (_: Exception) {}
        try { player?.release() } catch (_: Exception) {}
        player = null
        isPlaying = false
    }

    fun loadSavedAudio(app: InstalledApp?) {
        if (app == null) {
            currentAudio = null
            return
        }
        val raw = prefs.getString("audio:${app.packageName}", null)
        val label = prefs.getString("name:${app.packageName}", null)
        currentAudio = if (raw != null && label != null) {
            runCatching { AudioChoice(Uri.parse(raw), label) }.getOrNull()
        } else null
    }

    val audioPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                val name = displayName(context, uri)
                val choice = AudioChoice(uri, name)
                audioChoices = (audioChoices.filterNot { it.uri == uri } + choice)
                if (selected != null) {
                    prefs.edit()
                        .putString("audio:${selected!!.packageName}", uri.toString())
                        .putString("name:${selected!!.packageName}", name)
                        .apply()
                    currentAudio = choice
                    statusMessage = "Saved '$name' as NotifyX's preference for ${selected!!.label}. Apply it in Android's notification channel settings if supported."
                }
            } catch (e: Exception) {
                statusMessage = "Couldn't retain access to this audio file. Please select it again."
            }
        }
    }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) { loadInstalledApps(context) }
    }

    DisposableEffect(Unit) { onDispose { stopPlayback() } }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = androidx.compose.ui.graphics.Color(0xFFAA9AFF),
            secondary = androidx.compose.ui.graphics.Color(0xFF70DCCB),
            background = androidx.compose.ui.graphics.Color(0xFF10111A),
            surface = androidx.compose.ui.graphics.Color(0xFF1A1C29)
        )
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("NotifyX", fontWeight = FontWeight.Bold)
                            Text("Your notifications. Your rules.", style = MaterialTheme.typography.labelSmall)
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            }
                            context.startActivity(intent)
                        }) { Icon(Icons.Default.Notifications, "Notification settings") }
                    }
                )
            }
        ) { padding ->
            if (selected == null) {
                Column(Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
                    Text("Notification control center", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(5.dp))
                    Text("${apps.size} installed apps detected", color = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.height(14.dp))
                    OutlinedTextField(
                        value = query, onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        placeholder = { Text("Search installed apps") },
                        singleLine = true
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !showSystem, onClick = { showSystem = false }, label = { Text("User apps") })
                        FilterChip(selected = showSystem, onClick = { showSystem = true }, label = { Text("System apps") })
                    }
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(apps.filter {
                            it.label.contains(query, true) && it.isSystem == showSystem
                        }, key = { it.packageName }) { app ->
                            Card(Modifier.fillMaxWidth().clickable {
                                selected = app
                                loadSavedAudio(app)
                                statusMessage = "Choose a local audio file for ${app.label}."
                            }) {
                                Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(app.label, fontWeight = FontWeight.SemiBold)
                                        Text(app.packageName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Icon(Icons.Default.ChevronRight, contentDescription = "Customize")
                                }
                            }
                        }
                    }
                }
            } else {
                Column(Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
                    TextButton(onClick = { stopPlayback(); selected = null }) {
                        Icon(Icons.Default.ArrowBack, null); Spacer(Modifier.width(6.dp)); Text("All apps")
                    }
                    Text(selected!!.label, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(selected!!.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.height(14.dp))

                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Local notification audio", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(6.dp))
                            Text("Choose an audio file stored on your phone. NotifyX remembers your selection for this app.", style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(12.dp))
                            Text(currentAudio?.name ?: "No custom audio assigned", color = MaterialTheme.colorScheme.secondary)
                            Spacer(Modifier.height(10.dp))
                            Button(onClick = { audioPicker.launch(arrayOf("audio/*")) }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.FolderOpen, null); Spacer(Modifier.width(8.dp)); Text("Browse phone audio")
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(
                                    enabled = currentAudio != null && !isPlaying,
                                    onClick = {
                                        stopPlayback()
                                        try {
                                            val mp = MediaPlayer()
                                            mp.setDataSource(context, currentAudio!!.uri)
                                            mp.setOnPreparedListener { it.start(); player = it; isPlaying = true }
                                            mp.setOnCompletionListener { it.release(); player = null; isPlaying = false }
                                            mp.setOnErrorListener { p, _, _ -> p.release(); player = null; isPlaying = false; statusMessage = "This audio file could not be played."; true }
                                            mp.prepareAsync()
                                        } catch (_: Exception) { statusMessage = "This audio file is no longer accessible." }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) { Icon(Icons.Default.PlayArrow, null); Text("Preview") }
                                OutlinedButton(
                                    enabled = currentAudio != null,
                                    onClick = {
                                        stopPlayback()
                                        prefs.edit().remove("audio:${selected!!.packageName}")
                                            .remove("name:${selected!!.packageName}").apply()
                                        currentAudio = null
                                        statusMessage = "NotifyX custom audio preference removed."
                                    },
                                    modifier = Modifier.weight(1f)
                                ) { Icon(Icons.Default.DeleteOutline, null); Text("Remove") }
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Apply through Android", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Android does not let ordinary apps silently change another app's notification sound. Open this app's Android notification settings, choose the relevant notification category/channel, and select the audio there if Android offers a sound picker.",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(Modifier.height(10.dp))
                            Button(onClick = {
                                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                    putExtra(Settings.EXTRA_APP_PACKAGE, selected!!.packageName)
                                }
                                context.startActivity(intent)
                            }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.Settings, null); Spacer(Modifier.width(8.dp)); Text("Open Android notification settings")
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Vibration test", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { vibrate(context, longArrayOf(0, 150)) }) { Text("Short") }
                                OutlinedButton(onClick = { vibrate(context, longArrayOf(0, 100, 80, 100)) }) { Text("Double") }
                                OutlinedButton(onClick = { vibrate(context, longArrayOf(0, 80, 60, 180, 60, 80)) }) { Text("Pulse") }
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(statusMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                }
            }
        }
    }
}

private fun loadInstalledApps(context: Context): List<InstalledApp> {
    val pm = context.packageManager
    @Suppress("DEPRECATION")
    return pm.getInstalledApplications(PackageManager.GET_META_DATA)
        .map {
            InstalledApp(
                label = pm.getApplicationLabel(it).toString(),
                packageName = it.packageName,
                isSystem = (it.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            )
        }.sortedBy { it.label.lowercase() }
}

private fun displayName(context: Context, uri: Uri): String {
    var name = uri.lastPathSegment ?: "Selected audio"
    var cursor: Cursor? = null
    try {
        cursor = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        if (cursor != null && cursor.moveToFirst()) {
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0) name = cursor.getString(index) ?: name
        }
    } catch (_: Exception) {
    } finally {
        cursor?.close()
    }
    return name
}

private fun vibrate(context: Context, timings: LongArray) {
    try {
        val vibrator: Vibrator = if (android.os.Build.VERSION.SDK_INT >= 31) {
            context.getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            vibrator.vibrate(VibrationEffect.createWaveform(timings, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(timings, -1)
        }
    } catch (_: Exception) {
    }
}
