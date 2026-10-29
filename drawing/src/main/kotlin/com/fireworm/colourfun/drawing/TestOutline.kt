package com.fireworm.colourfun.drawing

import com.fireworm.colourfun.drawing.document.DocumentSize
import com.fireworm.colourfun.generator.LineArt
import com.fireworm.colourfun.generator.OutlineShape
import com.fireworm.colourfun.generator.geometry.PathShape
import com.fireworm.colourfun.generator.geometry.Vec2

/**
 * Hand-written outline used until the animal generator exists: a body oval and two ears, with a
 * head circle drawn on top that overlaps the body and both ears.
 */
object TestOutline {
    val BODY_CENTER = Vec2(1024f, 1000f)
    const val BODY_RX = 520f
    const val BODY_RY = 340f

    val HEAD_CENTER = Vec2(1024f, 560f)
    const val HEAD_RADIUS = 280f

    val LEFT_EAR_CENTER = Vec2(830f, 330f)
    val RIGHT_EAR_CENTER = Vec2(1218f, 330f)
    const val EAR_RX = 110f
    const val EAR_RY = 150f

    const val STROKE_WIDTH = 14f

    val lineArt = LineArt(
        width = DocumentSize.BASE_WIDTH,
        height = DocumentSize.BASE_HEIGHT,
        shapes = listOf(
            OutlineShape(PathShape.ellipse(BODY_CENTER, BODY_RX, BODY_RY)),
            OutlineShape(PathShape.ellipse(LEFT_EAR_CENTER, EAR_RX, EAR_RY)),
            OutlineShape(PathShape.ellipse(RIGHT_EAR_CENTER, EAR_RX, EAR_RY)),
            OutlineShape(PathShape.ellipse(HEAD_CENTER, HEAD_RADIUS, HEAD_RADIUS)),
        ),
        strokeWidth = STROKE_WIDTH,
        seed = 0L,
        generatorVersion = 0,
    )
}
