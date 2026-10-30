package com.shelfsweep.app

import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/** Live camera preview (4:3, portrait) with a frame watcher that decides when to scan. */
@Composable
fun CameraPreview(vm: AppViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    @Suppress("DEPRECATION")
    val owner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FIT_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    DisposableEffect(owner) {
        val future = ProcessCameraProvider.getInstance(context)
        val exec = Executors.newSingleThreadExecutor()
        var provider: ProcessCameraProvider? = null
        future.addListener({
            try {
                val p = future.get()
                provider = p
                val selector = ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                    .setResolutionStrategy(ResolutionStrategy(Size(1280, 960), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                    .build()
                val preview = Preview.Builder().setResolutionSelector(selector).build()
                preview.setSurfaceProvider(previewView.surfaceProvider)
                val analysis = ImageAnalysis.Builder()
                    .setResolutionSelector(selector)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(exec, SceneWatcher(vm))
                p.unbindAll()
                p.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            } catch (e: Exception) {
                vm.banner = "The camera couldn't start."
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            runCatching { provider?.unbindAll() }
            exec.shutdown()
        }
    }
    AndroidView(factory = { previewView }, modifier = modifier)
}

/**
 * Watches frames: when the phone is held steady on a view that differs from the last scan,
 * grabs that frame and hands it to the view model. A tap on the shutter forces a capture.
 */
class SceneWatcher(private val vm: AppViewModel) : ImageAnalysis.Analyzer {
    private var prev: IntArray? = null
    private var steady = 0
    private var lastAt = 0L

    override fun analyze(image: ImageProxy) {
        try {
            val now = System.currentTimeMillis()
            if (now - lastAt < 90) return
            lastAt = now
            val g = grid(image)
            val p = prev
            prev = g
            val motion = if (p == null) 255.0 else diff(g, p)
            steady = if (motion < 7.0) steady + 1 else 0
            val last = vm.lastScanGrid
            val change = if (last == null) 255.0 else diff(g, last)
            if (change > 26.0) vm.onViewChanged()

            val manual = vm.consumeCaptureRequest()
            val s = vm.settings
            val auto = s.live && vm.closeUpFor == null && vm.tab == "scan" && vm.deck == null &&
                !vm.showSettings && vm.viewShot == null &&
                steady >= 6 && change > 18.0 && vm.canAutoScan() && now - vm.lastScanAt > 2500
            if (manual || auto) {
                val (jpeg, aspect) = toJpeg(image.toBitmap(), image.imageInfo.rotationDegrees)
                steady = 0
                vm.onFrameCaptured(jpeg, aspect, g)
            }
        } catch (_: Exception) {
        } finally {
            image.close()
        }
    }

    private fun grid(image: ImageProxy): IntArray {
        val plane = image.planes[0]
        val buf = plane.buffer
        val rs = plane.rowStride
        val ps = plane.pixelStride
        val w = image.width
        val h = image.height
        val out = IntArray(32 * 24)
        for (j in 0 until 24) for (i in 0 until 32) {
            val x = i * w / 32 + w / 64
            val y = j * h / 24 + h / 48
            out[j * 32 + i] = buf.get(y * rs + x * ps).toInt() and 0xff
        }
        return out
    }

    private fun diff(a: IntArray, b: IntArray): Double {
        var t = 0L
        for (i in a.indices) t += abs(a[i] - b[i])
        return t.toDouble() / a.size
    }

    private fun toJpeg(src: Bitmap, rotation: Int): Pair<ByteArray, Float> {
        val m = Matrix()
        if (rotation != 0) m.postRotate(rotation.toFloat())
        val rotated = Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
        val k = 1280f / max(rotated.width, rotated.height)
        val out = if (k < 1f) Bitmap.createScaledBitmap(rotated, (rotated.width * k).roundToInt(), (rotated.height * k).roundToInt(), true) else rotated
        val bos = ByteArrayOutputStream()
        out.compress(Bitmap.CompressFormat.JPEG, 82, bos)
        return bos.toByteArray() to (out.width.toFloat() / out.height)
    }
}
