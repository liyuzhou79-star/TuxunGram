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
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import android.view.TextureView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.tuxungram.android.td.TdClient
import com.tuxungram.android.td.TdRepository
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Scan-to-confirm login (v0.9.30, W1): the reverse of the
 * v0.9.24 QR login — THIS device is the already-logged-in
 * one, and the camera reads the tg://login?token=… code a
 * NEW device is showing, then confirmQrCodeAuthentication
 * (verified in td_api.tl) authorizes it.
 *
 * Route choice, recorded per the brief: Camera2 + ZXing
 * core, no CameraX. CameraX would pull a whole camera
 * stack (camera-core/camera2/lifecycle) for one screen;
 * Camera2 is framework API since long before minSdk 29,
 * and the only added dependency is zxing core (~small,
 * decoder only). Frames come from an ImageReader in
 * YUV_420_888; the Y plane feeds PlanarYUVLuminanceSource.
 *
 * Safety shape: nothing is confirmed on scan alone. A
 * valid login code raises an explicit confirmation dialog
 * that says a new device will be authorized; anything else
 * the camera reads gets a plain "not a login code" note.
 */
@Composable
fun QrScanScreen(onBack: () -> Unit) {
    BackHandler { onBack() }
    val context = LocalContext.current
    val palette = OfficialTheme.current()
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
    // The scanned login link awaiting the user's explicit
    // confirmation; scanning pauses while it is set.
    var pendingLink by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.windowBg)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(28.dp))
        Text(
            "扫描二维码登录其他设备",
            fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
            color = palette.textPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "在新设备上打开 TuxunGram 的「二维码登录」，把它的二维码对准下面的框。",
            color = palette.textSecondary, fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(24.dp))
        when {
            done -> {
                Text(
                    "✓",
                    fontSize = 44.sp, color = palette.accentText
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "已授权一台新设备登录。它会出现在「已登录设备」列表里，不认识就到那里把它结束掉。",
                    color = palette.textPrimary, fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            !hasPermission -> {
                Box(
                    modifier = Modifier
                        .size(260.dp)
                        .background(
                            palette.graySection,
                            RoundedCornerShape(16.dp)
                        )
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (deniedOnce) {
                            "相机权限被拒绝了，扫码没法用。可以在系统设置里给 TuxunGram 相机权限后再回来。"
                        } else {
                            "扫码需要用一下相机，只用来识别登录二维码，不拍照、不保存。"
                        },
                        color = palette.textSecondary, fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                }
                Spacer(Modifier.height(16.dp))
                if (!deniedOnce) {
                    Button(onClick = {
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    }) { Text("允许使用相机") }
                }
            }
            else -> {
                Box(
                    modifier = Modifier
                        .size(280.dp)
                        .border(
                            2.dp, palette.accent,
                            RoundedCornerShape(16.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    QrCameraPreview(
                        active = pendingLink == null && !busy && !done,
                        onCode = { text ->
                            if (isTelegramLoginLink(text)) {
                                pendingLink = text
                            } else {
                                notice = "这不是 Telegram 登录二维码，没有做任何授权。登录码是新设备在「二维码登录」页上显示的那张。"
                            }
                        }
                    )
                }
                Spacer(Modifier.height(14.dp))
                if (busy) {
                    CircularProgressIndicator(color = palette.accent)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "正在向服务器确认…",
                        color = palette.textSecondary, fontSize = 13.sp
                    )
                } else {
                    Text(
                        "对准后会自动识别，授权前会再让你确认一次。",
                        color = palette.textSecondary, fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onBack) {
            Text("返回", color = palette.accentText, fontSize = 15.sp)
        }
    }

    val link = pendingLink
    if (link != null) {
        AlertDialog(
            onDismissRequest = { pendingLink = null },
            title = { Text("授权这台新设备登录？") },
            text = {
                Text(
                    "确认后，这台新设备会直接登上你的 Telegram 账号，不再需要验证码。" +
                        "只在你本人正在给自己的新设备登录时才确认；别人发来的登录码不要扫、更不要确认。"
                )
            },
            confirmButton = {
                Button(onClick = {
                    pendingLink = null
                    busy = true
                    TdRepository.confirmQrCodeLogin(link) { response ->
                        busy = false
                        if (TdClient.isError(response)) {
                            notice = confirmErrorText(response)
                        } else {
                            done = true
                        }
                    }
                }) { Text("确认授权") }
            },
            dismissButton = {
                TextButton(onClick = { pendingLink = null }) {
                    Text("取消")
                }
            }
        )
    }
    val note = notice
    if (note != null) {
        AlertDialog(
            onDismissRequest = { notice = null },
            title = { Text("提示") },
            text = { Text(note) },
            confirmButton = {
                TextButton(onClick = { notice = null }) {
                    Text("知道了")
                }
            }
        )
    }
}

/** The login link shape: the authorizationStateWaitOther
 *  DeviceConfirmation link is tg://login?token=… — anything
 *  else (a t.me profile link, a URL, plain text) is not a
 *  login code and must never reach confirmQrCodeAuthentication. */
private fun isTelegramLoginLink(text: String): Boolean =
    text.startsWith("tg://login?token=") ||
        text.startsWith("tg:login?token=")

private fun confirmErrorText(response: org.json.JSONObject): String {
    val message = response.optString("message")
    return when {
        message.contains("TOKEN_INVALID") ||
            message.contains("TOKEN_EXPIRED") ||
            message.contains("AUTH_TOKEN") ->
            "这个登录码已经失效或被用过了。让新设备刷新出一个新码，再扫一次。"
        message.contains("FLOOD_WAIT") ->
            "操作太频繁，服务器让等一会儿。过几分钟再试。"
        else ->
            "确认没有成功，服务器拒绝了这次授权。让新设备刷新登录码后再扫一次。"
    }
}

/**
 * Camera2 preview + frame tap. Owns its camera thread and
 * closes everything on dispose. Decoding runs on a single
 * background executor, throttled, and only while [active];
 * a successful read is delivered once per distinct code.
 */
@Composable
private fun QrCameraPreview(
    active: Boolean,
    onCode: (String) -> Unit
) {
    val context = LocalContext.current
    val activeRef = remember { AtomicBoolean(true) }
    activeRef.set(active)
    val deliveredRef = remember { mutableStateOf<String?>(null) }
    val closerRef = remember { mutableStateOf<(() -> Unit)?>(null) }
    DisposableEffect(Unit) {
        onDispose {
            activeRef.set(false)
            closerRef.value?.invoke()
        }
    }
    AndroidView(
        factory = { ctx ->
            val textureView = TextureView(ctx)
            textureView.surfaceTextureListener =
                object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(
                        surface: SurfaceTexture, width: Int, height: Int
                    ) {
                        closerRef.value = startQrCamera(
                            ctx, surface, activeRef,
                            deliveredRef, onCode
                        )
                    }

                    override fun onSurfaceTextureSizeChanged(
                        surface: SurfaceTexture, width: Int, height: Int
                    ) = Unit

                    override fun onSurfaceTextureDestroyed(
                        surface: SurfaceTexture
                    ): Boolean = true

                    override fun onSurfaceTextureUpdated(
                        surface: SurfaceTexture
                    ) = Unit
                }
            textureView
        },
        modifier = Modifier.fillMaxSize()
    )
}

private fun startQrCamera(
    context: Context,
    surfaceTexture: SurfaceTexture,
    activeRef: AtomicBoolean,
    deliveredRef: androidx.compose.runtime.MutableState<String?>,
    onCode: (String) -> Unit
): () -> Unit {
    val thread = HandlerThread("qr-camera").apply { start() }
    val handler = Handler(thread.looper)
    val decodeExecutor = Executors.newSingleThreadExecutor()
    val decoding = AtomicBoolean(false)
    var cameraDevice: CameraDevice? = null
    var session: CameraCaptureSession? = null
    var reader: ImageReader? = null
    val closer: () -> Unit = {
        try { session?.close() } catch (t: Throwable) { }
        try { cameraDevice?.close() } catch (t: Throwable) { }
        try { reader?.close() } catch (t: Throwable) { }
        decodeExecutor.shutdownNow()
        thread.quitSafely()
    }
    try {
        val manager = context.getSystemService(
            Context.CAMERA_SERVICE
        ) as CameraManager
        val cameraId = manager.cameraIdList.firstOrNull { id ->
            manager.getCameraCharacteristics(id)
                .get(CameraCharacteristics.LENS_FACING) ==
                CameraCharacteristics.LENS_FACING_BACK
        } ?: manager.cameraIdList.firstOrNull() ?: return closer
        val imageReader = ImageReader.newInstance(
            1280, 960, ImageFormat.YUV_420_888, 2
        )
        reader = imageReader
        imageReader.setOnImageAvailableListener({ r ->
            val image = r.acquireLatestImage() ?: return@setOnImageAvailableListener
            try {
                if (!activeRef.get() || !decoding.compareAndSet(false, true)) {
                    return@setOnImageAvailableListener
                }
                val plane = image.planes[0]
                val buffer = plane.buffer
                val rowStride = plane.rowStride
                val pixelStride = plane.pixelStride
                val width = image.width
                val height = image.height
                val data = ByteArray(width * height)
                val row = ByteArray(buffer.remaining())
                buffer.get(row)
                if (pixelStride == 1) {
                    for (y in 0 until height) {
                        System.arraycopy(
                            row, y * rowStride, data, y * width, width
                        )
                    }
                } else {
                    for (y in 0 until height) {
                        for (x in 0 until width) {
                            data[y * width + x] =
                                row[y * rowStride + x * pixelStride]
                        }
                    }
                }
                decodeExecutor.execute {
                    try {
                        val text = decodeQr(data, width, height)
                        if (text != null &&
                            deliveredRef.value != text
                        ) {
                            deliveredRef.value = text
                            android.os.Handler(
                                android.os.Looper.getMainLooper()
                            ).post { onCode(text) }
                        }
                    } finally {
                        decoding.set(false)
                    }
                }
            } catch (t: Throwable) {
                decoding.set(false)
            } finally {
                image.close()
            }
        }, handler)
        surfaceTexture.setDefaultBufferSize(1280, 960)
        val previewSurface = Surface(surfaceTexture)
        if (ContextCompat.checkSelfPermission(
                context, Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return closer
        }
        manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(device: CameraDevice) {
                cameraDevice = device
                try {
                    device.createCaptureSession(
                        listOf(previewSurface, imageReader.surface),
                        object : CameraCaptureSession.StateCallback() {
                            override fun onConfigured(
                                s: CameraCaptureSession
                            ) {
                                session = s
                                try {
                                    val request = device.createCaptureRequest(
                                        CameraDevice.TEMPLATE_PREVIEW
                                    )
                                    request.addTarget(previewSurface)
                                    request.addTarget(imageReader.surface)
                                    s.setRepeatingRequest(
                                        request.build(), null, handler
                                    )
                                } catch (t: Throwable) {
                                    // Preview start failed; the
                                    // screen stays, back works.
                                }
                            }

                            override fun onConfigureFailed(
                                s: CameraCaptureSession
                            ) = Unit
                        },
                        handler
                    )
                } catch (t: Throwable) {
                    // Camera busy or gone: leave the frame empty.
                }
            }

            override fun onDisconnected(device: CameraDevice) {
                device.close()
            }

            override fun onError(device: CameraDevice, error: Int) {
                device.close()
            }
        }, handler)
    } catch (t: Throwable) {
        // Any camera failure leaves a static frame; the user
        // can still go back — never crash over a scanner.
        try {
            session?.close()
            cameraDevice?.close()
            reader?.close()
        } catch (t: Throwable) {
        }
    }
    return closer
}

private fun decodeQr(data: ByteArray, width: Int, height: Int): String? {
    val hints = mapOf(
        DecodeHintType.POSSIBLE_FORMATS to
            listOf(com.google.zxing.BarcodeFormat.QR_CODE),
        DecodeHintType.TRY_HARDER to true
    )
    val reader = QRCodeReader()
    val source = PlanarYUVLuminanceSource(
        data, width, height, 0, 0, width, height, false
    )
    try {
        return reader.decode(
            BinaryBitmap(HybridBinarizer(source)), hints
        ).text
    } catch (t: Throwable) {
        // Fall through to the inverted pass (light code on a
        // dark background, as some screens render it).
    }
    return try {
        reader.decode(
            BinaryBitmap(HybridBinarizer(source.invert())), hints
        ).text
    } catch (t: Throwable) {
        null
    }
}
