package com.tuxungram.android.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.media.ExifInterface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tuxungram.android.td.MediaEntry
import com.tuxungram.android.td.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** One freehand stroke, in normalized coordinates of the
 *  (rotation-applied) image: x is a fraction of the width,
 *  y of the height; [widthFrac] is the pen width as a
 *  fraction of the image width. */
private data class StrokeData(
    val points: List<Offset>,
    val color: Color,
    val widthFrac: Float
)

/** One placed text label; [x]/[y] are the label CENTER in
 *  normalized image coordinates, [sizeFrac] the text size
 *  as a fraction of the image width. */
private data class TextData(
    val id: Int,
    val text: String,
    val x: Float,
    val y: Float,
    val color: Color,
    val sizeFrac: Float
)

/** One placed sticker (v0.9.32, Y1). [x]/[y] are the
 *  sticker CENTER in normalized image coordinates,
 *  [scaleFrac] its width as a fraction of the image
 *  width, [rotation] its clockwise tilt in degrees —
 *  the same normalized scheme as strokes and texts, so
 *  preview and export share the geometry. [fileId] is
 *  the image to bake: the sticker file itself for webp
 *  stickers, the static thumbnail for tgs/webm ones
 *  (their own files are not bitmaps). [aspect] is the
 *  sticker's width/height. */
private data class PlacedSticker(
    val id: Int,
    val fileId: Int,
    val aspect: Float,
    val x: Float,
    val y: Float,
    val scaleFrac: Float,
    val rotation: Float
)

/** One row of the 调色 panel (v0.9.31, X1): label, current
 *  value, slider range, and the state writer. */
private data class AdjustSlider(
    val label: String,
    val value: Float,
    val range: ClosedFloatingPointRange<Float>,
    val onChange: (Float) -> Unit
)

/** Crop rectangle in normalized image coordinates. */
private data class CropRect(
    val l: Float, val t: Float, val r: Float, val b: Float
) {
    companion object {
        val FULL = CropRect(0f, 0f, 1f, 1f)
    }
}

private enum class EditMode { CROP, DRAW, TEXT, ADJUST, STICKER }

/** The two tools sharing the draw gesture layer (v0.9.31,
 *  X1): the doodle pen paints colored strokes; the mosaic
 *  pen marks regions the export pixelates. */
private enum class DrawTool { PEN, MOSAIC }

/** Color filter presets (v0.9.31, X1): key -> label. The
 *  matrices live in editorColorMatrix(). */
private val filterPresets = listOf(
    "none" to "原图",
    "mono" to "黑白",
    "sepia" to "复古",
    "vivid" to "鲜明",
    "cool" to "冷色",
    "warm" to "暖色"
)

private val editorPalette = listOf(
    Color(0xFF000000), Color(0xFFFFFFFF), Color(0xFFE53935),
    Color(0xFFFFC107), Color(0xFF1E88E5), Color(0xFF43A047)
)

private fun rotateBitmap(src: Bitmap, degrees: Float): Bitmap {
    if (degrees % 360f == 0f) return src
    val matrix = Matrix().apply { postRotate(degrees) }
    return Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
}

/** Bake the file's EXIF orientation into the pixels so the
 *  exported JPEG needs no orientation flag (the plain send
 *  path never edits, so it keeps sending the untouched
 *  original; only an edited photo goes through here). */
private fun applyExifOrientation(path: String, bmp: Bitmap): Bitmap {
    val orientation = try {
        ExifInterface(path).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )
    } catch (t: Throwable) {
        ExifInterface.ORIENTATION_NORMAL
    }
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL ->
            matrix.postScale(-1f, 1f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL ->
            matrix.postScale(1f, -1f)
        ExifInterface.ORIENTATION_TRANSPOSE -> {
            matrix.postRotate(90f)
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_TRANSVERSE -> {
            matrix.postRotate(270f)
            matrix.postScale(-1f, 1f)
        }
        else -> return bmp
    }
    return Bitmap.createBitmap(
        bmp, 0, 0, bmp.width, bmp.height, matrix, true
    )
}

/**
 * Pre-send photo editor (v0.9.27, T1; basic edition, all
 * client-side, no editor library): crop with a draggable
 * frame and aspect presets （自由/1:1/4:3/16:9), rotate in
 * 90° steps, freehand doodles (color / width / undo) and
 * movable text labels. v0.9.31 (X1) adds a color grade —
 * filter presets plus brightness / contrast / saturation
 * sliders — and a mosaic pen that pixelates what it
 * covers. Everything is stored in normalized coordinates
 * of the rotated image, so the preview and the exported
 * JPEG run the exact same geometry. Export draws
 * crop -> grade -> mosaic -> strokes -> texts onto a
 * fresh bitmap and saves a JPEG into the cache; the
 * caller then sends that file through the normal photo
 * path. Videos never come here. v0.9.32 (Y1) adds
 * sticker overlays: picked from the installed sets via
 * MediaStore's catalogs, dragged / pinch-scaled /
 * pinch-rotated on the canvas, and baked between the
 * strokes and the texts (the export order is fixed:
 * crop -> grade -> mosaic -> strokes -> stickers ->
 * texts). Animated (tgs) and video (webm) stickers bake
 * from their static thumbnail, like the brief requires.
 */
@Composable
fun PhotoEditorScreen(
    path: String,
    onDone: (String) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    var oriented by remember(path) { mutableStateOf<Bitmap?>(null) }
    var loadFailed by remember(path) { mutableStateOf(false) }
    LaunchedEffect(path) {
        val loaded = withContext(Dispatchers.IO) {
            val decoded = decodeDownsampled(path, 2048)
            if (decoded == null) {
                null
            } else {
                applyExifOrientation(path, decoded)
            }
        }
        if (loaded == null) loadFailed = true else oriented = loaded
    }
    var rotation by remember { mutableStateOf(0) }
    val base = remember(oriented, rotation) {
        val src = oriented ?: return@remember null
        if (rotation == 0) src else rotateBitmap(src, rotation * 90f)
    }

    var mode by remember { mutableStateOf(EditMode.CROP) }
    var crop by remember { mutableStateOf(CropRect.FULL) }
    var aspect by remember { mutableStateOf<Float?>(null) }
    var strokes by remember { mutableStateOf<List<StrokeData>>(emptyList()) }
    var currentStroke by remember { mutableStateOf<List<Offset>?>(null) }
    var drawColor by remember { mutableStateOf(editorPalette[2]) }
    var drawWidthFrac by remember { mutableStateOf(0.012f) }
    // Mosaic pen + color grade (v0.9.31, X1): mosaic
    // strokes share the doodle coordinate system; the grade
    // is one filter preset plus three adjustment sliders,
    // composed by editorColorMatrix() in a fixed order.
    var drawTool by remember { mutableStateOf(DrawTool.PEN) }
    var mosaicStrokes by remember {
        mutableStateOf<List<StrokeData>>(emptyList())
    }
    var mosaicWidthFrac by remember { mutableFloatStateOf(0.05f) }
    var filterKey by remember { mutableStateOf("none") }
    var brightness by remember { mutableFloatStateOf(0f) }
    var contrast by remember { mutableFloatStateOf(1f) }
    var saturation by remember { mutableFloatStateOf(1f) }
    // Preview bake (X1): the color grade and the mosaic are
    // baked into a preview bitmap by the same functions the
    // export runs, so what the user sees is what ships.
    // Doodles and texts stay vector overlays on top of it.
    var baked by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(
        base, filterKey, brightness, contrast, saturation,
        mosaicStrokes
    ) {
        val src = base
        baked = if (src == null) {
            null
        } else {
            withContext(Dispatchers.Default) {
                runCatching {
                    bakeColorAndMosaic(
                        src,
                        editorColorMatrix(
                            filterKey, brightness, contrast,
                            saturation
                        ),
                        mosaicStrokes
                    )
                }.getOrNull()
            }
        }
    }
    var texts by remember { mutableStateOf<List<TextData>>(emptyList()) }
    var selectedTextId by remember { mutableStateOf<Int?>(null) }
    var nextTextId by remember { mutableStateOf(1) }
    // Placed stickers (v0.9.32, Y1): the picker reads
    // MediaStore's catalogs (the chat panel's store);
    // stickerPaths seeds the bake file's local path at
    // pick time, and liveStickerPath() also resolves
    // through the catalog flows as downloads land.
    var placedStickers by remember {
        mutableStateOf<List<PlacedSticker>>(emptyList())
    }
    var selectedStickerId by remember { mutableStateOf<Int?>(null) }
    var nextStickerId by remember { mutableStateOf(1) }
    val stickerPaths = remember { mutableStateMapOf<Int, String>() }
    val recentStickers by MediaStore.recentStickers.collectAsState()
    val favoriteStickers by MediaStore.favoriteStickers.collectAsState()
    val stickerSets by MediaStore.stickerSets.collectAsState()
    val setStickers by MediaStore.setStickers.collectAsState()
    // null = recent, -1 = favorites, otherwise a set id.
    var pickerSet by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(mode) {
        if (mode == EditMode.STICKER) MediaStore.load()
    }

    /** The bake file of a catalog entry: the sticker file
     *  itself for webp stickers (bitmap-decodable), the
     *  static thumbnail for tgs/webm ones. */
    fun bakeFileOf(entry: MediaEntry): Pair<Int, String?> {
        val isWebp = entry.format == "webp" || entry.format.isEmpty()
        return if (isWebp) {
            entry.fileId to entry.previewPath
        } else {
            entry.thumbFileId to entry.thumbPath
        }
    }

    fun liveStickerPath(fileId: Int): String? {
        stickerPaths[fileId]?.let { return it }
        val all = recentStickers + favoriteStickers + setStickers
        for (entry in all) {
            val (bakeId, known) = bakeFileOf(entry)
            if (bakeId == fileId && known != null) return known
        }
        return null
    }

    fun placeSticker(entry: MediaEntry) {
        val (bakeId, known) = bakeFileOf(entry)
        if (bakeId == 0) return
        if (known != null) stickerPaths[bakeId] = known
        val aspect = if (entry.width > 0 && entry.height > 0) {
            entry.width.toFloat() / entry.height.toFloat()
        } else {
            1f
        }
        val id = nextStickerId++
        placedStickers = placedStickers + PlacedSticker(
            id = id, fileId = bakeId, aspect = aspect,
            x = 0.5f, y = 0.5f, scaleFrac = 0.35f, rotation = 0f
        )
        selectedStickerId = id
    }
    // Text dialog: -1 = new label, otherwise the label id
    // being edited; null = closed.
    var textDialogFor by remember { mutableStateOf<Int?>(null) }
    var textDialogValue by remember { mutableStateOf("") }
    var exporting by remember { mutableStateOf(false) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    val labelSizes = remember { mutableStateMapOf<Int, IntSize>() }

    val dirty = rotation != 0 || crop != CropRect.FULL ||
        strokes.isNotEmpty() || texts.isNotEmpty() ||
        placedStickers.isNotEmpty() ||
        mosaicStrokes.isNotEmpty() || filterKey != "none" ||
        brightness != 0f || contrast != 1f || saturation != 1f

    fun rotateAll() {
        // 90° clockwise: (x, y) -> (1 - y, x); the crop rect
        // follows from its corners.
        crop = CropRect(
            l = 1f - crop.b, t = crop.l,
            r = 1f - crop.t, b = crop.r
        )
        strokes = strokes.map { stroke ->
            stroke.copy(
                points = stroke.points.map {
                    Offset(1f - it.y, it.x)
                }
            )
        }
        mosaicStrokes = mosaicStrokes.map { stroke ->
            stroke.copy(
                points = stroke.points.map {
                    Offset(1f - it.y, it.x)
                }
            )
        }
        texts = texts.map {
            it.copy(x = 1f - it.y, y = it.x)
        }
        // Stickers follow the content like texts; their
        // width fraction is re-based on the NEW image
        // width (the old height), and their tilt gains the
        // same 90 degrees.
        val preRotation = base
        placedStickers = placedStickers.map {
            it.copy(
                x = 1f - it.y, y = it.x,
                rotation = it.rotation + 90f,
                scaleFrac = if (preRotation != null &&
                    preRotation.height > 0
                ) {
                    it.scaleFrac * preRotation.width.toFloat() /
                        preRotation.height.toFloat()
                } else {
                    it.scaleFrac
                }
            )
        }
        rotation = (rotation + 1) % 4
    }

    fun applyAspect(ratio: Float?) {
        aspect = ratio
        if (ratio == null) return
        val bmp = base ?: return
        val imgW = bmp.width.toFloat()
        val imgH = bmp.height.toFloat()
        val cx = (crop.l + crop.r) / 2f * imgW
        val cy = (crop.t + crop.b) / 2f * imgH
        var w = (crop.r - crop.l) * imgW
        var h = w / ratio
        val curH = (crop.b - crop.t) * imgH
        if (h > curH) {
            h = curH
            w = h * ratio
        }
        if (w > imgW) {
            w = imgW
            h = w / ratio
        }
        if (h > imgH) {
            h = imgH
            w = h * ratio
        }
        var l = cx - w / 2f
        var t = cy - h / 2f
        l = l.coerceIn(0f, imgW - w)
        t = t.coerceIn(0f, imgH - h)
        crop = CropRect(
            l / imgW, t / imgH, (l + w) / imgW, (t + h) / imgH
        )
    }

    fun exportAndDone() {
        val bmp = base ?: return
        if (!dirty) {
            onDone(path)
            return
        }
        // Resolve every placed sticker's bake file BEFORE
        // leaving the composition: a sticker whose image
        // has not downloaded yet must not silently vanish
        // from the export — the export simply waits (the
        // user stays in the editor and can retry).
        val resolvedPaths = HashMap<Int, String>()
        for (sticker in placedStickers) {
            val p = liveStickerPath(sticker.fileId) ?: return
            resolvedPaths[sticker.fileId] = p
        }
        exporting = true
        scope.launch {
            val out = withContext(Dispatchers.Default) {
                runCatching {
                    val stickerBitmaps = HashMap<Int, Bitmap>()
                    for ((fileId, p) in resolvedPaths) {
                        BitmapFactory.decodeFile(p)?.let {
                            stickerBitmaps[fileId] = it
                        }
                    }
                    if (stickerBitmaps.size < resolvedPaths.size) {
                        null
                    } else {
                        exportEdited(
                            bmp, crop, strokes, texts,
                            editorColorMatrix(
                                filterKey, brightness, contrast,
                                saturation
                            ),
                            mosaicStrokes,
                            placedStickers,
                            stickerBitmaps,
                            File(
                                context.cacheDir,
                                "edit_${System.currentTimeMillis()}.jpg"
                            )
                        )
                    }
                }.getOrNull()
            }
            exporting = false
            if (out != null) {
                onDone(out)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onCancel) { Text("取消") }
            Text(
                "编辑图片",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = { exportAndDone() },
                enabled = base != null && !exporting
            ) {
                Text(if (exporting) "处理中…" else "完成")
            }
        }
        val bmp = base
        if (bmp == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color(0xFF111111)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (loadFailed) "这张图片读不出来" else "正在载入…",
                    color = Color.Gray
                )
            }
        } else {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color(0xFF111111)),
                contentAlignment = Alignment.Center
            ) {
                val maxW = constraints.maxWidth
                val maxH = constraints.maxHeight
                val scale = min(
                    maxW / bmp.width.toFloat(),
                    maxH / bmp.height.toFloat()
                )
                val dispW = (bmp.width * scale).toInt().coerceAtLeast(1)
                val dispH = (bmp.height * scale).toInt().coerceAtLeast(1)
                Box(
                    modifier = Modifier
                        .size(
                            with(density) { dispW.toDp() },
                            with(density) { dispH.toDp() }
                        )
                        .onSizeChanged { containerSize = it }
                        // TEXT mode background tap = deselect.
                        // The labels are children: their own
                        // handlers get the events first (main
                        // pass runs leaf-first), so this only
                        // fires for taps on empty image area.
                        .pointerInput(mode) {
                            if (mode == EditMode.TEXT) {
                                detectTapGestures(
                                    onTap = { selectedTextId = null }
                                )
                            }
                        }
                ) {
                    Image(
                        // The baked preview (grade + mosaic)
                        // once it lands; the plain rotated
                        // base until then (X1).
                        bitmap = (baked ?: bmp).asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier.fillMaxSize()
                    )
                    // Doodles (always visible).
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        // In-progress stroke: the pen draws in
                        // its color; the mosaic pen draws a
                        // translucent gray smear (its baked
                        // pixelation replaces it on release).
                        val all = if (currentStroke != null) {
                            strokes + StrokeData(
                                currentStroke!!,
                                if (drawTool == DrawTool.MOSAIC) {
                                    Color(0x99616161)
                                } else {
                                    drawColor
                                },
                                if (drawTool == DrawTool.MOSAIC) {
                                    mosaicWidthFrac
                                } else {
                                    drawWidthFrac
                                }
                            )
                        } else {
                            strokes
                        }
                        for (stroke in all) {
                            val wPx = stroke.widthFrac * size.width
                            if (stroke.points.size == 1) {
                                drawCircle(
                                    color = stroke.color,
                                    radius = wPx / 2f,
                                    center = Offset(
                                        stroke.points[0].x * size.width,
                                        stroke.points[0].y * size.height
                                    )
                                )
                            } else if (stroke.points.size > 1) {
                                val p = Path()
                                p.moveTo(
                                    stroke.points[0].x * size.width,
                                    stroke.points[0].y * size.height
                                )
                                for (pt in stroke.points.drop(1)) {
                                    p.lineTo(
                                        pt.x * size.width,
                                        pt.y * size.height
                                    )
                                }
                                drawPath(
                                    path = p,
                                    color = stroke.color,
                                    style = Stroke(
                                        width = wPx,
                                        cap = androidx.compose.ui.graphics
                                            .StrokeCap.Round,
                                        join = androidx.compose.ui.graphics
                                            .StrokeJoin.Round
                                    )
                                )
                            }
                        }
                    }
                    // Crop dimming + frame (handles only in
                    // CROP mode, the frame itself always).
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val l = crop.l * size.width
                        val t = crop.t * size.height
                        val r = crop.r * size.width
                        val b = crop.b * size.height
                        val dim = Color.Black.copy(alpha = 0.55f)
                        drawRect(dim, Offset(0f, 0f), androidx.compose.ui.geometry.Size(size.width, t))
                        drawRect(dim, Offset(0f, b), androidx.compose.ui.geometry.Size(size.width, size.height - b))
                        drawRect(dim, Offset(0f, t), androidx.compose.ui.geometry.Size(l, b - t))
                        drawRect(dim, Offset(r, t), androidx.compose.ui.geometry.Size(size.width - r, b - t))
                        drawRect(
                            color = Color.White,
                            topLeft = Offset(l, t),
                            size = androidx.compose.ui.geometry.Size(r - l, b - t),
                            style = Stroke(width = 2.dp.toPx())
                        )
                        if (mode == EditMode.CROP) {
                            val handle = 5.dp.toPx()
                            for ((hx, hy) in listOf(
                                l to t, r to t, l to b, r to b
                            )) {
                                drawCircle(
                                    color = Color.White,
                                    radius = handle,
                                    center = Offset(hx, hy)
                                )
                            }
                        }
                    }
                    // Placed stickers (Y1): between the
                    // doodles and the text labels, matching
                    // the fixed export order. Their own
                    // gestures live in the STICKER overlay
                    // at the end of this Box.
                    for (sticker in placedStickers) {
                        val sPath = liveStickerPath(sticker.fileId)
                        val sBitmap by produceState<Bitmap?>(
                            initialValue = null, sPath
                        ) {
                            value = if (sPath == null) {
                                null
                            } else {
                                withContext(Dispatchers.IO) {
                                    decodeCached(sPath)
                                }
                            }
                        }
                        val sb = sBitmap ?: continue
                        val wPx = sticker.scaleFrac *
                            containerSize.width
                        val hPx = if (sticker.aspect > 0f) {
                            wPx / sticker.aspect
                        } else {
                            wPx
                        }
                        Image(
                            bitmap = sb.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .offset {
                                    IntOffset(
                                        (sticker.x *
                                            containerSize.width -
                                            wPx / 2f).roundToInt(),
                                        (sticker.y *
                                            containerSize.height -
                                            hPx / 2f).roundToInt()
                                    )
                                }
                                .size(
                                    with(density) { wPx.toDp() },
                                    with(density) { hPx.toDp() }
                                )
                                .graphicsLayer {
                                    rotationZ = sticker.rotation
                                }
                                .then(
                                    if (selectedStickerId ==
                                        sticker.id &&
                                        mode == EditMode.STICKER
                                    ) {
                                        Modifier.border(
                                            1.5.dp,
                                            MaterialTheme.colorScheme
                                                .primary,
                                            RoundedCornerShape(6.dp)
                                        )
                                    } else {
                                        Modifier
                                    }
                                )
                        )
                    }
                    // Text labels.
                    for (label in texts) {
                        val labelSize = labelSizes[label.id]
                        Text(
                            text = label.text,
                            color = label.color,
                            style = TextStyle(
                                fontSize = with(density) {
                                    (label.sizeFrac * dispW).toSp()
                                },
                                fontWeight = FontWeight.Bold,
                                shadow = Shadow(
                                    color = Color.Black.copy(alpha = 0.6f),
                                    offset = Offset(2f, 2f),
                                    blurRadius = 4f
                                )
                            ),
                            modifier = Modifier
                                .offset {
                                    val cx = label.x * containerSize.width
                                    val cy = label.y * containerSize.height
                                    IntOffset(
                                        (cx - (labelSize?.width ?: 0) / 2f)
                                            .roundToInt(),
                                        (cy - (labelSize?.height ?: 0) / 2f)
                                            .roundToInt()
                                    )
                                }
                                .onSizeChanged {
                                    labelSizes[label.id] = it
                                }
                                .then(
                                    if (selectedTextId == label.id) {
                                        Modifier.border(
                                            1.dp,
                                            MaterialTheme.colorScheme.primary,
                                            RoundedCornerShape(4.dp)
                                        )
                                    } else {
                                        Modifier
                                    }
                                )
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                .pointerInput(label.id, mode) {
                                    if (mode != EditMode.TEXT) {
                                        return@pointerInput
                                    }
                                    detectTapGestures(
                                        onTap = {
                                            selectedTextId = label.id
                                        }
                                    )
                                }
                                .pointerInput(label.id, mode, containerSize) {
                                    if (mode != EditMode.TEXT) {
                                        return@pointerInput
                                    }
                                    detectDragGestures(
                                        onDragStart = {
                                            selectedTextId = label.id
                                        },
                                        onDrag = { change, drag ->
                                            change.consume()
                                            val w = containerSize.width
                                                .coerceAtLeast(1)
                                            val h = containerSize.height
                                                .coerceAtLeast(1)
                                            texts = texts.map {
                                                if (it.id == label.id) {
                                                    it.copy(
                                                        x = (it.x + drag.x / w)
                                                            .coerceIn(0f, 1f),
                                                        y = (it.y + drag.y / h)
                                                            .coerceIn(0f, 1f)
                                                    )
                                                } else {
                                                    it
                                                }
                                            }
                                        }
                                    )
                                }
                        )
                    }
                    // Mode gesture layer (CROP / DRAW only —
                    // in TEXT mode it would sit over the labels
                    // and eat their drags, so it is not composed
                    // there; the container handles deselect).
                    if (mode != EditMode.TEXT) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(
                                mode, containerSize, rotation, drawTool
                            ) {
                                val w = containerSize.width
                                    .coerceAtLeast(1)
                                val h = containerSize.height
                                    .coerceAtLeast(1)
                                when (mode) {
                                    EditMode.DRAW -> detectDragGestures(
                                        onDragStart = { start ->
                                            currentStroke = listOf(
                                                Offset(
                                                    (start.x / w)
                                                        .coerceIn(0f, 1f),
                                                    (start.y / h)
                                                        .coerceIn(0f, 1f)
                                                )
                                            )
                                        },
                                        onDrag = { change, _ ->
                                            change.consume()
                                            val pt = Offset(
                                                (change.position.x / w)
                                                    .coerceIn(0f, 1f),
                                                (change.position.y / h)
                                                    .coerceIn(0f, 1f)
                                            )
                                            currentStroke =
                                                (currentStroke ?: emptyList()) + pt
                                        },
                                        onDragEnd = {
                                            currentStroke?.let { pts ->
                                                if (pts.isNotEmpty()) {
                                                    if (drawTool ==
                                                        DrawTool.MOSAIC
                                                    ) {
                                                        mosaicStrokes =
                                                            mosaicStrokes +
                                                                StrokeData(
                                                                    pts,
                                                                    Color.Gray,
                                                                    mosaicWidthFrac
                                                                )
                                                    } else {
                                                        strokes = strokes +
                                                            StrokeData(
                                                                pts,
                                                                drawColor,
                                                                drawWidthFrac
                                                            )
                                                    }
                                                }
                                            }
                                            currentStroke = null
                                        },
                                        onDragCancel = {
                                            currentStroke = null
                                        }
                                    )
                                    // Unreachable (the layer is
                                    // not composed in TEXT mode);
                                    // kept for exhaustiveness.
                                    EditMode.TEXT -> Unit
                                    // Adjust mode has no image
                                    // gestures; sliders live in
                                    // its control row.
                                    EditMode.ADJUST -> Unit
                                    // Sticker gestures live
                                    // in the dedicated overlay
                                    // composed after this
                                    // layer (below).
                                    EditMode.STICKER -> Unit
                                    EditMode.CROP -> {
                                        var dragCorner = -1
                                        var moveStart: Offset? = null
                                        var cropAtStart = crop
                                        detectDragGestures(
                                            onDragStart = { start ->
                                                cropAtStart = crop
                                                val corners = listOf(
                                                    Offset(
                                                        crop.l * w, crop.t * h
                                                    ),
                                                    Offset(
                                                        crop.r * w, crop.t * h
                                                    ),
                                                    Offset(
                                                        crop.l * w, crop.b * h
                                                    ),
                                                    Offset(
                                                        crop.r * w, crop.b * h
                                                    )
                                                )
                                                val threshold = 48.dp.toPx()
                                                var best = -1
                                                var bestDist = Float.MAX_VALUE
                                                for ((i, c) in corners
                                                    .withIndex()) {
                                                    val d = (start - c)
                                                        .getDistance()
                                                    if (d < bestDist) {
                                                        bestDist = d
                                                        best = i
                                                    }
                                                }
                                                dragCorner =
                                                    if (bestDist <= threshold) {
                                                        best
                                                    } else {
                                                        -1
                                                    }
                                                moveStart =
                                                    if (dragCorner == -1 &&
                                                        start.x >= crop.l * w &&
                                                        start.x <= crop.r * w &&
                                                        start.y >= crop.t * h &&
                                                        start.y <= crop.b * h
                                                    ) {
                                                        start
                                                    } else {
                                                        null
                                                    }
                                            },
                                            onDrag = { change, _ ->
                                                change.consume()
                                                val pos = change.position
                                                val imgW = bmp.width
                                                    .toFloat()
                                                val imgH = bmp.height
                                                    .toFloat()
                                                if (dragCorner >= 0) {
                                                    crop = dragCropCorner(
                                                        cropAtStart,
                                                        dragCorner,
                                                        pos.x / w, pos.y / h,
                                                        aspect, imgW, imgH
                                                    )
                                                } else if (moveStart != null) {
                                                    val start = moveStart!!
                                                    val dx =
                                                        (pos.x - start.x) / w
                                                    val dy =
                                                        (pos.y - start.y) / h
                                                    val width0 =
                                                        cropAtStart.r - cropAtStart.l
                                                    val height0 =
                                                        cropAtStart.b - cropAtStart.t
                                                    val nl = (cropAtStart.l + dx)
                                                        .coerceIn(
                                                            0f, 1f - width0
                                                        )
                                                    val nt = (cropAtStart.t + dy)
                                                        .coerceIn(
                                                            0f, 1f - height0
                                                        )
                                                    crop = CropRect(
                                                        nl, nt,
                                                        nl + width0,
                                                        nt + height0
                                                    )
                                                }
                                            },
                                            onDragEnd = {
                                                dragCorner = -1
                                                moveStart = null
                                            },
                                            onDragCancel = {
                                                dragCorner = -1
                                                moveStart = null
                                            }
                                        )
                                    }
                                }
                            }
                    )
                    }
                    // Sticker gesture overlay (Y1): composed
                    // only in STICKER mode, above every
                    // other layer. Tap selects the topmost
                    // sticker under the point (or deselects
                    // on empty canvas); transform gestures
                    // move / pinch-scale / pinch-rotate the
                    // selected one.
                    if (mode == EditMode.STICKER) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(
                                    containerSize, placedStickers
                                ) {
                                    val w = containerSize.width
                                        .coerceAtLeast(1)
                                    val h = containerSize.height
                                        .coerceAtLeast(1)
                                    detectTapGestures(
                                        onTap = { pos ->
                                            val nx = pos.x / w
                                            val ny = pos.y / h
                                            var hit: Int? = null
                                            for (st in placedStickers) {
                                                val swN = st.scaleFrac
                                                val shN = if (
                                                    st.aspect > 0f
                                                ) {
                                                    swN *
                                                        (w.toFloat() / h) /
                                                        st.aspect
                                                } else {
                                                    swN
                                                }
                                                if (abs(nx - st.x) <=
                                                    swN / 2f &&
                                                    abs(ny - st.y) <=
                                                    shN / 2f
                                                ) {
                                                    hit = st.id
                                                }
                                            }
                                            selectedStickerId = hit
                                        }
                                    )
                                }
                                .pointerInput(
                                    containerSize, selectedStickerId
                                ) {
                                    val w = containerSize.width
                                        .coerceAtLeast(1)
                                    val h = containerSize.height
                                        .coerceAtLeast(1)
                                    detectTransformGestures {
                                            _, pan, zoom, rot ->
                                        val sel = selectedStickerId
                                            ?: return@detectTransformGestures
                                        placedStickers =
                                            placedStickers.map {
                                                if (it.id == sel) {
                                                    it.copy(
                                                        x = (it.x +
                                                            pan.x / w)
                                                            .coerceIn(
                                                                0f, 1f
                                                            ),
                                                        y = (it.y +
                                                            pan.y / h)
                                                            .coerceIn(
                                                                0f, 1f
                                                            ),
                                                        scaleFrac =
                                                            (it.scaleFrac *
                                                                zoom)
                                                                .coerceIn(
                                                                    0.05f,
                                                                    1.6f
                                                                ),
                                                        rotation =
                                                            it.rotation +
                                                                rot
                                                    )
                                                } else {
                                                    it
                                                }
                                            }
                                    }
                                }
                        )
                    }
                }
            }
        }
        // Mode tabs.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            for ((m, label) in listOf(
                EditMode.CROP to "裁剪",
                EditMode.DRAW to "涂鸦",
                EditMode.TEXT to "文字",
                EditMode.ADJUST to "调色",
                EditMode.STICKER to "贴纸"
            )) {
                Box(
                    modifier = Modifier
                        .background(
                            if (mode == m) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            RoundedCornerShape(50)
                        )
                        .clickable { mode = m }
                        .padding(horizontal = 16.dp, vertical = 7.dp)
                ) {
                    Text(
                        label, fontSize = 14.sp,
                        color = if (mode == m) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        fontWeight = if (mode == m) {
                            FontWeight.SemiBold
                        } else {
                            FontWeight.Normal
                        }
                    )
                }
                Spacer(Modifier.width(8.dp))
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { rotateAll() }) {
                Text("旋转 90°")
            }
        }
        // Mode-specific controls.
        when (mode) {
            EditMode.CROP -> Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("比例", fontSize = 13.5.sp)
                Spacer(Modifier.width(8.dp))
                for ((label, ratio) in listOf<Pair<String, Float?>>(
                    "自由" to null,
                    "1:1" to 1f,
                    "4:3" to 4f / 3f,
                    "16:9" to 16f / 9f
                )) {
                    Box(
                        modifier = Modifier
                            .background(
                                if (aspect == ratio) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme
                                        .surfaceVariant
                                },
                                RoundedCornerShape(50)
                            )
                            .clickable { applyAspect(ratio) }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            label, fontSize = 13.sp,
                            color = if (aspect == ratio) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme
                                    .onSurfaceVariant
                            }
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = {
                    crop = CropRect.FULL
                    aspect = null
                }) {
                    Text("重置")
                }
            }
            EditMode.DRAW -> Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 10.dp)
            ) {
                // Tool toggle (v0.9.31, X1): doodle pen /
                // mosaic pen. Undo and clear act on the
                // ACTIVE tool's stroke list.
                val activeIsMosaic = drawTool == DrawTool.MOSAIC
                val activeStrokes =
                    if (activeIsMosaic) mosaicStrokes else strokes
                Row(verticalAlignment = Alignment.CenterVertically) {
                    for ((tool, label) in listOf(
                        DrawTool.PEN to "画笔",
                        DrawTool.MOSAIC to "打码"
                    )) {
                        Box(
                            modifier = Modifier
                                .background(
                                    if (drawTool == tool) {
                                        MaterialTheme.colorScheme
                                            .primary
                                    } else {
                                        MaterialTheme.colorScheme
                                            .surfaceVariant
                                    },
                                    RoundedCornerShape(50)
                                )
                                .clickable { drawTool = tool }
                                .padding(
                                    horizontal = 14.dp, vertical = 6.dp
                                )
                        ) {
                            Text(
                                label, fontSize = 13.sp,
                                color = if (drawTool == tool) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme
                                        .onSurfaceVariant
                                }
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(
                        onClick = {
                            if (activeIsMosaic) {
                                mosaicStrokes =
                                    mosaicStrokes.dropLast(1)
                            } else {
                                strokes = strokes.dropLast(1)
                            }
                        },
                        enabled = activeStrokes.isNotEmpty()
                    ) { Text("撤销") }
                    TextButton(
                        onClick = {
                            if (activeIsMosaic) {
                                mosaicStrokes = emptyList()
                            } else {
                                strokes = emptyList()
                            }
                        },
                        enabled = activeStrokes.isNotEmpty()
                    ) { Text("清空") }
                }
                if (activeIsMosaic) {
                    Text(
                        "涂抹要遮住的地方，导出时这些区域会变成马赛克",
                        fontSize = 12.5.sp, color = Color.Gray,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        for (color in editorPalette) {
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .background(color, CircleShape)
                                    .then(
                                        if (drawColor == color) {
                                            Modifier.border(
                                                2.dp,
                                                MaterialTheme.colorScheme
                                                    .primary,
                                                CircleShape
                                            )
                                        } else {
                                            Modifier.border(
                                                1.dp, Color.Gray,
                                                CircleShape
                                            )
                                        }
                                    )
                                    .clickable { drawColor = color }
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("粗细", fontSize = 13.5.sp)
                    Spacer(Modifier.width(8.dp))
                    val widths = if (activeIsMosaic) {
                        listOf(
                            "细" to 0.03f, "中" to 0.05f, "粗" to 0.08f
                        )
                    } else {
                        listOf(
                            "细" to 0.006f, "中" to 0.012f, "粗" to 0.024f
                        )
                    }
                    val activeWidth =
                        if (activeIsMosaic) {
                            mosaicWidthFrac
                        } else {
                            drawWidthFrac
                        }
                    for ((label, wf) in widths) {
                        Box(
                            modifier = Modifier
                                .background(
                                    if (activeWidth == wf) {
                                        MaterialTheme.colorScheme
                                            .primary
                                    } else {
                                        MaterialTheme.colorScheme
                                            .surfaceVariant
                                    },
                                    RoundedCornerShape(50)
                                )
                                .clickable {
                                    if (activeIsMosaic) {
                                        mosaicWidthFrac = wf
                                    } else {
                                        drawWidthFrac = wf
                                    }
                                }
                                .padding(
                                    horizontal = 12.dp, vertical = 6.dp
                                )
                        ) {
                            Text(
                                label, fontSize = 13.sp,
                                color = if (activeWidth == wf) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme
                                        .onSurfaceVariant
                                }
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                    }
                }
            }
            EditMode.ADJUST -> Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 10.dp)
            ) {
                // Filter presets (v0.9.31, X1): one tap
                // applies the preset matrix; the sliders
                // below compose after it (fixed order, see
                // editorColorMatrix).
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("滤镜", fontSize = 13.5.sp)
                    Spacer(Modifier.width(8.dp))
                    for ((key, label) in filterPresets) {
                        Box(
                            modifier = Modifier
                                .background(
                                    if (filterKey == key) {
                                        MaterialTheme.colorScheme
                                            .primary
                                    } else {
                                        MaterialTheme.colorScheme
                                            .surfaceVariant
                                    },
                                    RoundedCornerShape(50)
                                )
                                .clickable { filterKey = key }
                                .padding(
                                    horizontal = 11.dp, vertical = 6.dp
                                )
                        ) {
                            Text(
                                label, fontSize = 13.sp,
                                color = if (filterKey == key) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme
                                        .onSurfaceVariant
                                }
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                    }
                }
                for ((label, value, range, onChange) in listOf(
                    AdjustSlider(
                        "亮度", brightness, -0.25f..0.25f
                    ) { brightness = it },
                    AdjustSlider(
                        "对比度", contrast, 0.5f..1.5f
                    ) { contrast = it },
                    AdjustSlider(
                        "饱和度", saturation, 0f..2f
                    ) { saturation = it }
                )) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            label, fontSize = 13.5.sp,
                            modifier = Modifier.width(52.dp)
                        )
                        Slider(
                            value = value,
                            onValueChange = onChange,
                            valueRange = range,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "${(value * 100).roundToInt()}",
                            fontSize = 12.sp, color = Color.Gray,
                            modifier = Modifier.width(36.dp)
                        )
                    }
                }
                Row {
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = {
                        filterKey = "none"
                        brightness = 0f
                        contrast = 1f
                        saturation = 1f
                    }) {
                        Text("重置调色")
                    }
                }
            }
            EditMode.TEXT -> Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(onClick = {
                    textDialogValue = ""
                    textDialogFor = -1
                }) {
                    Text("添加文字")
                }
                val selected = texts.firstOrNull {
                    it.id == selectedTextId
                }
                if (selected != null) {
                    Spacer(Modifier.width(4.dp))
                    TextButton(onClick = {
                        textDialogValue = selected.text
                        textDialogFor = selected.id
                    }) { Text("改字") }
                    TextButton(onClick = {
                        val idx = editorPalette.indexOf(selected.color)
                        val next = editorPalette[
                            (idx + 1) % editorPalette.size
                        ]
                        texts = texts.map {
                            if (it.id == selected.id) {
                                it.copy(color = next)
                            } else {
                                it
                            }
                        }
                    }) { Text("换颜色") }
                    TextButton(onClick = {
                        texts = texts.filter { it.id != selected.id }
                        selectedTextId = null
                    }) { Text("删除") }
                } else {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "点一个文字可以拖动它、改字或换颜色",
                        fontSize = 12.5.sp, color = Color.Gray
                    )
                }
            }
            EditMode.STICKER -> Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 10.dp)
            ) {
                // Sticker picker (Y1): the chat panel's
                // catalogs — recent, favorites and every
                // installed set. Tapping one places it in
                // the middle of the photo.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { pickerSet = null }) {
                            Text(
                                "最近",
                                fontWeight = if (pickerSet == null) {
                                    FontWeight.SemiBold
                                } else {
                                    FontWeight.Normal
                                }
                            )
                        }
                        TextButton(onClick = { pickerSet = -1L }) {
                            Text(
                                "收藏",
                                fontWeight = if (pickerSet == -1L) {
                                    FontWeight.SemiBold
                                } else {
                                    FontWeight.Normal
                                }
                            )
                        }
                        for (set in stickerSets) {
                            TextButton(onClick = {
                                pickerSet = set.id
                                MediaStore.loadSet(set.id)
                            }) {
                                Text(
                                    set.title,
                                    maxLines = 1,
                                    fontWeight = if (
                                        pickerSet == set.id
                                    ) {
                                        FontWeight.SemiBold
                                    } else {
                                        FontWeight.Normal
                                    }
                                )
                            }
                        }
                    }
                    val selSticker = placedStickers.firstOrNull {
                        it.id == selectedStickerId
                    }
                    if (selSticker != null) {
                        TextButton(onClick = {
                            placedStickers = placedStickers.filter {
                                it.id != selSticker.id
                            }
                            selectedStickerId = null
                        }) {
                            Text(
                                "删除选中的贴纸",
                                color = Color(0xFFC62828)
                            )
                        }
                    }
                }
                val pickerEntries = when (pickerSet) {
                    null -> recentStickers
                    -1L -> favoriteStickers
                    else -> setStickers
                }
                if (pickerEntries.isEmpty()) {
                    Text(
                        "这个分类里还没有贴纸，去聊天里用过贴纸后这里就会有。",
                        fontSize = 12.5.sp, color = Color.Gray,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(5),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                    ) {
                        items(pickerEntries, key = { it.fileId }) {
                                entry ->
                            StickerPickerCell(entry) {
                                placeSticker(entry)
                            }
                        }
                    }
                }
                Text(
                    "点贴纸放上照片；点已放的贴纸选中它，拖动移动、双指缩放和旋转。",
                    fontSize = 12.5.sp, color = Color.Gray,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }

    val dialogFor = textDialogFor
    if (dialogFor != null) {
        AlertDialog(
            onDismissRequest = { textDialogFor = null },
            title = {
                Text(if (dialogFor == -1) "添加文字" else "修改文字")
            },
            text = {
                OutlinedTextField(
                    value = textDialogValue,
                    onValueChange = { textDialogValue = it },
                    label = { Text("写点什么") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    enabled = textDialogValue.isNotBlank(),
                    onClick = {
                        val value = textDialogValue.trim()
                        if (dialogFor == -1) {
                            val id = nextTextId++
                            texts = texts + TextData(
                                id = id,
                                text = value,
                                x = 0.5f, y = 0.5f,
                                color = Color.White,
                                sizeFrac = 0.07f
                            )
                            selectedTextId = id
                        } else {
                            texts = texts.map {
                                if (it.id == dialogFor) {
                                    it.copy(text = value)
                                } else {
                                    it
                                }
                            }
                        }
                        textDialogFor = null
                    }
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { textDialogFor = null }) {
                    Text("取消")
                }
            }
        )
    }
}

/** One cell of the editor's sticker picker (Y1): the
 *  sticker's own image when it is a static webp, its
 *  static thumbnail for animated/video stickers, and
 *  the emoji as the loading fallback — the same display
 *  rule the bake uses. */
@Composable
private fun StickerPickerCell(
    entry: MediaEntry,
    onClick: () -> Unit
) {
    val isWebp = entry.format == "webp" || entry.format.isEmpty()
    val path = if (isWebp) {
        entry.previewPath
    } else {
        entry.thumbPath ?: entry.previewPath
    }
    val bitmap by produceState<Bitmap?>(
        initialValue = null, path
    ) {
        value = if (path == null) {
            null
        } else {
            withContext(Dispatchers.IO) { decodeCached(path) }
        }
    }
    Box(
        modifier = Modifier
            .size(56.dp)
            .clickable { onClick() }
            .padding(5.dp),
        contentAlignment = Alignment.Center
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = entry.emoji,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                entry.emoji.ifEmpty { "…" },
                fontSize = 26.sp
            )
        }
    }
}

/** Corner-drag geometry: the dragged corner follows the
 *  pointer, the opposite corner anchors. With an aspect
 *  ratio locked, the size follows the pointer while the
 *  ratio is preserved; both clamp to the image and to a
 *  sane minimum. All in normalized coordinates, sizes
 *  computed in pixels via [imgW]/[imgH]. */
private fun dragCropCorner(
    start: CropRect,
    corner: Int,
    nx: Float,
    ny: Float,
    ratio: Float?,
    imgW: Float,
    imgH: Float
): CropRect {
    // Anchor = the opposite corner.
    val (ax, ay) = when (corner) {
        0 -> start.r to start.b
        1 -> start.l to start.b
        2 -> start.r to start.t
        else -> start.l to start.t
    }
    val px = nx.coerceIn(0f, 1f)
    val py = ny.coerceIn(0f, 1f)
    var wPx = abs(px - ax) * imgW
    var hPx = abs(py - ay) * imgH
    val minPx = 0.08f * min(imgW, imgH)
    if (ratio != null) {
        // Grow from the width, then fit the height.
        hPx = wPx / ratio
        val maxH = (if (py >= ay) 1f - ay else ay) * imgH
        if (hPx > maxH) {
            hPx = maxH
            wPx = hPx * ratio
        }
        if (wPx < minPx) {
            wPx = minPx
            hPx = wPx / ratio
        }
    } else {
        wPx = max(wPx, minPx)
        hPx = max(hPx, minPx)
    }
    val dirX = if (px >= ax) 1f else -1f
    val dirY = if (py >= ay) 1f else -1f
    var x2 = ax + dirX * (wPx / imgW)
    var y2 = ay + dirY * (hPx / imgH)
    x2 = x2.coerceIn(0f, 1f)
    y2 = y2.coerceIn(0f, 1f)
    return CropRect(
        l = min(ax, x2), t = min(ay, y2),
        r = max(ax, x2), b = max(ay, y2)
    )
}

/** Compose the editor's color matrix (v0.9.31, X1) in a
 *  FIXED order: the filter preset first, then the
 *  saturation slider, then contrast + brightness. Preview
 *  and export share this builder, so the order can never
 *  drift between what is shown and what is sent. */
private fun editorColorMatrix(
    filterKey: String,
    brightness: Float,
    contrast: Float,
    saturation: Float
): ColorMatrix {
    val m = ColorMatrix()
    when (filterKey) {
        "mono" -> m.setSaturation(0f)
        "sepia" -> m.set(
            floatArrayOf(
                0.393f, 0.769f, 0.189f, 0f, 0f,
                0.349f, 0.686f, 0.168f, 0f, 0f,
                0.272f, 0.534f, 0.131f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        "vivid" -> m.setSaturation(1.6f)
        "cool" -> m.set(
            floatArrayOf(
                0.90f, 0f, 0f, 0f, 0f,
                0f, 1.0f, 0f, 0f, 0f,
                0f, 0f, 1.15f, 0f, 6f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        "warm" -> m.set(
            floatArrayOf(
                1.12f, 0f, 0f, 0f, 6f,
                0f, 1.02f, 0f, 0f, 0f,
                0f, 0f, 0.88f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )
        )
    }
    val sat = ColorMatrix()
    sat.setSaturation(saturation)
    m.postConcat(sat)
    val t = brightness * 255f
    val cb = ColorMatrix(
        floatArrayOf(
            contrast, 0f, 0f, 0f, t,
            0f, contrast, 0f, 0f, t,
            0f, 0f, contrast, 0f, t,
            0f, 0f, 0f, 1f, 0f
        )
    )
    m.postConcat(cb)
    return m
}

/** Bake the color grade and the mosaic onto a copy of
 *  [src] (v0.9.31, X1): the preview's bitmap. The export
 *  runs the same two steps on the cropped region. */
private fun bakeColorAndMosaic(
    src: Bitmap,
    matrix: ColorMatrix,
    mosaicStrokes: List<StrokeData>
): Bitmap {
    val out = Bitmap.createBitmap(
        src.width, src.height, Bitmap.Config.ARGB_8888
    )
    val canvas = AndroidCanvas(out)
    canvas.drawBitmap(
        src, 0f, 0f,
        Paint().apply {
            colorFilter = ColorMatrixColorFilter(matrix)
        }
    )
    applyMosaicInPlace(out, mosaicStrokes, src.width, src.height, 0, 0)
    return out
}

/** Pixelate the regions [strokes] cover, in place
 *  (v0.9.31, X1). Stroke points are normalized to the
 *  FULL image ([fullW] x [fullH]); [offX]/[offY] shift
 *  them into the target bitmap's frame (the crop offset
 *  at export, 0 in the preview bake). Method: rasterize
 *  the strokes into an alpha mask, build a blocky
 *  downscale/upscale copy of the image, and stamp it
 *  back through the mask (DST_IN). Cell size follows the
 *  widest stroke, so a thicker pen makes bigger blocks. */
private fun applyMosaicInPlace(
    img: Bitmap,
    strokes: List<StrokeData>,
    fullW: Int,
    fullH: Int,
    offX: Int,
    offY: Int
) {
    if (strokes.isEmpty()) return
    val w = img.width
    val h = img.height
    val mask = Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8)
    val maskCanvas = AndroidCanvas(mask)
    for (stroke in strokes) {
        if (stroke.points.isEmpty()) continue
        val paint = Paint().apply {
            color = android.graphics.Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = stroke.widthFrac * fullW
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            isAntiAlias = true
        }
        if (stroke.points.size == 1) {
            paint.style = Paint.Style.FILL
            maskCanvas.drawCircle(
                stroke.points[0].x * fullW - offX,
                stroke.points[0].y * fullH - offY,
                paint.strokeWidth / 2f, paint
            )
        } else {
            val path = android.graphics.Path()
            path.moveTo(
                stroke.points[0].x * fullW - offX,
                stroke.points[0].y * fullH - offY
            )
            for (pt in stroke.points.drop(1)) {
                path.lineTo(pt.x * fullW - offX, pt.y * fullH - offY)
            }
            maskCanvas.drawPath(path, paint)
        }
    }
    val cell = (strokes.maxOf { it.widthFrac } * fullW * 0.8f)
        .toInt().coerceIn(6, 80)
    val small = Bitmap.createScaledBitmap(
        img, max(1, w / cell), max(1, h / cell), false
    )
    val blocky = Bitmap.createScaledBitmap(small, w, h, false)
    val tmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val tmpCanvas = AndroidCanvas(tmp)
    tmpCanvas.drawBitmap(blocky, 0f, 0f, null)
    tmpCanvas.drawBitmap(
        mask, 0f, 0f,
        Paint().apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        }
    )
    AndroidCanvas(img).drawBitmap(tmp, 0f, 0f, null)
}

/** Render the edit onto a fresh bitmap in the FIXED
 *  order: crop the rotated base, apply the color grade,
 *  stamp the mosaic, paint the strokes, bake the placed
 *  stickers, paint the texts, save JPEG. Returns the
 *  output path, or null on any failure (the caller then
 *  keeps the user in the editor). */
private fun exportEdited(
    base: Bitmap,
    crop: CropRect,
    strokes: List<StrokeData>,
    texts: List<TextData>,
    matrix: ColorMatrix,
    mosaicStrokes: List<StrokeData>,
    stickers: List<PlacedSticker>,
    stickerBitmaps: Map<Int, Bitmap>,
    outFile: File
): String? {
    val w = base.width
    val h = base.height
    val l = (crop.l * w).toInt().coerceIn(0, w - 1)
    val t = (crop.t * h).toInt().coerceIn(0, h - 1)
    val r = (crop.r * w).toInt().coerceIn(l + 1, w)
    val b = (crop.b * h).toInt().coerceIn(t + 1, h)
    val cropped = Bitmap.createBitmap(base, l, t, r - l, b - t)
    val out = Bitmap.createBitmap(
        r - l, b - t, Bitmap.Config.ARGB_8888
    )
    val canvas = AndroidCanvas(out)
    canvas.drawBitmap(
        cropped, 0f, 0f,
        Paint().apply {
            colorFilter = ColorMatrixColorFilter(matrix)
        }
    )
    applyMosaicInPlace(out, mosaicStrokes, w, h, l, t)
    for (stroke in strokes) {
        if (stroke.points.isEmpty()) continue
        val paint = Paint().apply {
            color = stroke.color.toArgb()
            style = Paint.Style.STROKE
            strokeWidth = stroke.widthFrac * w
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            isAntiAlias = true
        }
        if (stroke.points.size == 1) {
            paint.style = Paint.Style.FILL
            canvas.drawCircle(
                stroke.points[0].x * w - l,
                stroke.points[0].y * h - t,
                paint.strokeWidth / 2f, paint
            )
        } else {
            val path = android.graphics.Path()
            path.moveTo(
                stroke.points[0].x * w - l,
                stroke.points[0].y * h - t
            )
            for (pt in stroke.points.drop(1)) {
                path.lineTo(pt.x * w - l, pt.y * h - t)
            }
            canvas.drawPath(path, paint)
        }
    }
    // Placed stickers (Y1), baked after the strokes and
    // before the texts — the same order the preview
    // composes. Each sticker is centred on its normalized
    // point, scaled to scaleFrac of the full image width
    // and rotated around its own centre.
    for (sticker in stickers) {
        val sb = stickerBitmaps[sticker.fileId] ?: continue
        if (sb.width <= 0 || sb.height <= 0) continue
        val targetW = sticker.scaleFrac * w
        val scale = targetW / sb.width.toFloat()
        val m = Matrix()
        m.postTranslate(-sb.width / 2f, -sb.height / 2f)
        m.postScale(scale, scale)
        m.postRotate(sticker.rotation)
        m.postTranslate(sticker.x * w - l, sticker.y * h - t)
        canvas.drawBitmap(
            sb, m,
            Paint().apply {
                isAntiAlias = true
                isFilterBitmap = true
            }
        )
    }
    for (label in texts) {
        val paint = Paint().apply {
            color = label.color.toArgb()
            textSize = label.sizeFrac * w
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
            setShadowLayer(4f, 0f, 2f, android.graphics.Color.BLACK)
        }
        val cx = label.x * w - l
        val cy = label.y * h - t
        val fm = paint.fontMetrics
        val baseline = cy - (fm.ascent + fm.descent) / 2f
        canvas.drawText(label.text, cx, baseline, paint)
    }
    FileOutputStream(outFile).use { stream ->
        out.compress(Bitmap.CompressFormat.JPEG, 92, stream)
    }
    return outFile.absolutePath
}
