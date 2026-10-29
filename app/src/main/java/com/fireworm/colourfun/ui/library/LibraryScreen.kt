package com.fireworm.colourfun.ui.library

import android.graphics.BitmapFactory
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fireworm.colourfun.R
import com.fireworm.colourfun.data.library.Picture
import com.fireworm.colourfun.data.library.Section
import com.fireworm.colourfun.feedback.Sound
import com.fireworm.colourfun.feedback.rememberFeedback
import com.fireworm.colourfun.ui.parentgate.ParentGateDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val Background = Color(0xFFF3EFE6)
private val ThumbnailShape = RoundedCornerShape(16.dp)
private val SectionButtonSize = 72.dp

/** Thumbnails are at least this tall; 4:3 like the pictures. */
private val ThumbnailHeight = 160.dp
private val ThumbnailMinWidth = ThumbnailHeight * 4 / 3
private val Spacing = 16.dp

/**
 * The start screen: "my pictures" (saved artworks, newest first; tap = continue), then the
 * sections as icon tabs, each with a grid of pictures (tap = new artwork). No text: the child
 * navigates by pictures and icons. Settings sit behind the parent gate.
 */
@Composable
fun LibraryScreen(
    onOpenPicture: (pictureId: String) -> Unit,
    onContinue: (artworkId: String) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val feedback = rememberFeedback()
    var gate by rememberSaveable { mutableStateOf(false) }
    if (gate) {
        ParentGateDialog(
            onPass = {
                gate = false
                onOpenSettings()
            },
            onDismiss = { gate = false },
        )
    }
    LibraryContent(
        uiState = uiState,
        loadThumbnail = viewModel::thumbnail,
        onOpenPicture = {
            feedback(Sound.Tap)
            onOpenPicture(it)
        },
        onContinue = {
            feedback(Sound.Tap)
            onContinue(it)
        },
        onSettings = {
            feedback(Sound.Tap)
            gate = true
        },
        onSelectSection = { feedback(Sound.Tick) },
    )
}

@Composable
internal fun LibraryContent(
    uiState: LibraryUiState,
    loadThumbnail: suspend (Picture) -> ImageBitmap?,
    onOpenPicture: (String) -> Unit,
    onContinue: (String) -> Unit,
    onSettings: () -> Unit,
    onSelectSection: () -> Unit = {},
) {
    val sections = uiState.sections.orEmpty()
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val current = sections.getOrNull(selected) ?: sections.firstOrNull()
    val saved = uiState.saved.orEmpty()

    Surface(modifier = Modifier.fillMaxSize(), color = Background) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = Spacing)) {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(Spacing)) {
                    sections.forEachIndexed { index, item ->
                        SectionTab(item.section, selected = item == current) {
                            selected = index
                            onSelectSection()
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                // Small and plain: for grown-ups, behind the parent gate.
                IconButton(onClick = onSettings, modifier = Modifier.size(48.dp)) {
                    Icon(painterResource(R.drawable.ic_settings), stringResource(R.string.settings), tint = Color.Gray)
                }
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = ThumbnailMinWidth),
                contentPadding = PaddingValues(bottom = Spacing),
                horizontalArrangement = Arrangement.spacedBy(Spacing),
                verticalArrangement = Arrangement.spacedBy(Spacing),
            ) {
                if (saved.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "saved") {
                        SavedRow(saved, onContinue)
                    }
                }
                items(current?.pictures.orEmpty(), key = { it.id }) { picture ->
                    val image by produceState<ImageBitmap?>(null, picture.id) { value = loadThumbnail(picture) }
                    Thumbnail(image, stringResource(R.string.open_picture), Modifier.fillMaxWidth()) { onOpenPicture(picture.id) }
                }
            }
        }
    }
}

/** "My pictures": saved artworks on a tinted strip above the sections. */
@Composable
private fun SavedRow(saved: List<SavedPicture>, onContinue: (String) -> Unit) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ThumbnailShape)
            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(Spacing),
    ) {
        items(saved, key = { it.id }) { item ->
            val image by produceState<ImageBitmap?>(null, item.thumbnail, item.updatedAt) {
                value = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(item.thumbnail.path)?.asImageBitmap() }
            }
            Thumbnail(image, stringResource(R.string.continue_picture), Modifier.height(ThumbnailHeight)) { onContinue(item.id) }
        }
    }
}

@Composable
private fun Thumbnail(image: ImageBitmap?, description: String, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .aspectRatio(4f / 3f)
            .shadow(4.dp, ThumbnailShape)
            .clip(ThumbnailShape)
            .background(Color.White)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
    ) {
        image?.let { Image(it, contentDescription = null, modifier = Modifier.fillMaxSize()) }
    }
}

@Composable
private fun SectionTab(section: Section, selected: Boolean, onClick: () -> Unit) {
    val label = section.label()
    Box(
        Modifier
            .size(SectionButtonSize)
            .shadow(if (selected) 6.dp else 2.dp, CircleShape)
            .clip(CircleShape)
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.White)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(section.iconRes()),
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else Color.Gray,
        )
    }
}

/** Icons from [Section.of]; an unknown one falls back to the star. */
@DrawableRes
private fun Section.iconRes(): Int = when (icon) {
    "paw" -> R.drawable.ic_section_paw
    "leaf" -> R.drawable.ic_section_leaf
    else -> R.drawable.ic_section_star
}

/** For accessibility only; nothing is shown as text. */
@Composable
private fun Section.label(): String = when (id) {
    "animals" -> stringResource(R.string.section_animals)
    "plants" -> stringResource(R.string.section_plants)
    "various" -> stringResource(R.string.section_various)
    else -> id
}
