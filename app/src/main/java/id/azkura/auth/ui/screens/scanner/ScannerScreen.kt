package id.azkura.auth.ui.screens.scanner

import android.content.pm.PackageManager
import android.Manifest
import android.net.Uri
import android.util.Log
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import id.azkura.auth.ui.theme.Accent
import id.azkura.auth.ui.theme.BgBase
import id.azkura.auth.ui.theme.BgCard
import id.azkura.auth.ui.theme.TextMuted
import id.azkura.auth.ui.theme.TextPrimary
import id.azkura.auth.ui.theme.TextSecondary
import id.azkura.auth.util.TotpGenerator
import id.azkura.auth.util.UriParser
import java.util.concurrent.Executors

private const val TAG = "ScannerScreen"

/**
 * Validate a raw barcode payload as a usable Azkura Auth account before ever
 * handing it back to AddAccount. Centralized here so both the live camera
 * scanner and the "import from gallery" path apply exactly the same safety
 * checks and never forward a value that could crash TOTP generation later
 * (see GitHub issues around QR/import safety).
 */
private fun validateOtpauthPayload(value: String?): Result<String> {
    if (value.isNullOrBlank()) {
        return Result.failure(IllegalArgumentException("QR code kosong atau tidak terbaca"))
    }
    if (!value.startsWith("otpauth://", ignoreCase = true)) {
        return Result.failure(IllegalArgumentException("Bukan kode QR akun autentikasi (otpauth://)"))
    }
    return try {
        val parsed = UriParser.parse(value)
        if (!TotpGenerator.isValidSecret(parsed.secret)) {
            Result.failure(IllegalArgumentException("Secret key pada QR tidak valid (Base32)"))
        } else {
            Result.success(value)
        }
    } catch (e: Exception) {
        Result.failure(IllegalArgumentException(e.message ?: "Format QR tidak dikenali"))
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun ScannerScreen(
    onAccountScanned: (String) -> Unit,
    onNavigateBack: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Some devices (tablets, emulators, foldables in certain configurations)
    // have no camera at all. Detect this up front so we never request the
    // CAMERA permission or try to bind CameraX on hardware that doesn't
    // support it — doing so is a common source of crashes/ANRs on such
    // devices. Gallery import remains fully available either way.
    val hasCamera = remember {
        try {
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
        } catch (_: Exception) {
            false
        }
    }

    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)
    var hasScanned by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isProcessingGalleryImage by remember { mutableStateOf(false) }
    var isGalleryActive by remember { mutableStateOf(false) }

    // The ML Kit barcode client is used by both the live camera analyzer and
    // the gallery-import path. Creating it can theoretically fail if Google
    // Play services / the on-device barcode model is unavailable or broken,
    // so it is wrapped and re-attempted lazily rather than crashing the
    // screen on entry.
    val barcodeScanner = remember {
        try {
            BarcodeScanning.getClient()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to initialize barcode scanner", e)
            null
        }
    }

    fun handleScannedValue(value: String?) {
        if (hasScanned) return
        validateOtpauthPayload(value)
            .onSuccess { uri ->
                hasScanned = true
                errorMessage = null
                onAccountScanned(uri)
            }
            .onFailure { error ->
                errorMessage = error.message
            }
    }

    // Photo Picker contract: needs NO storage/media permission on any
    // supported API level (it runs the picker in a separate, system-owned
    // process and only grants access to the single selected image). This
    // avoids the permission-crash / permission-denial pitfalls of the older
    // READ_EXTERNAL_STORAGE / READ_MEDIA_IMAGES flows entirely.
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        isGalleryActive = false
        if (uri == null) return@rememberLauncherForActivityResult
        val scanner = barcodeScanner
        if (scanner == null) {
            errorMessage = "Pemindai QR tidak tersedia di perangkat ini"
            return@rememberLauncherForActivityResult
        }
        isProcessingGalleryImage = true
        errorMessage = null
        try {
            val image = InputImage.fromFilePath(context, uri)
            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    isProcessingGalleryImage = false
                    val value = barcodes.firstNotNullOfOrNull { it.rawValue }
                    if (value == null) {
                        errorMessage = "Tidak ditemukan kode QR pada gambar yang dipilih"
                    } else {
                        handleScannedValue(value)
                    }
                }
                .addOnFailureListener { e ->
                    isProcessingGalleryImage = false
                    Log.w(TAG, "Gallery QR decode failed", e)
                    errorMessage = "Gagal membaca gambar. Pastikan gambar berisi kode QR yang jelas."
                }
        } catch (e: Exception) {
            // InputImage.fromFilePath throws IOException for unreadable/
            // corrupted/unsupported image files (e.g. a HEIC the device
            // can't decode, a 0-byte file, or a revoked content:// grant).
            isProcessingGalleryImage = false
            Log.w(TAG, "Unable to open selected image", e)
            errorMessage = "Tidak dapat membuka gambar yang dipilih"
        }
    }

    // Fallback launcher for devices without the system Photo Picker
    // (Android < 11, or OEMs that removed it). Uses ACTION_GET_CONTENT
    // which is universally available.
    val fallbackGalleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        isGalleryActive = false
        if (uri == null) return@rememberLauncherForActivityResult
        val scanner = barcodeScanner
        if (scanner == null) {
            errorMessage = "Pemindai QR tidak tersedia di perangkat ini"
            return@rememberLauncherForActivityResult
        }
        isProcessingGalleryImage = true
        errorMessage = null
        try {
            val image = InputImage.fromFilePath(context, uri)
            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    isProcessingGalleryImage = false
                    val value = barcodes.firstNotNullOfOrNull { it.rawValue }
                    if (value == null) {
                        errorMessage = "Tidak ditemukan kode QR pada gambar yang dipilih"
                    } else {
                        handleScannedValue(value)
                    }
                }
                .addOnFailureListener { e ->
                    isProcessingGalleryImage = false
                    Log.w(TAG, "Fallback gallery QR decode failed", e)
                    errorMessage = "Gagal membaca gambar. Pastikan gambar berisi kode QR yang jelas."
                }
        } catch (e: Exception) {
            isProcessingGalleryImage = false
            Log.w(TAG, "Fallback unable to open selected image", e)
            errorMessage = "Tidak dapat membuka gambar yang dipilih"
        }
    }

    fun launchGalleryPicker() {
        try {
            isGalleryActive = true
            if (ActivityResultContracts.PickVisualMedia.isPhotoPickerAvailable(context)) {
                galleryLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            } else {
                Log.i(TAG, "Photo picker not available, falling back to GetContent")
                fallbackGalleryLauncher.launch("image/*")
            }
        } catch (e: Exception) {
            isGalleryActive = false
            // No app on the device can handle the picker intent, or the
            // launcher failed to start — try the other launcher as fallback.
            Log.w(TAG, "Primary gallery picker failed, trying fallback", e)
            try {
                fallbackGalleryLauncher.launch("image/*")
            } catch (e2: Exception) {
                Log.w(TAG, "Fallback gallery picker also failed", e2)
                errorMessage = "Tidak dapat membuka galeri di perangkat ini"
            }
        }
    }

    LaunchedEffect(hasCamera) {
        if (hasCamera && !cameraPermission.status.isGranted) {
            cameraPermission.launchPermissionRequest()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                barcodeScanner?.close()
            } catch (_: Exception) {
                // Already closed / never initialized — safe to ignore.
            }
        }
    }

    Scaffold(
        containerColor = BgBase,
        topBar = {
            TopAppBar(
                title = { Text("Scan QR Code", color = TextPrimary, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TextSecondary)
                    }
                },
                actions = {
                    IconButton(onClick = { launchGalleryPicker() }) {
                        Icon(Icons.Default.PhotoLibrary, "Import from gallery", tint = Accent)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgBase),
            )
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.Center,
        ) {
            when {
                !hasCamera -> {
                    NoCameraContent(onPickFromGallery = { launchGalleryPicker() })
                }
                cameraPermission.status is PermissionStatus.Denied &&
                    !(cameraPermission.status as PermissionStatus.Denied).shouldShowRationale &&
                    !cameraPermission.status.isGranted -> {
                    // Permanently denied ("Don't ask again") or not yet
                    // decided — either way, gallery import still works.
                    CameraUnavailableContent(
                        message = "Izin kamera diperlukan untuk memindai langsung.\nAnda tetap bisa impor QR dari galeri.",
                        onPickFromGallery = { launchGalleryPicker() },
                    )
                }
                !cameraPermission.status.isGranted -> {
                    CameraUnavailableContent(
                        message = "Izin kamera diperlukan untuk memindai kode QR",
                        onPickFromGallery = { launchGalleryPicker() },
                    )
                }
                else -> {
                    CameraScannerContent(
                        context = context,
                        lifecycleOwner = lifecycleOwner,
                        barcodeScanner = barcodeScanner,
                        hasScanned = hasScanned,
                        onBarcodeValue = ::handleScannedValue,
                    )
                }
            }

            if (isProcessingGalleryImage) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .clip(RoundedCornerShape(16.dp))
                        .background(BgCard)
                        .padding(24.dp),
                ) {
                    CircularProgressIndicator(color = Accent)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Membaca kode QR...", color = TextSecondary)
                }
            }

            errorMessage?.let { message ->
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                ) {
                    Text(
                        text = message,
                        color = TextPrimary,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.85f))
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }

    // Auto-dismiss transient errors after being shown so they don't linger
    // and block the screen forever. Gallery import errors stay longer (8s)
    // since they appear briefly after the picker closes.
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            val delayMs = if (isProcessingGalleryImage || errorMessage?.contains("gambar") == true) 8000L else 4000L
            kotlinx.coroutines.delay(delayMs)
            errorMessage = null
        }
    }
}

@Composable
private fun NoCameraContent(onPickFromGallery: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(32.dp),
    ) {
        Icon(
            Icons.Default.PhotoLibrary,
            contentDescription = null,
            tint = TextMuted,
            modifier = Modifier.size(48.dp),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Perangkat ini tidak memiliki kamera",
            color = TextPrimary,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Anda tetap bisa menambahkan akun dengan mengimpor kode QR dari galeri.",
            color = TextSecondary,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = onPickFromGallery,
            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.Black),
            shape = RoundedCornerShape(12.dp),
        ) {
            Icon(Icons.Default.PhotoLibrary, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Pilih dari Galeri", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun CameraUnavailableContent(message: String, onPickFromGallery: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
        Text(
            message,
            color = TextSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(20.dp))
        Row {
            Button(
                onClick = onPickFromGallery,
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.Black),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Pilih dari Galeri", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun BoxScope.CameraScannerContent(
    context: android.content.Context,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    barcodeScanner: com.google.mlkit.vision.barcode.BarcodeScanner?,
    hasScanned: Boolean,
    onBarcodeValue: (String?) -> Unit,
) {
    if (barcodeScanner == null) {
        CameraUnavailableContent(
            message = "Pemindai QR tidak tersedia di perangkat ini.\nGunakan impor dari galeri.",
            onPickFromGallery = {},
        )
        return
    }

    val previewView = remember { PreviewView(context) }
    val executor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        onDispose {
            executor.shutdown()
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = Modifier.fillMaxSize(),
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().apply {
                    surfaceProvider = previewView.surfaceProvider
                }

                @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
                @Suppress("DEPRECATION")
                val imageAnalysis = ImageAnalysis.Builder()
                    .setTargetResolution(Size(1280, 720))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .apply {
                        setAnalyzer(executor) { imageProxy ->
                            try {
                                val mediaImage = imageProxy.image
                                if (mediaImage != null && !hasScanned && !isGalleryActive) {
                                    val image = InputImage.fromMediaImage(
                                        mediaImage,
                                        imageProxy.imageInfo.rotationDegrees,
                                    )
                                    barcodeScanner.process(image)
                                        .addOnSuccessListener { barcodes ->
                                            val value = barcodes.firstOrNull {
                                                it.rawValue?.startsWith("otpauth://", ignoreCase = true) == true
                                            }?.rawValue
                                            if (value != null) onBarcodeValue(value)
                                        }
                                        .addOnFailureListener { e ->
                                            Log.w(TAG, "Live frame decode failed", e)
                                        }
                                        .addOnCompleteListener {
                                            imageProxy.close()
                                        }
                                } else {
                                    imageProxy.close()
                                }
                            } catch (e: Exception) {
                                // Never let a single bad frame kill the
                                // analyzer thread / crash the app.
                                Log.w(TAG, "Frame analysis error", e)
                                imageProxy.close()
                            }
                        }
                    }

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageAnalysis,
                )
            } catch (e: Exception) {
                // Camera binding failed (in-use by another app, hardware
                // fault, unsupported configuration, etc.) — the gallery
                // import path remains available via the top bar action.
                Log.w(TAG, "Camera binding failed", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    // QR viewfinder overlay
    Box(
        modifier = Modifier
            .size(250.dp)
            .border(2.dp, Accent.copy(alpha = 0.6f), RoundedCornerShape(16.dp)),
    )

    Text(
        "Point camera at a QR code",
        color = TextPrimary,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 80.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(BgBase.copy(alpha = 0.7f))
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}
