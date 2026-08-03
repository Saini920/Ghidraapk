package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.math.roundToInt

@Composable
fun DebuggerOverlay() {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current

    var offsetX by remember { mutableFloatStateOf(50f) }
    var offsetY by remember { mutableFloatStateOf(200f) }
    var showDialog by remember { mutableStateOf(false) }

    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }

    Box(modifier = Modifier.fillMaxSize()) {
        FloatingActionButton(
            onClick = { showDialog = true },
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        offsetX = (offsetX + dragAmount.x).coerceIn(0f, screenWidthPx - 150f)
                        offsetY = (offsetY + dragAmount.y).coerceIn(0f, screenHeightPx - 150f)
                    }
                }
                .size(56.dp),
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            shape = CircleShape
        ) {
            Icon(Icons.Default.BugReport, contentDescription = "Debugger")
        }
    }

    if (showDialog) {
        DebuggerDialog(onDismiss = { showDialog = false }, context = context)
    }
}

@Composable
fun DebuggerDialog(onDismiss: () -> Unit, context: Context) {
    var logs by remember { mutableStateOf("Loading logs...") }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        coroutineScope.launch {
            logs = fetchLogs()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("App Debugger", style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = {
                        coroutineScope.launch {
                            logs = "Refreshing..."
                            logs = fetchLogs()
                        }
                    }) {
                        Text("🔄")
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "Close")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("App Logs", logs)
                        clipboard.setPrimaryClip(clip)
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Copy")
                    }
                    Button(onClick = {
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, logs)
                            type = "text/plain"
                        }
                        val shareIntent = Intent.createChooser(sendIntent, null)
                        context.startActivity(shareIntent)
                    }) {
                        Icon(Icons.Default.Share, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Share")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color.Black)
                        .padding(8.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = logs,
                        color = Color.Green,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 12.sp
                    )
                }
            }
        }
    }
}

private suspend fun fetchLogs(): String = withContext(Dispatchers.IO) {
    try {
        val pid = android.os.Process.myPid().toString()
        val process = Runtime.getRuntime().exec("logcat -d -v threadtime -t 2000")
        val bufferedReader = BufferedReader(InputStreamReader(process.inputStream))
        val log = StringBuilder()
        var line: String?
        while (bufferedReader.readLine().also { line = it } != null) {
            if (line?.contains(pid) == true) {
                log.append(line).append("\n")
            }
        }
        if (log.isEmpty()) "No logs found for PID $pid" else log.toString()
    } catch (e: Exception) {
        "Error reading logs: ${e.message}"
    }
}
