/*
 * Copyright 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.compose.material3

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableDefaults
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.requiredHeightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.SheetValue.Expanded
import androidx.compose.material3.SheetValue.Hidden
import androidx.compose.material3.SheetValue.PartiallyExpanded
import androidx.compose.material3.internal.Strings
import androidx.compose.material3.internal.draggableAnchors
import androidx.compose.material3.internal.getString
import androidx.compose.material3.tokens.MotionSchemeKeyTokens
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastMap
import androidx.compose.ui.util.fastMaxOfOrNull
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * CompositionLocal that holds the measured height of the bottom bar (in pixels).
 * Used internally to adjust the bottom sheet's anchor positions.
 */
internal val LocalBottomBarHeightPx = compositionLocalOf { 0f }

/**
 * Modified version of [BottomSheetScaffold] that supports a [bottomBar].
 * The bottom sheet will slide above the bottom bar and never cover it.
 *
 * @param bottomBar optional bottom bar (e.g., [NavigationBar] or [BottomAppBar]) to be placed at
 * the very bottom of the screen. The bottom sheet will respect its height.
 */
@Composable
@ExperimentalMaterial3Api
fun BottomSheetScaffold(
    sheetContent: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
    scaffoldState: BottomSheetScaffoldState = rememberBottomSheetScaffoldState(),
    sheetPeekHeight: Dp = BottomSheetDefaults.SheetPeekHeight,
    sheetMaxWidth: Dp = BottomSheetDefaults.SheetMaxWidth,
    sheetShape: Shape = BottomSheetDefaults.ExpandedShape,
    sheetContainerColor: Color = BottomSheetDefaults.ContainerColor,
    sheetContentColor: Color = contentColorFor(sheetContainerColor),
    sheetTonalElevation: Dp = 0.dp,
    sheetShadowElevation: Dp = BottomSheetDefaults.Elevation,
    sheetDragHandle: @Composable (() -> Unit)? = { BottomSheetDefaults.DragHandle() },
    sheetSwipeEnabled: Boolean = true,
    topBar: @Composable (() -> Unit)? = null,
    bottomBar: @Composable (() -> Unit)? = null,          // <-- NEW parameter
    snackbarHost: @Composable (SnackbarHostState) -> Unit = { SnackbarHost(it) },
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = contentColorFor(containerColor),
    content: @Composable (PaddingValues) -> Unit,
) {
    Box(modifier.fillMaxSize().background(containerColor)) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            BottomSheetScaffoldLayout(
                topBar = topBar,
                body = { content(PaddingValues(bottom = sheetPeekHeight)) },
                snackbarHost = { snackbarHost(scaffoldState.snackbarHostState) },
                sheetOffset = { scaffoldState.bottomSheetState.requireOffset() },
                sheetState = scaffoldState.bottomSheetState,
                bottomSheet = {
                    // The bottom bar height is provided via CompositionLocal
                    val bottomBarHeightPx = LocalBottomBarHeightPx.current
                    StandardBottomSheet(
                        state = scaffoldState.bottomSheetState,
                        peekHeight = sheetPeekHeight,
                        sheetMaxWidth = sheetMaxWidth,
                        sheetSwipeEnabled = sheetSwipeEnabled,
                        shape = sheetShape,
                        containerColor = sheetContainerColor,
                        contentColor = sheetContentColor,
                        tonalElevation = sheetTonalElevation,
                        shadowElevation = sheetShadowElevation,
                        dragHandle = sheetDragHandle,
                        bottomBarHeightPx = bottomBarHeightPx,   // <-- pass measured height
                        content = sheetContent,
                    )
                },
                bottomBar = bottomBar,
            )
        }
    }
}

@ExperimentalMaterial3Api
@Stable
class BottomSheetScaffoldState(
    val bottomSheetState: SheetState,
    val snackbarHostState: SnackbarHostState,
)

@Composable
@ExperimentalMaterial3Api
fun rememberBottomSheetScaffoldState(
    bottomSheetState: SheetState = rememberStandardBottomSheetState(),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
): BottomSheetScaffoldState = remember(bottomSheetState, snackbarHostState) {
    BottomSheetScaffoldState(
        bottomSheetState = bottomSheetState,
        snackbarHostState = snackbarHostState,
    )
}

@Composable
@ExperimentalMaterial3Api
fun rememberStandardBottomSheetState(
    initialValue: SheetValue = PartiallyExpanded,
    confirmValueChange: (SheetValue) -> Boolean = { true },
    skipHiddenState: Boolean = true,
) = rememberSheetState(
    confirmValueChange = confirmValueChange,
    initialValue = initialValue,
    skipHiddenState = skipHiddenState,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StandardBottomSheet(
    state: SheetState,
    peekHeight: Dp,
    sheetMaxWidth: Dp,
    sheetSwipeEnabled: Boolean,
    shape: Shape,
    containerColor: Color,
    contentColor: Color,
    tonalElevation: Dp,
    shadowElevation: Dp,
    dragHandle: @Composable (() -> Unit)?,
    bottomBarHeightPx: Float,   // <-- NEW: measured bottom bar height in pixels
    content: @Composable ColumnScope.() -> Unit,
) {
    val showMotion: FiniteAnimationSpec<Float> = MaterialTheme.motionScheme.defaultSpatialSpec()
    val hideMotion: FiniteAnimationSpec<Float> = MaterialTheme.motionScheme.fastEffectsSpec()
    val spatialFlingSpec: FiniteAnimationSpec<Float> = MaterialTheme.motionScheme.defaultSpatialSpec()

    SideEffect {
        state.showMotionSpec = showMotion
        state.hideMotionSpec = hideMotion
        state.anchoredDraggableMotionSpec = spatialFlingSpec
    }

    val scope = rememberCoroutineScope()
    val orientation = Orientation.Vertical
    val peekHeightPx = with(LocalDensity.current) { peekHeight.toPx() }
    val anchoredDraggableFlingBehavior =
        AnchoredDraggableDefaults.flingBehavior(
            state = state.anchoredDraggableState,
            positionalThreshold = { _ -> state.positionalThreshold.invoke() },
            animationSpec = spatialFlingSpec,
        )

    val nestedScroll =
        if (sheetSwipeEnabled) {
            Modifier.nestedScroll(
                remember(state.anchoredDraggableState) {
                    ConsumeSwipeWithinBottomSheetBoundsNestedScrollConnection(
                        sheetState = state,
                        orientation = orientation,
                        flingBehavior = anchoredDraggableFlingBehavior,
                    )
                }
            )
        } else {
            Modifier
        }

    Surface(
        modifier = Modifier
            .widthIn(max = sheetMaxWidth)
            .fillMaxWidth()
            .requiredHeightIn(min = peekHeight)
            .then(nestedScroll)
            .draggableAnchors(state.anchoredDraggableState, orientation) { sheetSize, constraints ->
                // The total height available for the sheet is the layout height minus the bottom bar height
                val layoutHeight = constraints.maxHeight.toFloat()
                val effectiveLayoutHeight = layoutHeight - bottomBarHeightPx
                val sheetHeight = sheetSize.height.toFloat()

                val newAnchors = DraggableAnchors {
                    val isHiddenAnchorAvailable =
                        sheetHeight == 0f || peekHeightPx == 0f || !state.skipHiddenState

                    val isInitialLayout = state.anchoredDraggableState.anchors.size == 0
                    val isStableAtPartial =
                        state.currentValue == PartiallyExpanded && !state.isAnimationRunning

                    val isAmbiguousPartialAllowed =
                        peekHeightPx == 0f && (isInitialLayout || isStableAtPartial)

                    val isPartiallyExpandedAnchorAvailable =
                        !state.skipPartiallyExpanded &&
                                (peekHeightPx > 0f || isAmbiguousPartialAllowed) &&
                                peekHeightPx != sheetHeight

                    val isExpandedAnchorAvailable = sheetHeight > 0f

                    require(
                        isHiddenAnchorAvailable || isPartiallyExpandedAnchorAvailable || isExpandedAnchorAvailable
                    ) {
                        "BottomSheetScaffold: Require at least 1 anchor to be initialized"
                    }

                    if (isPartiallyExpandedAnchorAvailable) {
                        PartiallyExpanded at (effectiveLayoutHeight - peekHeightPx)
                    }
                    if (isHiddenAnchorAvailable) {
                        // Hidden = sheet completely scrolled off-screen above the bottom bar
                        Hidden at effectiveLayoutHeight
                    }
                    if (isExpandedAnchorAvailable) {
                        Expanded at maxOf(effectiveLayoutHeight - sheetHeight, 0f)
                    }
                }

                val newTarget = when (val oldTarget = state.targetValue) {
                    Hidden -> if (newAnchors.hasPositionFor(Hidden)) Hidden else oldTarget
                    PartiallyExpanded -> when {
                        newAnchors.hasPositionFor(PartiallyExpanded) -> PartiallyExpanded
                        newAnchors.hasPositionFor(Expanded) -> Expanded
                        newAnchors.hasPositionFor(Hidden) -> Hidden
                        else -> oldTarget
                    }
                    Expanded -> if (newAnchors.hasPositionFor(Expanded)) Expanded else Hidden
                }

                return@draggableAnchors newAnchors to newTarget
            }
            .anchoredDraggable(
                state = state.anchoredDraggableState,
                orientation = orientation,
                enabled = sheetSwipeEnabled,
                flingBehavior = anchoredDraggableFlingBehavior,
            )
            .verticalScaleUp(state, bottomBarHeightPx),
        shape = shape,
        color = containerColor,
        contentColor = contentColor,
        tonalElevation = tonalElevation,
        shadowElevation = shadowElevation,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScaleDown(state, bottomBarHeightPx)
        ) {
            if (dragHandle != null) {
                val partialExpandActionLabel = getString(Strings.BottomSheetPartialExpandDescription)
                val dismissActionLabel = getString(Strings.BottomSheetDismissDescription)
                val expandActionLabel = getString(Strings.BottomSheetExpandDescription)
                DragHandleWithTooltip(
                    modifier = Modifier
                        .clickable {
                            when (state.currentValue) {
                                Expanded -> scope.launch {
                                    if (!state.skipHiddenState) state.hide() else state.partialExpand()
                                }
                                PartiallyExpanded -> scope.launch { state.expand() }
                                else -> scope.launch { state.show() }
                            }
                        }
                        .semantics(mergeDescendants = true) {
                            with(state) {
                                if (anchoredDraggableState.anchors.size > 1 && sheetSwipeEnabled) {
                                    if (currentValue == PartiallyExpanded) {
                                        expand(expandActionLabel) {
                                            val canExpand = confirmValueChange(Expanded)
                                            if (canExpand) scope.launch { expand() }
                                            return@expand canExpand
                                        }
                                    } else {
                                        collapse(partialExpandActionLabel) {
                                            val canPartiallyExpand = confirmValueChange(PartiallyExpanded)
                                            scope.launch { partialExpand() }
                                            return@collapse canPartiallyExpand
                                        }
                                    }
                                    if (!state.skipHiddenState) {
                                        dismiss(dismissActionLabel) {
                                            val canHide = confirmValueChange(Hidden)
                                            scope.launch { hide() }
                                            return@dismiss canHide
                                        }
                                    }
                                }
                            }
                        },
                    content = dragHandle,
                )
            }
            content()
        }
    }
}

/**
 * Modified layout that uses [SubcomposeLayout] to first measure the bottom bar,
 * then provides its height to the sheet composition.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BottomSheetScaffoldLayout(
    topBar: @Composable (() -> Unit)?,
    body: @Composable () -> Unit,
    bottomSheet: @Composable () -> Unit,
    snackbarHost: @Composable () -> Unit,
    bottomBar: @Composable (() -> Unit)?,
    sheetOffset: () -> Float,
    sheetState: SheetState,
) {
    SubcomposeLayout { constraints ->
        val layoutWidth = constraints.maxWidth
        val layoutHeight = constraints.maxHeight
        val looseConstraints = constraints.copy(minWidth = 0, minHeight = 0)

        // Step 1: Measure bottom bar (if present) to get its height
        val bottomBarPlaceables = subcompose("bottomBar") {
            bottomBar?.let { Box { it() } } ?: Box {}
        }.map { it.measure(looseConstraints) }
        val bottomBarHeight = bottomBarPlaceables.fastMaxOfOrNull { it.height } ?: 0

        // Step 2: Provide the measured height to the sheet composition via CompositionLocal
        val sheetPlaceables = subcompose("sheet") {
            CompositionLocalProvider(LocalBottomBarHeightPx provides bottomBarHeight.toFloat()) {
                bottomSheet()
            }
        }.map { measurable ->
            // The sheet is constrained to the area above the bottom bar
            val sheetConstraints = looseConstraints.copy(maxHeight = layoutHeight - bottomBarHeight)
            measurable.measure(sheetConstraints)
        }

        // Step 3: Measure the rest (top bar, body, snackbar)
        val topBarPlaceables = subcompose("topBar") {
            topBar?.let { Box { it() } } ?: Box {}
        }.map { it.measure(looseConstraints) }
        val topBarHeight = topBarPlaceables.fastMaxOfOrNull { it.height } ?: 0

        val bodyConstraints = looseConstraints.copy(maxHeight = layoutHeight - topBarHeight)
        val bodyPlaceables = subcompose("body") { body() }
            .map { it.measure(bodyConstraints) }

        val snackbarPlaceables = subcompose("snackbar") { snackbarHost() }
            .map { it.measure(looseConstraints) }

        layout(layoutWidth, layoutHeight) {
            // Placement
            val sheetWidth = sheetPlaceables.fastMaxOfOrNull { it.width } ?: 0
            val sheetOffsetX = max(0, (layoutWidth - sheetWidth) / 2)

            val sheetOffsetPx = sheetOffset().roundToInt()
            val sheetY = layoutHeight - bottomBarHeight - sheetOffsetPx

            val snackbarWidth = snackbarPlaceables.fastMaxOfOrNull { it.width } ?: 0
            val snackbarHeight = snackbarPlaceables.fastMaxOfOrNull { it.height } ?: 0
            val snackbarOffsetX = (layoutWidth - snackbarWidth) / 2
            val snackbarOffsetY = when (sheetState.currentValue) {
                PartiallyExpanded -> sheetY - snackbarHeight
                Expanded, Hidden -> layoutHeight - bottomBarHeight - snackbarHeight
            }

            // Z-order: body -> top bar -> bottom bar -> sheet -> snackbar
            bodyPlaceables.fastForEach { it.placeRelative(0, topBarHeight) }
            topBarPlaceables.fastForEach { it.placeRelative(0, 0) }
            bottomBarPlaceables.fastForEach { it.placeRelative(0, layoutHeight - bottomBarHeight) }
            sheetPlaceables.fastForEach { it.placeRelative(sheetOffsetX, sheetY) }
            snackbarPlaceables.fastForEach { it.placeRelative(snackbarOffsetX, snackbarOffsetY) }
        }
    }
}

// --- Helper scaling modifiers adjusted for bottom bar height ---

@OptIn(ExperimentalMaterial3Api::class)
internal fun Modifier.verticalScaleUp(state: SheetState, bottomBarHeightPx: Float) = graphicsLayer {
    val offset = state.anchoredDraggableState.offset
    // The min anchor is now the Hidden anchor (effectiveLayoutHeight), not 0.
    // We need to compute overflow relative to the expanded anchor (the smallest offset)
    val minAnchor = state.anchoredDraggableState.anchors.minPosition()
    val overflow = if (offset < minAnchor) minAnchor - offset else 0f
    scaleY = if (overflow > 0f) (size.height + overflow) / size.height else 1f
    transformOrigin = TransformOrigin(pivotFractionX = 0.5f, pivotFractionY = 0f)
}

@OptIn(ExperimentalMaterial3Api::class)
internal fun Modifier.verticalScaleDown(state: SheetState, bottomBarHeightPx: Float) = graphicsLayer {
    val offset = state.anchoredDraggableState.offset
    val minAnchor = state.anchoredDraggableState.anchors.minPosition()
    val overflow = if (offset < minAnchor) minAnchor - offset else 0f
    scaleY = if (overflow > 0f) 1f / ((size.height + overflow) / size.height) else 1f
    transformOrigin = TransformOrigin(pivotFractionX = 0.5f, pivotFractionY = 0f)
}
