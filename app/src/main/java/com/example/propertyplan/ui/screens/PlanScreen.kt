package com.example.propertyplan.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.propertyplan.ui.widgets.*
import com.example.propertyplan.util.sharePlanJson
import com.example.propertyplan.vm.PlanViewModel

@Composable
fun PlanScreen(vm: PlanViewModel = viewModel()) {
    val ctx = LocalContext.current
    var canvasW by remember { mutableStateOf(1920f) }
    var canvasH by remember { mutableStateOf(1080f) }

    Scaffold(
        topBar = {
            TopBar(
                onDownloadJson = { sharePlanJson(ctx, com.example.propertyplan.model.PlanData(vm.floors)) },
                onSubmitDemo = { Toast.makeText(ctx, "Demo submission only.", Toast.LENGTH_SHORT).show() }
            )
        },
        bottomBar = {
            BottomActionBar(
                mode = vm.mode,
                onModeChange = { vm.mode = it },
                onUndo = { /* optional: hook to your future undo stack */ },
                onRedo = { /* optional */ },
                onDuplicate = { vm.duplicateSelectedRoom() },
                onDelete = { vm.deleteSelectedRoom() }
            )
        }
    ) { pad ->
        Row(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Left controls
            Column(Modifier.weight(0.9f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ElevatedCard { Column(Modifier.padding(12.dp)) {
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
                } }
                ElevatedCard { Column(Modifier.padding(12.dp)) {
                    RoomTemplatesPanel(vm, canvasW, canvasH)
                } }
            }

            // Center canvas
            Column(Modifier.weight(1.6f)) {
                ElevatedCard { Column(Modifier.padding(8.dp)) {
                    CanvasBoard(vm, vm.currentFloor()) { w, h -> canvasW = w; canvasH = h }
                } }
            }

            // Right inspector + summary
            Column(Modifier.weight(1.1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ElevatedCard { Column(Modifier.padding(12.dp)) { RoomInspectorPanel(vm, vm.currentFloor()) } }
                ElevatedCard { Column(Modifier.padding(12.dp)) { FloorSummaryPanel(vm, vm.currentFloor()) } }
            }
        }
    }

}
