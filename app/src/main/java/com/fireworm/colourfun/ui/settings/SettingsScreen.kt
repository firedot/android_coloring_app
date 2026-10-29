package com.fireworm.colourfun.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.fireworm.colourfun.R
import com.fireworm.colourfun.data.settings.Settings
import com.fireworm.colourfun.data.settings.SettingsRepository
import com.fireworm.colourfun.feedback.Sound
import com.fireworm.colourfun.feedback.rememberFeedback
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(private val repository: SettingsRepository) : ViewModel() {
    val settings: StateFlow<Settings?> = repository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setSound(enabled: Boolean) = viewModelScope.launch { repository.setSoundEnabled(enabled) }
    fun setHaptics(enabled: Boolean) = viewModelScope.launch { repository.setHapticsEnabled(enabled) }
    fun setStayInsideLines(enabled: Boolean) = viewModelScope.launch { repository.setStayInsideLines(enabled) }
}

/** Parent settings; only reachable through the parent gate. */
@Composable
fun SettingsScreen(onBack: () -> Unit, onOpenLicenses: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val feedback = rememberFeedback()

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFE8E8E8)) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(8.dp)) {
            FilledTonalIconButton(onClick = { feedback(Sound.Tap); onBack() }, modifier = Modifier.size(64.dp)) {
                Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.back), Modifier.size(32.dp))
            }
            val current = settings ?: return@Column
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.settings), style = MaterialTheme.typography.headlineMedium)
                SettingRow(R.string.settings_sound, current.soundEnabled) { feedback(Sound.Tap); viewModel.setSound(it) }
                SettingRow(R.string.settings_haptics, current.hapticsEnabled) { feedback(Sound.Tap); viewModel.setHaptics(it) }
                SettingRow(R.string.settings_stay_inside, current.stayInsideLines) { feedback(Sound.Tap); viewModel.setStayInsideLines(it) }
                TextButton(onClick = { feedback(Sound.Tap); onOpenLicenses() }, modifier = Modifier.heightIn(min = 64.dp)) {
                    Text(stringResource(R.string.licenses), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun SettingRow(@StringRes label: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(stringResource(label), style = MaterialTheme.typography.titleLarge)
        // The whole row toggles; the switch only shows the state.
        Switch(checked = checked, onCheckedChange = null)
    }
}
