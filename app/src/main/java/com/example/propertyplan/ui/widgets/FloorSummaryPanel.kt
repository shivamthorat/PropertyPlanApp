package com.example.propertyplan.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.propertyplan.model.Floor
import com.example.propertyplan.vm.PlanViewModel

@Composable
fun FloorSummaryPanel(vm: PlanViewModel, floor: Floor?) {
    if (floor == null) return

    val total = vm.totalAreaM2(floor)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Floor Summary")

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SummaryCard(
                title = "Rooms",
                value = floor.rooms.size.toString(),
                modifier = Modifier.weight(1f)
            )
            SummaryCard(
                title = "Total Area (m²)",
                value = String.format("%.2f", total),
                modifier = Modifier.weight(1f)
            )
        }

        Text("Rooms")

        LazyColumn(
            modifier = Modifier.heightIn(max = 320.dp)
        ) {
            items(floor.rooms, key = { it.id }) { r ->
                ElevatedCard(
                    onClick = { vm.selectRoom(r.id) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // ✅ ensure color always has an alpha channel
                            val c = Color(ensureOpaque(r.color))
                            Box(
                                Modifier
                                    .size(12.dp)
                                    .background(c, shape = MaterialTheme.shapes.small)
                            )
                            Text(r.name)
                        }
                        Text(
                            String.format(
                                "%.2f m²",
                                (r.w / floor.params.ppm) * (r.h / floor.params.ppm)
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(title: String, value: String, modifier: Modifier = Modifier) {
    ElevatedCard(modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary
            )
            Text(value, style = MaterialTheme.typography.titleLarge)
        }
    }
}

/** If incoming color int is RGB (no alpha), make it opaque ARGB. */
private fun ensureOpaque(rgbOrArgb: Int): Int {
    val a = (rgbOrArgb ushr 24) and 0xFF
    return if (a == 0) rgbOrArgb or 0xFF000000.toInt() else rgbOrArgb
}
