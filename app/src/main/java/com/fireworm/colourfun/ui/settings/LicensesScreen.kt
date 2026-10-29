package com.fireworm.colourfun.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.fireworm.colourfun.R
import com.fireworm.colourfun.feedback.Sound
import com.fireworm.colourfun.feedback.rememberFeedback

private class Library(val name: String, val copyright: String)

/**
 * Every library in the release runtime classpath, grouped by project. All of them are Apache 2.0;
 * check `:app:dependencies --configuration releaseRuntimeClasspath` when adding a dependency.
 */
private val libraries = listOf(
    Library("AndroidX (Jetpack Compose, Room, Navigation, Lifecycle, DataStore and others)", "Copyright The Android Open Source Project"),
    Library("Dagger and Hilt", "Copyright The Dagger Authors"),
    Library("Guava ListenableFuture", "Copyright The Guava Authors"),
    Library("JSR 305 annotations", "Copyright The FindBugs Authors"),
    Library("javax.inject and jakarta.inject", "Copyright The JSR-330 Expert Group"),
    Library("JSpecify", "Copyright The JSpecify Authors"),
    Library("Kotlin, kotlinx.coroutines and JetBrains annotations", "Copyright JetBrains s.r.o. and Kotlin contributors"),
    Library("Okio", "Copyright Square, Inc."),
)

/** Open-source licences of the bundled libraries; reached from the parent settings. */
@Composable
fun LicensesScreen(onBack: () -> Unit) {
    val feedback = rememberFeedback()
    val resources = LocalResources.current
    val apacheLicense = remember(resources) {
        resources.openRawResource(R.raw.apache_license_2_0).bufferedReader().use { it.readText() }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFE8E8E8)) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(8.dp)) {
            FilledTonalIconButton(onClick = { feedback(Sound.Tap); onBack() }, modifier = Modifier.size(64.dp)) {
                Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.back), Modifier.size(32.dp))
            }
            LazyColumn(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                item {
                    Text(
                        stringResource(R.string.licenses),
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                item {
                    Text(
                        stringResource(R.string.licenses_intro),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(vertical = 8.dp),
                    )
                }
                items(libraries) { library ->
                    Column(Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(vertical = 8.dp)) {
                        Text(library.name, style = MaterialTheme.typography.titleMedium)
                        Text(library.copyright, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                item {
                    Text(
                        apacheLicense,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(vertical = 16.dp),
                    )
                }
            }
        }
    }
}
