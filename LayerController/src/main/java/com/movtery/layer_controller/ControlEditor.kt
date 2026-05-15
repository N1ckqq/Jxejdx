/*
 * Zalith Launcher 2
 * Copyright (C) 2025 MovTery <movtery228@qq.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package com.movtery.layer_controller

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.movtery.layer_controller.data.ButtonSize
import com.movtery.layer_controller.data.MAX_SIZE_PERCENTAGE
import com.movtery.layer_controller.data.MIN_SIZE_DP
import com.movtery.layer_controller.data.MIN_SIZE_PERCENTAGE
import com.movtery.layer_controller.layout.TextButton
import com.movtery.layer_controller.observable.ObservableButtonStyle
import com.movtery.layer_controller.observable.ObservableControlLayer
import com.movtery.layer_controller.observable.ObservableControlLayout
import com.movtery.layer_controller.observable.ObservableWidget
import com.movtery.layer_controller.utils.getWidgetPosition
import com.movtery.layer_controller.utils.snap.GuideLine
import com.movtery.layer_controller.utils.snap.LineDirection
import com.movtery.layer_controller.utils.snap.SnapMode
import com.movtery.layer_controller.utils.toPercentagePosition
import kotlin.math.roundToInt

@Composable
fun ControlEditorLayer(
    observedLayout: ObservableControlLayout,
    selectedWidget: ObservableWidget?,
    onButtonTap: (data: ObservableWidget, layer: ObservableControlLayer) -> Unit,
    onBackgroundClick: () -> Unit,
    floatingButtons: @Composable RowScope.() -> Unit,
    enableSnap: Boolean,
    snapInAllLayers: Boolean,
    snapMode: SnapMode,
    focusedLayer: ObservableControlLayer? = null,
    isDark: Boolean = isSystemInDarkTheme(),
    localSnapRange: Dp = 20.dp,
    snapThresholdValue: Dp = 4.dp
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        val primaryColor = MaterialTheme.colorScheme.primary

        val layers by observedLayout.layers.collectAsStateWithLifecycle()
        val styles by observedLayout.styles.collectAsStateWithLifecycle()

        val guideLines = remember { mutableStateMapOf<ObservableWidget, List<GuideLine>>() }

        val renderingLayers = when (focusedLayer) {
            null -> layers.filter { !it.editorHide }.reversed()
            else -> listOf(focusedLayer)
        }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            var resizingWidget by remember { mutableStateOf<ObservableWidget?>(null) }
            var dragTL by remember { mutableStateOf(Offset.Zero) }
            var dragBR by remember { mutableStateOf(Offset.Zero) }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(0f)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onBackgroundClick
                    )
            )

            val density = LocalDensity.current
            val screenSize = remember(maxWidth, maxHeight) {
                with(density) {
                    IntSize(width = maxWidth.roundToPx(), height = maxHeight.roundToPx())
                }
            }

            val selectedWidgetBounds by remember(selectedWidget, resizingWidget, dragTL, dragBR, screenSize) {
                derivedStateOf {
                    val widget = selectedWidget ?: return@derivedStateOf null
                    val widgetSize = widget.internalRenderSize
                    if (widgetSize == IntSize.Zero) return@derivedStateOf null
                    if (resizingWidget == widget) {
                        dragTL to dragBR
                    } else {
                        val position = getWidgetPosition(widget, widgetSize, screenSize)
                        position to Offset(position.x + widgetSize.width, position.y + widgetSize.height)
                    }
                }
            }

            ControlWidgetRenderer(
                screenSize = screenSize,
                isDark = isDark,
                renderingLayers = renderingLayers,
                styles = styles,
                enableSnap = enableSnap,
                snapInAllLayers = snapInAllLayers,
                snapMode = snapMode,
                localSnapRange = localSnapRange,
                snapThresholdValue = snapThresholdValue,
                onButtonTap = onButtonTap,
                drawLine = { data, line -> guideLines[data] = line },
                onLineCancel = { data -> guideLines.remove(data) }
            )

            Canvas(modifier = Modifier.fillMaxSize()) {
                guideLines.values.forEach { guidelines ->
                    guidelines.forEach { guideline ->
                        drawLine(guideline = guideline, color = primaryColor)
                    }
                }
                selectedWidgetBounds?.let { (tl, br) ->
                    val padding = 4.dp.toPx()
                    drawRect(
                        color = primaryColor,
                        topLeft = Offset(tl.x - padding, tl.y - padding),
                        size = Size((br.x - tl.x) + padding * 2, (br.y - tl.y) + padding * 2),
                        style = Stroke(width = 1.dp.toPx())
                    )
                }
            }

            selectedWidget
                ?.takeIf { it.widgetSize.type != ButtonSize.Type.WrapContent }
                ?.let { widget ->
                    selectedWidgetBounds?.let { (handleTL, handleBR) ->
                        val minSizePx = with(density) { MIN_SIZE_DP.dp.toPx() }
                        val oldSize = widget.widgetSize

                        val (minWidth, maxWidgetWidth) = when (oldSize.type) {
                            ButtonSize.Type.Dp -> minSizePx to screenSize.width.toFloat()
                            ButtonSize.Type.Percentage -> {
                                val ref = if (oldSize.widthReference == ButtonSize.Reference.ScreenWidth)
                                    screenSize.width else screenSize.height
                                (ref * 0.01f) to (ref * 1.0f)
                            }
                            ButtonSize.Type.WrapContent -> minSizePx to screenSize.width.toFloat()
                        }

                        val (minHeight, maxWidgetHeight) = when (oldSize.type) {
                            ButtonSize.Type.Dp -> minSizePx to screenSize.height.toFloat()
                            ButtonSize.Type.Percentage -> {
                                val ref = if (oldSize.heightReference == ButtonSize.Reference.ScreenWidth)
                                    screenSize.width else screenSize.height
                                (ref * 0.01f) to (ref * 1.0f)
                            }
                            ButtonSize.Type.WrapContent -> minSizePx to screenSize.height.toFloat()
                        }

                        val updateSizeAndPos = { newTopLeft: Offset, newSize: IntSize ->
                            widget.putRenderPosition(newTopLeft.toPercentagePosition(newSize, screenSize))
                            val cur = widget.widgetSize
                            val newWidgetSize = when (cur.type) {
                                ButtonSize.Type.Dp -> cur.copy(
                                    widthDp = with(density) { newSize.width.toDp().value },
                                    heightDp = with(density) { newSize.height.toDp().value }
                                )
                                ButtonSize.Type.Percentage -> {
                                    val wRef = when (cur.widthReference) {
                                        ButtonSize.Reference.ScreenWidth -> screenSize.width
                                        ButtonSize.Reference.ScreenHeight -> screenSize.height
                                    }
                                    val hRef = when (cur.heightReference) {
                                        ButtonSize.Reference.ScreenWidth -> screenSize.width
                                        ButtonSize.Reference.ScreenHeight -> screenSize.height
                                    }
                                    cur.copy(
                                        widthPercentage = (newSize.width.toFloat() / wRef * MAX_SIZE_PERCENTAGE)
                                            .roundToInt().coerceIn(MIN_SIZE_PERCENTAGE, MAX_SIZE_PERCENTAGE),
                                        heightPercentage = (newSize.height.toFloat() / hRef * MAX_SIZE_PERCENTAGE)
                                            .roundToInt().coerceIn(MIN_SIZE_PERCENTAGE, MAX_SIZE_PERCENTAGE)
                                    )
                                }
                                ButtonSize.Type.WrapContent -> cur
                            }
                            widget.putWidgetSize(newWidgetSize)
                        }

                        ResizeHandle(
                            isTopLeft = true,
                            currentPos = handleTL,
                            primaryColor = primaryColor,
                            density = density,
                            widget = widget,
                            screenSize = screenSize,
                            dragTL = dragTL,
                            dragBR = dragBR,
                            minWidth = minWidth,
                            maxWidgetWidth = maxWidgetWidth,
                            minHeight = minHeight,
                            maxWidgetHeight = maxWidgetHeight,
                            onDragTLChange = { dragTL = it },
                            onDragBRChange = { dragBR = it },
                            onResizingWidgetChange = { resizingWidget = it },
                            updateSizeAndPos = updateSizeAndPos
                        )
                        ResizeHandle(
                            isTopLeft = false,
                            currentPos = handleBR,
                            primaryColor = primaryColor,
                            density = density,
                            widget = widget,
                            screenSize = screenSize,
                            dragTL = dragTL,
                            dragBR = dragBR,
                            minWidth = minWidth,
                            maxWidgetWidth = maxWidgetWidth,
                            minHeight = minHeight,
                            maxWidgetHeight = maxWidgetHeight,
                            onDragTLChange = { dragTL = it },
                            onDragBRChange = { dragBR = it },
                            onResizingWidgetChange = { resizingWidget = it },
                            updateSizeAndPos = updateSizeAndPos
                        )
                    }
                }

            selectedWidget?.let {
                selectedWidgetBounds?.let { (tl, br) ->
                    var barSize by remember { mutableStateOf(IntSize.Zero) }
                    val centerX = (tl.x + br.x) / 2
                    val targetY = br.y + with(density) { 8.dp.toPx() }
                    val xPos = (centerX - barSize.width / 2)
                        .coerceIn(0f, maxOf(0f, screenSize.width.toFloat() - barSize.width))
                    val yPos = targetY
                        .coerceAtMost(maxOf(0f, screenSize.height.toFloat() - barSize.height))
                    Row(
                        modifier = Modifier
                            .onSizeChanged { barSize = it }
                            .alpha(if (barSize != IntSize.Zero) 1f else 0f)
                            .offset { IntOffset(xPos.roundToInt(), yPos.roundToInt()) },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        content = floatingButtons
                    )
                }
            }
        }
    }
}

@Composable
private fun ResizeHandle(
    isTopLeft: Boolean,
    currentPos: Offset,
    primaryColor: Color,
    density: Density,
    widget: ObservableWidget,
    screenSize: IntSize,
    dragTL: Offset,
    dragBR: Offset,
    minWidth: Float,
    maxWidgetWidth: Float,
    minHeight: Float,
    maxWidgetHeight: Float,
    onDragTLChange: (Offset) -> Unit,
    onDragBRChange: (Offset) -> Unit,
    onResizingWidgetChange: (ObservableWidget?) -> Unit,
    updateSizeAndPos: (Offset, IntSize) -> Unit,
    touchSize: Dp = 30.dp,
    visualSize: Dp = 14.dp
) {
    var activeHandleCount by remember { mutableIntStateOf(0) }
    val touchSizePx = with(density) { touchSize.toPx() }
    val visualSizePx = with(density) { visualSize.toPx() }

    // keep latest lambda captures without restarting pointerInput
    val latestDragTL by rememberUpdatedState(dragTL)
    val latestDragBR by rememberUpdatedState(dragBR)
    val latestUpdate by rememberUpdatedState(updateSizeAndPos)
    val latestOnTLChange by rememberUpdatedState(onDragTLChange)
    val latestOnBRChange by rememberUpdatedState(onDragBRChange)
    val latestOnResizing by rememberUpdatedState(onResizingWidgetChange)

    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    (currentPos.x - touchSizePx / 2).roundToInt(),
                    (currentPos.y - touchSizePx / 2).roundToInt()
                )
            }
            .size(touchSize)
            .drawBehind {
                drawCircle(color = primaryColor, radius = visualSizePx / 2, center = center)
            }
            .pointerInput(widget, screenSize, minWidth, maxWidgetWidth, minHeight, maxWidgetHeight) {
                detectDragGestures(
                    onDragStart = {
                        if (activeHandleCount == 0) {
                            val ws = widget.internalRenderSize
                            val pos = getWidgetPosition(widget, ws, screenSize)
                            latestOnTLChange(pos)
                            latestOnBRChange(Offset(pos.x + ws.width, pos.y + ws.height))
                        }
                        activeHandleCount++
                        latestOnResizing(widget)
                        widget.movingOffset = latestDragTL
                        widget.isEditingPos = true
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        if (isTopLeft) {
                            val newTL = latestDragTL + dragAmount
                            val finalTL = Offset(
                                newTL.x.coerceIn(maxOf(0f, latestDragBR.x - maxWidgetWidth), latestDragBR.x - minWidth),
                                newTL.y.coerceIn(maxOf(0f, latestDragBR.y - maxWidgetHeight), latestDragBR.y - minHeight)
                            )
                            latestOnTLChange(finalTL)
                            widget.movingOffset = finalTL
                            latestUpdate(
                                finalTL,
                                IntSize((latestDragBR.x - finalTL.x).roundToInt(), (latestDragBR.y - finalTL.y).roundToInt())
                            )
                        } else {
                            val newBR = latestDragBR + dragAmount
                            val finalBR = Offset(
                                newBR.x.coerceIn(latestDragTL.x + minWidth, minOf(screenSize.width.toFloat(), latestDragTL.x + maxWidgetWidth)),
                                newBR.y.coerceIn(latestDragTL.y + minHeight, minOf(screenSize.height.toFloat(), latestDragTL.y + maxWidgetHeight))
                            )
                            latestOnBRChange(finalBR)
                            latestUpdate(
                                latestDragTL,
                                IntSize((finalBR.x - latestDragTL.x).roundToInt(), (finalBR.y - latestDragTL.y).roundToInt())
                            )
                        }
                    },
                    onDragEnd = {
                        activeHandleCount = (activeHandleCount - 1).coerceAtLeast(0)
                        if (activeHandleCount == 0) {
                            latestOnResizing(null)
                            widget.isEditingPos = false
                        }
                    },
                    onDragCancel = {
                        activeHandleCount = (activeHandleCount - 1).coerceAtLeast(0)
                        if (activeHandleCount == 0) {
                            latestOnResizing(null)
                            widget.isEditingPos = false
                        }
                    }
                )
            }
    )
}

private fun DrawScope.drawLine(
    guideline: GuideLine,
    color: Color,
    strokeWidth: Float = 2f
) {
    when (guideline.direction) {
        LineDirection.Vertical -> drawLine(
            color = color,
            start = Offset(guideline.coordinate, 0f),
            end = Offset(guideline.coordinate, size.height),
            strokeWidth = strokeWidth
        )
        LineDirection.Horizontal -> drawLine(
            color = color,
            start = Offset(0f, guideline.coordinate),
            end = Offset(size.width, guideline.coordinate),
            strokeWidth = strokeWidth
        )
    }
}

@Composable
private fun ControlWidgetRenderer(
    screenSize: IntSize,
    isDark: Boolean,
    renderingLayers: List<ObservableControlLayer>,
    styles: List<ObservableButtonStyle>,
    enableSnap: Boolean,
    snapInAllLayers: Boolean,
    snapMode: SnapMode,
    localSnapRange: Dp,
    snapThresholdValue: Dp,
    onButtonTap: (data: ObservableWidget, layer: ObservableControlLayer) -> Unit,
    drawLine: (ObservableWidget, List<GuideLine>) -> Unit,
    onLineCancel: (ObservableWidget) -> Unit
) {
    val allWidgetsMap = remember { mutableStateMapOf<ObservableControlLayer, List<ObservableWidget>>() }
    val latestSnapInAllLayers by rememberUpdatedState(snapInAllLayers)

    Layout(
        content = {
            renderingLayers.forEach { layer ->
                val normalButtons by layer.normalButtons.collectAsStateWithLifecycle()
                val textBoxes by layer.textBoxes.collectAsStateWithLifecycle()

                allWidgetsMap[layer] = normalButtons + textBoxes

                textBoxes.forEach { data ->
                    RenderWidget(
                        data = data,
                        layer = layer,
                        isPressed = false,
                        styles = styles,
                        screenSize = screenSize,
                        isDark = isDark,
                        enableSnap = enableSnap,
                        snapMode = snapMode,
                        localSnapRange = localSnapRange,
                        snapThresholdValue = snapThresholdValue,
                        drawLine = drawLine,
                        onLineCancel = onLineCancel,
                        allWidgetsMap = allWidgetsMap,
                        snapInAllLayers = latestSnapInAllLayers,
                        onButtonTap = onButtonTap
                    )
                }
                normalButtons.forEach { data ->
                    RenderWidget(
                        data = data,
                        layer = layer,
                        isPressed = data.isPressed,
                        styles = styles,
                        screenSize = screenSize,
                        isDark = isDark,
                        enableSnap = enableSnap,
                        snapMode = snapMode,
                        localSnapRange = localSnapRange,
                        snapThresholdValue = snapThresholdValue,
                        drawLine = drawLine,
                        onLineCancel = onLineCancel,
                        allWidgetsMap = allWidgetsMap,
                        snapInAllLayers = latestSnapInAllLayers,
                        onButtonTap = onButtonTap
                    )
                }
            }
        }
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints) }

        var index = 0
        fun ObservableWidget.putSize() {
            if (index < placeables.size) {
                internalRenderSize = IntSize(placeables[index].width, placeables[index].height)
                index++
            }
        }
        renderingLayers.fastForEach { layer ->
            layer.textBoxes.value.fastForEach { it.putSize() }
            layer.normalButtons.value.fastForEach { it.putSize() }
        }

        layout(constraints.maxWidth, constraints.maxHeight) {
            var pi = 0
            fun ObservableWidget.place() {
                if (pi < placeables.size) {
                    val p = placeables[pi]
                    val pos = getWidgetPosition(this, IntSize(p.width, p.height), screenSize)
                    p.place(pos.x.toInt(), pos.y.toInt())
                    pi++
                }
            }
            renderingLayers.fastForEach { layer ->
                layer.textBoxes.value.fastForEach { it.place() }
                layer.normalButtons.value.fastForEach { it.place() }
            }
        }
    }
}

@Composable
private fun RenderWidget(
    data: ObservableWidget,
    layer: ObservableControlLayer,
    isPressed: Boolean,
    styles: List<ObservableButtonStyle>,
    screenSize: IntSize,
    isDark: Boolean,
    enableSnap: Boolean,
    snapMode: SnapMode,
    localSnapRange: Dp,
    snapThresholdValue: Dp,
    drawLine: (ObservableWidget, List<GuideLine>) -> Unit,
    onLineCancel: (ObservableWidget) -> Unit,
    allWidgetsMap: Map<ObservableControlLayer, List<ObservableWidget>>,
    snapInAllLayers: Boolean,
    onButtonTap: (data: ObservableWidget, layer: ObservableControlLayer) -> Unit
) {
    TextButton(
        isEditMode = true,
        data = data,
        allStyles = styles,
        screenSize = screenSize,
        isDark = isDark,
        enableSnap = enableSnap,
        snapMode = snapMode,
        localSnapRange = localSnapRange,
        getOtherWidgets = {
            allWidgetsMap
                .filter { (l, _) -> snapInAllLayers || l == layer }
                .values.flatten()
                .filter { it != data }
        },
        snapThresholdValue = snapThresholdValue,
        drawLine = drawLine,
        onLineCancel = onLineCancel,
        isPressed = isPressed,
        onTapInEditMode = { onButtonTap(data, layer) }
    )
}
