package com.example.colourfun.drawing.layers

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import androidx.core.graphics.createBitmap
import com.example.colourfun.drawing.document.DocumentSize
import com.example.colourfun.generator.LineArt
import com.example.colourfun.generator.geometry.PathCommand
import com.example.colourfun.generator.geometry.PathShape

/**
 * Renders [LineArt] into an ALPHA_8 outline bitmap that contains only the visible lines.
 *
 * Shapes are drawn back to front. Each occluding shape first clears its interior, erasing the
 * lines of shapes behind it, then strokes its own outline.
 *
 * Blocking and side-effect free: call it off the main thread.
 */
object OutlineRasterizer {

    fun rasterize(lineArt: LineArt, size: DocumentSize): Bitmap {
        require(lineArt.width.toLong() * size.height == lineArt.height.toLong() * size.width) {
            "Line art ${lineArt.width}×${lineArt.height} does not match the document aspect ratio"
        }
        val bitmap = createBitmap(size.width, size.height, Bitmap.Config.ALPHA_8)
        val canvas = Canvas(bitmap)
        val scale = size.width / lineArt.width.toFloat()

        val clear = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = lineArt.strokeWidth * scale
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        for (shape in lineArt.shapes) {
            val path = shape.path.toAndroidPath(scale)
            if (shape.occludes) canvas.drawPath(path, clear)
            canvas.drawPath(path, stroke)
        }
        return bitmap
    }

    private fun PathShape.toAndroidPath(scale: Float): Path {
        val path = Path()
        for (command in commands) {
            when (command) {
                is PathCommand.MoveTo -> path.moveTo(command.p.x * scale, command.p.y * scale)
                is PathCommand.LineTo -> path.lineTo(command.p.x * scale, command.p.y * scale)
                is PathCommand.CubicTo -> path.cubicTo(
                    command.c1.x * scale, command.c1.y * scale,
                    command.c2.x * scale, command.c2.y * scale,
                    command.p.x * scale, command.p.y * scale,
                )
                PathCommand.Close -> path.close()
            }
        }
        return path
    }
}
