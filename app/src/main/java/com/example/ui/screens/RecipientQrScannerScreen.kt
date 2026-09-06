package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.qr.RecipientQrParser
import com.google.mlkit.vision.barcode.Barcode
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun RecipientQrScannerScreen(
    onRecipientScanned: (name: String, fcn: String) -> Unit,
    onClose: () -> Unit
) {

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var scannerStatus by remember {
        mutableStateOf("Point the camera at a recipient QR code")
    }

    var detectedRawValue by remember {
        mutableStateOf<String?>(null)
    }

    var errorMessage by remember {
        mutableStateOf<String?>(null)
    }

    var cameraStarted by remember {
        mutableStateOf(false)
    }

    val scanLock = remember {
        AtomicBoolean(false)
    }

    val cameraPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { granted ->
            hasCameraPermission = granted

            if (!granted) {
                errorMessage = "Camera permission is required to scan QR codes."
            } else {
                errorMessage = null
            }
        }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            cameraPermissionLauncher.launch(
                Manifest.permission.CAMERA
            )
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {

        if (!hasCameraPermission) {

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {

                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )

                Spacer(
                    modifier = Modifier.height(16.dp)
                )

                Text(
                    text = "Camera Permission Required",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(
                    modifier = Modifier.height(8.dp)
                )

                Text(
                    text = errorMessage
                        ?: "Allow camera access to scan the recipient QR code."
                )

                Spacer(
                    modifier = Modifier.height(20.dp)
                )

                Button(
                    onClick = {
                        cameraPermissionLauncher.launch(
                            Manifest.permission.CAMERA
                        )
                    }
                ) {
                    Text("Allow Camera")
                }

                Spacer(
                    modifier = Modifier.height(8.dp)
                )

                Button(
                    onClick = onClose
                ) {
                    Text("Close")
                }
            }

            return@Box
        }

        /*
         * Camera preview + ML Kit analyzer
         */
        androidx.compose.ui.viewinterop.AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->

                val previewView = PreviewView(ctx)

                previewView.scaleType =
                    PreviewView.ScaleType.FILL_CENTER

                val cameraProviderFuture =
                    ProcessCameraProvider.getInstance(ctx)

                val cameraExecutor =
                    Executors.newSingleThreadExecutor()

                val barcodeScanner =
                    BarcodeScanning.getClient()

                cameraProviderFuture.addListener({

                    try {

                        val cameraProvider =
                            cameraProviderFuture.get()

                        val preview =
                            Preview.Builder()
                                .build()

                        preview.surfaceProvider =
                            previewView.surfaceProvider

                        val imageAnalysis =
                            ImageAnalysis.Builder()
                                .setBackpressureStrategy(
                                    ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
                                )
                                .build()

                        imageAnalysis.setAnalyzer(
                            cameraExecutor
                        ) { imageProxy ->

                            val mediaImage =
                                imageProxy.image

                            if (mediaImage == null) {
                                imageProxy.close()
                                return@setAnalyzer
                            }

                            val image = InputImage.fromMediaImage(
                                mediaImage,
                                imageProxy.imageInfo.rotationDegrees
                            )

                            barcodeScanner.process(image)
                                .addOnSuccessListener { barcodes ->

                                    if (barcodes.isEmpty()) {
                                        return@addOnSuccessListener
                                    }

                                    /*
                                     * ML Kit has detected at least one barcode.
                                     */
                                    scannerStatus =
                                        "QR code detected — reading recipient information..."

                                    val qrBarcode =
                                        barcodes.firstOrNull {
                                            it.format ==
                                                Barcode.FORMAT_QR_CODE
                                        }

                                    if (qrBarcode == null) {
                                        scannerStatus =
                                            "A barcode was detected, but it is not a QR code."
                                        return@addOnSuccessListener
                                    }

                                    val rawValue =
                                        qrBarcode.rawValue
                                            ?.trim()

                                    if (rawValue.isNullOrBlank()) {
                                        scannerStatus =
                                            "QR code detected, but it contains no readable data."
                                        return@addOnSuccessListener
                                    }

                                    detectedRawValue = rawValue

                                    /*
                                     * Parse recipient information.
                                     */
                                    val result =
                                        RecipientQrParser.parse(
                                            rawValue
                                        )

                                    if (result == null) {

                                        scannerStatus =
                                            "QR detected, but recipient information was not recognized."

                                        return@addOnSuccessListener
                                    }

                                    /*
                                     * Prevent multiple callbacks.
                                     */
                                    if (
                                        scanLock.compareAndSet(
                                            false,
                                            true
                                        )
                                    ) {

                                        scannerStatus =
                                            "Recipient found: ${result.name}"

                                        onRecipientScanned(
                                            result.name,
                                            result.fcn
                                        )
                                    }
                                }
                                .addOnFailureListener { exception ->

                                    scannerStatus =
                                        "QR scanning error: ${
                                            exception.message
                                                ?: "Unknown error"
                                        }"
                                }
                                .addOnCompleteListener {

                                    imageProxy.close()
                                }
                        }

                        cameraProvider.unbindAll()

                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageAnalysis
                        )

                        cameraStarted = true
                        errorMessage = null
                        scannerStatus =
                            "Camera ready — point at the recipient QR code."

                    } catch (exception: Exception) {

                        cameraStarted = false

                        errorMessage =
                            "Unable to start camera: ${
                                exception.message
                                    ?: "Unknown error"
                            }"

                        scannerStatus =
                            "Camera could not be started."

                    }

                }, ContextCompat.getMainExecutor(ctx))

                /*
                 * Store cleanup objects on the PreviewView.
                 */
                previewView.addOnAttachStateChangeListener(
                    object :
                        android.view.View.OnAttachStateChangeListener {

                        override fun onViewAttachedToWindow(
                            v: android.view.View
                        ) {
                        }

                        override fun onViewDetachedFromWindow(
                            v: android.view.View
                        ) {

                            try {
                                cameraProviderFuture.get()
                                    .unbindAll()
                            } catch (_: Exception) {
                            }

                            barcodeScanner.close()
                            cameraExecutor.shutdown()
                        }
                    }
                )

                previewView
            }
        )

        /*
         * Dark overlay to make scanner UI easier to see.
         */
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Color.Black.copy(alpha = 0.12f)
                )
        )

        /*
         * Top bar.
         */
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    top = 16.dp,
                    start = 16.dp,
                    end = 16.dp
                ),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor =
                    MaterialTheme.colorScheme.surface.copy(
                        alpha = 0.95f
                    )
            )
        ) {

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {

                Text(
                    text = "Scan Recipient QR",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(
                        Alignment.Center
                    )
                )

                IconButton(
                    onClick = onClose,
                    modifier = Modifier.align(
                        Alignment.CenterEnd
                    )
                ) {

                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close"
                    )
                }
            }
        }

        /*
         * Scanner frame.
         */
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(270.dp)
                .border(
                    width = 3.dp,
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(20.dp)
                )
        )

        /*
         * Status card.
         */
        Card(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor =
                    MaterialTheme.colorScheme.surface.copy(
                        alpha = 0.96f
                    )
            )
        ) {

            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                val statusIcon =
                    when {
                        scannerStatus.startsWith(
                            "Recipient found"
                        ) -> Icons.Default.CheckCircle

                        scannerStatus.startsWith(
                            "QR detected"
                        ) -> Icons.Default.QrCodeScanner

                        errorMessage != null ->
                            Icons.Default.ErrorOutline

                        else ->
                            Icons.Default.QrCodeScanner
                    }

                Icon(
                    imageVector = statusIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )

                Spacer(
                    modifier = Modifier.height(8.dp)
                )

                Text(
                    text = scannerStatus,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )

                if (errorMessage != null) {

                    Spacer(
                        modifier = Modifier.height(6.dp)
                    )

                    Text(
                        text = errorMessage!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                /*
                 * This is intentionally visible while testing.
                 * It proves whether ML Kit actually received QR data.
                 */
                if (!detectedRawValue.isNullOrBlank()) {

                    Spacer(
                        modifier = Modifier.height(8.dp)
                    )

                    Text(
                        text = "QR data detected",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(
                    modifier = Modifier.height(8.dp)
                )

                Text(
                    text = if (cameraStarted) {
                        "Place the recipient QR code inside the frame."
                    } else {
                        "Starting camera..."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
