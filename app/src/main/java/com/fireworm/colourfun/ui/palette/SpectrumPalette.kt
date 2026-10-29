package com.fireworm.colourfun.ui.palette

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp
import com.fireworm.colourfun.R

// The palette is a primary control: at least 64 dp to hit.
private val StripThickness = 64.dp
private val StripGap = 8.dp
private val KnobOutline = Color(0xFF212121)
private val KnobRing = Color.White
private const val RAINBOW_STOPS = 24
private const val SHADE_STOPS = 16

/**
 * The colour picker for the screen edge: a rainbow strip for the colour and a light-to-dark strip
 * for its shade. Tap or slide along either one; the shade strip's knob shows the selected colour.
 *
 * @param vertical strips run down a side edge (landscape); otherwise along the bottom.
 */
@Composable
fun SpectrumPalette(
    selected: Int,
    onSelect: (Int) -> Unit,
    vertical: Boolean,
    modifier: Modifier = Modifier,
) {
    // Kept locally so sliding the shade to white or black keeps the chosen hue. A colour from
    // outside (the pipette) that isn't this place moves the knobs to where it sits instead.
    var picked by remember { mutableStateOf(Spectrum.position(selected)) }
    val position = if (Spectrum.color(picked) == selected) picked else Spectrum.position(selected)
    fun pick(next: SpectrumPosition) {
        picked = next
        onSelect(Spectrum.color(next))
    }

    val rainbowStops = remember { rainbowStops() }
    val shadeStops = remember(position.hue) { shadeStops(position.hue) }
    val strip = if (vertical) Modifier.width(StripThickness).fillMaxHeight() else Modifier.fillMaxWidth().height(StripThickness)

    val hueStrip = @Composable {
        SpectrumStrip(
            stops = rainbowStops,
            position = position.hue,
            knobColor = Color(Spectrum.color(position.copy(shade = 0.5f))),
            vertical = vertical,
            label = stringResource(R.string.palette_hue),
            onPosition = { pick(position.copy(hue = it)) },
            modifier = strip,
        )
    }
    val shadeStrip = @Composable {
        SpectrumStrip(
            stops = shadeStops,
            position = position.shade,
            knobColor = Color(selected),
            vertical = vertical,
            label = stringResource(R.string.palette_shade),
            onPosition = { pick(position.copy(shade = it)) },
            modifier = strip,
        )
    }
    if (vertical) {
        Row(modifier, horizontalArrangement = Arrangement.spacedBy(StripGap)) { hueStrip(); shadeStrip() }
    } else {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(StripGap)) { hueStrip(); shadeStrip() }
    }
}

/**
 * A rounded gradient strip with a round knob at [position]. The knob's centre travels between the
 * strip's rounded ends, so the full `0..1` range is reachable with a finger.
 */
@Composable
private fun SpectrumStrip(
    stops: Array<Pair<Float, Color>>,
    position: Float,
    knobColor: Color,
    vertical: Boolean,
    label: String,
    onPosition: (Float) -> Unit,
    modifier: Modifier,
) {
    val currentOnPosition by rememberUpdatedState(onPosition)
    Canvas(
        modifier
            .semantics {
                contentDescription = label
                progressBarRangeInfo = ProgressBarRangeInfo(position, 0f..1f)
                setProgress { currentOnPosition(it.coerceIn(0f, 1f)); true }
            }
            .pointerInput(vertical) {
                fun positionAt(offset: Offset): Float {
                    val radius = (if (vertical) size.width else size.height) / 2f
                    val length = if (vertical) size.height else size.width
                    val along = if (vertical) offset.y else offset.x
                    return ((along - radius) / (length - 2f * radius)).coerceIn(0f, 1f)
                }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    currentOnPosition(positionAt(down.position))
                    drag(down.id) { change ->
                        change.consume()
                        currentOnPosition(positionAt(change.position))
                    }
                }
            },
    ) {
        val radius = (if (vertical) size.width else size.height) / 2f
        val length = if (vertical) size.height else size.width
        val brush = if (vertical) {
            Brush.verticalGradient(*stops, startY = radius, endY = length - radius)
        } else {
            Brush.horizontalGradient(*stops, startX = radius, endX = length - radius)
        }
        drawRoundRect(brush, cornerRadius = CornerRadius(radius))

        val along = radius + position * (length - 2f * radius)
        val center = if (vertical) Offset(radius, along) else Offset(along, radius)
        drawCircle(KnobOutline, radius = radius, center = center)
        drawCircle(KnobRing, radius = radius - 2.dp.toPx(), center = center)
        drawCircle(knobColor, radius = radius - 7.dp.toPx(), center = center)
    }
}

private fun rainbowStops(): Array<Pair<Float, Color>> {
    val rainbow = (0..RAINBOW_STOPS).map { i ->
        val hue = i * Spectrum.RAINBOW_END / RAINBOW_STOPS
        hue to Color(Spectrum.color(SpectrumPosition(hue, shade = 0.5f)))
    }
    // Hard edge between pink and the grey block.
    val grey = Color(Spectrum.color(SpectrumPosition(hue = 1f, shade = 0.5f)))
    return (rainbow + listOf(Spectrum.RAINBOW_END to grey, 1f to grey)).toTypedArray()
}

private fun shadeStops(hue: Float): Array<Pair<Float, Color>> = Array(SHADE_STOPS + 1) { i ->
    val shade = i / SHADE_STOPS.toFloat()
    shade to Color(Spectrum.color(SpectrumPosition(hue, shade)))
}
