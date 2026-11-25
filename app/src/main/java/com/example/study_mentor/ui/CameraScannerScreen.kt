package com.example.study_mentor.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.study_mentor.ui.theme.BackgroundOffWhite
import com.example.study_mentor.ui.theme.PrimaryBlue
import com.example.study_mentor.ui.theme.SoftOrangePrimaryLight
import java.io.File
import java.io.FileOutputStream

// ─── Drag Handle Identifier for Resizing ─────────────────────────────────────

enum class ViewfinderHandle {
    NONE, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, BODY
}

/**
 * Live CameraX Preview Composable using AndroidView to inflate PreviewView.
 * Binds CameraX Preview and ImageCapture use cases to the local lifecycle owner
 * with the default back-facing camera.
 *
 * Performance Fix:
 * Uses PreviewView.ImplementationMode.PERFORMANCE (SurfaceView) for lower latency
 * and hardware-accelerated preview rendering. Binding is performed strictly ONCE
 * inside the AndroidView factory block (with no update block), preventing camera
 * re-binding during recompositions.
 */
@Composable
fun CameraPreview(
    imageCapture: ImageCapture,
    modifier: Modifier = Modifier,
    cameraSelector: CameraSelector = CameraSelector.DEFAULT_BACK_CAMERA,
    onCameraBound: ((Camera) -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnCameraBound by rememberUpdatedState(onCameraBound)

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                // PERFORMANCE uses SurfaceView for lower latency and smoother camera rendering
                implementationMode = PreviewView.ImplementationMode.PERFORMANCE
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }

            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                try {
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    cameraProvider.unbindAll()
                    val camera = cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageCapture
                    )
                    currentOnCameraBound?.invoke(camera)
                } catch (exc: Exception) {
                    Log.e("CameraScannerScreen", "CameraX binding failed", exc)
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        }
        // Strictly omit the update block to guarantee zero re-binding on recompositions
    )
}

/**
 * Interactive Resizable Crop Frame Overlay.
 *
 * Performance & UX Optimizations:
 * 1. Isolated Recomposition (Draw Phase Deferral): Reads crop rectangle state strictly inside the
 *    DrawScope of Canvas and pointerInput(Unit) lambda. Dragging corners NEVER triggers recomposition
 *    of CameraPreview, the camera viewfinder, or the bottom input panel.
 * 2. Zero-Allocation Draw Loop: Pre-allocates and remembers CornerRadius, Stroke, and dimensions
 *    outside Canvas to prevent GC pressure and stuttering.
 * 3. 48.dp+ Touch Hit-Targets: Generous touch radius around each corner handle ensures effortless grabbing.
 * 4. Strict Viewport Clamping: Clamps 4 corner coordinates within visible camera viewport bounds above
 *    the bottom panel, with a minimum crop box size of 80.dp x 80.dp.
 */
@Composable
fun InteractiveCropOverlay(
    cropRectState: MutableState<Rect>,
    modifier: Modifier = Modifier,
    minWidth: Dp = 80.dp,
    minHeight: Dp = 80.dp,
    dimColor: Color = Color.Black.copy(alpha = 0.6f),
    strokeColor: Color = Color.White,
    onCropRectChange: ((Rect) -> Unit)? = null
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val totalW = constraints.maxWidth.toFloat()
        val totalH = constraints.maxHeight.toFloat()

        // 1. Precompute dimensions and generous 48.dp hit-targets
        val minWidthPx = remember(density, minWidth) { with(density) { minWidth.toPx() } }
        val minHeightPx = remember(density, minHeight) { with(density) { minHeight.toPx() } }
        val handleTouchRadius = remember(density) { with(density) { 48.dp.toPx() } }

        val boundMinX = remember(density) { with(density) { 16.dp.toPx() } }
        val boundMaxX = (totalW - boundMinX).coerceAtLeast(boundMinX + minWidthPx)
        val boundMinY = remember(density) { with(density) { 72.dp.toPx() } }
        val boundMaxY = (totalH - remember(density) { with(density) { 16.dp.toPx() } }).coerceAtLeast(boundMinY + minHeightPx)

        // 2. Pre-allocate drawing styles & dimensions outside Canvas draw loop to achieve 0-allocations per frame
        val cornerRadius = remember(density) { with(density) { CornerRadius(12.dp.toPx(), 12.dp.toPx()) } }
        val borderStroke = remember(density) { with(density) { Stroke(width = 2.dp.toPx()) } }
        val cornerLen = remember(density) { with(density) { 24.dp.toPx() } }
        val cornerStrokeWidth = remember(density) { with(density) { 3.5.dp.toPx() } }
        val dotRadius = remember(density) { with(density) { 5.dp.toPx() } }
        val dotBorderRadius = remember(density) { with(density) { 8.dp.toPx() } }
        val borderStrokeColor = remember(strokeColor) { strokeColor.copy(alpha = 0.85f) }

        // Capture dynamic values for pointerInput(Unit) without restarting the gesture detector
        val currentBoundMinX by rememberUpdatedState(boundMinX)
        val currentBoundMaxX by rememberUpdatedState(boundMaxX)
        val currentBoundMinY by rememberUpdatedState(boundMinY)
        val currentBoundMaxY by rememberUpdatedState(boundMaxY)
        val currentMinWidthPx by rememberUpdatedState(minWidthPx)
        val currentMinHeightPx by rememberUpdatedState(minHeightPx)
        val currentHandleTouchRadius by rememberUpdatedState(handleTouchRadius)
        val currentOnCropRectChange by rememberUpdatedState(onCropRectChange)

        var activeHandle by remember { mutableStateOf(ViewfinderHandle.NONE) }

        // Initialize default crop frame when viewport dimensions are measured
        LaunchedEffect(totalW, totalH) {
            if (totalW > 0f && totalH > 0f) {
                val current = cropRectState.value
                if (current == Rect.Zero || current.right > totalW || current.bottom > totalH) {
                    val initW = (totalW * 0.82f).coerceIn(minWidthPx, boundMaxX - boundMinX)
                    val initH = with(density) { 180.dp.toPx() }.coerceIn(minHeightPx, boundMaxY - boundMinY)
                    val initL = (totalW - initW) / 2f
                    val initT = (totalH - initH) / 2f
                    val initialRect = Rect(initL, initT, initL + initW, initT + initH)
                    cropRectState.value = initialRect
                    onCropRectChange?.invoke(initialRect)
                }
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { startOffset ->
                            val currentRect = cropRectState.value
                            val distTL = (startOffset - Offset(currentRect.left, currentRect.top)).getDistance()
                            val distTR = (startOffset - Offset(currentRect.right, currentRect.top)).getDistance()
                            val distBL = (startOffset - Offset(currentRect.left, currentRect.bottom)).getDistance()
                            val distBR = (startOffset - Offset(currentRect.right, currentRect.bottom)).getDistance()

                            val minDist = minOf(distTL, minOf(distTR, minOf(distBL, distBR)))
                            activeHandle = when {
                                minDist <= currentHandleTouchRadius -> {
                                    when (minDist) {
                                        distTL -> ViewfinderHandle.TOP_LEFT
                                        distTR -> ViewfinderHandle.TOP_RIGHT
                                        distBL -> ViewfinderHandle.BOTTOM_LEFT
                                        else -> ViewfinderHandle.BOTTOM_RIGHT
                                    }
                                }
                                currentRect.contains(startOffset) -> ViewfinderHandle.BODY
                                else -> ViewfinderHandle.NONE
                            }
                        },
                        onDrag = { change, dragAmount ->
                            if (activeHandle != ViewfinderHandle.NONE) {
                                change.consume()
                                val cur = cropRectState.value
                                val updated = when (activeHandle) {
                                    ViewfinderHandle.TOP_LEFT -> {
                                        val newL = (cur.left + dragAmount.x).coerceIn(currentBoundMinX, cur.right - currentMinWidthPx)
                                        val newT = (cur.top + dragAmount.y).coerceIn(currentBoundMinY, cur.bottom - currentMinHeightPx)
                                        Rect(newL, newT, cur.right, cur.bottom)
                                    }
                                    ViewfinderHandle.TOP_RIGHT -> {
                                        val newR = (cur.right + dragAmount.x).coerceIn(cur.left + currentMinWidthPx, currentBoundMaxX)
                                        val newT = (cur.top + dragAmount.y).coerceIn(currentBoundMinY, cur.bottom - currentMinHeightPx)
                                        Rect(cur.left, newT, newR, cur.bottom)
                                    }
                                    ViewfinderHandle.BOTTOM_LEFT -> {
                                        val newL = (cur.left + dragAmount.x).coerceIn(currentBoundMinX, cur.right - currentMinWidthPx)
                                        val newB = (cur.bottom + dragAmount.y).coerceIn(cur.top + currentMinHeightPx, currentBoundMaxY)
                                        Rect(newL, cur.top, cur.right, newB)
                                    }
                                    ViewfinderHandle.BOTTOM_RIGHT -> {
                                        val newR = (cur.right + dragAmount.x).coerceIn(cur.left + currentMinWidthPx, currentBoundMaxX)
                                        val newB = (cur.bottom + dragAmount.y).coerceIn(cur.top + currentMinHeightPx, currentBoundMaxY)
                                        Rect(cur.left, cur.top, newR, newB)
                                    }
                                    ViewfinderHandle.BODY -> {
                                        val dx = dragAmount.x.coerceIn(currentBoundMinX - cur.left, currentBoundMaxX - cur.right)
                                        val dy = dragAmount.y.coerceIn(currentBoundMinY - cur.top, currentBoundMaxY - cur.bottom)
                                        cur.translate(dx, dy)
                                    }
                                    ViewfinderHandle.NONE -> cur
                                }
                                cropRectState.value = updated
                                currentOnCropRectChange?.invoke(updated)
                            }
                        },
                        onDragEnd = { activeHandle = ViewfinderHandle.NONE },
                        onDragCancel = { activeHandle = ViewfinderHandle.NONE }
                    )
                }
        ) {
            // Strictly read crop coordinates inside the Draw phase: zero recompositions during drag!
            val rect = cropRectState.value
            if (rect == Rect.Zero || rect.width <= 0f || rect.height <= 0f) return@Canvas

            // 1. Semi-transparent dark overlay covering entire camera area
            drawRect(color = dimColor)

            // 2. Punch clear transparent cutout
            drawRoundRect(
                color = Color.Transparent,
                topLeft = rect.topLeft,
                size = rect.size,
                cornerRadius = cornerRadius,
                blendMode = BlendMode.Clear
            )

            // 3. Crisp white bounding border
            drawRoundRect(
                color = borderStrokeColor,
                topLeft = rect.topLeft,
                size = rect.size,
                cornerRadius = cornerRadius,
                style = borderStroke
            )

            // 4. Photomath-style L-bracket corner guides
            // Top-Left
            drawLine(Color.White, Offset(rect.left, rect.top), Offset(rect.left + cornerLen, rect.top), cornerStrokeWidth, StrokeCap.Round)
            drawLine(Color.White, Offset(rect.left, rect.top), Offset(rect.left, rect.top + cornerLen), cornerStrokeWidth, StrokeCap.Round)

            // Top-Right
            drawLine(Color.White, Offset(rect.right, rect.top), Offset(rect.right - cornerLen, rect.top), cornerStrokeWidth, StrokeCap.Round)
            drawLine(Color.White, Offset(rect.right, rect.top), Offset(rect.right, rect.top + cornerLen), cornerStrokeWidth, StrokeCap.Round)

            // Bottom-Left
            drawLine(Color.White, Offset(rect.left, rect.bottom), Offset(rect.left + cornerLen, rect.bottom), cornerStrokeWidth, StrokeCap.Round)
            drawLine(Color.White, Offset(rect.left, rect.bottom), Offset(rect.left, rect.bottom - cornerLen), cornerStrokeWidth, StrokeCap.Round)

            // Bottom-Right
            drawLine(Color.White, Offset(rect.right, rect.bottom), Offset(rect.right - cornerLen, rect.bottom), cornerStrokeWidth, StrokeCap.Round)
            drawLine(Color.White, Offset(rect.right, rect.bottom), Offset(rect.right, rect.bottom - cornerLen), cornerStrokeWidth, StrokeCap.Round)

            // 5. Visual Corner Dots/Handles (drawn directly without list allocation)
            drawCircle(Color.White, radius = dotBorderRadius, center = Offset(rect.left, rect.top))
            drawCircle(PrimaryBlue, radius = dotRadius, center = Offset(rect.left, rect.top))

            drawCircle(Color.White, radius = dotBorderRadius, center = Offset(rect.right, rect.top))
            drawCircle(PrimaryBlue, radius = dotRadius, center = Offset(rect.right, rect.top))

            drawCircle(Color.White, radius = dotBorderRadius, center = Offset(rect.left, rect.bottom))
            drawCircle(PrimaryBlue, radius = dotRadius, center = Offset(rect.left, rect.bottom))

            drawCircle(Color.White, radius = dotBorderRadius, center = Offset(rect.right, rect.bottom))
            drawCircle(PrimaryBlue, radius = dotRadius, center = Offset(rect.right, rect.bottom))
        }

        // Floating guidance pill pinned nicely below top bar
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.Black.copy(alpha = 0.65f),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 76.dp)
        ) {
            Text(
                text = "Drag 4 corners to frame the question or problem",
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

/**
 * Backwards-compatible wrapper delegating to [InteractiveCropOverlay].
 */
@Deprecated(
    message = "Use InteractiveCropOverlay for isolated recomposition and performance",
    replaceWith = ReplaceWith("InteractiveCropOverlay(cropRectState, modifier, minWidth, minHeight, dimColor, strokeColor)")
)
@Composable
fun InteractiveViewfinderOverlay(
    cropRect: Rect,
    onCropRectChange: (Rect) -> Unit,
    modifier: Modifier = Modifier,
    minWidth: Dp = 80.dp,
    minHeight: Dp = 80.dp,
    dimColor: Color = Color.Black.copy(alpha = 0.6f),
    strokeColor: Color = Color.White
) {
    val state = remember { mutableStateOf(cropRect) }
    LaunchedEffect(cropRect) { state.value = cropRect }
    InteractiveCropOverlay(
        cropRectState = state,
        modifier = modifier,
        minWidth = minWidth,
        minHeight = minHeight,
        dimColor = dimColor,
        strokeColor = strokeColor,
        onCropRectChange = onCropRectChange
    )
}

/**
 * Accurately crops a captured JPEG photo to the user's viewfinder selection rectangle.
 * Reads EXIF orientation, rotates the bitmap to match portrait orientation, computes the
 * PreviewView.ScaleType.FILL_CENTER aspect ratio mapping, crops the ROI, and saves to cache.
 */
fun cropAndSaveImage(
    context: Context,
    photoFile: File,
    cropRect: Rect,
    previewWidth: Int,
    previewHeight: Int
): Uri? {
    if (!photoFile.exists()) return null

    // 1. Read EXIF Orientation
    val exif = ExifInterface(photoFile.absolutePath)
    val orientation = exif.getAttributeInt(
        ExifInterface.TAG_ORIENTATION,
        ExifInterface.ORIENTATION_NORMAL
    )
    val rotationDegrees = when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> 0f
    }

    // 2. Decode full raw bitmap
    val rawBitmap = BitmapFactory.decodeFile(photoFile.absolutePath) ?: return null

    // 3. Rotate bitmap if needed to match screen orientation
    val orientedBitmap = if (rotationDegrees != 0f) {
        val matrix = Matrix().apply { postRotate(rotationDegrees) }
        val rotated = Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
        if (rotated != rawBitmap) rawBitmap.recycle()
        rotated
    } else {
        rawBitmap
    }

    val bWidth = orientedBitmap.width
    val bHeight = orientedBitmap.height

    // 4. Calculate aspect ratio scaling with PreviewView.ScaleType.FILL_CENTER
    val scale = maxOf(
        previewWidth.toFloat() / bWidth.toFloat(),
        previewHeight.toFloat() / bHeight.toFloat()
    )
    val scaledW = bWidth * scale
    val scaledH = bHeight * scale
    val offsetX = (scaledW - previewWidth) / 2f
    val offsetY = (scaledH - previewHeight) / 2f

    // 5. Map screen cropRect coordinates to the oriented bitmap
    val startX = ((cropRect.left + offsetX) / scale).toInt().coerceIn(0, bWidth - 1)
    val startY = ((cropRect.top + offsetY) / scale).toInt().coerceIn(0, bHeight - 1)
    val cropW = (cropRect.width / scale).toInt().coerceIn(1, bWidth - startX)
    val cropH = (cropRect.height / scale).toInt().coerceIn(1, bHeight - startY)

    // 6. Crop bitmap to the user's viewfinder selection
    val croppedRaw = Bitmap.createBitmap(orientedBitmap, startX, startY, cropW, cropH)
    if (croppedRaw != orientedBitmap) orientedBitmap.recycle()

    // 7. Downscale cropped bitmap so its longest side is at most 1024px
    val maxDim = 1024
    val longestSide = maxOf(croppedRaw.width, croppedRaw.height)
    val croppedBitmap = if (longestSide > maxDim) {
        val downscaleRatio = maxDim.toFloat() / longestSide.toFloat()
        val targetW = (croppedRaw.width * downscaleRatio).toInt().coerceAtLeast(1)
        val targetH = (croppedRaw.height * downscaleRatio).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(croppedRaw, targetW, targetH, true)
        if (scaled != croppedRaw) croppedRaw.recycle()
        scaled
    } else {
        croppedRaw
    }

    // 8. Save compressed bitmap (75-80% quality) to temporary cache file
    val croppedFile = File.createTempFile("CROP_${System.currentTimeMillis()}_", ".jpg", context.cacheDir)
    FileOutputStream(croppedFile).use { out ->
        croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 78, out)
    }
    croppedBitmap.recycle()

    return FileProvider.getUriForFile(
        context,
        "com.example.study_mentor.fileprovider",
        croppedFile
    )
}

/**
 * Triggers CameraX image capture, processes and crops the resulting image on a background thread,
 * and passes the cropped file URI to the callback.
 */
fun takePhotoAndCrop(
    context: Context,
    imageCapture: ImageCapture,
    cropRect: Rect,
    previewWidth: Int,
    previewHeight: Int,
    onSuccess: (Uri) -> Unit,
    onError: (Exception) -> Unit
) {
    val tempFile = try {
        File.createTempFile("RAW_SCAN_${System.currentTimeMillis()}_", ".jpg", context.cacheDir)
    } catch (e: Exception) {
        onError(e)
        return
    }

    val outputOptions = ImageCapture.OutputFileOptions.Builder(tempFile).build()

    imageCapture.takePicture(
        outputOptions,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                // Background coroutine on Dispatchers.Default for rotation, downscaling, and memory-safe cropping
                CoroutineScope(Dispatchers.Default).launch {
                    try {
                        val croppedUri = cropAndSaveImage(
                            context = context,
                            photoFile = tempFile,
                            cropRect = cropRect,
                            previewWidth = previewWidth,
                            previewHeight = previewHeight
                        )
                        // Delete full-size uncropped temporary file
                        try { tempFile.delete() } catch (_: Exception) {}

                        withContext(Dispatchers.Main) {
                            if (croppedUri != null) {
                                onSuccess(croppedUri)
                            } else {
                                onError(Exception("Failed to crop captured bitmap"))
                            }
                        }
                    } catch (e: Exception) {
                        try { tempFile.delete() } catch (_: Exception) {}
                        withContext(Dispatchers.Main) {
                            onError(e)
                        }
                    }
                }
            }

            override fun onError(exception: ImageCaptureException) {
                try { tempFile.delete() } catch (_: Exception) {}
                onError(exception)
            }
        }
    )
}

/**
 * Polite rationale UI displayed when camera permission is not yet granted.
 */
@Composable
fun CameraPermissionRationale(
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF1E1E1E))
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = PrimaryBlue.copy(alpha = 0.15f),
            modifier = Modifier.size(76.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.PhotoCamera,
                    contentDescription = null,
                    tint = PrimaryBlue,
                    modifier = Modifier.size(38.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Camera Permission Required",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Study Mentor requires camera access to scan and guide you through study problems using Socratic mentoring. Please grant permission to continue.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.LightGray,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onRequestPermission,
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = "Grant Permission",
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        TextButton(
            onClick = {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                }
                context.startActivity(intent)
            }
        ) {
            Text(
                text = "Open App Settings",
                color = Color.LightGray,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

// ─── Main Screen Composable ─────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScannerScreen(
    onBackClick: () -> Unit,
    chatViewModel: ChatViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by chatViewModel.uiState.collectAsState()
    val listState = rememberLazyListState()

    // Permission state & launcher
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // CameraX use case & controls
    val imageCapture = remember {
        ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
    }
    var cameraControl by remember { mutableStateOf<CameraControl?>(null) }
    var isTorchOn by remember { mutableStateOf(false) }
    var isCapturing by remember { mutableStateOf(false) }

    // Requirement 2: Reset context chip selections on exit (screen unmounted or ON_STOP)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                chatViewModel.resetContextFilters()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            chatViewModel.resetContextFilters()
        }
    }

    // Clear active chat session & context filters when pressing the system back button
    BackHandler {
        chatViewModel.resetContextFilters()
        chatViewModel.startNewChat()
        onBackClick()
    }

    // Photo picker launcher for selecting images from gallery
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        chatViewModel.onImageSelected(uri, context)
    }

    // Auto-scroll to the bottom whenever a new message arrives
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    // Viewport dimensions for accurate FILL_CENTER image cropping
    var cameraViewportWidth by remember { mutableStateOf(0) }
    var cameraViewportHeight by remember { mutableStateOf(0) }

    // Dynamic, resizable crop frame state (read in Draw phase and capture callback only)
    val cropRectState = remember { mutableStateOf(Rect.Zero) }

    // Shared function: captures photo, crops it to the viewfinder frame, evaluates prompt, and dispatches to AI
    fun performCaptureAndSend() {
        if (isCapturing || uiState.isLoading) return
        isCapturing = true

        val currentCropRect = cropRectState.value
        val vWidth = if (cameraViewportWidth > 0) cameraViewportWidth else 1080
        val vHeight = if (cameraViewportHeight > 0) cameraViewportHeight else 1920

        val effectiveCropRect = if (currentCropRect != Rect.Zero && currentCropRect.width > 0f && currentCropRect.height > 0f) {
            currentCropRect
        } else {
            val initW = vWidth * 0.82f
            val initH = 200f * context.resources.displayMetrics.density
            val initL = (vWidth - initW) / 2f
            val initT = (vHeight - initH) / 2f
            Rect(initL, initT, initL + initW, initT + initH)
        }

        takePhotoAndCrop(
            context = context,
            imageCapture = imageCapture,
            cropRect = effectiveCropRect,
            previewWidth = vWidth,
            previewHeight = vHeight,
            onSuccess = { croppedUri ->
                isCapturing = false
                // Dynamic prompt building in ViewModel using selectedSubject and selectedFormat
                chatViewModel.sendImagePrompt(context, croppedUri)
            },
            onError = { exc ->
                isCapturing = false
                Log.e("CameraScannerScreen", "Capture or crop error", exc)
            }
        )
    }

    // Listen for capture requests from external triggers (e.g. MainScreen center Send FAB)
    LaunchedEffect(Unit) {
        chatViewModel.captureTrigger.collect {
            if (hasCameraPermission && !isCapturing && !uiState.isLoading) {
                performCaptureAndSend()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1E1E1E))
            .navigationBarsPadding()
            .imePadding()
    ) {
        // ── 1. Live Camera Preview + Crop Overlay Viewport (Occupies all space strictly above bottom panel) ──
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .onSizeChanged { size ->
                    cameraViewportWidth = size.width
                    cameraViewportHeight = size.height
                }
        ) {
            if (hasCameraPermission) {
                CameraPreview(
                    imageCapture = imageCapture,
                    modifier = Modifier.fillMaxSize(),
                    onCameraBound = { camera -> cameraControl = camera.cameraControl }
                )

                // Interactive Crop Overlay sits strictly above the bottom panel
                if (uiState.messages.isEmpty()) {
                    InteractiveCropOverlay(
                        cropRectState = cropRectState,
                        modifier = Modifier.fillMaxSize(),
                        minWidth = 80.dp,
                        minHeight = 80.dp
                    )
                }
            } else {
                CameraPermissionRationale(
                    onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Top Bar: Floating above the camera viewport
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, start = 8.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        chatViewModel.resetContextFilters()
                        chatViewModel.startNewChat()
                        onBackClick()
                    }
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Ask AI", style = MaterialTheme.typography.titleLarge, color = Color.White)
                Spacer(modifier = Modifier.weight(1f))

                if (hasCameraPermission && cameraControl != null) {
                    IconButton(
                        onClick = {
                            val nextTorchState = !isTorchOn
                            cameraControl?.enableTorch(nextTorchState)
                            isTorchOn = nextTorchState
                        }
                    ) {
                        Icon(
                            imageVector = if (isTorchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                            contentDescription = "Toggle Torch",
                            tint = if (isTorchOn) Color(0xFFFFD700) else Color.White
                        )
                    }
                }

                IconButton(onClick = { chatViewModel.startNewChat() }) {
                    Icon(imageVector = Icons.Default.Edit, contentDescription = "New Chat", tint = Color.White)
                }
            }
        }

        // ── 2. Persistent Bottom Panel (Sits strictly below the Camera Viewport) ──
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            color = Color(0xFF1E1E22),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ── Chat Message List ──────────────────────────────────────
                if (uiState.messages.isNotEmpty() || uiState.isLoading) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 0.dp, max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(uiState.messages) { message ->
                            ChatBubble(message = message)
                        }

                        if (uiState.isLoading) {
                            item { AiThinkingBubble() }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                }

                // ── Error Banner ───────────────────────────────────────────
                if (uiState.errorMessage != null) {
                    Text(
                        text = uiState.errorMessage!!,
                        color = Color(0xFFFFB4AB),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }

                // ── Section Label (shown only before first message) ────────
                if (uiState.messages.isEmpty()) {
                    Text(
                        text = "OR TYPE YOUR QUESTION",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFA1A1AA)
                    )
                }

                // ── Selected Image Thumbnail Preview ──────────────────────
                if (uiState.selectedImageUri != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(70.dp)) {
                            AsyncImage(
                                model = uiState.selectedImageUri,
                                contentDescription = "Selected image preview",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(70.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                            )

                            IconButton(
                                onClick = { chatViewModel.clearSelectedImage() },
                                modifier = Modifier
                                    .size(22.dp)
                                    .align(Alignment.TopEnd)
                                    .padding(2.dp)
                                    .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear selected image",
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                }

                val canSend = (uiState.inputText.isNotBlank() || uiState.selectedImageUri != null) && !uiState.isLoading

                OutlinedTextField(
                    value = uiState.inputText,
                    onValueChange = { chatViewModel.onInputTextChanged(it) },
                    placeholder = { Text("Ask for a hint or step-by-step help...", color = Color(0xFFA1A1AA)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (canSend) {
                                chatViewModel.sendMessage()
                            } else if (hasCameraPermission && uiState.messages.isEmpty()) {
                                performCaptureAndSend()
                            }
                        }
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = SoftOrangePrimaryLight,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                        focusedContainerColor = Color(0xFF28282D),
                        unfocusedContainerColor = Color(0xFF28282D),
                        cursorColor = SoftOrangePrimaryLight
                    ),
                    leadingIcon = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 4.dp)
                        ) {
                            // Gallery picker
                            IconButton(
                                onClick = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Attach image from gallery",
                                    tint = SoftOrangePrimaryLight,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // Camera capture button: triggers one-tap capture & solve
                            IconButton(
                                onClick = {
                                    if (!hasCameraPermission) {
                                        permissionLauncher.launch(Manifest.permission.CAMERA)
                                    } else {
                                        performCaptureAndSend()
                                    }
                                },
                                enabled = !isCapturing && !uiState.isLoading,
                                modifier = Modifier.size(36.dp)
                            ) {
                                if (isCapturing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        color = SoftOrangePrimaryLight,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.PhotoCamera,
                                        contentDescription = "Capture photo and solve",
                                        tint = SoftOrangePrimaryLight,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    },
                    trailingIcon = {
                        val isSendActive = canSend || (hasCameraPermission && uiState.messages.isEmpty())
                        IconButton(
                            onClick = {
                                if (canSend) {
                                    chatViewModel.sendMessage()
                                } else if (hasCameraPermission && uiState.messages.isEmpty()) {
                                    performCaptureAndSend()
                                }
                            },
                            enabled = !uiState.isLoading && !isCapturing,
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .size(36.dp)
                                .background(
                                    if (isSendActive) SoftOrangePrimaryLight else Color(0xFF3F3F46),
                                    CircleShape
                                )
                        ) {
                            if (isCapturing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Send",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                )

                // ── Interactive Context Panel (hidden when a conversation has started) ──
                if (uiState.messages.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Row 1: Subject Selection
                        Text(
                            text = "SUBJECT",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFA1A1AA)
                        )
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(ChatViewModel.SUBJECT_OPTIONS) { subject ->
                                val isSelected = uiState.selectedSubject == subject
                                ContextFilterChip(
                                    text = subject,
                                    selected = isSelected,
                                    onClick = {
                                        chatViewModel.onSubjectSelected(if (isSelected) null else subject)
                                    }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        // Row 2: Format Selection
                        Text(
                            text = "GUIDANCE MODE (SOCRATIC)",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFA1A1AA)
                        )
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(ChatViewModel.FORMAT_OPTIONS) { format ->
                                val isSelected = uiState.selectedFormat == format
                                ContextFilterChip(
                                    text = format,
                                    selected = isSelected,
                                    onClick = {
                                        chatViewModel.onFormatSelected(if (isSelected) null else format)
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

// ─── Chat Bubble Composable ─────────────────────────────────────────────────

@Composable
fun ChatBubble(message: ChatMessage) {
    val isUser = message.role == "user"
    val displayText = remember(message.text) { cleanAiFormatting(message.text) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            color = if (isUser) SoftOrangePrimaryLight else Color(0xFF2C2C32),
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Text(
                text = displayText,
                color = if (isUser) Color.White else Color(0xFFEDE0DB),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            )
        }
    }
}

// ─── AI Thinking Indicator ──────────────────────────────────────────────────

@Composable
fun AiThinkingBubble() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 4.dp),
            color = Color(0xFF2C2C32)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    color = SoftOrangePrimaryLight,
                    strokeWidth = 2.dp
                )
                Text(
                    text = "AI is thinking...",
                    color = Color(0xFFA1A1AA),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

// ─── Interactive FilterChip Composable ──────────────────────────────────────

/**
 * Modern interactive FilterChip with smooth color transitions, animated checkmark,
 * and high legibility against the camera scanner screen's dark theme.
 */
@Composable
fun ContextFilterChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (selected) SoftOrangePrimaryLight else Color.White.copy(alpha = 0.08f),
        animationSpec = tween(durationMillis = 200),
        label = "chipBgColor"
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) Color.White else Color(0xFFE2E8F0),
        animationSpec = tween(durationMillis = 200),
        label = "chipContentColor"
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) SoftOrangePrimaryLight else Color.White.copy(alpha = 0.18f),
        animationSpec = tween(durationMillis = 200),
        label = "chipBorderColor"
    )

    Surface(
        selected = selected,
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = backgroundColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            AnimatedVisibility(
                visible = selected,
                enter = fadeIn() + expandHorizontally(),
                exit = fadeOut() + shrinkHorizontally()
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = contentColor,
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                text = text,
                color = contentColor,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
            )
        }
    }
}
