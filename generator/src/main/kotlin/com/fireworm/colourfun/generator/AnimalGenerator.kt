package com.fireworm.colourfun.generator

import com.fireworm.colourfun.generator.geometry.Bounds
import com.fireworm.colourfun.generator.geometry.PathShape
import com.fireworm.colourfun.generator.geometry.Transform
import com.fireworm.colourfun.generator.geometry.Vec2
import com.fireworm.colourfun.generator.parts.Part
import com.fireworm.colourfun.generator.parts.PartLibrary
import com.fireworm.colourfun.generator.random.Pcg32
import com.fireworm.colourfun.generator.template.BodyTemplate
import com.fireworm.colourfun.generator.template.QuadrupedTemplate
import com.fireworm.colourfun.generator.validation.LineArtValidator

/**
 * Turns a seed into an animal colouring page. Pure and deterministic: the same seed, parts and
 * [GENERATOR_VERSION] always give the same [LineArt].
 *
 * Each attempt fills the template's slots with random compatible parts, varies their proportions
 * and fits the animal into the document. Attempt `n` uses `Pcg32(seed, stream = n)`; after
 * [MAX_ATTEMPTS] invalid attempts it falls back to a curated seed known to be good.
 */
class AnimalGenerator(
    private val library: PartLibrary,
    private val template: BodyTemplate = QuadrupedTemplate,
    private val validator: LineArtValidator = LineArtValidator(),
) {
    fun generate(seed: Long): LineArt = generateWithAttempt(seed).first

    /** The picture and the attempt that produced it, or `null` when it is the curated fallback. */
    internal fun generateWithAttempt(seed: Long): Pair<LineArt, Int?> {
        for (attempt in 0 until MAX_ATTEMPTS) {
            val art = compose(seed, Pcg32(seed, stream = attempt.toLong())) ?: continue
            if (validator.problem(art) == null) return art to attempt
        }
        val fallback = checkNotNull(compose(FALLBACK_SEED, Pcg32(FALLBACK_SEED, stream = 0))) { "Fallback seed is invalid" }
        return fallback.copy(seed = seed) to null
    }

    private class Placed(val part: Part, val transform: Transform, val relativeScale: Float, val mirrored: Boolean)

    /** One attempt, or `null` when the chosen parts cannot meet the template's proportions. */
    internal fun compose(seed: Long, random: Pcg32): LineArt? {
        val placed = LinkedHashMap<String, Placed>()
        for (slot in template.slots) {
            val twin = slot.mirrorOf?.let { placed[it] ?: continue }
            val parent = slot.parent?.let { placed[it] ?: continue }

            val part: Part
            val relativeScale: Float
            val mirrored: Boolean
            if (twin != null) {
                part = twin.part
                relativeScale = twin.relativeScale
                mirrored = !twin.mirrored
            } else {
                val candidates = library.candidates(slot.category, template.id)
                if (slot.optional && (candidates.isEmpty() || random.nextFloat() < SKIP_OPTIONAL)) continue
                check(candidates.isNotEmpty()) { "No ${slot.category} parts for ${template.id}" }
                part = candidates[random.nextInt(candidates.size)]
                relativeScale = if (slot.category == HEAD && parent != null) {
                    headScale(part, parent.part, random) ?: return null
                } else {
                    random.nextFloat(part.minScale, part.maxScale)
                }
                mirrored = part.mirrorable && random.nextFloat() < 0.5f
            }

            val anchor = slot.anchor?.let { name ->
                val local = checkNotNull(parent?.part?.anchors?.get(name)) { "${parent?.part?.id} has no anchor $name" }
                if (mirrored && (twin != null || part.mirrorable)) Vec2(-local.x, local.y) else local
            } ?: Vec2.Zero
            val own = Transform.translate(anchor) * Transform.scale(relativeScale) * (if (mirrored) Transform.MirrorX else Transform.Identity)
            placed[slot.name] = Placed(part, (parent?.transform ?: Transform.Identity) * own, relativeScale, mirrored)
        }

        // Back to front: by z-order, then slot order, then path order within a part.
        val shapes = placed.values
            .flatMapIndexed { slotIndex, p -> p.part.paths.mapIndexed { i, path -> Triple(p, slotIndex * 100 + i, p.transform.apply(path)) } }
            .sortedWith(compareBy({ it.first.part.zOrder }, { it.second }))
            .map { it.third }
        return LineArt(
            width = WIDTH,
            height = HEIGHT,
            shapes = fit(shapes, random).map { OutlineShape(it) },
            strokeWidth = template.strokeWidth,
            seed = seed,
            generatorVersion = GENERATOR_VERSION,
        )
    }

    /** Head scale relative to the body for a random head:body width ratio, or `null` if impossible. */
    private fun headScale(head: Part, body: Part, random: Pcg32): Float? {
        val ratio = random.nextFloat(template.headToBody.start, template.headToBody.endInclusive)
        val scale = (ratio * body.bounds.width / head.bounds.width).coerceIn(head.minScale, head.maxScale)
        return scale.takeIf { it * head.bounds.width / body.bounds.width in template.headToBody }
    }

    /** Scales and centres the animal to cover a random 55–75 % of the document. */
    private fun fit(shapes: List<PathShape>, random: Pcg32): List<PathShape> {
        val bounds = shapes.map { it.bounds() }.reduce(Bounds::union)
        val coverage = random.nextFloat(0.55f, 0.75f)
        val scale = minOf(coverage * WIDTH / bounds.width, coverage * HEIGHT / bounds.height)
        val toDocument = Transform.translate(Vec2(WIDTH / 2f, HEIGHT / 2f)) *
            Transform.scale(scale) *
            Transform.translate(bounds.center * -1f)
        return shapes.map(toDocument::apply)
    }

    companion object {
        /** Bump whenever output for a seed changes, so saved pictures know which version made them. */
        const val GENERATOR_VERSION = 1
        const val MAX_ATTEMPTS = 8

        /** A seed whose first attempt is valid; tested. */
        const val FALLBACK_SEED = 1L

        private const val WIDTH = 2048
        private const val HEIGHT = 1536
        private const val HEAD = "head"
        private const val SKIP_OPTIONAL = 0.3f
    }
}
