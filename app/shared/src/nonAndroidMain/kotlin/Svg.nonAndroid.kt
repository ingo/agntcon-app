package org.jetbrains.kotlinconf.screens

import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.skiaCanvas
import io.ktor.utils.io.core.toByteArray
import org.jetbrains.kotlinconf.event.pngSize
import org.jetbrains.kotlinconf.event.rasterMapPng
import org.jetbrains.skia.Data
import org.jetbrains.skia.Image
import org.jetbrains.skia.svg.SVGDOM

actual class Svg actual constructor(svgString: String) {
    // A bundled floor plan is a PNG; Skia's SVG renderer doesn't draw images embedded in an SVG
    private val raster: Image? = rasterMapPng(svgString)?.let { Image.makeFromEncoded(it) }
    private val rasterSize = rasterMapPng(svgString)?.let { pngSize(it) }
    private val svg = if (raster == null) SVGDOM(Data.makeFromBytes(svgString.toByteArray())) else null

    actual val width: Float get() = rasterSize?.first?.toFloat() ?: svg?.root?.width?.value ?: 0f
    actual val height: Float get() = rasterSize?.second?.toFloat() ?: svg?.root?.height?.value ?: 0f

    actual fun renderTo(scope: DrawScope) {
        scope.drawIntoCanvas { canvas ->
            if (raster != null) canvas.skiaCanvas.drawImage(raster, 0f, 0f) else svg?.render(canvas.skiaCanvas)
        }
    }
}
