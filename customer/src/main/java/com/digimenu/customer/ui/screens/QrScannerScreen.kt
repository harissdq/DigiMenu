package com.digimenu.customer.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.digimenu.customer.ui.theme.TextSecondary
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

private const val DUPLICATE_WINDOW_MS = 2_000L

/** Tracks the last decoded payload so the same QR is not re-processed in a loop. */
private data class LastScan(val value: String, val atMillis: Long)

/**
 * Camera view that decodes table QR codes. Every barcode the analyzer produces
 * is handed to [onQrScanned]; the ViewModel decides whether it is a valid table.
 */
@Composable
fun QrScannerScreen(
    message: String?,
    onQrScanned: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    Box(Modifier.fillMaxSize()) {
        if (hasPermission) {
            CameraPreview(
                lifecycleOwner = lifecycleOwner,
                onQrScanned = onQrScanned,
            )
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(16.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            ) {
                Text(
                    text = message ?: "Point the camera at the table's QR code",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "Camera permission is required to scan the table QR code.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary,
                )
                Button(
                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    modifier = Modifier.padding(top = 16.dp),
                ) { Text("Grant camera permission") }
            }
        }
    }
}

@Composable
private fun CameraPreview(
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    onQrScanned: (String) -> Unit,
) {
    val context = LocalContext.current
    val mainExecutor: Executor = ContextCompat.getMainExecutor(context)
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val lastScan = remember { AtomicReference<LastScan>() }

    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }

    DisposableEffect(lifecycleOwner) {
        val scanner: BarcodeScanner = BarcodeScanning.getClient()

        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        analysis.setAnalyzer(analysisExecutor) { image ->
            analyzeImage(image, scanner, lastScan, onQrScanned)
        }

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val listener = Runnable {
            runCatching {
                val provider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis,
                )
            }
        }
        cameraProviderFuture.addListener(listener, mainExecutor)

        onDispose {
            analysisExecutor.shutdown()
            scanner.close()
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = Modifier.fillMaxSize(),
    )
}

private fun analyzeImage(
    image: ImageProxy,
    scanner: BarcodeScanner,
    lastScan: AtomicReference<LastScan>,
    onQrScanned: (String) -> Unit,
) {
    val mediaImage = image.image
    if (mediaImage != null) {
        val input = InputImage.fromMediaImage(mediaImage, image.imageInfo.rotationDegrees)
        runCatching {
            scanner.process(input)
                .addOnSuccessListener { barcodes ->
                    val value = barcodes.firstNotNullOfOrNull { it.rawValue }
                    if (value != null && shouldProcess(lastScan, value)) {
                        onQrScanned(value)
                    }
                }
        }
    }
    image.close()
}

/** Drops repeats of the same payload until the [DUPLICATE_WINDOW_MS] has elapsed. */
private fun shouldProcess(lastScan: AtomicReference<LastScan>, value: String): Boolean {
    val now = SystemClock.elapsedRealtime()
    val previous = lastScan.get()
    if (previous != null && previous.value == value && now - previous.atMillis < DUPLICATE_WINDOW_MS) {
        return false
    }
    lastScan.set(LastScan(value, now))
    return true
}
