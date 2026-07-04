package takagi.ru.monica.ui.screens

import android.Manifest
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.zxing.BarcodeFormat
import takagi.ru.monica.R
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

private val DEFAULT_SCANNER_FORMATS = listOf(
    BarcodeFormat.QR_CODE,
    BarcodeFormat.CODE_128,
    BarcodeFormat.CODE_39,
    BarcodeFormat.CODE_93,
    BarcodeFormat.EAN_13,
    BarcodeFormat.EAN_8,
    BarcodeFormat.UPC_A,
    BarcodeFormat.UPC_E,
    BarcodeFormat.ITF,
    BarcodeFormat.CODABAR,
    BarcodeFormat.DATA_MATRIX,
    BarcodeFormat.AZTEC,
    BarcodeFormat.PDF_417
)

/**
 * QR码扫描屏幕
 * 用于扫描TOTP密钥的QR码
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun QrScannerScreen(
    onQrCodeScanned: (String) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    allowedFormats: Collection<BarcodeFormat> = DEFAULT_SCANNER_FORMATS,
    resultValidator: (String) -> Boolean = { true },
    bottomContent: @Composable (launchGallery: () -> Unit) -> Unit = { launchGallery ->
        DefaultQrScannerBottomContent(launchGallery = launchGallery)
    }
) {
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    
    // 添加权限状态监听，在页面显示时自动检查权限
    LaunchedEffect(Unit) {
        if (!cameraPermissionState.status.isGranted) {
            cameraPermissionState.launchPermissionRequest()
        }
    }
    
    Box(
        modifier = modifier.fillMaxSize()
    ) {
        when {
            cameraPermissionState.status.isGranted -> {
                QrCodeScanner(
                    onQrCodeScanned = onQrCodeScanned,
                    onNavigateBack = onNavigateBack,
                    title = title ?: stringResource(R.string.scan_qr_code_title),
                    subtitle = subtitle ?: stringResource(R.string.qr_align_hint),
                    allowedFormats = allowedFormats,
                    resultValidator = resultValidator,
                    bottomContent = bottomContent
                )
            }
            else -> {
                CameraPermissionRequest(
                    permissionState = cameraPermissionState,
                    onNavigateBack = onNavigateBack
                )
            }
        }
    }
}

/**
 * 请求相机权限界面
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun CameraPermissionRequest(
    permissionState: PermissionState,
    onNavigateBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.qr_camera_permission_title),
            style = MaterialTheme.typography.headlineSmall
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = stringResource(R.string.camera_permission_required),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(
            onClick = { permissionState.launchPermissionRequest() }
        ) {
            Text(stringResource(R.string.grant_permission))
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        TextButton(onClick = onNavigateBack) {
            Text(stringResource(R.string.go_back))
        }
    }
}

@Composable
private fun QrCodeScanner(
    onQrCodeScanned: (String) -> Unit,
    onNavigateBack: () -> Unit,
    title: String,
    subtitle: String,
    allowedFormats: Collection<BarcodeFormat>,
    resultValidator: (String) -> Boolean,
    bottomContent: @Composable (launchGallery: () -> Unit) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mlKitFormats = remember(allowedFormats) { allowedFormats.toMlKitFormatList() }
    val scanner = remember(mlKitFormats) { createMlKitBarcodeScanner(mlKitFormats) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val previewView = remember(context) {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    val scanConsumed = remember { AtomicBoolean(false) }
    val processingFrame = remember { AtomicBoolean(false) }
    var showOverlay by remember { mutableStateOf(false) }

    fun acceptResult(raw: String?) {
        val value = raw?.trim()?.takeIf { it.isNotBlank() } ?: return
        if (resultValidator(value) && scanConsumed.compareAndSet(false, true)) {
            onQrCodeScanned(value)
        }
    }

    // 图片选择器 - 使用 GetContent 以兼容所有设备
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            processImageWithMlKit(
                context = context,
                uri = uri,
                scanner = scanner,
                resultValidator = resultValidator,
                onResult = { acceptResult(it) },
                onNotFound = {
                    Toast.makeText(context, context.getString(R.string.qr_not_found), Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    DisposableEffect(previewView, lifecycleOwner, scanner, mlKitFormats) {
        var boundPreview: Preview? = null
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { imageAnalysis ->
                imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
                    analyzeFrameWithMlKit(
                        imageProxy = imageProxy,
                        scanner = scanner,
                        processingFrame = processingFrame,
                        resultValidator = resultValidator,
                        onResult = { acceptResult(it) }
                    )
                }
            }

        val mainExecutor = ContextCompat.getMainExecutor(context)
        cameraProviderFuture.addListener(
            {
                val cameraProvider = runCatching { cameraProviderFuture.get() }.getOrNull()
                    ?: return@addListener
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                boundPreview = preview
                runCatching {
                    cameraProvider.unbind(preview, analysis)
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis
                    )
                }
            },
            mainExecutor
        )

        val lifecycleObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && cameraProviderFuture.isDone) {
                runCatching {
                    val preview = boundPreview ?: return@runCatching
                    val cameraProvider = cameraProviderFuture.get()
                    cameraProvider.unbind(preview, analysis)
                    cameraProviderFuture.get().bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis
                    )
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            analysis.clearAnalyzer()
            runCatching {
                if (cameraProviderFuture.isDone) {
                    val cameraProvider = cameraProviderFuture.get()
                    if (boundPreview != null) {
                        cameraProvider.unbind(boundPreview, analysis)
                    } else {
                        cameraProvider.unbind(analysis)
                    }
                }
            }
            runCatching { scanner.close() }
            analysisExecutor.shutdown()
        }
    }

    LaunchedEffect(Unit) {
        showOverlay = true
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.scrim.copy(alpha = 0.46f),
                            Color.Transparent,
                            MaterialTheme.colorScheme.scrim.copy(alpha = 0.66f)
                        )
                    )
                )
        )

        AnimatedVisibility(
            visible = showOverlay,
            enter = fadeIn(animationSpec = tween(280)) + slideInVertically(
                animationSpec = tween(280),
                initialOffsetY = { -it / 8 }
            ),
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .statusBarsPadding()
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                tonalElevation = 6.dp,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.go_back)
                        )
                    }
                    Column(
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        ScannerFrame(
            modifier = Modifier.align(Alignment.Center)
        )

        AnimatedVisibility(
            visible = showOverlay,
            enter = fadeIn(animationSpec = tween(320)) + slideInVertically(
                animationSpec = tween(320),
                initialOffsetY = { it / 6 }
            ),
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    bottomContent { photoPickerLauncher.launch("image/*") }
                }
            }
        }
    }
}

@Composable
private fun DefaultQrScannerBottomContent(
    launchGallery: () -> Unit
) {
    Text(
        text = stringResource(R.string.qr_align_hint),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Medium
    )
    FilledTonalButton(
        onClick = launchGallery,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Image,
            contentDescription = stringResource(R.string.qr_pick_from_gallery)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(stringResource(R.string.qr_pick_from_gallery))
    }
}

@Composable
private fun ScannerFrame(
    modifier: Modifier = Modifier
) {
    val cornerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.95f)

    Box(
        modifier = modifier
            .size(268.dp)
            .clip(RoundedCornerShape(28.dp))
            .border(
                width = 2.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
                shape = RoundedCornerShape(28.dp)
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.08f))
        )

        ScannerCorner(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(14.dp),
            color = cornerColor,
            position = ScannerCornerPosition.TopStart
        )
        ScannerCorner(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(14.dp),
            color = cornerColor,
            position = ScannerCornerPosition.TopEnd
        )
        ScannerCorner(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(14.dp),
            color = cornerColor,
            position = ScannerCornerPosition.BottomStart
        )
        ScannerCorner(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(14.dp),
            color = cornerColor,
            position = ScannerCornerPosition.BottomEnd
        )
    }
}

private enum class ScannerCornerPosition {
    TopStart,
    TopEnd,
    BottomStart,
    BottomEnd
}

@Composable
private fun ScannerCorner(
    modifier: Modifier = Modifier,
    color: Color,
    position: ScannerCornerPosition
) {
    val horizontalAlignment = when (position) {
        ScannerCornerPosition.TopStart, ScannerCornerPosition.BottomStart -> Alignment.CenterStart
        ScannerCornerPosition.TopEnd, ScannerCornerPosition.BottomEnd -> Alignment.CenterEnd
    }
    val verticalAlignment = when (position) {
        ScannerCornerPosition.TopStart, ScannerCornerPosition.TopEnd -> Alignment.TopCenter
        ScannerCornerPosition.BottomStart, ScannerCornerPosition.BottomEnd -> Alignment.BottomCenter
    }
    val cornerAlignment = when (position) {
        ScannerCornerPosition.TopStart -> Alignment.TopStart
        ScannerCornerPosition.TopEnd -> Alignment.TopEnd
        ScannerCornerPosition.BottomStart -> Alignment.BottomStart
        ScannerCornerPosition.BottomEnd -> Alignment.BottomEnd
    }

    Box(modifier = modifier.size(30.dp)) {
        Box(
            modifier = Modifier
                .align(verticalAlignment)
                .height(5.dp)
                .width(30.dp)
                .clip(CircleShape)
                .background(color)
        )
        Box(
            modifier = Modifier
                .align(horizontalAlignment)
                .width(5.dp)
                .height(30.dp)
                .clip(CircleShape)
                .background(color)
        )
        Box(
            modifier = Modifier
                .align(cornerAlignment)
                .size(6.dp)
                .clip(CircleShape)
                .background(color)
        )
    }
}

private fun Collection<BarcodeFormat>.toMlKitFormatList(): List<Int> {
    val mapped = mapNotNull { format ->
        when (format) {
            BarcodeFormat.QR_CODE -> Barcode.FORMAT_QR_CODE
            BarcodeFormat.CODE_128 -> Barcode.FORMAT_CODE_128
            BarcodeFormat.CODE_39 -> Barcode.FORMAT_CODE_39
            BarcodeFormat.CODE_93 -> Barcode.FORMAT_CODE_93
            BarcodeFormat.EAN_13 -> Barcode.FORMAT_EAN_13
            BarcodeFormat.EAN_8 -> Barcode.FORMAT_EAN_8
            BarcodeFormat.UPC_A -> Barcode.FORMAT_UPC_A
            BarcodeFormat.UPC_E -> Barcode.FORMAT_UPC_E
            BarcodeFormat.ITF -> Barcode.FORMAT_ITF
            BarcodeFormat.CODABAR -> Barcode.FORMAT_CODABAR
            BarcodeFormat.DATA_MATRIX -> Barcode.FORMAT_DATA_MATRIX
            BarcodeFormat.AZTEC -> Barcode.FORMAT_AZTEC
            BarcodeFormat.PDF_417 -> Barcode.FORMAT_PDF417
            else -> null
        }
    }.distinct()
    return mapped.ifEmpty { listOf(Barcode.FORMAT_ALL_FORMATS) }
}

private fun createMlKitBarcodeScanner(formats: List<Int>): BarcodeScanner {
    val builder = BarcodeScannerOptions.Builder()
    if (formats.size == 1) {
        builder.setBarcodeFormats(formats.first())
    } else {
        builder.setBarcodeFormats(formats.first(), *formats.drop(1).toIntArray())
    }
    return BarcodeScanning.getClient(builder.build())
}

private fun analyzeFrameWithMlKit(
    imageProxy: ImageProxy,
    scanner: BarcodeScanner,
    processingFrame: AtomicBoolean,
    resultValidator: (String) -> Boolean,
    onResult: (String) -> Unit
) {
    if (!processingFrame.compareAndSet(false, true)) {
        imageProxy.close()
        return
    }

    val mediaImage = imageProxy.image
    if (mediaImage == null) {
        processingFrame.set(false)
        imageProxy.close()
        return
    }

    val proxyClosed = AtomicBoolean(false)
    fun finishFrame() {
        processingFrame.set(false)
        if (proxyClosed.compareAndSet(false, true)) {
            runCatching { imageProxy.close() }
        }
    }

    val timeout = Runnable { finishFrame() }
    val handler = Handler(Looper.getMainLooper())
    handler.postDelayed(timeout, ML_KIT_FRAME_TIMEOUT_MS)

    val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
    scanner.process(inputImage)
        .addOnSuccessListener { barcodes ->
            barcodes.asSequence()
                .mapNotNull { it.rawValue?.trim()?.takeIf(String::isNotBlank) }
                .firstOrNull(resultValidator)
                ?.let(onResult)
        }
        .addOnCompleteListener {
            handler.removeCallbacks(timeout)
            finishFrame()
        }
}

private fun processImageWithMlKit(
    context: Context,
    uri: Uri,
    scanner: BarcodeScanner,
    resultValidator: (String) -> Boolean,
    onResult: (String) -> Unit,
    onNotFound: () -> Unit
) {
    val image = runCatching { InputImage.fromFilePath(context, uri) }
        .getOrElse {
            onNotFound()
            return
        }

    scanner.process(image)
        .addOnSuccessListener { barcodes ->
            val value = barcodes.asSequence()
                .mapNotNull { it.rawValue?.trim()?.takeIf(String::isNotBlank) }
                .firstOrNull(resultValidator)
            if (value != null) {
                onResult(value)
            } else {
                onNotFound()
            }
        }
        .addOnFailureListener { onNotFound() }
}

private const val ML_KIT_FRAME_TIMEOUT_MS = 1500L
