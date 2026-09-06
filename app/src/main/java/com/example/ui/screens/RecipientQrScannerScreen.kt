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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.viewinterop.AndroidView

import androidx.core.content.ContextCompat

import com.example.data.qr.RecipientQrParser
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
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
        mutableStateOf(
            "Point the camera at a recipient QR code"
        )
    }

    var errorMessage by remember {
        mutableStateOf<String?>(null)
    }

    /*
     * Prevent the same QR code from being submitted
     * multiple times.
     */
    val scanLock = remember {
        AtomicBoolean(false)
    }

    /*
     * CameraX analyzer executor.
     */
    val cameraExecutor = remember {
        Executors.newSingleThreadExecutor()
    }

    /*
     * ML Kit barcode scanner.
     */
    val barcodeScanner = remember {
        BarcodeScanning.getClient()
    }

    var cameraProvider by remember {
        mutableStateOf<ProcessCameraProvider?>(null)
    }

    /*
     * Camera permission launcher.
     */
    val cameraPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { granted ->

            hasCameraPermission = granted

            if (!granted) {

                errorMessage =
                    "Camera permission is required to scan QR codes."

                scannerStatus =
                    "Camera permission denied."

            } else {

                errorMessage = null

                scannerStatus =
                    "Starting camera..."
            }
        }

    /*
     * Request camera permission when screen opens.
     */
    LaunchedEffect(Unit) {

        if (!hasCameraPermission) {

            cameraPermissionLauncher.launch(
                Manifest.permission.CAMERA
            )
        }
    }

    /*
     * Clean up CameraX and ML Kit.
     */
    DisposableEffect(Unit) {

        onDispose {

            cameraProvider?.unbindAll()

            barcodeScanner.close()

            cameraExecutor.shutdown()
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {

        /*
         * Camera permission screen.
         */
        if (!hasCameraPermission) {

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),

                horizontalAlignment =
                    Alignment.CenterHorizontally,

                verticalArrangement =
                    Arrangement.Center
            ) {

                Icon(
                    imageVector =
                        Icons.Default.CameraAlt,

                    contentDescription = null,

                    modifier = Modifier.size(64.dp),

                    tint =
                        MaterialTheme.colorScheme.primary
                )

                Spacer(
                    modifier = Modifier.height(16.dp)
                )

                Text(
                    text =
                        "Camera Permission Required",

                    style =
                        MaterialTheme.typography.titleLarge,

                    fontWeight =
                        FontWeight.Bold
                )

                Spacer(
                    modifier = Modifier.height(8.dp)
                )

                Text(
                    text =
                        errorMessage
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
         * Camera preview.
         */
        AndroidView(
            modifier = Modifier.fillMaxSize(),

            factory = { ctx ->

                val previewView =
                    PreviewView(ctx)

                previewView.scaleType =
                    PreviewView.ScaleType.FILL_CENTER

                val cameraProviderFuture =
                    ProcessCameraProvider.getInstance(ctx)

                cameraProviderFuture.addListener({

                    try {

                        val provider =
                            cameraProviderFuture.get()

                        cameraProvider =
                            provider

                        /*
                         * Camera preview.
                         */
                        val preview =
                            Preview.Builder()
                                .build()

                        preview.surfaceProvider =
                            previewView.surfaceProvider

                        /*
                         * Camera image analysis.
                         */
                        val imageAnalysis =
                            ImageAnalysis.Builder()
                                .setBackpressureStrategy(
                                    ImageAnalysis
                                        .STRATEGY_KEEP_ONLY_LATEST
                                )
                                .build()

                        /*
                         * Analyze camera frames.
                         */
                        imageAnalysis.setAnalyzer(
                            cameraExecutor
                        ) { imageProxy ->

                            val mediaImage =
                                imageProxy.image

                            if (mediaImage == null) {

                                imageProxy.close()

                                return@setAnalyzer
                            }

                            /*
                             * Convert CameraX image
                             * into ML Kit InputImage.
                             */
                            val inputImage =
                                InputImage.fromMediaImage(
                                    mediaImage,
                                    imageProxy
                                        .imageInfo
                                        .rotationDegrees
                                )

                            /*
                             * Send image to ML Kit.
                             */
                            barcodeScanner
                                .process(inputImage)

                                .addOnSuccessListener { barcodes ->

                                    /*
                                     * Find QR code only.
                                     */
                                    val qrBarcode =
                                        barcodes.firstOrNull {

                                            it.format ==
                                                Barcode.FORMAT_QR_CODE
                                        }

                                    /*
                                     * No QR code found.
                                     */
                                    if (qrBarcode == null) {

                                        return@addOnSuccessListener
                                    }

                                    /*
                                     * Get the actual text
                                     * stored inside the QR.
                                     */
                                    val rawValue =
                                        qrBarcode
                                            .rawValue
                                            ?.trim()

                                    if (rawValue.isNullOrBlank()) {

                                        scannerStatus =
                                            "QR code detected, but no data could be read."

                                        return@addOnSuccessListener
                                    }

                                    scannerStatus =
                                        "QR code detected — reading recipient information..."

                                    /*
                                     * Parse the QR data.
                                     */
                                    val result =
                                        RecipientQrParser.parse(
                                            rawValue
                                        )

                                    /*
                                     * QR was detected but
                                     * recipient information
                                     * could not be extracted.
                                     */
                                    if (result == null) {

                                        scannerStatus =
                                            "QR detected, but recipient information was not recognized."

                                        return@addOnSuccessListener
                                    }

                                    /*
                                     * Prevent duplicate scans.
                                     */
                                    if (
                                        scanLock.compareAndSet(
                                            false,
                                            true
                                        )
                                    ) {

                                        scannerStatus =
                                            "Recipient found: ${result.name}"

                                        /*
                                         * Send:
                                         *
                                         * Name = Shobi Alam
                                         * FCN  = 600884
                                         *
                                         * to CollectionEntryScreen.
                                         */
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

                                /*
                                 * VERY IMPORTANT:
                                 * Always close the image.
                                 */
                                .addOnCompleteListener {

                                    imageProxy.close()
                                }
                        }

                        /*
                         * Remove any previous camera binding.
                         */
                        provider.unbindAll()

                        /*
                         * Start camera.
                         */
                        provider.bindToLifecycle(
                            lifecycleOwner,

                            CameraSelector
                                .DEFAULT_BACK_CAMERA,

                            preview,

                            imageAnalysis
                        )

                        scannerStatus =
                            "Camera ready — point at the recipient QR code."

                        errorMessage = null

                    } catch (exception: Exception) {

                        errorMessage =
                            "Unable to start camera: ${
                                exception.message
                                    ?: "Unknown error"
                            }"

                        scannerStatus =
                            "Camera could not be started."
                    }

                }, ContextCompat.getMainExecutor(ctx))

                previewView
            }
        )

        /*
         * Scanner overlay.
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

            shape =
                RoundedCornerShape(16.dp),

            colors =
                CardDefaults.cardColors(
                    containerColor =
                        MaterialTheme
                            .colorScheme
                            .surface
                            .copy(alpha = 0.95f)
                )
        ) {

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = 8.dp,
                        vertical = 4.dp
                    ),

                contentAlignment =
                    Alignment.CenterStart
            ) {

                IconButton(
                    onClick = onClose
                ) {

                    Icon(
                        imageVector =
                            Icons.Default.Close,

                        contentDescription =
                            "Close scanner"
                    )
                }

                Text(
                    text =
                        "Scan Recipient QR",

                    modifier =
                        Modifier.padding(
                            start = 48.dp
                        ),

                    style =
                        MaterialTheme
                            .typography
                            .titleMedium,

                    fontWeight =
                        FontWeight.Bold
                )
            }
        }

        /*
         * QR scanning frame.
         */
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(260.dp)
                .border(
                    width = 3.dp,

                    color =
                        MaterialTheme
                            .colorScheme
                            .primary,

                    shape =
                        RoundedCornerShape(20.dp)
                )
        )

        /*
         * Bottom status card.
         */
        Card(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp),

            shape =
                RoundedCornerShape(16.dp),

            colors =
                CardDefaults.cardColors(
                    containerColor =
                        MaterialTheme
                            .colorScheme
                            .surface
                            .copy(alpha = 0.95f)
                )
        ) {

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),

                horizontalAlignment =
                    Alignment.CenterHorizontally
            ) {

                Icon(
                    imageVector =
                        if (scanLock.get()) {

                            Icons.Default.CheckCircle

                        } else {

                            Icons.Default.QrCodeScanner
                        },

                    contentDescription = null,

                    tint =
                        MaterialTheme
                            .colorScheme
                            .primary,

                    modifier =
                        Modifier.size(32.dp)
                )

                Spacer(
                    modifier = Modifier.height(8.dp)
                )

                Text(
                    text = scannerStatus,

                    style =
                        MaterialTheme
                            .typography
                            .bodyMedium,

                    fontWeight =
                        FontWeight.Medium
                )

                if (errorMessage != null) {

                    Spacer(
                        modifier = Modifier.height(8.dp)
                    )

                    Icon(
                        imageVector =
                            Icons.Default.ErrorOutline,

                        contentDescription = null,

                        tint =
                            MaterialTheme
                                .colorScheme
                                .error,

                        modifier =
                            Modifier.size(24.dp)
                    )

                    Spacer(
                        modifier = Modifier.height(4.dp)
                    )

                    Text(
                        text = errorMessage!!,

                        color =
                            MaterialTheme
                                .colorScheme
                                .error,

                        style =
                            MaterialTheme
                                .typography
                                .bodySmall
                    )
                }

                Spacer(
                    modifier = Modifier.height(8.dp)
                )

                Text(
                    text =
                        "Place the recipient QR code inside the frame",

                    style =
                        MaterialTheme
                            .typography
                            .bodySmall
                )
            }
        }
    }
}
