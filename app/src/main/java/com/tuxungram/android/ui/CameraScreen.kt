package com.tuxungram.android.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Size
import android.view.Surface
import android.view.TextureView
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * In-app camera (v0.9.55, Z2): Camera2 still capture, no
 * CameraX and no new dependency — the same framework-API
 * route the QR scanner took in v0.9.30, with the teardown
 * discipline its v0.9.34 audit produced: close the previous
 * pipeline before a surface is recreated or the facing
 * flips, close everything on surface destroy AND on
 * dispose, an idempotent closer, and callbacks guarded
 * against teardown races.
 *
 * Photos only, no video recording. The captured JPEG
 * lands in the cache dir and the caller routes it into
 * the existing send-photo / story chains; this screen
 * never sends anything itself.
 */
@Composable
fun CameraScreen(
    onTaken: (String) -> Unit,
    onCancel: () -> Unit
) {
    BackHandler { onCancel() }
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    var deniedOnce by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (!granted) deniedOnce = true
    }
    val facings = remember { availableFacings(context) }
    var facing by remember {
        mutableIntStateOf(
            if (CameraCharacteristics.LENS_FACING_BACK in facings) {
                CameraCharacteristics.LENS_FACING_BACK
            } else {
                facings.firstOrNull()
                    ?: CameraCharacteristics.LENS_FACING_BACK
            }
        )
    }
    var capturedPath by remember { mutableStateOf<String?>(null) }
    var capturing by remember { mutableStateOf(false) }
    var cameraFailed by remember { mutableStateOf(false) }
    val controllerRef = remember { mutableStateOf<StillCamera?>(null) }
    var textureRef by remember { mutableStateOf<TextureView?>(null) }
    DisposableEffect(Unit) {
        onDispose {
            controllerRef.value?.close()
            controllerRef.value = null
        }
    }
    fun startPipeline(surface: SurfaceTexture, useFacing: Int) {
        controllerRef.value?.close()
        val camera = StillCamera(
            context, useFacing,
            onPhoto = { path ->
                capturing = false
                capturedPath = path
            },
            onFailed = {
                capturing = false
                cameraFailed = true
            },
            onCaptureFailed = { capturing = false }
        )
        controllerRef.value = camera
        camera.start(surface)
    }
    // Facing flip: the TextureView survives, so restart the
    // pipeline against its (still available) surface.
    LaunchedEffect(facing) {
        val tv = textureRef ?: return@LaunchedEffect
        if (!hasPermission || capturedPath != null) {
            return@LaunchedEffect
        }
        val surface = tv.surfaceTexture ?: return@LaunchedEffect
        startPipeline(surface, facing)
        tv.scaleX = if (facing ==
            CameraCharacteristics.LENS_FACING_FRONT
        ) {
            -1f
        } else {
            1f
        }
    }
    // Retake needs no pipeline work: the preview session
    // stays open under the captured-photo preview, so
    // clearing the path just returns to the live view.
    // This effect only re-arms the shutter state.
    LaunchedEffect(capturedPath) {
        if (capturedPath == null) capturing = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val shot = capturedPath
        when {
            shot != null -> {
                val bitmap by produceState<android.graphics.Bitmap?>(
                    initialValue = null, shot
                ) {
                    value = withContext(Dispatchers.IO) {
                        decodeCached(shot, 2048)
                    }
                }
                val bmp = bitmap
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { capturedPath = null }) {
                        Text(
                            "重拍",
                            color = Color.White, fontSize = 16.sp
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Button(onClick = { onTaken(shot) }) {
                        Text("使用照片")
                    }
                }
            }
            !hasPermission -> {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        if (deniedOnce) {
                            "相机权限被拒绝了，拍照用不了。" +
                                "可以在系统设置里给 TuxunGram " +
                                "相机权限后再回来。"
                        } else {
                            "要用应用内相机拍照，需要相机权限。" +
                                "照片只存在这台手机上，你确认发送后才会发出去。"
                        },
                        color = Color.White, fontSize = 15.sp,
                        textAlign = TextAlign.Center
                    )
                    if (!deniedOnce) {
                        Spacer(Modifier.height(18.dp))
                        Button(onClick = {
                            permissionLauncher.launch(
                                Manifest.permission.CAMERA
                            )
                        }) { Text("允许使用相机") }
                    }
                }
            }
            cameraFailed -> {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "相机打不开，可能正被其他应用占用。" +
                            "关掉占用相机的应用再试，或者先用系统相机" +
                            "拍好再从相册发。",
                        color = Color.White, fontSize = 15.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
            else -> {
                AndroidView(
                    factory = { ctx ->
                        val textureView = TextureView(ctx)
                        textureRef = textureView
                        textureView.surfaceTextureListener =
                            object :
                                TextureView.SurfaceTextureListener {
                                override fun onSurfaceTextureAvailable(
                                    surface: SurfaceTexture,
                                    width: Int, height: Int
                                ) {
                                    // A re-created surface:
                                    // startPipeline closes the
                                    // previous pipeline first
                                    // (the v0.9.34 leak lesson).
                                    startPipeline(surface, facing)
                                    textureView.scaleX = if (facing ==
                                        CameraCharacteristics
                                            .LENS_FACING_FRONT
                                    ) {
                                        -1f
                                    } else {
                                        1f
                                    }
                                }

                                override fun onSurfaceTextureSizeChanged(
                                    surface: SurfaceTexture,
                                    width: Int, height: Int
                                ) = Unit

                                override fun onSurfaceTextureDestroyed(
                                    surface: SurfaceTexture
                                ): Boolean {
                                    controllerRef.value?.close()
                                    controllerRef.value = null
                                    return true
                                }

                                override fun onSurfaceTextureUpdated(
                                    surface: SurfaceTexture
                                ) = Unit
                            }
                        textureView
                    },
                    modifier = Modifier.fillMaxSize()
                )
                // Bottom control bar: flip (when the device
                // has both facings) and the shutter.
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 32.dp, vertical = 22.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.width(48.dp)) {
                        if (facings.size > 1) {
                            IconButton(onClick = {
                                facing = if (facing ==
                                    CameraCharacteristics
                                        .LENS_FACING_BACK
                                ) {
                                    CameraCharacteristics
                                        .LENS_FACING_FRONT
                                } else {
                                    CameraCharacteristics
                                        .LENS_FACING_BACK
                                }
                            }) {
                                Icon(
                                    Icons.Filled.Refresh,
                                    contentDescription = "切换前后摄像头",
                                    tint = Color.White,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .border(3.dp, Color.White, CircleShape)
                            .padding(5.dp)
                            .background(
                                if (capturing) {
                                    Color.White.copy(alpha = 0.45f)
                                } else {
                                    Color.White
                                },
                                CircleShape
                            )
                            .clickable(enabled = !capturing) {
                                val controller = controllerRef.value
                                capturing = controller != null &&
                                    controller.capture()
                            }
                    )
                    Spacer(Modifier.weight(1f))
                    Box(modifier = Modifier.width(48.dp))
                }
            }
        }
        // Close button rides over every state except the
        // captured preview (which has its own actions).
        if (capturedPath == null) {
            IconButton(
                onClick = onCancel,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(8.dp)
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "关闭相机",
                    tint = Color.White
                )
            }
        }
    }
}

private fun availableFacings(context: Context): List<Int> {
    return try {
        val manager = context.getSystemService(
            Context.CAMERA_SERVICE
        ) as CameraManager
        manager.cameraIdList.mapNotNull { id ->
            manager.getCameraCharacteristics(id)
                .get(CameraCharacteristics.LENS_FACING)
        }.distinct()
    } catch (t: Throwable) {
        emptyList()
    }
}

/**
 * One still-camera pipeline on its own background thread:
 * preview session into the given surface, JPEG ImageReader
 * for the shot. Created synchronously; [start] kicks off
 * the async open. [close] is idempotent and safe to call
 * from any thread; [capture] returns false when the
 * pipeline is not ready yet instead of failing the screen.
 */
private class StillCamera(
    private val context: Context,
    private val facing: Int,
    private val onPhoto: (String) -> Unit,
    private val onFailed: () -> Unit,
    private val onCaptureFailed: () -> Unit
) {
    private val thread = HandlerThread("camera-photo").apply { start() }
    private val handler = Handler(thread.looper)
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var closed = false
    private var device: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var reader: ImageReader? = null
    private var sensorOrientation = 0
    private var actualFacing = facing

    fun start(surfaceTexture: SurfaceTexture) {
        try {
            val manager = context.getSystemService(
                Context.CAMERA_SERVICE
            ) as CameraManager
            val cameraId = manager.cameraIdList.firstOrNull { id ->
                manager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.LENS_FACING) == facing
            } ?: manager.cameraIdList.firstOrNull()
            if (cameraId == null) {
                fail()
                return
            }
            val chars = manager.getCameraCharacteristics(cameraId)
            actualFacing = chars.get(
                CameraCharacteristics.LENS_FACING
            ) ?: facing
            sensorOrientation = chars.get(
                CameraCharacteristics.SENSOR_ORIENTATION
            ) ?: 0
            val map = chars.get(
                CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP
            )
            val jpegSize = map?.getOutputSizes(ImageFormat.JPEG)
                ?.filter {
                    it.width.toLong() * it.height <= 13_000_000L
                }
                ?.maxByOrNull { it.width.toLong() * it.height }
                ?: Size(1920, 1440)
            val jpegAspect = jpegSize.width.toFloat() / jpegSize.height
            val previewSize = map
                ?.getOutputSizes(SurfaceTexture::class.java)
                ?.filter { it.width <= 1920 }
                ?.minByOrNull {
                    kotlin.math.abs(
                        it.width.toFloat() / it.height - jpegAspect
                    )
                } ?: Size(1280, 720)
            val imageReader = ImageReader.newInstance(
                jpegSize.width, jpegSize.height, ImageFormat.JPEG, 2
            )
            reader = imageReader
            imageReader.setOnImageAvailableListener({ r ->
                // Acquiring can throw when the reader was
                // closed by a teardown racing this callback
                // (v0.9.34, A4) — that ends the callback,
                // not the process.
                val image = try {
                    r.acquireLatestImage()
                } catch (t: Throwable) {
                    null
                } ?: return@setOnImageAvailableListener
                try {
                    val buffer = image.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining())
                    buffer.get(bytes)
                    val file = File(
                        context.cacheDir,
                        "camera_${System.currentTimeMillis()}.jpg"
                    )
                    FileOutputStream(file).use { it.write(bytes) }
                    mainHandler.post { onPhoto(file.absolutePath) }
                } catch (t: Throwable) {
                    mainHandler.post { onCaptureFailed() }
                } finally {
                    try { image.close() } catch (t: Throwable) { }
                }
            }, handler)
            surfaceTexture.setDefaultBufferSize(
                previewSize.width, previewSize.height
            )
            val previewSurface = Surface(surfaceTexture)
            if (ContextCompat.checkSelfPermission(
                    context, Manifest.permission.CAMERA
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                close()
                return
            }
            manager.openCamera(
                cameraId,
                object : CameraDevice.StateCallback() {
                    override fun onOpened(d: CameraDevice) {
                        if (closed) {
                            d.close()
                            return
                        }
                        device = d
                        try {
                            d.createCaptureSession(
                                listOf(
                                    previewSurface,
                                    imageReader.surface
                                ),
                                object :
                                    CameraCaptureSession.StateCallback() {
                                    override fun onConfigured(
                                        s: CameraCaptureSession
                                    ) {
                                        if (closed) {
                                            s.close()
                                            return
                                        }
                                        session = s
                                        try {
                                            val request = d
                                                .createCaptureRequest(
                                                    CameraDevice
                                                        .TEMPLATE_PREVIEW
                                                )
                                            request.addTarget(
                                                previewSurface
                                            )
                                            request.set(
                                                CaptureRequest
                                                    .CONTROL_AF_MODE,
                                                CaptureRequest
                                                    .CONTROL_AF_MODE_CONTINUOUS_PICTURE
                                            )
                                            s.setRepeatingRequest(
                                                request.build(),
                                                null, handler
                                            )
                                        } catch (t: Throwable) {
                                            fail()
                                        }
                                    }

                                    override fun onConfigureFailed(
                                        s: CameraCaptureSession
                                    ) {
                                        fail()
                                    }
                                },
                                handler
                            )
                        } catch (t: Throwable) {
                            fail()
                        }
                    }

                    override fun onDisconnected(d: CameraDevice) {
                        d.close()
                        if (device === d) device = null
                    }

                    override fun onError(d: CameraDevice, error: Int) {
                        d.close()
                        if (device === d) device = null
                        fail()
                    }
                },
                handler
            )
        } catch (t: Throwable) {
            fail()
        }
    }

    /** Issue one still capture; false = pipeline not ready
     *  (or already closed), the caller just re-arms. */
    fun capture(): Boolean {
        val s = session
        val d = device
        val r = reader
        if (closed || s == null || d == null || r == null) {
            return false
        }
        return try {
            val request = d.createCaptureRequest(
                CameraDevice.TEMPLATE_STILL_CAPTURE
            )
            request.addTarget(r.surface)
            request.set(
                CaptureRequest.CONTROL_AF_MODE,
                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
            )
            request.set(CaptureRequest.JPEG_ORIENTATION, jpegOrientation())
            s.capture(request.build(), null, handler)
            true
        } catch (t: Throwable) {
            false
        }
    }

    private fun jpegOrientation(): Int {
        val rotation = try {
            (context.getSystemService(Context.WINDOW_SERVICE)
                as WindowManager).defaultDisplay.rotation
        } catch (t: Throwable) {
            Surface.ROTATION_0
        }
        val degrees = when (rotation) {
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
        return if (actualFacing ==
            CameraCharacteristics.LENS_FACING_FRONT
        ) {
            (sensorOrientation + degrees) % 360
        } else {
            (sensorOrientation - degrees + 360) % 360
        }
    }

    private fun fail() {
        if (!closed) mainHandler.post { onFailed() }
    }

    fun close() {
        if (closed) return
        closed = true
        try { session?.close() } catch (t: Throwable) { }
        try { device?.close() } catch (t: Throwable) { }
        try { reader?.close() } catch (t: Throwable) { }
        thread.quitSafely()
    }
}
