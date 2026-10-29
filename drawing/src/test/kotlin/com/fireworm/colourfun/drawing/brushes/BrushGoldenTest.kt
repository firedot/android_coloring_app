package com.fireworm.colourfun.drawing.brushes

import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.ThresholdValidator
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * Golden images of every medium × size drawing [FixedStroke] with a fixed seed.
 * Record with `./gradlew :drawing:recordRoborazziDebug`; `./gradlew build` verifies them.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BrushGoldenTest {

    @Test fun pencil() = goldens(Medium.Pencil)

    @Test fun pen() = goldens(Medium.Pen)

    @Test fun chalk() = goldens(Medium.Chalk)

    @Test fun spray() = goldens(Medium.Spray)

    @Test fun brush() = goldens(Medium.Brush)

    @Test fun paintBrush() = goldens(Medium.PaintBrush)

    @Test fun magicPen() = goldens(Medium.MagicPen)

    @Test fun eraser() = goldens(Medium.Eraser)

    private fun goldens(medium: Medium) {
        BrushSize.entries.forEach { size ->
            val engine = FixedStroke.engine()
            if (medium in OverPaint) FixedStroke.paintBands(engine)
            FixedStroke.draw(engine, BrushSpec(medium, size), COLOR)
            engine.endStroke()
            FixedStroke.render(engine).captureRoboImage(
                filePath = "src/test/screenshots/${medium.name.lowercase()}_${size.name.lowercase()}.png",
                roborazziOptions = OPTIONS,
            )
        }
    }

    private companion object {
        const val COLOR = 0xFF1565C0.toInt()

        /** Mediums that only show against existing paint. */
        val OverPaint = setOf(Medium.PaintBrush, Medium.Eraser)

        /** Tolerates anti-aliasing differences between the machines that record and verify. */
        val OPTIONS = RoborazziOptions(
            compareOptions = RoborazziOptions.CompareOptions(resultValidator = ThresholdValidator(0.001f)),
        )
    }
}
