package com.example.propertyplan.ui.adaptive

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun AdaptivePanelsScaffold(
    // state
    leftOpen: Boolean,
    rightOpen: Boolean,
    // slots
    topBar: @Composable () -> Unit,
    bottomBar: @Composable () -> Unit,
    leftPanel: @Composable () -> Unit,
    rightPanel: @Composable () -> Unit,
    centerCanvas: @Composable () -> Unit,
    // controls
    onToggleLeft: () -> Unit,
    onToggleRight: () -> Unit,
    // layout knobs
    leftMax: Dp = 380.dp,
    rightMax: Dp = 360.dp,
    mediumLayout: Boolean,    // true = slide-in panels
    expandedLayout: Boolean   // true = permanent panels
) {
    Scaffold(topBar = topBar, bottomBar = bottomBar) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {

            // Center canvas rendered first (behind panels)
            Box(Modifier.fillMaxSize()) {
                centerCanvas()
            }

            when {
                // EXPANDED: use a Row with fixed-width side panels; no .align() here
                expandedLayout -> {
                    Row(Modifier.fillMaxSize()) {
                        Surface(
                            tonalElevation = 2.dp,
                            modifier = Modifier
                                .widthIn(max = leftMax)
                                .fillMaxHeight()
                        ) { leftPanel() }

                        // Middle space is occupied by the already-drawn canvas behind this Row.
                        // We add a spacer to push the right panel to the end.
                        androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))

                        Surface(
                            tonalElevation = 2.dp,
                            modifier = Modifier
                                .widthIn(max = rightMax)
                                .fillMaxHeight()
                        ) { rightPanel() }
                    }
                }

                // MEDIUM: slide-in/out side panels over the canvas (BoxScope, so .align works)
                mediumLayout -> {
                    AnimatedVisibility(
                        visible = leftOpen,
                        enter = slideInHorizontally(spring()) { -it },
                        exit = slideOutHorizontally(spring()) { -it },
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .widthIn(max = leftMax)
                            .fillMaxHeight()
                            .padding(8.dp)
                    ) { ElevatedCard(Modifier.fillMaxSize()) { leftPanel() } }

                    AnimatedVisibility(
                        visible = rightOpen,
                        enter = slideInHorizontally(spring()) { it },
                        exit = slideOutHorizontally(spring()) { it },
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .widthIn(max = rightMax)
                            .fillMaxHeight()
                            .padding(8.dp)
                    ) { ElevatedCard(Modifier.fillMaxSize()) { rightPanel() } }

                    // Edge tabs (peek handles) to toggle panels
                    EdgeTab(
                        text = if (leftOpen) "Hide" else "Tools",
                        align = Alignment.CenterStart,
                        onClick = onToggleLeft
                    )
                    EdgeTab(
                        text = if (rightOpen) "Hide" else "Inspector",
                        align = Alignment.CenterEnd,
                        onClick = onToggleRight
                    )
                }

                // COMPACT: caller shows bottom sheets; no side panels here
                else -> Unit
            }
        }
    }
}

@Composable
private fun BoxScope.EdgeTab(text: String, align: Alignment, onClick: () -> Unit) {
    ElevatedButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        modifier = Modifier
            .align(align)
            .padding(
                start = if (align == Alignment.CenterStart) 4.dp else 0.dp,
                end = if (align == Alignment.CenterEnd) 4.dp else 0.dp
            )
    ) { Text(text) }
}
