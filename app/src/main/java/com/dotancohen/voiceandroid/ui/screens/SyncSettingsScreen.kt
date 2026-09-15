package com.dotancohen.voiceandroid.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dotancohen.voiceandroid.viewmodel.SettingsViewModel
import androidx.compose.material3.Switch
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dotancohen.voiceandroid.data.PairingRequests
import com.dotancohen.voiceandroid.ui.components.QrCodeImage
import com.dotancohen.voiceandroid.ui.components.QrReader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncSettingsScreen(
    viewModel: SettingsViewModel = viewModel(),
    onBack: () -> Unit
) {
    val devices by viewModel.devices.collectAsState()
    val lastDevice by viewModel.lastDevice.collectAsState()
    val deviceMessage by viewModel.deviceMessage.collectAsState()
    val lastOperation by viewModel.lastOperation.collectAsState()
    val progressSentence by viewModel.progressSentence.collectAsState()
    val idleStopHours by viewModel.idleStopHours.collectAsState()
    val thisDeviceId by viewModel.thisDeviceId.collectAsState()
    val thisDeviceName by viewModel.thisDeviceName.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val isUploading by viewModel.isUploading.collectAsState()
    val joinMessage by viewModel.joinMessage.collectAsState()
    val justJoined by viewModel.justJoined.collectAsState()
    val myCode by viewModel.myCode.collectAsState()
    val codeSecondsLeft by viewModel.codeSecondsLeft.collectAsState()
    val pairingLink by PairingRequests.link.collectAsState()
    val openReaderRequest by PairingRequests.openReader.collectAsState()
    var readerOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val checkRows by viewModel.checkRows.collectAsState()
    val listening by viewModel.listening.collectAsState()
    val listenAddresses by viewModel.listenAddresses.collectAsState()
    val certificateFingerprint by viewModel.certificateFingerprint.collectAsState()
    val accountId by viewModel.accountId.collectAsState()
    val uploadMessage by viewModel.uploadMessage.collectAsState()
    val syncResult by viewModel.syncResult.collectAsState()
    val syncError by viewModel.syncError.collectAsState()
    val debugInfo by viewModel.debugInfo.collectAsState()
    val notDuplicatedLine by viewModel.notDuplicatedLine.collectAsState()
    val deviceSummaries by viewModel.deviceSummaries.collectAsState()
    val maxUploadMb by viewModel.maxUploadMb.collectAsState()

    var editedThisDeviceId by remember(thisDeviceId) { mutableStateOf(thisDeviceId) }
    var editedThisDeviceName by remember(thisDeviceName) { mutableStateOf(thisDeviceName) }
    var editedMaxUploadMb by remember(maxUploadMb) { mutableStateOf(maxUploadMb.toString()) }

    // Check for unsynced changes and update debug info when this screen becomes visible
    LaunchedEffect(Unit) {
        viewModel.refreshProof()
        viewModel.updateDebugInfo()
    }
    // A setup text that arrived as a link, or the first-run screen's "Pair with another device" (Stage 9)
    LaunchedEffect(pairingLink) {
        val link = pairingLink ?: return@LaunchedEffect
        PairingRequests.link.value = null
        viewModel.pairWith(link)
    }
    LaunchedEffect(openReaderRequest) {
        if (openReaderRequest) { PairingRequests.openReader.value = false; readerOpen = true }
    }
    if (readerOpen) {
        // Edge to edge, so the reader receives the system bars' insets and keeps its buttons above them
        Dialog(onDismissRequest = { readerOpen = false }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            QrReader(
                onSetupText = { text -> readerOpen = false; viewModel.pairWith(text) },
                onClose = { readerOpen = false },
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Sync Settings") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Which device this is, before anything else; the devices it syncs with are
            // "other devices" under a heading of their own (the desktop's Sync window says the same)
            val thisDeviceLine = SyncScreenWords.thisDeviceLine(thisDeviceName)
            Text(
                text = thisDeviceLine,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { contentDescription = thisDeviceLine }
            )

            // Proof (Stage 10): what is on this phone only, and when each device was last reached.
            // This line, here, is the only place it is said: no notification, no badge.
            Text(
                text = notDuplicatedLine ?: "Counting what is on this device only…",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.semantics { contentDescription = notDuplicatedLine ?: "Counting what is on this device only" }
            )
            deviceSummaries.forEach { device ->
                val reached = device.lastReachedAt?.let { java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(it * 1000)) } ?: "never"
                Text(
                    text = "${device.deviceName.ifEmpty { device.deviceId.take(8) }}: last reached $reached" + (if (device.lastOperation.isEmpty()) "" else ", last operation ${device.lastOperation}"),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            // The devices (Stage 5): from the cards, with a local name, an address and the last operation
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(text = SyncScreenWords.OTHER_DEVICES_HEADING, style = MaterialTheme.typography.titleMedium)
                    if (devices.isEmpty()) {
                        Text("No device yet: read a code shown by another device below, or add one by its address.", style = MaterialTheme.typography.bodySmall)
                    }
                    devices.forEach { device ->
                        var renaming by remember(device.deviceId) { mutableStateOf(false) }
                        var newName by remember(device.deviceId) { mutableStateOf(device.name) }
                        val reached = device.lastReachedAt?.let { java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(it * 1000)) } ?: "never"
                        Column(modifier = Modifier.semantics { contentDescription = SyncScreenWords.otherDeviceDescription(device.name) }) {
                            Text(device.name + (if (device.isLast) "  (last used)" else ""), style = MaterialTheme.typography.bodyMedium)
                            Text("${device.deviceId.take(8)}  ${device.url.ifEmpty { "no address yet" }}", style = MaterialTheme.typography.bodySmall)
                            Text("Last reached $reached" + (if (device.lastOperation.isEmpty()) "" else ", last operation ${device.lastOperation}"), style = MaterialTheme.typography.bodySmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { renaming = !renaming }) { Text("Rename") }
                                TextButton(onClick = { viewModel.forgetDevice(device.deviceId) }, modifier = Modifier.semantics { contentDescription = "Forget ${device.name}" }) { Text("Forget") }
                            }
                            if (renaming) {
                                OutlinedTextField(value = newName, onValueChange = { newName = it }, label = { Text("Name on this phone") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                                TextButton(onClick = { viewModel.renameDevice(device.deviceId, newName); renaming = false }, enabled = newName.isNotBlank()) { Text("Save name") }
                            }
                        }
                    }
                    deviceMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }

                    // A device typed by hand (Stage 7, the third way)
                    var addId by remember { mutableStateOf("") }
                    var addName by remember { mutableStateOf("") }
                    var addUrl by remember { mutableStateOf("") }
                    Text("Add a device by its address", style = MaterialTheme.typography.titleSmall)
                    OutlinedTextField(value = addId, onValueChange = { addId = it }, label = { Text("Its device id (32 hex characters)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = addUrl, onValueChange = { addUrl = it }, label = { Text("Where it listens") }, placeholder = { Text("https://192.168.1.10:8384") }, singleLine = true, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                    OutlinedTextField(value = addName, onValueChange = { addName = it }, label = { Text("A name for it") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedButton(
                        onClick = { viewModel.addDevice(addId.trim(), addName.trim(), addUrl.trim()); addId = ""; addName = ""; addUrl = "" },
                        enabled = addId.trim().length == 32 && addUrl.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Add device") }
                }
            }

            // This device: its own name and id
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = SyncScreenWords.THIS_DEVICE_HEADING,
                        style = MaterialTheme.typography.titleMedium
                    )

                    OutlinedTextField(
                        value = editedThisDeviceName,
                        onValueChange = { editedThisDeviceName = it },
                        label = { Text(SyncScreenWords.THIS_DEVICE_NAME_LABEL) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = editedThisDeviceId,
                        onValueChange = { editedThisDeviceId = it },
                        label = { Text(SyncScreenWords.THIS_DEVICE_ID_LABEL) },
                        placeholder = { Text("32 hex characters (or generate new)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    TextButton(
                        onClick = { editedThisDeviceId = viewModel.generateNewThisDeviceId() }
                    ) {
                        Text(SyncScreenWords.NEW_THIS_DEVICE_ID_BUTTON)
                    }
                }
            }

            // Save Button
            Button(
                onClick = {
                    viewModel.saveSettings(
                        thisDeviceId = editedThisDeviceId,
                        thisDeviceName = editedThisDeviceName
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Settings")
            }

            // Sync Limits Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Upload limit",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Text(
                        text = "Recordings larger than this are not uploaded to the bucket. They stay on the devices that hold them and are listed under Issues. The limit is the account's: every device uses the same one.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = editedMaxUploadMb,
                        onValueChange = { newValue ->
                            // Only allow numeric input
                            if (newValue.isEmpty() || newValue.all { it.isDigit() }) {
                                editedMaxUploadMb = newValue
                            }
                        },
                        label = { Text("Upload limit (MB)") },
                        placeholder = { Text("100") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        supportingText = { Text("100 MB until it is set; at least 1 MB") }
                    )

                    Button(
                        onClick = {
                            editedMaxUploadMb.toULongOrNull()?.let { viewModel.saveMaxUploadMb(it) }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Save the upload limit")
                    }
                }
            }

            // Sync Actions Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Sync Actions",
                        style = MaterialTheme.typography.titleMedium
                    )

                    // This device: the listener switch, the address a device would type, the certificate
                    LaunchedEffect(Unit) { viewModel.loadThisDevice() }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Listen for devices", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                if (listening) "Other devices can reach this phone" else "Off; nothing can reach this phone",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Switch(checked = listening, onCheckedChange = { viewModel.setListening(it) })
                    }
                    // When the listener stops itself (Stage 6): never by default
                    LaunchedEffect(Unit) { viewModel.loadIdleStop() }
                    var idleOpen by remember { mutableStateOf(false) }
                    val idleChoices = listOf(0 to "keep listening", 1 to "stop after 1 hour of silence", 4 to "stop after 4 hours of silence", 8 to "stop after 8 hours of silence")
                    TextButton(onClick = { idleOpen = true }, modifier = Modifier.semantics { contentDescription = "When the listener stops itself" }) {
                        Text(idleChoices.firstOrNull { it.first == idleStopHours }?.second ?: "keep listening")
                    }
                    DropdownMenu(expanded = idleOpen, onDismissRequest = { idleOpen = false }) {
                        idleChoices.forEach { (hours, label) ->
                            DropdownMenuItem(text = { Text(label) }, onClick = { idleOpen = false; viewModel.setIdleStopHours(hours) })
                        }
                    }
                    Text("Account $accountId", style = MaterialTheme.typography.bodySmall)
                    Text(
                        "Address " + (listenAddresses?.let { com.dotancohen.voiceandroid.util.AddressText.words(it.detected, it.shown, it.sentence) } ?: "not read yet"),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text("Certificate $certificateFingerprint", style = MaterialTheme.typography.bodySmall)

                    // One visible button, naming the last device (Stage 5); the arrow
                    // beside it chooses another device or another operation
                    var chooserOpen by remember { mutableStateOf(false) }
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(
                            onClick = { viewModel.exchange() },
                            enabled = !isSyncing && lastDevice != null,
                            modifier = Modifier.weight(1f).semantics { contentDescription = lastDevice?.let { "Exchange with ${it.name}" } ?: "No device to exchange with yet" }
                        ) {
                            if (isSyncing) {
                                CircularProgressIndicator(modifier = Modifier.height(20.dp))
                                Text("  Working…")
                            } else {
                                Text(lastDevice?.let { "Exchange with ${it.name}" } ?: (if (devices.isEmpty()) "No device yet" else "Choose a device ▸"))
                            }
                        }
                        IconButton(onClick = { chooserOpen = true }, enabled = !isSyncing && devices.isNotEmpty(), modifier = Modifier.semantics { contentDescription = "Choose another device or operation" }) {
                            Text("▾")
                        }
                        DropdownMenu(expanded = chooserOpen, onDismissRequest = { chooserOpen = false }) {
                            devices.forEach { device ->
                                listOf(
                                    "exchange" to "Exchange with ${device.name}: sync, then send and fetch recordings",
                                    "deliver" to "Deliver to ${device.name}: sync, then send recordings",
                                    "sync" to "Sync with ${device.name}: notes only",
                                    "send" to "Send to ${device.name}: recordings it lacks, no sync",
                                    "fetch" to "Fetch from ${device.name}: recordings this phone lacks, no sync"
                                ).forEach { (operation, label) ->
                                    DropdownMenuItem(text = { Text(label) }, onClick = { chooserOpen = false; viewModel.operate(operation, device.deviceId) })
                                }
                            }
                        }
                    }

                    // Pairing (Stage 9): show this phone's code, or read another device's
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { if (myCode == null) viewModel.showMyCode() else viewModel.hideMyCode() }, modifier = Modifier.weight(1f)) {
                            Text(if (myCode == null) "Show my code" else "Hide my code")
                        }
                        OutlinedButton(onClick = { readerOpen = true }, modifier = Modifier.weight(1f)) { Text("Read a code") }
                    }
                    myCode?.let { code ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Treat this like a password: whoever reads it joins your account. Hidden in $codeSecondsLeft s.", style = MaterialTheme.typography.bodySmall)
                                QrCodeImage(code, modifier = Modifier.fillMaxWidth(0.8f))
                                Text(code, style = MaterialTheme.typography.bodySmall, modifier = Modifier.semantics { contentDescription = "Setup text $code" })
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = {
                                        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Voice setup text", code))
                                    }, modifier = Modifier.weight(1f)) { Text("Copy") }
                                    OutlinedButton(onClick = {
                                        val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, code) }
                                        context.startActivity(Intent.createChooser(send, "Send the setup text"))
                                    }, modifier = Modifier.weight(1f)) { Text("Share") }
                                }
                            }
                        }
                    }
                    joinMessage?.let { message ->
                        Text(text = message, color = MaterialTheme.colorScheme.primary)
                    }
                    // After pairing: the new device, with one Exchange button (Stage 9)
                    justJoined?.let { joined ->
                        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(if (joined.granted) "${joined.deviceName} hosts this account now. Deliver sends it what this phone holds." else "Paired with ${joined.deviceName}. Exchange brings its notes and recordings here, and yours there.")
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = { viewModel.dismissJoined(); viewModel.operate(if (joined.granted) "deliver" else "exchange", joined.deviceId) }, enabled = !isSyncing, modifier = Modifier.weight(1f)) {
                                        Text(if (joined.granted) "Deliver now" else "Exchange now")
                                    }
                                    OutlinedButton(onClick = { viewModel.dismissJoined() }, modifier = Modifier.weight(1f)) { Text("Later") }
                                }
                            }
                        }
                    }

                    // Upload button: recordings to the bucket, never part of a sync
                    OutlinedButton(
                        onClick = { viewModel.upload() },
                        enabled = !isUploading,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (isUploading) "Uploading..." else "Upload")
                    }
                    uploadMessage?.let { message ->
                        Text(text = message, color = MaterialTheme.colorScheme.primary)
                    }

                    // Progress and Cancel while an operation runs (Stage 4)
                    progressSentence?.let { sentence ->
                        Text(text = sentence, style = MaterialTheme.typography.bodySmall, modifier = Modifier.semantics { contentDescription = sentence })
                        OutlinedButton(onClick = { viewModel.cancelOperation() }, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
                    }

                    // Sync result
                    syncResult?.let { result ->
                        val verb = lastOperation.replaceFirstChar { it.uppercase() }
                        val deviceName = lastDevice?.name ?: "the device"
                        if (result.success) {
                            val parts = mutableListOf<String>()
                            if (lastOperation in listOf("sync", "deliver", "exchange")) parts.add("received ${result.notesReceived} changes and sent ${result.notesSent}")
                            if (result.filesSent > 0) parts.add("sent ${result.filesSent} recordings")
                            if (result.filesFetched > 0) parts.add("fetched ${result.filesFetched} recordings")
                            if (result.bytesMoved > 0) parts.add("${result.bytesMoved / (1024 * 1024)} MB moved")
                            Text(
                                text = "$verb with $deviceName: " + (if (parts.isEmpty()) "nothing to move" else parts.joinToString(", ")) + ".",
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.semantics { contentDescription = "$verb with $deviceName done" }
                            )
                        } else {
                            Text(
                                text = "$verb with $deviceName failed: ${result.errorMessage ?: "it did not say why"}",
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        result.warnings.forEach { warning ->
                            Text(
                                text = "Warning: $warning",
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                        if (result.requestId.isNotEmpty()) {
                            Text(text = "Request ${result.requestId}", style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    // The connection check: one line per thing that can be wrong, each with its code
                    OutlinedButton(
                        onClick = { viewModel.checkConnection() },
                        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Check the connection to the device" }
                    ) {
                        Text("Check connection")
                    }
                    checkRows?.forEach { row ->
                        Text(
                            text = (if (row.passed) "✓ " else "✗ ") + row.name + ": " + row.detail + (if (row.code.isEmpty()) "" else " (" + row.code + ")"),
                            color = if (row.passed) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    // Sync error
                    syncError?.let { error ->
                        Text(
                            text = "Error: $error",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // Advanced Sync Options
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Advanced Sync Options",
                        style = MaterialTheme.typography.titleMedium
                    )

                    // Full Re-sync explanation
                    Text(
                        text = "Full Re-sync",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = "Fetches all data from the server in a single request. Use this if some data like attachments or transcriptions are missing. Does not affect local data.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = { viewModel.fullResync() },
                        enabled = !isSyncing && lastDevice != null,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Full Re-sync")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Reset Sync explanation
                    Text(
                        text = "Reset Sync Timestamps",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = "Clears the 'last synced' timestamps, causing the next regular sync to exchange all data with devices. Use this if instances are out of sync and regular sync isn't picking up all changes. Server configuration is preserved.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = { viewModel.resetSyncTimestamps() },
                        enabled = !isSyncing,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Reset Sync Timestamps")
                    }
                }
            }

            // Debug Info Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Debug Info",
                        style = MaterialTheme.typography.titleMedium
                    )

                    debugInfo?.let { info ->
                        Text(
                            text = info,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } ?: Text(
                        text = "No debug info available",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    TextButton(
                        onClick = { viewModel.updateDebugInfo() }
                    ) {
                        Text("Refresh Debug Info")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
