package com.example.propertyplan.ui.widgets

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.propertyplan.model.Floor
import com.example.propertyplan.vm.PlanViewModel

@Composable
fun FloorDetailsPanel(
    vm: PlanViewModel,
    floor: Floor?,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onAdd: () -> Unit,
    onDuplicate: () -> Unit,
    onCopy: () -> Unit,
    onPaste: () -> Unit
) {
    if (floor == null) {
        Text("Add a floor to begin.")
        Button(onClick = onAdd, modifier = Modifier.padding(top = 8.dp)) { Text("Add Floor") }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FloorTabs(
            floors = vm.floors,
            currentIndex = vm.currentFloorIndex,
            onSelect = { idx ->
                vm.selectRoom(null)
                vm.apply { currentFloorIndex = idx }
            }
        )
        OutlinedTextField(
            value = floor.params.floorId,
            onValueChange = { vm.saveFloorParams(floorId = it) },
            label = { Text("Floor ID") },
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = floor.params.year.toString(),
                onValueChange = { it.toIntOrNull()?.let { y -> vm.saveFloorParams(year = y) } },
                label = { Text("Year") },
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = floor.params.useType,
                onValueChange = { vm.saveFloorParams(useType = it) },
                label = { Text("Use Type") },
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = floor.params.subUseType,
                onValueChange = { vm.saveFloorParams(subUseType = it) },
                label = { Text("Sub Type") },
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = floor.params.constructionType,
                onValueChange = { vm.saveFloorParams(constructionType = it) },
                label = { Text("Construction Type") },
                modifier = Modifier.weight(1f)
            )
        }
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Checkbox(checked = floor.params.isRenter, onCheckedChange = { vm.saveFloorParams(isRenter = it) })
            Text("Is Renter")
        }
        Column {
            Text("Scale: pixels per meter • ${floor.params.ppm} px/m")
            Slider(
                value = floor.params.ppm.toFloat(),
                valueRange = 20f..150f,
                onValueChange = { vm.saveFloorParams(ppm = it.toInt()) }
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onSave) { Text("Save Floor") }
            Button(onClick = onDelete, colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.error)) {
                Text("Delete Floor")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onDuplicate) { Text("Duplicate") }
            OutlinedButton(onClick = onCopy) { Text("Copy") }
            OutlinedButton(onClick = onPaste) { Text("Paste") }
        }
    }
}
