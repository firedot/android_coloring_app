package com.fireworm.colourfun.ui.gate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fireworm.colourfun.R
import kotlin.random.Random

/**
 * A number written as words ("seven, two, nine, four") that must be typed as digits ("7294").
 * Easy for an adult who can read, hard for a young child.
 */
class ParentGateChallenge(val digits: List<Int>) {
    init {
        require(digits.isNotEmpty() && digits.all { it in 0..9 })
    }

    fun words(digitWords: List<String>): String = digits.joinToString(", ") { digitWords[it] }

    fun isAnswer(input: String): Boolean = input.trim() == digits.joinToString("")

    companion object {
        const val LENGTH = 4

        fun random(random: Random = Random.Default) = ParentGateChallenge(List(LENGTH) { random.nextInt(10) })
    }
}

/**
 * Asks for the [ParentGateChallenge] before settings, saving to photos and sharing. A wrong answer
 * clears the field and shows a new number.
 */
@Composable
fun ParentGateDialog(
    onPass: () -> Unit,
    onDismiss: () -> Unit,
    challengeFactory: () -> ParentGateChallenge = { ParentGateChallenge.random() },
) {
    var digits by rememberSaveable { mutableStateOf(challengeFactory().digits) }
    var input by rememberSaveable { mutableStateOf("") }
    var wrong by rememberSaveable { mutableStateOf(false) }
    val challenge = ParentGateChallenge(digits)
    val digitWords = stringArrayResource(R.array.digit_words).toList()

    fun submit() {
        if (challenge.isAnswer(input)) {
            onPass()
        } else {
            wrong = true
            input = ""
            digits = challengeFactory().digits
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.parent_gate_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.parent_gate_prompt))
                Text(
                    challenge.words(digitWords),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.testTag(TAG_WORDS),
                )
                OutlinedTextField(
                    value = input,
                    onValueChange = { value -> input = value.filter(Char::isDigit).take(ParentGateChallenge.LENGTH) },
                    label = { Text(stringResource(R.string.parent_gate_answer)) },
                    isError = wrong,
                    supportingText = if (wrong) ({ Text(stringResource(R.string.parent_gate_wrong)) }) else null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier.testTag(TAG_INPUT),
                )
            }
        },
        confirmButton = { TextButton(onClick = ::submit) { Text(stringResource(R.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

internal const val TAG_WORDS = "parentGateWords"
internal const val TAG_INPUT = "parentGateInput"
