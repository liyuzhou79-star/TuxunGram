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
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.util.Size
import android.view.Surface
import android.view.TextureView
import android.view.WindowManager
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * In-app camera (v0.9.55, Z2; video v0.9.56, A2): Camera2
 * still capture plus MediaRecorder video, no CameraX and
 * no new dependency — the same framework-API route the QR
 * scanner took in v0.9.30, with the teardown discipline
 * its v0.9.34 audit produced: close the previous pipeline
 * before a surface is recreated or the facing flips,
 * close everything on surface destroy AND on dispose, an
 * idempotent closer, and callbacks guarded against
 * teardown races.
 *
 * The captured JPEG / MP4 lands in the cache dir and the
 * caller routes it into the existing send-photo /
 * send-video / story chains; this screen never sends
 * anything itself. A shot the user never confirmed is
 * deleted again when the screen goes away (retake,
 * cancel, dismiss) — before v0.9.56 those files stayed in
 * the cache forever.
 */
@Composable
fun CameraScreen(
    onTaken: (String) -> Unit,
    onVideoTaken: (String) -> Unit = {},
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
    // Video mode (v0.9.56, A2): a mode switch, a separate
    // pipeline, its own permission (microphone) and its
    // own preview state.
    var videoMode by remember { mutableStateOf(false) }
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    var micDeniedOnce by remember { mutableStateOf(false) }
    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPermission = granted
        if (!granted) micDeniedOnce = true
    }
    var recording by remember { mutableStateOf(false) }
    var recordSeconds by remember { mutableIntStateOf(0) }
    var recordStartMs by remember { mutableStateOf(0L) }
    var recordedPath by remember { mutableStateOf<String?>(null) }
    var recordNote by remember { mutableStateOf<String?>(null) }
    // Set the moment a shot is handed to the caller; the
    // dispose cleanup below deletes only unconfirmed files.
    var delivered by remember { mutableStateOf(false) }
    val controllerRef = remember { mutableStateOf<StillCamera?>(null) }
    val videoControllerRef = remember { mutableStateOf<VideoCamera?>(null) }
    var textureRef by remember { mutableStateOf<TextureView?>(null) }
    fun closePipelines() {
        controllerRef.value?.close()
        controllerRef.value = null
        videoControllerRef.value?.close()
        videoControllerRef.value = null
    }
    val capturedState = rememberUpdatedState(capturedPath)
    val recordedState = rememberUpdatedState(recordedPath)
    val deliveredState = rememberUpdatedState(delivered)
    DisposableEffect(Unit) {
        onDispose {
            closePipelines()
            // Unconfirmed shots are this screen's temp
            // files: reclaim them instead of leaving them
            // in the cache (v0.9.56, A3). A delivered file
            // belongs to the send chain from here on.
            if (!deliveredState.value) {
                listOfNotNull(
                    capturedState.value, recordedState.value
                ).forEach { path ->
                    try {
                        File(path).delete()
                    } catch (t: Throwable) {
                        // Already gone — nothing to reclaim.
                    }
                }
            }
        }
    }
    fun startPipeline(surface: SurfaceTexture, useFacing: Int) {
        closePipelines()
        if (videoMode) {
            val camera = VideoCamera(
                context, useFacing,
                onRecordingChange = { active ->
                    recording = active
                    if (active) {
                        recordStartMs = SystemClock.elapsedRealtime()
                        recordSeconds = 0
                        recordNote = null
                    }
                },
                onVideoReady = { path -> recordedPath = path },
                onFailed = { cameraFailed = true },
                onRecordFailed = { note ->
                    recording = false
                    recordNote = note
                }
            )
            videoControllerRef.value = camera
            camera.start(surface)
        } else {
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
    }
    // Facing flip or mode switch: the TextureView survives,
    // so restart the pipeline against its (still available)
    // surface. In video mode without the microphone the
    // gate column replaces the view instead — no pipeline
    // starts until the permission lands and the view
    // remounts (its surface callback starts the pipeline).
    LaunchedEffect(facing, videoMode) {
        val tv = textureRef ?: return@LaunchedEffect
        if (!hasPermission || capturedPath != null ||
            recordedPath != null
        ) {
            return@LaunchedEffect
        }
        if (videoMode && !hasMicPermission) {
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
    // Ask for the microphone the first time video mode is
    // entered without it; a refusal lands on the gate
    // column, which explains in plain language.
    LaunchedEffect(videoMode) {
        if (videoMode && !hasMicPermission && !micDeniedOnce) {
            micLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    // Recording clock: wall-clock based, so a janky frame
    // cannot make the timer drift.
    LaunchedEffect(recording) {
        if (recording) {
            while (true) {
                delay(500)
                recordSeconds = (
                    (SystemClock.elapsedRealtime() - recordStartMs) /
                        1000
                    ).toInt()
            }
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
                    TextButton(onClick = {
                        // Retake discards the shot: delete
                        // its file, it was never handed off.
                        try {
                            File(shot).delete()
                        } catch (t: Throwable) {
                            // Already gone.
                        }
                        capturedPath = null
                    }) {
                        Text(
                            "重拍",
                            color = Color.White, fontSize = 16.sp
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Button(onClick = {
                        delivered = true
                        onTaken(shot)
                    }) {
                        Text("使用照片")
                    }
                }
            }
            recordedPath != null -> {
                // Recorded-video preview (v0.9.56, A2): the
                // clip loops in a plain framework VideoView;
                // tapping it pauses / resumes. Retake deletes
                // the file; 使用 hands it to the caller's
                // send chain.
                val clip = recordedPath!!
                var videoViewRef by remember {
                    mutableStateOf<VideoView?>(null)
                }
                var clipPlaying by remember { mutableStateOf(true) }
                Box(modifier = Modifier.fillMaxSize()) {
                    AndroidView(
                        factory = { ctx ->
                            val view = VideoView(ctx)
                            videoViewRef = view
                            view.setVideoPath(clip)
                            view.setOnPreparedListener { player ->
                                player.isLooping = true
                                view.start()
                            }
                            view
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable {
                                val view = videoViewRef
                                    ?: return@clickable
                                if (view.isPlaying) {
                                    view.pause()
                                    clipPlaying = false
                                } else {
                                    view.start()
                                    clipPlaying = true
                                }
                            }
                            // Accessibility (v0.9.60, E2): the
                            // full-screen tap target is invisible
                            // — without a label a screen reader
                            // user lands on a nameless button.
                            .semantics {
                                contentDescription = if (clipPlaying) {
                                    "暂停播放"
                                } else {
                                    "继续播放"
                                }
                                role = Role.Button
                            }
                    )
                    if (!clipPlaying) {
                        Text(
                            "已暂停，点一下继续播放",
                            color = Color.White, fontSize = 13.sp,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .statusBarsPadding()
                                .padding(top = 16.dp)
                                .background(
                                    Color.Black.copy(alpha = 0.55f),
                                    RoundedCornerShape(12.dp)
                                )
                                .padding(
                                    horizontal = 12.dp, vertical = 5.dp
                                )
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = {
                        try {
                            File(clip).delete()
                        } catch (t: Throwable) {
                            // Already gone.
                        }
                        recordedPath = null
                    }) {
                        Text(
                            "重录",
                            color = Color.White, fontSize = 16.sp
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Button(onClick = {
                        delivered = true
                        onVideoTaken(clip)
                    }) {
                        Text("使用视频")
                    }
                }
            }
            !hasPermission -> {
                // Scrollable as insurance (v0.9.56, A5): on
                // a short screen at a large font scale the
                // explanation and its button must stay
                // reachable, never clipped.
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 32.dp),
                    verticalArrangement = Arrangement.Center,
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
            videoMode && !hasMicPermission -> {
                // Microphone gate (v0.9.56, A2): recording
                // without sound is not offered silently —
                // the choice is explained and the photo mode
                // stays one tap away.
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        if (micDeniedOnce) {
                            "麦克风权限被拒绝了，录像用不了。" +
                                "可以在系统设置里给 TuxunGram " +
                                "麦克风权限后再回来，或者先用拍照。"
                        } else {
                            "录像要同时录下声音，需要麦克风权限。" +
                                "声音只会录进你确认发送的视频里。"
                        },
                        color = Color.White, fontSize = 15.sp,
                        textAlign = TextAlign.Center
                    )
                    if (!micDeniedOnce) {
                        Spacer(Modifier.height(18.dp))
                        Button(onClick = {
                            micLauncher.launch(
                                Manifest.permission.RECORD_AUDIO
                            )
                        }) { Text("允许麦克风") }
                    }
                    Spacer(Modifier.height(10.dp))
                    TextButton(onClick = { videoMode = false }) {
                        Text("先用拍照", color = Color.White)
                    }
                }
            }
            cameraFailed -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // A take interrupted mid-recording
                    // (v0.9.57, B2) lands here too; its
                    // plain note rides along so the user
                    // hears the clip is gone, not just that
                    // the camera is unavailable.
                    val note = recordNote
                    if (note != null) {
                        Text(
                            note,
                            color = Color.White, fontSize = 15.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(10.dp))
                    }
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
                                    closePipelines()
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
                // Recording clock chip (v0.9.56, A2).
                if (recording) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .statusBarsPadding()
                            .padding(top = 14.dp)
                            .background(
                                Color.Black.copy(alpha = 0.55f),
                                RoundedCornerShape(14.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .background(Color(0xFFE53935), CircleShape)
                        )
                        Spacer(Modifier.width(7.dp))
                        Text(
                            "%d:%02d".format(
                                recordSeconds / 60, recordSeconds % 60
                            ),
                            color = Color.White, fontSize = 14.sp
                        )
                    }
                }
                // Bottom control block: an optional note line
                // (a recording that could not be saved), the
                // photo/video mode switch, then the shutter
                // row — flip (when the device has both
                // facings) and the shutter / record button.
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 32.dp, vertical = 22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val note = recordNote
                    if (note != null) {
                        Text(
                            note,
                            color = Color.White, fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .background(
                                    Color.Black.copy(alpha = 0.55f),
                                    RoundedCornerShape(10.dp)
                                )
                                .padding(
                                    horizontal = 12.dp, vertical = 6.dp
                                )
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = { videoMode = false },
                            enabled = !recording
                        ) {
                            Text(
                                "拍照",
                                color = if (!videoMode) {
                                    Color.White
                                } else {
                                    Color.White.copy(alpha = 0.55f)
                                },
                                fontSize = 15.sp,
                                fontWeight = if (!videoMode) {
                                    androidx.compose.ui.text.font
                                        .FontWeight.SemiBold
                                } else {
                                    androidx.compose.ui.text.font
                                        .FontWeight.Normal
                                }
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        TextButton(
                            onClick = { videoMode = true },
                            enabled = !recording
                        ) {
                            Text(
                                "录像",
                                color = if (videoMode) {
                                    Color.White
                                } else {
                                    Color.White.copy(alpha = 0.55f)
                                },
                                fontSize = 15.sp,
                                fontWeight = if (videoMode) {
                                    androidx.compose.ui.text.font
                                        .FontWeight.SemiBold
                                } else {
                                    androidx.compose.ui.text.font
                                        .FontWeight.Normal
                                }
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.width(48.dp)) {
                            if (facings.size > 1) {
                                IconButton(
                                    onClick = {
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
                                    },
                                    enabled = !recording
                                ) {
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
                        if (videoMode) {
                            // Record button: red disc, turning
                            // into a stop square while rolling.
                            Box(
                                modifier = Modifier
                                    .size(68.dp)
                                    .border(
                                        3.dp,
                                        Color(0xFFE53935),
                                        CircleShape
                                    )
                                    .padding(5.dp)
                                    .clickable {
                                        val controller =
                                            videoControllerRef.value
                                        if (controller != null) {
                                            if (recording) {
                                                controller.stopRecording()
                                            } else {
                                                controller.startRecording()
                                            }
                                        }
                                    }
                                    // Accessibility (v0.9.60, E2):
                                    // the record disc carries no
                                    // text — name it for readers.
                                    .semantics {
                                        contentDescription =
                                            if (recording) {
                                                "停止录像"
                                            } else {
                                                "开始录像"
                                            }
                                        role = Role.Button
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = if (recording) {
                                        Modifier
                                            .size(26.dp)
                                            .background(
                                                Color(0xFFE53935),
                                                RoundedCornerShape(6.dp)
                                            )
                                    } else {
                                        Modifier
                                            .size(52.dp)
                                            .background(
                                                Color(0xFFE53935),
                                                CircleShape
                                            )
                                    }
                                )
                            }
                        } else {
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
                                        val controller =
                                            controllerRef.value
                                        capturing = controller != null &&
                                            controller.capture()
                                    }
                                    // Accessibility (v0.9.60, E2):
                                    // name the shutter disc.
                                    .semantics {
                                        contentDescription = "拍照"
                                        role = Role.Button
                                    }
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        Box(modifier = Modifier.width(48.dp))
                    }
                }
            }
        }
        // Close button rides over every state except the
        // captured / recorded previews (which have their own
        // actions). Closing mid-recording discards the clip:
        // dispose stops the recorder and deletes the file.
        if (capturedPath == null && recordedPath == null) {
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
                        // Taken over by another app
                        // (v0.9.57, B2): land on the failure
                        // column instead of leaving a frozen
                        // preview with no explanation.
                        fail()
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

/**
 * One video-camera pipeline on its own background thread
 * (v0.9.56, A2): a capture session feeding the preview
 * surface plus a prepared MediaRecorder surface. Starting
 * a take retargets the repeating request at both surfaces
 * and starts the recorder; stopping tears the recorder
 * and session down and rebuilds them for the next take —
 * a MediaRecorder is single-shot, so reuse without a
 * rebuild is not an option. The output file is chosen at
 * recorder-setup time (the recorder API demands it before
 * prepare); a file that was never delivered is deleted by
 * [close] / the stop-failure path. Same teardown
 * discipline as [StillCamera]: idempotent [close], every
 * callback guarded against racing a teardown.
 */
private class VideoCamera(
    private val context: Context,
    private val facing: Int,
    private val onRecordingChange: (Boolean) -> Unit,
    private val onVideoReady: (String) -> Unit,
    private val onFailed: () -> Unit,
    private val onRecordFailed: (String) -> Unit
) {
    private val thread = HandlerThread("camera-video").apply { start() }
    private val handler = Handler(thread.looper)
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var closed = false
    private var device: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var recorder: MediaRecorder? = null
    private var previewSurface: Surface? = null
    private var outputFile: File? = null
    private var sensorOrientation = 0
    private var actualFacing = facing
    private var videoWidth = 1280
    private var videoHeight = 720

    @Volatile
    private var recording = false

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
            val videoSize = map
                ?.getOutputSizes(MediaRecorder::class.java)
                ?.filter { it.width <= 1920 && it.height <= 1080 }
                ?.maxByOrNull { it.width.toLong() * it.height }
                ?: map?.getOutputSizes(MediaRecorder::class.java)
                    ?.maxByOrNull { it.width.toLong() * it.height }
                ?: Size(1280, 720)
            videoWidth = videoSize.width
            videoHeight = videoSize.height
            val videoAspect = videoSize.width.toFloat() / videoSize.height
            val previewSize = map
                ?.getOutputSizes(SurfaceTexture::class.java)
                ?.filter { it.width <= 1920 }
                ?.minByOrNull {
                    kotlin.math.abs(
                        it.width.toFloat() / it.height - videoAspect
                    )
                } ?: Size(1280, 720)
            surfaceTexture.setDefaultBufferSize(
                previewSize.width, previewSize.height
            )
            previewSurface = Surface(surfaceTexture)
            if (ContextCompat.checkSelfPermission(
                    context, Manifest.permission.CAMERA
                ) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    context, Manifest.permission.RECORD_AUDIO
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
                        buildSession()
                    }

                    override fun onDisconnected(d: CameraDevice) {
                        d.close()
                        if (device === d) device = null
                        abortTake()
                        fail()
                    }

                    override fun onError(d: CameraDevice, error: Int) {
                        d.close()
                        if (device === d) device = null
                        abortTake()
                        fail()
                    }
                },
                handler
            )
        } catch (t: Throwable) {
            fail()
        }
    }

    /** Configure a fresh recorder and a session over the
     *  preview + recorder surfaces, then run the preview
     *  request (preview surface only — the recorder gets
     *  frames only once a take starts). */
    private fun buildSession() {
        val d = device ?: return
        val preview = previewSurface ?: return
        if (closed) return
        val recSurface = setupRecorder()
        if (recSurface == null) {
            fail()
            return
        }
        try {
            d.createCaptureSession(
                listOf(preview, recSurface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(
                        s: CameraCaptureSession
                    ) {
                        if (closed) {
                            s.close()
                            return
                        }
                        session = s
                        try {
                            val request = d.createCaptureRequest(
                                CameraDevice.TEMPLATE_RECORD
                            )
                            request.addTarget(preview)
                            request.set(
                                CaptureRequest.CONTROL_AF_MODE,
                                CaptureRequest
                                    .CONTROL_AF_MODE_CONTINUOUS_VIDEO
                            )
                            s.setRepeatingRequest(
                                request.build(), null, handler
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

    /** Prepare a single-shot recorder writing to a fresh
     *  cache file; returns its input surface, or null (and
     *  cleans up) when preparation fails. */
    private fun setupRecorder(): Surface? {
        releaseRecorder()
        val file = File(
            context.cacheDir,
            "camera_video_${System.currentTimeMillis()}.mp4"
        )
        outputFile = file
        val rec = if (Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.S
        ) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        return try {
            rec.setAudioSource(MediaRecorder.AudioSource.MIC)
            rec.setVideoSource(MediaRecorder.VideoSource.SURFACE)
            rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            rec.setOutputFile(file.absolutePath)
            val pixels = videoWidth.toLong() * videoHeight
            rec.setVideoEncodingBitRate(
                when {
                    pixels >= 1920L * 1080 -> 14_000_000
                    pixels >= 1280L * 720 -> 8_000_000
                    else -> 5_000_000
                }
            )
            rec.setVideoFrameRate(30)
            rec.setVideoSize(videoWidth, videoHeight)
            rec.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            rec.setAudioEncodingBitRate(128_000)
            rec.setAudioSamplingRate(44_100)
            rec.setOrientationHint(orientationHint())
            // A recorder that dies on its own (a full disk
            // is the classic case) must not leave the
            // screen "recording" a dead take (v0.9.57, B2).
            rec.setOnErrorListener { _, _, _ ->
                onRecorderError()
            }
            rec.prepare()
            recorder = rec
            rec.surface
        } catch (t: Throwable) {
            try { rec.release() } catch (ignored: Throwable) { }
            null
        }
    }

    private fun releaseRecorder() {
        try { recorder?.reset() } catch (t: Throwable) { }
        try { recorder?.release() } catch (t: Throwable) { }
        recorder = null
    }

    /** Start a take; false = pipeline not ready. */
    fun startRecording(): Boolean {
        val s = session
        val d = device
        val rec = recorder
        val preview = previewSurface
        if (closed || recording || s == null || d == null ||
            rec == null || preview == null
        ) {
            return false
        }
        return try {
            val request = d.createCaptureRequest(
                CameraDevice.TEMPLATE_RECORD
            )
            request.addTarget(preview)
            request.addTarget(rec.surface)
            request.set(
                CaptureRequest.CONTROL_AF_MODE,
                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO
            )
            s.setRepeatingRequest(request.build(), null, handler)
            rec.start()
            recording = true
            mainHandler.post { onRecordingChange(true) }
            true
        } catch (t: Throwable) {
            false
        }
    }

    /** Stop the current take: a clean stop hands the file
     *  to the screen; a failed stop (the classic case is a
     *  clip too short to finalize) deletes it and says so.
     *  Either way the recorder + session are rebuilt for
     *  the next take. */
    fun stopRecording() {
        if (closed || !recording) return
        recording = false
        mainHandler.post { onRecordingChange(false) }
        val rec = recorder
        val file = outputFile
        var ok = false
        try {
            rec?.stop()
            ok = file != null && file.exists() && file.length() > 0
        } catch (t: Throwable) {
            ok = false
        }
        try { session?.close() } catch (t: Throwable) { }
        session = null
        releaseRecorder()
        if (ok && file != null) {
            // Ownership passes to the screen, which deletes
            // the clip if the user never confirms it.
            outputFile = null
            mainHandler.post { onVideoReady(file.absolutePath) }
        } else {
            if (file != null) {
                try { file.delete() } catch (t: Throwable) { }
            }
            outputFile = null
            mainHandler.post {
                onRecordFailed("这段录像没能存下来，再录一次吧。")
            }
        }
        if (!closed && device != null) buildSession()
    }

    /** A take whose camera just went away (v0.9.57, B2)
     *  can never be finalized: stop the recorder quietly,
     *  delete the partial file and hand the screen its
     *  recording-over signal plus the plain note. Without
     *  this, a mid-take disconnect left the recorder
     *  running against a dead surface and the screen's
     *  timer counting over a frozen preview; the failure
     *  column (shown by the caller right after) explains
     *  the camera side. */
    private fun abortTake() {
        if (!recording) return
        recording = false
        try { recorder?.stop() } catch (t: Throwable) { }
        val file = outputFile
        if (file != null) {
            try { file.delete() } catch (t: Throwable) { }
            outputFile = null
        }
        try { session?.close() } catch (t: Throwable) { }
        session = null
        releaseRecorder()
        mainHandler.post {
            onRecordingChange(false)
            onRecordFailed("录制被打断了，这段没有存下来。")
        }
    }

    /** The recorder stopped by itself mid-take (the
     *  error listener set in setupRecorder): the take is
     *  unrecoverable, but the camera is still here, so —
     *  like a failed stop — the partial file is deleted,
     *  the user gets the plain note, and a fresh session
     *  is built for the next take. */
    private fun onRecorderError() {
        if (closed || !recording) return
        recording = false
        mainHandler.post { onRecordingChange(false) }
        val file = outputFile
        if (file != null) {
            try { file.delete() } catch (t: Throwable) { }
            outputFile = null
        }
        try { session?.close() } catch (t: Throwable) { }
        session = null
        releaseRecorder()
        mainHandler.post {
            onRecordFailed(
                "录制中断了，这段没有存下来" +
                    "（可能是存储空间不足）。"
            )
        }
        if (!closed && device != null) buildSession()
    }

    private fun orientationHint(): Int {
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
        if (recording) {
            recording = false
            // Discarded mid-take: stop quietly and delete
            // the partial file below via outputFile.
            try { recorder?.stop() } catch (t: Throwable) { }
        }
        try { session?.close() } catch (t: Throwable) { }
        try { device?.close() } catch (t: Throwable) { }
        releaseRecorder()
        val file = outputFile
        if (file != null) {
            try { file.delete() } catch (t: Throwable) { }
            outputFile = null
        }
        thread.quitSafely()
    }
}
