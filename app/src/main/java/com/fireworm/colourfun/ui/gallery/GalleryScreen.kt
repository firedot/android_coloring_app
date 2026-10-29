package com.fireworm.colourfun.ui.gallery

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fireworm.colourfun.R
import com.fireworm.colourfun.ui.feedback.Sound
import com.fireworm.colourfun.ui.feedback.rememberFeedback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val Background = Color(0xFFE8E8E8)
private val ThumbnailShape = RoundedCornerShape(16.dp)

/** Saved pictures, newest first. Tapping one opens it for colouring. */
@Composable
fun GalleryScreen(onBack: () -> Unit, onOpen: (String) -> Unit, viewModel: GalleryViewModel = hiltViewModel()) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val feedback = rememberFeedback()

    Surface(modifier = Modifier.fillMaxSize(), color = Background) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(onClick = { feedback(Sound.Tap); onBack() }, modifier = Modifier.size(64.dp)) {
                    Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.back), Modifier.size(32.dp))
                }
            }
            val list = items
            when {
                list == null -> Unit
                list.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(painterResource(R.drawable.ic_paw), null, Modifier.size(96.dp), tint = Color.Gray)
                        Text(stringResource(R.string.gallery_empty), style = MaterialTheme.typography.titleLarge, color = Color.Gray)
                    }
                }
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 200.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(list, key = { it.id }) { item ->
                        Thumbnail(item, onClick = { feedback(Sound.Tap); onOpen(item.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun Thumbnail(item: GalleryItem, onClick: () -> Unit) {
    val image by produceState<ImageBitmap?>(null, item.thumbnail, item.updatedAt) {
        value = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(item.thumbnail.path)?.asImageBitmap() }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(4f / 3f)
            .shadow(4.dp, ThumbnailShape)
            .clip(ThumbnailShape)
            .background(Color.White)
            .clickable(onClickLabel = stringResource(R.string.open_picture), onClick = onClick),
    ) {
        image?.let { Image(it, contentDescription = null, modifier = Modifier.fillMaxSize()) }
    }
}
