package com.fireworm.colourfun.generator

import com.fireworm.colourfun.generator.geometry.PathShape

/** Black line art for one picture, in the 2048×1536 base document space. */
data class LineArt(
    val width: Int,
    val height: Int,
    /** Back-to-front order. */
    val shapes: List<OutlineShape>,
    /** Document pixels; at least 10 so lines are thick and never leak. */
    val strokeWidth: Float,
    val seed: Long,
    val generatorVersion: Int,
)

/** A closed outline. If [occludes], it hides the lines of shapes behind it. */
data class OutlineShape(val path: PathShape, val occludes: Boolean = true)
