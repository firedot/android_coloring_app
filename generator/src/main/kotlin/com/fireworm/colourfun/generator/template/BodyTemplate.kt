package com.fireworm.colourfun.generator.template

/**
 * Where a part goes. A slot is filled with a part of [category] and attached at its parent's
 * [anchor]. A slot with [mirrorOf] gets the same part and scale as that slot, mirrored left-right
 * around its parent's centre line, at the mirrored anchor.
 */
data class Slot(
    val name: String,
    val category: String,
    val parent: String? = null,
    val anchor: String? = null,
    val mirrorOf: String? = null,
    val optional: Boolean = false,
)

/**
 * The layout of one kind of animal. Slots are listed parents first.
 *
 * @param headToBody allowed head width ÷ body width.
 * @param strokeWidth outline width in base document pixels (≥ 10).
 */
data class BodyTemplate(
    val id: String,
    val slots: List<Slot>,
    val headToBody: ClosedFloatingPointRange<Float>,
    val strokeWidth: Float,
) {
    init {
        val names = mutableSetOf<String>()
        for (slot in slots) {
            slot.parent?.let { require(it in names) { "Slot ${slot.name}: parent $it must come first" } }
            slot.mirrorOf?.let { require(it in names) { "Slot ${slot.name}: mirrorOf $it must come first" } }
            require(names.add(slot.name)) { "Duplicate slot ${slot.name}" }
        }
        require(strokeWidth >= 10f) { "Outlines must be at least 10 px wide" }
    }
}

/** A four-legged animal sitting and facing the child: body, head, ears, eyes, nose, paws and tail. */
val QuadrupedTemplate = BodyTemplate(
    id = "quadruped",
    slots = listOf(
        Slot("body", "body"),
        Slot("tail", "tail", parent = "body", anchor = "tail"),
        Slot("leg_left", "leg", parent = "body", anchor = "leg_left"),
        Slot("leg_right", "leg", parent = "body", anchor = "leg_left", mirrorOf = "leg_left"),
        Slot("head", "head", parent = "body", anchor = "head"),
        Slot("ear_left", "ear", parent = "head", anchor = "ear_left"),
        Slot("ear_right", "ear", parent = "head", anchor = "ear_left", mirrorOf = "ear_left"),
        Slot("eye_left", "eye", parent = "head", anchor = "eye_left"),
        Slot("eye_right", "eye", parent = "head", anchor = "eye_left", mirrorOf = "eye_left"),
        Slot("nose", "nose", parent = "head", anchor = "nose"),
    ),
    headToBody = 0.45f..0.7f,
    strokeWidth = 12f,
)
