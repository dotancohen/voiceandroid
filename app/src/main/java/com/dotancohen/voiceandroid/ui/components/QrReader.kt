package com.dotancohen.voiceandroid.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.dotancohen.voiceandroid.data.PairingRequests
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import kotlinx.coroutines.delay
import java.util.concurrent.Executors

/** Reads a setup text out of one camera frame's brightness (Stage 9). Pure, no hardware. */
object QrDecoder {
    private val reader = MultiFormatReader().apply {
        setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(com.google.zxing.BarcodeFormat.QR_CODE), DecodeHintType.CHARACTER_SET to "UTF-8"))
    }

    /** The text of the code in the frame, or null when there is none. */
    fun decode(luminance: ByteArray, width: Int, height: Int): String? {
        val source = PlanarYUVLuminanceSource(luminance, width, height, 0, 0, width, height, false)
        return try {
            synchronized(reader) { reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text }
        } catch (e: NotFoundException) {
            null
        } finally {
            reader.reset()
        }
    }

    /** The text if it is a setup text; a code for something else is not one. */
    fun setupText(luminance: ByteArray, width: Int, height: Int): String? =
        decode(luminance, width, height)?.takeIf { PairingRequests.isSetupLink(it) }
}

/**
 * Which camera to try, every camera in turn, front-facing last (Stage 9):
 * rear, then external or unknown, then front. `next()` moves on when a
 * camera fails to open or delivers no frame within [NO_FRAME_MILLIS].
 * Pure, so it is tested without hardware (TECHNICAL-DECISIONS 6.7).
 */
class CameraChoice<T>(cameras: List<T>, facingOf: (T) -> Int?) {
    /** The cameras in the order they are tried. */
    val ordered: List<T> = cameras.sortedBy { rank(facingOf(it)) }
    private var index = 0

    val current: T? get() = ordered.getOrNull(index)
    val hasAnother: Boolean get() = ordered.size > 1

    /** The next camera in turn, round and round. */
    fun next(): T? {
        if (ordered.isEmpty()) return null
        index = (index + 1) % ordered.size
        return current
    }

    companion object {
        const val NO_FRAME_MILLIS = 3000L

        /** Rear first, then external or unknown, front last. */
        fun rank(facing: Int?): Int = when (facing) {
            CameraSelector.LENS_FACING_BACK -> 0
            CameraSelector.LENS_FACING_FRONT -> 2
            else -> 1
        }
    }
}

/** The Y plane of a camera frame as one tight array, row stride removed. */
fun ImageProxy.luminance(): ByteArray {
    val plane = planes[0]
    val buffer = plane.buffer
    val out = ByteArray(width * height)
    if (plane.rowStride == width && plane.pixelStride == 1) {
        buffer.get(out)
        return out
    }
    val row = ByteArray(plane.rowStride)
    for (y in 0 until height) {
        buffer.position(y * plane.rowStride)
        buffer.get(row, 0, minOf(plane.rowStride, buffer.remaining()))
        for (x in 0 until width) out[y * width + x] = row[x * plane.pixelStride]
    }
    return out
}

/**
 * The camera reading a code, with the paste field under it for a phone
 * whose camera is poor (Stage 9). Every camera is tried in turn; the
 * first frame that carries a setup text calls [onSetupText].
 */
@Composable
fun QrReader(onSetupText: (String) -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var asked by remember { mutableStateOf(false) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it; asked = true }
    LaunchedEffect(Unit) { if (!granted) ask.launch(Manifest.permission.CAMERA) }

    var pasted by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Point the camera at the code shown by the other device.") }
    var cameraIndex by remember { mutableIntStateOf(0) }
    var lastFrameAt by remember { mutableLongStateOf(0L) }
    var done by remember { mutableStateOf(false) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) { onDispose { executor.shutdown() } }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        if (granted) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).also { view ->
                            val providerFuture = ProcessCameraProvider.getInstance(ctx)
                            providerFuture.addListener({
                                val provider = providerFuture.get()
                                val choice = CameraChoice(provider.availableCameraInfos) { info -> try { info.lensFacing } catch (e: Exception) { null } }
                                val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                                analysis.setAnalyzer(executor) { frame ->
                                    lastFrameAt = System.currentTimeMillis()
                                    val text = try { QrDecoder.setupText(frame.luminance(), frame.width, frame.height) } catch (e: Exception) { null } finally { frame.close() }
                                    if (text != null && !done) { done = true; onSetupText(text) }
                                }
                                val preview = Preview.Builder().build().also { it.surfaceProvider = view.surfaceProvider }
                                fun bind(index: Int) {
                                    provider.unbindAll()
                                    val camera = choice.ordered.getOrNull(index % maxOf(1, choice.ordered.size))
                                    if (camera == null) { status = "This phone has no camera; paste the setup text below."; return }
                                    val selector = CameraSelector.Builder().addCameraFilter { infos -> infos.filter { it == camera }.ifEmpty { infos } }.build()
                                    try {
                                        provider.bindToLifecycle(lifecycleOwner, selector, preview, analysis)
                                        lastFrameAt = System.currentTimeMillis()
                                        status = if (choice.hasAnother) "Camera ${index % choice.ordered.size + 1} of ${choice.ordered.size}. Point it at the code." else "Point the camera at the code shown by the other device."
                                    } catch (e: Exception) {
                                        status = "That camera did not open (${e.message}); trying the next."
                                        if (choice.hasAnother) cameraIndex += 1
                                    }
                                }
                                view.tag = { index: Int -> bind(index) }
                                bind(cameraIndex)
                            }, ContextCompat.getMainExecutor(ctx))
                        }
                    },
                    update = { view -> @Suppress("UNCHECKED_CAST") (view.tag as? (Int) -> Unit)?.invoke(cameraIndex) },
                    modifier = Modifier.fillMaxSize()
                )
            }
            // No frame for three seconds: the next camera in turn
            LaunchedEffect(cameraIndex, granted) {
                while (!done) {
                    delay(CameraChoice.NO_FRAME_MILLIS)
                    if (lastFrameAt != 0L && System.currentTimeMillis() - lastFrameAt > CameraChoice.NO_FRAME_MILLIS) {
                        status = "No picture from this camera; trying the next."
                        cameraIndex += 1
                    }
                }
            }
        } else {
            Text(if (asked) "Without the camera, paste the setup text below." else "Asking for the camera…", modifier = Modifier.weight(1f))
        }
        Text(status, style = MaterialTheme.typography.bodySmall, modifier = Modifier.semantics { contentDescription = status })
        OutlinedTextField(
            value = pasted,
            onValueChange = { pasted = it },
            label = { Text("Or paste the setup text") },
            placeholder = { Text(PairingRequests.SETUP_LINK_SCHEME + "…") },
            modifier = Modifier.fillMaxWidth()
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onClose, modifier = Modifier.weight(1f)) { Text("Cancel") }
            if (granted) OutlinedButton(onClick = { cameraIndex += 1 }, modifier = Modifier.weight(1f)) { Text("Another camera") }
            OutlinedButton(onClick = { if (!done) { done = true; onSetupText(pasted.trim()) } }, enabled = PairingRequests.isSetupLink(pasted), modifier = Modifier.weight(1f)) { Text("Use it") }
        }
    }
}
