package com.example.propertyplan.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.propertyplan.model.PlanData
import com.example.propertyplan.ui.adaptive.*
import com.example.propertyplan.ui.canvas.PlanCanvas
import com.example.propertyplan.ui.widgets.*
import com.example.propertyplan.util.sharePlanJson
import com.example.propertyplan.vm.PlanViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun PlanHome(vm: PlanViewModel = viewModel()) {
    val ctx = LocalContext.current
    val wsc = calculateWindowSizeClass(activity = androidx.compose.ui.platform.LocalContext.current as android.app.Activity)
    val bp = breakpoints(wsc)
    val orient = orientation()

    var canvasW by remember { mutableStateOf(1920f) }
    var canvasH by remember { mutableStateOf(1080f) }

    // panel controller heuristics (from your Smart version)
    val panels = remember { com.example.propertyplan.ui.panels.PanelController() }
    LaunchedEffect(vm.mode) { panels.onModeChanged(vm.mode) }
    LaunchedEffect(vm.currentFloorIndex, vm.floors.size) { panels.onFloorChanged(vm.currentFloor()) }
    LaunchedEffect(vm.selectedRoomId) { panels.onSelectionChanged(vm.selectedRoomId != null) }

    // Compact layout: show panels as bottom sheets
    val showLeftSheet = remember { mutableStateOf(false) }
    val showRightSheet = remember { mutableStateOf(false) }

    // In compact portrait: auto-open sheet only when needed
    LaunchedEffect(bp.compact, orient, vm.selectedRoomId) {
        if (bp.compact) {
            showLeftSheet.value = (vm.currentFloor()?.rooms?.isEmpty() == true)
            showRightSheet.value = (vm.selectedRoomId != null)
        }
    }

    val topBar: @Composable () -> Unit = {
        TopBar(
            onDownloadJson = { sharePlanJson(ctx, PlanData(vm.floors)) },
            onSubmitDemo = { Toast.makeText(ctx, "Demo submission only.", Toast.LENGTH_SHORT).show() }
        )
    }
    val bottomBar: @Composable () -> Unit = {
        BottomActionBar(
            mode = vm.mode,
            onModeChange = { vm.mode = it },
            onUndo = { /* hook undo later */ },
            onRedo = { /* hook redo later */ },
            onDuplicate = { vm.duplicateSelectedRoom() },
            onDelete = { vm.deleteSelectedRoom() }
        )
    }

    if (bp.compact && orient == Orientation.Portrait) {
        // Full canvas + modal sheets
        Scaffold(topBar = topBar, bottomBar = bottomBar) { pad ->
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(pad) // ✅ apply scaffold’s inner padding
            ) {
                PlanCanvas(
                    vm = vm,
                    floor = vm.currentFloor(),
                    onCanvasSize = { w, h -> canvasW = w; canvasH = h },
                    background = MaterialTheme.colorScheme.surface
                )
            }

            // LEFT (tools) sheet
            if (showLeftSheet.value) {
                ModalBottomSheet(
                    onDismissRequest = { showLeftSheet.value = false }
                ) {
                    FloorDetailsPanel(
                        vm = vm,
                        floor = vm.currentFloor(),
                        onSave = { },
                        onDelete = { vm.removeCurrentFloor() },
                        onAdd = { vm.addFloor() },
                        onDuplicate = { vm.duplicateCurrentFloor() },
                        onCopy = { vm.copyFloor() },
                        onPaste = { vm.pasteFloor() }
                    )
                    Divider()
                    RoomTemplatesPanel(vm, canvasW, canvasH)
                }
            }

            // RIGHT (inspector) sheet
            if (showRightSheet.value) {
                ModalBottomSheet(
                    onDismissRequest = { showRightSheet.value = false }
                ) {
                    RoomInspectorPanel(vm, vm.currentFloor())
                    Divider()
                    FloorSummaryPanel(vm, vm.currentFloor())
                }
            }
        }

        // Back closes sheet first
        BackHandler(enabled = showLeftSheet.value || showRightSheet.value) {
            if (showRightSheet.value) showRightSheet.value = false
            else if (showLeftSheet.value) showLeftSheet.value = false
        }
    } else {
        // Medium/Expanded: slide or permanent panels
        AdaptivePanelsScaffold(
            leftOpen = if (bp.expanded) true else panels.leftOpen,
            rightOpen = if (bp.expanded) true else panels.rightOpen,
            topBar = topBar,
            bottomBar = bottomBar,
            leftPanel = {
                FloorDetailsPanel(
                    vm = vm,
                    floor = vm.currentFloor(),
                    onSave = { },
                    onDelete = { vm.removeCurrentFloor() },
                    onAdd = { vm.addFloor() },
                    onDuplicate = { vm.duplicateCurrentFloor() },
                    onCopy = { vm.copyFloor() },
                    onPaste = { vm.pasteFloor() }
                )
                Divider()
                RoomTemplatesPanel(vm, canvasW, canvasH)
            },
            rightPanel = {
                RoomInspectorPanel(vm, vm.currentFloor())
                Divider()
                FloorSummaryPanel(vm, vm.currentFloor())
            },
            centerCanvas = {
                PlanCanvas(
                    vm = vm,
                    floor = vm.currentFloor(),
                    onCanvasSize = { w, h -> canvasW = w; canvasH = h },
                    background = MaterialTheme.colorScheme.surface
                )
            },
            onToggleLeft = { panels.toggleLeft() },
            onToggleRight = { panels.toggleRight() },
            mediumLayout = bp.medium || (bp.compact && orient == Orientation.Landscape),
            expandedLayout = bp.expanded
        )
    }
}
