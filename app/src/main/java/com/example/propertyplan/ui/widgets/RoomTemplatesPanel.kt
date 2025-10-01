package com.example.propertyplan.ui.widgets

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.propertyplan.ui.UiDimens
import com.example.propertyplan.vm.PlanViewModel

data class TemplateDef(val name: String, val w: Float, val h: Float, val isUtility: Boolean)


private val templateDefs = listOf(
    TemplateDef("Small Room", 240f, 160f, false),
    TemplateDef("Medium Room", 360f, 240f, false),
    TemplateDef("Large Room", 480f, 320f, false),
    TemplateDef("Utility", 300f, 220f, true)
)

@Composable
fun RoomTemplatesPanel(vm: PlanViewModel, canvasWidth: Float, canvasHeight: Float) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Add Rooms")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            templateDefs.forEach { t ->
                ElevatedCard(Modifier.weight(1f)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(if (t.isUtility) "${t.name} • Utility" else t.name, style = MaterialTheme.typography.titleMedium)
                        Text("${t.w.toInt()} × ${t.h.toInt()} px", style = MaterialTheme.typography.labelSmall)
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                vm.addRoomFromTemplate(
                                    t.name, t.w, t.h, t.isUtility,
                                    cx = canvasWidth / 2f, cy = canvasHeight / 2f
                                )
                            },
                            modifier = Modifier.height(UiDimens.Touch)
                        ) { Text("Add") }
                    }
                }
            }
        }
    }
}

