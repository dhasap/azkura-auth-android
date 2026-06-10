package id.azkura.auth.util

import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class ReorderState(
    val listState: LazyListState,
    val onMove: (fromIndex: Int, toIndex: Int) -> Unit,
    val scope: CoroutineScope
) {
    var draggedDistance by mutableStateOf(0f)
    var initialDraggingElement by mutableStateOf<LazyListItemInfo?>(null)
    var currentIndexOfDraggedItem by mutableStateOf<Int?>(null)

    val draggingItemOffset: Float
        get() = draggedDistance

    fun onDragStart(offset: Offset) {
        listState.layoutInfo.visibleItemsInfo
            .firstOrNull { offset.y.toInt() in it.offset..(it.offset + it.size) }
            ?.also {
                initialDraggingElement = it
                currentIndexOfDraggedItem = it.index
            }
    }

    fun onDrag(offset: Offset) {
        val draggedElement = initialDraggingElement ?: return
        draggedDistance += offset.y

        val currentIndexOfDraggedItem = currentIndexOfDraggedItem ?: return
        val startOffset = draggedElement.offset + draggedDistance.toInt()
        val endOffset = startOffset + draggedElement.size

        val targetItem = listState.layoutInfo.visibleItemsInfo.find { item ->
            val center = item.offset + item.size / 2
            center in startOffset..endOffset && item.index != currentIndexOfDraggedItem
        }

        if (targetItem != null) {
            onMove(currentIndexOfDraggedItem, targetItem.index)
            this.currentIndexOfDraggedItem = targetItem.index
        }
    }

    fun onDragInterrupted() {
        draggedDistance = 0f
        initialDraggingElement = null
        currentIndexOfDraggedItem = null
    }
}
