package com.fireworm.colourfun.generator.parts

import com.fireworm.colourfun.generator.geometry.Bounds
import com.fireworm.colourfun.generator.geometry.PathDataParser
import com.fireworm.colourfun.generator.geometry.PathShape
import com.fireworm.colourfun.generator.geometry.Vec2
import kotlinx.serialization.Serializable

/**
 * One part file in `assets/parts/`. Coordinates are design units with the origin where the part
 * attaches to its parent; y grows downwards.
 *
 * @property paths SVG path data, back to front; every sub-path must be closed.
 * @property anchors named attachment points for child slots, as `[x, y]`. Paired slots use the left
 * anchor; the right one is mirrored.
 * @property zOrder drawing order across the whole animal; lower is further back.
 * @property mirrorable an unpaired part that may be flipped left-right for variety.
 * @property scaleRange random scale relative to the parent part, `[min, max]`.
 */
@Serializable
data class PartJson(
    val id: String,
    val category: String,
    val compatibleWith: List<String>,
    val paths: List<String>,
    val anchors: Map<String, List<Float>> = emptyMap(),
    val zOrder: Int,
    val mirrorable: Boolean = false,
    val scaleRange: List<Float> = listOf(1f, 1f),
)

/** A parsed, validated part. */
class Part(json: PartJson) {
    val id = json.id
    val category = json.category
    val compatibleWith = json.compatibleWith.toSet()
    val paths: List<PathShape> = json.paths.map(PathDataParser::parse)
    val anchors: Map<String, Vec2>
    val zOrder = json.zOrder
    val mirrorable = json.mirrorable
    val minScale: Float
    val maxScale: Float

    /** Bounds of all paths in design units. */
    val bounds: Bounds

    init {
        require(paths.isNotEmpty()) { "Part $id has no paths" }
        paths.forEachIndexed { i, path -> require(path.isClosed) { "Part $id path $i is not closed" } }
        anchors = json.anchors.mapValues { (name, xy) ->
            require(xy.size == 2) { "Part $id anchor $name must be [x, y]" }
            Vec2(xy[0], xy[1])
        }
        require(json.scaleRange.size == 2 && json.scaleRange[0] in 0.01f..json.scaleRange[1]) {
            "Part $id scaleRange must be [min, max] with 0 < min <= max"
        }
        minScale = json.scaleRange[0]
        maxScale = json.scaleRange[1]
        bounds = paths.map { it.bounds() }.reduce(Bounds::union)
    }
}
