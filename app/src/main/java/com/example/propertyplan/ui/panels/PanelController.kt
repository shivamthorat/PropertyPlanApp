package com.example.propertyplan.ui.panels

import androidx.compose.runtime.*
import com.example.propertyplan.model.Floor
import com.example.propertyplan.vm.UIMode

@Stable
class PanelController {
    var leftOpen by mutableStateOf(false)
    var rightOpen by mutableStateOf(false)

    fun toggleLeft() { leftOpen = !leftOpen }
    fun toggleRight() { rightOpen = !rightOpen }

    fun openLeft() { leftOpen = true }
    fun openRight() { rightOpen = true }
    fun closeLeft() { leftOpen = false }
    fun closeRight() { rightOpen = false }

    /** UX heuristics that keep canvas big but helpful */
    fun onModeChanged(mode: UIMode) {
        if (mode == UIMode.Draw) {
            // while drawing, keep right closed to maximize space
            rightOpen = false
        }
    }

    fun onSelectionChanged(hasSelection: Boolean) {
        // show inspector when you actually have something selected
        rightOpen = hasSelection
    }

    fun onFloorChanged(floor: Floor?) {
        // when no rooms yet, surface details/templates to help the user start
        if (floor == null || floor.rooms.isEmpty()) {
            leftOpen = true
            rightOpen = false
        }
    }
}

@Composable
fun rememberPanelController(): PanelController {
    return remember { PanelController() }
}
