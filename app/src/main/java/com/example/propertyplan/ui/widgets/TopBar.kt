package com.example.propertyplan.ui.widgets

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopBar(
    onDownloadJson: () -> Unit,
    onSubmitDemo: () -> Unit
) {
    CenterAlignedTopAppBar(
        title = { Text("Property Plan Builder") },
        actions = {
            TextButton(onClick = onSubmitDemo) { Text("Submit") }
            Button(onClick = onDownloadJson) { Text("Export JSON") }
        }
    )
}
