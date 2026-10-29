package com.fireworm.colourfun.generator

import com.fireworm.colourfun.generator.geometry.PathCommand
import com.fireworm.colourfun.generator.geometry.PathDataParser
import com.fireworm.colourfun.generator.geometry.PathShape
import java.math.BigDecimal
import java.math.RoundingMode
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * [LineArt] as JSON, with each shape as SVG path data rounded to 0.01 px. Used for saved pictures
 * (`outline.json`) and generator goldens, so the text must be identical on every device.
 */
object LineArtCodec {
    @Serializable
    private data class Shape(val d: String, val occludes: Boolean = true)

    @Serializable
    private data class File(
        val width: Int,
        val height: Int,
        val strokeWidth: Float,
        val seed: Long,
        val generatorVersion: Int,
        val shapes: List<Shape>,
    )

    private val json = Json { prettyPrint = true }

    fun encode(art: LineArt): String = json.encodeToString(
        File.serializer(),
        File(
            width = art.width,
            height = art.height,
            strokeWidth = art.strokeWidth,
            seed = art.seed,
            generatorVersion = art.generatorVersion,
            shapes = art.shapes.map { Shape(pathData(it.path), it.occludes) },
        ),
    )

    fun decode(text: String): LineArt {
        val file = json.decodeFromString(File.serializer(), text)
        return LineArt(
            width = file.width,
            height = file.height,
            shapes = file.shapes.map { OutlineShape(PathDataParser.parse(it.d), it.occludes) },
            strokeWidth = file.strokeWidth,
            seed = file.seed,
            generatorVersion = file.generatorVersion,
        )
    }

    fun pathData(path: PathShape): String = path.commands.joinToString(" ") { command ->
        when (command) {
            is PathCommand.MoveTo -> "M${n(command.p.x)},${n(command.p.y)}"
            is PathCommand.LineTo -> "L${n(command.p.x)},${n(command.p.y)}"
            is PathCommand.CubicTo -> "C${n(command.c1.x)},${n(command.c1.y)} ${n(command.c2.x)},${n(command.c2.y)} ${n(command.p.x)},${n(command.p.y)}"
            PathCommand.Close -> "Z"
        }
    }

    /** Locale-independent, at most two decimals, no trailing zeros. */
    private fun n(value: Float): String =
        BigDecimal(value.toDouble()).setScale(2, RoundingMode.HALF_EVEN).stripTrailingZeros().toPlainString()
            .let { if (it == "-0") "0" else it }
}
