package com.example.colourfun.drawing.history

import android.graphics.Bitmap
import android.graphics.Rect
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Undo/redo stacks of [Command]s. Undo writes a command's `before` pixels, redo its `after` pixels,
 * so nothing is replayed.
 *
 * Holds at most [maxSteps] undo steps and [budgetBytes] of compressed patches; the oldest steps are
 * dropped first, but the newest step is always kept. A patch counts once its compression finishes,
 * because the uncompressed copy is only held briefly. A new command clears the redo stack.
 *
 * Main thread only: [scope] must run on the main thread. Compression runs on [compressDispatcher].
 */
class History(
    private val budgetBytes: Long = DEFAULT_BUDGET_BYTES,
    private val maxSteps: Int = MAX_STEPS,
    private val scope: CoroutineScope,
    private val compressDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val undoStack = ArrayDeque<Command>()
    private val redoStack = ArrayDeque<Command>()

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    internal val undoCount: Int get() = undoStack.size

    internal val retainedBytes: Long
        get() = undoStack.sumOf { it.compressedBytes } + redoStack.sumOf { it.compressedBytes }

    fun push(command: Command) {
        redoStack.clear()
        undoStack.addLast(command)
        while (undoStack.size > maxSteps) undoStack.removeFirst()
        compress(command.before)
        compress(command.after)
        trimToBudget()
        publish()
    }

    /** Restores the state before the last command. Returns the changed bounds, or `null` if there was nothing to undo. */
    fun undo(target: Bitmap): Rect? {
        val command = undoStack.removeLastOrNull() ?: return null
        command.before.writeTo(target)
        redoStack.addLast(command)
        publish()
        return command.bounds
    }

    /** Re-applies the last undone command. Returns the changed bounds, or `null` if there was nothing to redo. */
    fun redo(target: Bitmap): Rect? {
        val command = redoStack.removeLastOrNull() ?: return null
        command.after.writeTo(target)
        undoStack.addLast(command)
        publish()
        return command.bounds
    }

    private fun compress(patch: PixelPatch) {
        val raw = patch.pendingRaw ?: return
        scope.launch {
            val bytes = withContext(compressDispatcher) { PixelPatch.deflate(raw) }
            patch.onCompressed(bytes)
            trimToBudget()
            publish()
        }
    }

    private fun trimToBudget() {
        var total = retainedBytes
        while (total > budgetBytes && undoStack.size > 1) {
            total -= undoStack.removeFirst().compressedBytes
        }
    }

    private fun publish() {
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
    }

    companion object {
        const val DEFAULT_BUDGET_BYTES = 48L * 1024 * 1024
        const val LOW_RAM_BUDGET_BYTES = 24L * 1024 * 1024
        const val MAX_STEPS = 50
    }
}
