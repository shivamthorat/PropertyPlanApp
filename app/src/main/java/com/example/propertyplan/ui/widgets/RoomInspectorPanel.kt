package com.example.propertyplan.ui.widgets

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.propertyplan.model.Floor
import com.example.propertyplan.vm.PlanViewModel

@Composable
fun RoomInspectorPanel(vm: PlanViewModel, floor: Floor?) {
    val sel = floor?.rooms?.find { it.id == vm.selectedRoomId }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Room Inspector", style = MaterialTheme.typography.titleMedium)

        if (floor == null) {
            Text("Add a floor to begin.")
            return@Column
        }
        if (sel == null) {
            Text("Select a room to edit its details.")
            return@Column
        }

        // ---- Name ----
        OutlinedTextField(
            value = sel.name,
            onValueChange = { new -> vm.updateSelectedRoom { r -> r.name = new } },
            label = { Text("Name") },
            modifier = Modifier.fillMaxWidth()
        )

        // ---- Type/Color quick set ----
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                vm.updateSelectedRoom { r ->
                    r.isUtility = false
                    r.color = 0xFF22C55E.toInt() // green
                }
            }) { Text("Standard (Green)") }

            OutlinedButton(onClick = {
                vm.updateSelectedRoom { r ->
                    r.isUtility = true
                    r.color = 0xFFE53935.toInt() // red
                }
            }) { Text("Utility (Red)") }
        }

        // ---- Position ----
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(
                label = "X",
                value = sel.x,
                onChange = { v -> vm.updateSelectedRoom { r -> r.x = v } },
                modifier = Modifier.weight(1f)
            )
            NumberField(
                label = "Y",
                value = sel.y,
                onChange = { v -> vm.updateSelectedRoom { r -> r.y = v } },
                modifier = Modifier.weight(1f)
            )
        }

        // ---- Size (W/H) with aspect lock & sliders ----
        var lockAspect by remember(sel.id) { mutableStateOf(false) }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            AssistChip(
                onClick = { lockAspect = !lockAspect },
                label = { Text(if (lockAspect) "Aspect: LOCKED" else "Aspect: Free") }
            )
        }

        val minSize = 24f
        val aspect = if (sel.h > 0f) sel.w / sel.h else 1f

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(
                label = "Width",
                value = sel.w,
                onChange = { newW ->
                    vm.updateSelectedRoom { r ->
                        val w = newW.coerceAtLeast(minSize)
                        if (lockAspect) {
                            // keep center while scaling
                            val cx = r.x + r.w / 2f
                            r.w = w
                            r.h = (w / (if (aspect == 0f) 1f else aspect)).coerceAtLeast(minSize)
                            r.x = cx - r.w / 2f
                            r.y = (r.y + r.h / 2f) - r.h / 2f
                        } else {
                            val cx = r.x + r.w / 2f
                            r.w = w
                            r.x = cx - r.w / 2f
                        }
                    }
                },
                modifier = Modifier.weight(1f)
            )
            NumberField(
                label = "Height",
                value = sel.h,
                onChange = { newH ->
                    vm.updateSelectedRoom { r ->
                        val h = newH.coerceAtLeast(minSize)
                        if (lockAspect) {
                            val cy = r.y + r.h / 2f
                            r.h = h
                            r.w = (h * (if (aspect == 0f) 1f else aspect)).coerceAtLeast(minSize)
                            r.y = cy - r.h / 2f
                            r.x = (r.x + r.w / 2f) - r.w / 2f
                        } else {
                            val cy = r.y + r.h / 2f
                            r.h = h
                            r.y = cy - r.h / 2f
                        }
                    }
                },
                modifier = Modifier.weight(1f)
            )
        }

        // Sliders for quick W/H tweaks (use ppm to give reasonable max)
        val ppm = floor.params.ppm.coerceAtLeast(1)
        val maxWH = (ppm * 20).toFloat() // ~20 meters at given ppm
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Width: ${sel.w.toInt()} px")
            Slider(
                value = sel.w.coerceIn(minSize, maxWH),
                onValueChange = { v ->
                    vm.updateSelectedRoom { r ->
                        val w = v.coerceAtLeast(minSize)
                        if (lockAspect) {
                            val cx = r.x + r.w / 2f
                            r.w = w
                            r.h = (w / (if (aspect == 0f) 1f else aspect)).coerceAtLeast(minSize)
                            r.x = cx - r.w / 2f
                            r.y = (r.y + r.h / 2f) - r.h / 2f
                        } else {
                            val cx = r.x + r.w / 2f
                            r.w = w
                            r.x = cx - r.w / 2f
                        }
                    }
                },
                valueRange = minSize..maxWH
            )
            Text("Height: ${sel.h.toInt()} px")
            Slider(
                value = sel.h.coerceIn(minSize, maxWH),
                onValueChange = { v ->
                    vm.updateSelectedRoom { r ->
                        val h = v.coerceAtLeast(minSize)
                        if (lockAspect) {
                            val cy = r.y + r.h / 2f
                            r.h = h
                            r.w = (h * (if (aspect == 0f) 1f else aspect)).coerceAtLeast(minSize)
                            r.y = cy - r.h / 2f
                            r.x = (r.x + r.w / 2f) - r.w / 2f
                        } else {
                            val cy = r.y + r.h / 2f
                            r.h = h
                            r.y = cy - r.h / 2f
                        }
                    }
                },
                valueRange = minSize..maxWH
            )
        }

        // ---- Angle ----
        Column {
            Text("Angle (°): ${sel.angle.toInt()}")
            Slider(
                value = sel.angle,
                valueRange = 0f..359f,
                onValueChange = { v ->
                    vm.updateSelectedRoom { r ->
                        r.angle = if (vm.angleSnap) snapAngle(v, false) else v
                    }
                }
            )
        }

        // ---- Area ----
        ElevatedCard {
            val area = if (floor.params.ppm <= 0) 0f
            else (sel.w / floor.params.ppm) * (sel.h / floor.params.ppm)
            Column(Modifier.padding(12.dp)) {
                Text("Area")
                Text(String.format("%.2f m²", area), style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}

/** Small numeric field with graceful parsing */
@Composable
private fun NumberField(
    label: String,
    value: Float,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var text by remember(value) { mutableStateOf(value.toInt().toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            it.toFloatOrNull()?.let(onChange)
        },
        label = { Text(label) },
        modifier = modifier
    )
}

private fun snapAngle(deg: Float, fine: Boolean): Float {
    val step = if (fine) 5f else 15f
    val n = kotlin.math.round(deg / step) * step
    return ((n % 360f) + 360f) % 360f
}
