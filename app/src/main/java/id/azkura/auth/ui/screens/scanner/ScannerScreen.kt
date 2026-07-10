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
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.exifinterface.media.ExifInterface
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

private const val TAG = "ScannerScreen"

/** ML Kit accepts bitmap rotation in 0/90/180/270 only. */
private const val MAX_DECODE_DIMENSION_PX = 1024

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

/**
 * Result of loading an image from a content [Uri]: the downsampled bitmap
 * ready for ML Kit, plus the EXIF rotation (in degrees) that must be applied
 * so barcodes in portrait photos / rotated screenshots decode correctly.
 */
private data class DecodedGalleryImage(
    val bitmap: android.graphics.Bitmap,
    val rotationDegrees: Int,
)

/**
 * Read the EXIF orientation tag of the image at [uri] and convert it to the
 * 0/90/180/270 rotation that [InputImage.fromBitmap] expects. Many phones
 * (Samsung, Xiaomi, screenshots after auto-rotate) store photos with a
 * non-zero orientation tag rather than physically rotating the pixels — if
 * this is ignored, ML Kit receives an unrotated frame and can fail to find
 * the QR finder pattern on portrait-oriented photos.
 */
private fun readExifRotationDegrees(context: android.content.Context, uri: Uri): Int {
    return try {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            when (ExifInterface(stream).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } ?: 0
    } catch (e: Exception) {
        // EXIF is best-effort metadata — never let a malformed/missing tag
        // block the scan. Falling back to "no rotation" still lets ML Kit's
        // rotation-tolerant finder-pattern search attempt a decode.
        Log.w(TAG, "Unable to read EXIF orientation for $uri", e)
        0
    }
}

/**
 * Decode [uri] into a bitmap downsampled to at most [MAX_DECODE_DIMENSION_PX]
 * on the longest side, to bound memory use for large camera photos/
 * screenshots, and read its EXIF rotation. Performs ContentResolver +
 * BitmapFactory I/O, so callers must invoke this off the main thread.
 */
private fun decodeGalleryImage(context: android.content.Context, uri: Uri): DecodedGalleryImage? {
    val rotationDegrees = readExifRotationDegrees(context, uri)

    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { stream ->
        android.graphics.BitmapFactory.decodeStream(stream, null, bounds)
    } ?: return null

    var inSampleSize = 1
    while (bounds.outWidth / inSampleSize > MAX_DECODE_DIMENSION_PX ||
        bounds.outHeight / inSampleSize > MAX_DECODE_DIMENSION_PX
    ) {
        inSampleSize *= 2
    }
    Log.i(TAG, "Image size: ${bounds.outWidth}x${bounds.outHeight}, inSampleSize=$inSampleSize, rotation=$rotationDegrees")

    val decodeOptions = android.graphics.BitmapFactory.Options().apply { this.inSampleSize = inSampleSize }
    val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
        android.graphics.BitmapFactory.decodeStream(stream, null, decodeOptions)
    } ?: return null

    return DecodedGalleryImage(bitmap, rotationDegrees)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun ScannerScreen(
    onAccountScanned: (String) -> Unit,
    onNavigateBack: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

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

    val barcodeScanner = remember {
        try {
            // Configure scanner to detect QR codes specifically with higher success rate
            val options = com.google.mlkit.vision.barcode.BarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                    Barcode.FORMAT_QR_CODE,
                    Barcode.FORMAT_AZTEC,
                    Barcode.FORMAT_DATA_MATRIX,
                )
                .build()
            com.google.mlkit.vision.barcode.BarcodeScanning.getClient(options)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to initialize barcode scanner", e)
            null
        }
    }

    fun handleScannedValue(value: String?) {
        if (hasScanned) {
            Log.d(TAG, "handleScannedValue: ignored, an account was already scanned this session")
            return
        }
        validateOtpauthPayload(value)
            .onSuccess { uri ->
                Log.i(TAG, "handleScannedValue: valid otpauth payload, navigating to Add Account")
                hasScanned = true
                errorMessage = null
                onAccountScanned(uri)
            }
            .onFailure { error ->
                Log.w(TAG, "handleScannedValue: rejected payload — ${error.message}")
                errorMessage = error.message
            }
    }

    // Process a URI returned by either the Photo Picker or the GetContent
    // fallback. Decoding + ContentResolver I/O runs on Dispatchers.IO so a
    // large photo or a cloud-backed gallery item (e.g. Google Photos, not
    // yet cached locally) can never freeze the UI thread while it loads.
    fun processGalleryUri(uri: Uri?) {
        isGalleryActive = false
        if (uri == null) {
            // The user backed out of the picker without choosing anything —
            // this is a normal cancel, not a failure, so no error is shown.
            // Logged so a "nothing happened" report can be told apart from
            // a genuine decode/ML Kit failure in logcat.
            Log.d(TAG, "processGalleryUri: picker returned null URI (user cancelled)")
            return
        }
        Log.i(TAG, "processGalleryUri: received URI $uri, starting decode")
        val scanner = barcodeScanner
        if (scanner == null) {
            Log.e(TAG, "processGalleryUri: barcodeScanner is null, ML Kit failed to initialize")
            errorMessage = "Pemindai QR tidak tersedia di perangkat ini"
            return
        }
        isProcessingGalleryImage = true
        errorMessage = null

        coroutineScope.launch {
            val decoded = try {
                withContext(Dispatchers.IO) { decodeGalleryImage(context, uri) }
            } catch (e: Exception) {
                Log.e(TAG, "processGalleryUri: unable to open/decode selected image", e)
                isProcessingGalleryImage = false
                errorMessage = "Tidak dapat membuka gambar: ${e.message ?: e.javaClass.simpleName}"
                return@launch
            }

            if (decoded == null) {
                Log.e(TAG, "processGalleryUri: decodeGalleryImage returned null (openInputStream/decodeStream failed)")
                isProcessingGalleryImage = false
                errorMessage = "Gambar tidak valid, rusak, atau formatnya tidak didukung"
                return@launch
            }

            Log.i(TAG, "processGalleryUri: bitmap loaded ${decoded.bitmap.width}x${decoded.bitmap.height}, rotation=${decoded.rotationDegrees}, running ML Kit")
            val image = InputImage.fromBitmap(decoded.bitmap, decoded.rotationDegrees)
            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    isProcessingGalleryImage = false
                    Log.i(TAG, "processGalleryUri: ML Kit found ${barcodes.size} barcode(s)")
                    val value = barcodes.firstNotNullOfOrNull { it.rawValue }
                    if (value == null) {
                        Log.w(TAG, "processGalleryUri: ${barcodes.size} barcode(s) found but none had a rawValue")
                        errorMessage = "Tidak ditemukan kode QR pada gambar yang dipilih"
                    } else {
                        Log.i(TAG, "processGalleryUri: QR value: ${value.take(80)}...")
                        handleScannedValue(value)
                    }
                }
                .addOnFailureListener { e ->
                    isProcessingGalleryImage = false
                    Log.e(TAG, "processGalleryUri: ML Kit process() failed", e)
                    errorMessage = "Gagal membaca gambar: ${e.message ?: e.javaClass.simpleName}"
                }
        }
    }

    // Primary strategy: the system Photo Picker (androidx.activity
    // PickVisualMedia). This is Google's officially recommended way to let
    // users select an image — it requires NO runtime storage permission at
    // all (the picker runs in a separate, permission-scoped process and
    // only grants this app a one-shot read URI for the exact item chosen),
    // it is consistent across OEM skins, and it is backed by a Play-services
    // mainline module all the way back to API 19+ devices that have Google
    // Play services installed.
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        Log.i(TAG, "galleryLauncher (Photo Picker) callback fired, uri=$uri")
        processGalleryUri(uri)
    }

    // Fallback strategy: ACTION_OPEN_DOCUMENT / GetContent via the system
    // document picker. Used only on the rare device without a compatible
    // Photo Picker implementation (e.g. AOSP builds / devices without GMS).
    // Also permission-less — access is granted per-URI by the picker itself.
    val fallbackGalleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        Log.i(TAG, "fallbackGalleryLauncher (GetContent) callback fired, uri=$uri")
        processGalleryUri(uri)
    }

    fun launchGalleryPicker() {
        isGalleryActive = true
        val photoPickerAvailable = try {
            ActivityResultContracts.PickVisualMedia.isPhotoPickerAvailable(context)
        } catch (e: Exception) {
            Log.w(TAG, "isPhotoPickerAvailable check failed", e)
            false
        }
        Log.d(TAG, "launchGalleryPicker: photoPickerAvailable=$photoPickerAvailable")

        if (photoPickerAvailable) {
            try {
                galleryLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
                Log.d(TAG, "launchGalleryPicker: Photo Picker launched")
                return
            } catch (e: Exception) {
                Log.w(TAG, "Photo Picker launch failed, falling back to GetContent", e)
            }
        }

        try {
            fallbackGalleryLauncher.launch("image/*")
            Log.d(TAG, "launchGalleryPicker: GetContent fallback launched")
        } catch (e: Exception) {
            isGalleryActive = false
            Log.e(TAG, "All gallery pickers failed", e)
            errorMessage = "Tidak dapat membuka galeri di perangkat ini"
        }
    }

    LaunchedEffect(hasCamera) {
        if (hasCamera && !cameraPermission.status.isGranted) {
            cameraPermission.launchPermissionRequest()
        }
    }

    // Safety net for a documented real-world failure mode on some heavily
    // customized ROMs (e.g. certain Xiaomi/MIUI builds): the gallery picker
    // Activity can return to the app WITHOUT ever invoking the
    // ActivityResultContract callback above — no exception, no uri, no
    // callback at all — which previously looked exactly like "nothing
    // happens" with zero feedback. There is no exception to catch for that
    // case, so instead: when the app comes back to the foreground
    // (ON_RESUME) after a picker was launched, wait briefly for the normal
    // callback to arrive first, then — if isGalleryActive is *still* true —
    // treat it as a failed picker launch and show a clear, actionable error
    // instead of staying silent forever.
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                coroutineScope.launch {
                    kotlinx.coroutines.delay(700)
                    if (isGalleryActive) {
                        Log.e(TAG, "Gallery picker never invoked its result callback after resume — treating as failed")
                        isGalleryActive = false
                        isProcessingGalleryImage = false
                        errorMessage = "Galeri tidak merespons. Coba lagi, atau gunakan pemindai kamera."
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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
                        isGalleryActive = isGalleryActive,
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
    isGalleryActive: Boolean,
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
