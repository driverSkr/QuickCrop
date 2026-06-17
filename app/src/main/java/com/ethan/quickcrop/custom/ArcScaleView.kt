package com.ethan.quickcrop.custom

import android.os.SystemClock
import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Compose 版弧形刻度尺状态。
 *
 * 状态和绘制拆开，后续其它业务场景可以直接复用这个 state 来做外部同步、重置和读取当前值。
 */
@Stable
class ArcScaleViewState(
    val scaleMin: Int = DEFAULT_SCALE_MIN,
    val scaleMaxLength: Int = DEFAULT_SCALE_MAX_LENGTH,
    initialValue: Float = DEFAULT_VALUE
) {
    var currentValue by mutableFloatStateOf(
        initialValue.coerceIn(scaleMin.toFloat(), (scaleMin + scaleMaxLength).toFloat())
    )
        private set

    internal var frameTick by mutableIntStateOf(0)
        private set

    internal var tideVersion by mutableIntStateOf(0)
        private set

    private var lastMoveValue = currentValue
    private val tideTickStateMap = mutableStateMapOf<Int, Long>()

    internal val tideTickStates: Map<Int, Long>
        get() = tideTickStateMap

    val maxValue: Float
        get() = (scaleMin + scaleMaxLength).toFloat()

    fun syncCurrentValue(value: Float) {
        currentValue = value.coerceIn(scaleMin.toFloat(), maxValue)
        Log.d(TAG, "外部同步 Compose 刻度值: value=$currentValue")
    }

    fun reset() {
        syncCurrentValue(DEFAULT_VALUE)
    }

    internal fun startTideEffectForDrag() {
        lastMoveValue = currentValue
    }

    internal fun dragBy(deltaX: Float, eachScalePx: Float, onValueChanged: (Float) -> Unit) {
        if (eachScalePx <= 0F) {
            Log.w(TAG, "拖动刻度失败，刻度间距无效: eachScalePx=$eachScalePx")
            return
        }

        val targetValue = (currentValue - deltaX / eachScalePx)
            .coerceIn(scaleMin.toFloat(), maxValue)
        if (abs(targetValue - currentValue) <= VALUE_EPSILON) {
            return
        }

        updateTideEffectAfterMove(targetValue)
        currentValue = targetValue
        onValueChanged(targetValue)
    }

    internal suspend fun snapToZeroIfNeeded(
        onValueChanged: (Float) -> Unit,
        onEndMove: () -> Unit
    ): Boolean {
        val startValue = currentValue
        val displayValue = startValue.roundToInt()
        if (displayValue != ZERO_SNAP_DISPLAY_VALUE || abs(startValue) <= ZERO_SNAP_EPSILON) {
            return false
        }

        Log.d(TAG, "启动 Compose 0 刻度吸附动画: startValue=$startValue")
        val animatable = Animatable(startValue)
        animatable.animateTo(
            targetValue = DEFAULT_VALUE,
            animationSpec = tween(durationMillis = ZERO_SNAP_ANIMATION_DURATION_MS)
        ) {
            currentValue = value
            onValueChanged(value)
        }
        currentValue = DEFAULT_VALUE
        onValueChanged(DEFAULT_VALUE)
        onEndMove()
        return true
    }

    internal fun clearTideEffect() {
        tideTickStateMap.clear()
        frameTick++
    }

    internal fun hasTideTicks(): Boolean {
        return tideTickStateMap.isNotEmpty()
    }

    internal fun updateFrame(nowMs: Long) {
        val expiredKeys = tideTickStateMap.filterValues { triggerTimeMs ->
            nowMs - triggerTimeMs >= TIDE_RELEASE_DURATION_MS
        }.keys
        expiredKeys.forEach { tideTickStateMap.remove(it) }
        frameTick++
    }

    private fun updateTideEffectAfterMove(newValue: Float) {
        val crossedTicks = findCrossedTicks(lastMoveValue, newValue)
        if (crossedTicks.isNotEmpty()) {
            val now = SystemClock.uptimeMillis()
            crossedTicks.forEachIndexed { index, tickValue ->
                // 同一次 MOVE 中跨过多根刻度时，按穿过顺序错开回落进度，保留旧 View 的拖动质感。
                val elapsedOffset = (crossedTicks.lastIndex - index) * TIDE_STAIR_DELAY_MS
                tideTickStateMap[tickValue] = now - elapsedOffset
            }
            tideVersion++
        }
        lastMoveValue = newValue
    }

    private fun findCrossedTicks(fromValue: Float, toValue: Float): List<Int> {
        if (fromValue == toValue) {
            return emptyList()
        }

        val crossedTicks = mutableListOf<Int>()
        if (fromValue < toValue) {
            var tickValue = floor(fromValue).toInt() + 1
            while (tickValue <= floor(toValue).toInt()) {
                crossedTicks.add(tickValue)
                tickValue++
            }
        } else {
            var tickValue = floor(fromValue).toInt()
            while (tickValue > floor(toValue).toInt()) {
                crossedTicks.add(tickValue)
                tickValue--
            }
        }
        return crossedTicks
    }

    companion object {
        private const val TAG = "ArcScaleView"
        private const val DEFAULT_SCALE_MIN = -45
        private const val DEFAULT_SCALE_MAX_LENGTH = 90
        private const val DEFAULT_VALUE = 0F
        private const val VALUE_EPSILON = 0.0001F
        private const val ZERO_SNAP_DISPLAY_VALUE = 0
        private const val ZERO_SNAP_EPSILON = 0.001F
        private const val ZERO_SNAP_ANIMATION_DURATION_MS = 180
        private const val TIDE_RELEASE_DURATION_MS = 320L
        private const val TIDE_STAIR_DELAY_MS = 28L
    }
}

typealias ArcValueScaleState = ArcScaleViewState

@Composable
fun rememberArcScaleViewState(
    scaleMin: Int = -45,
    scaleMaxLength: Int = 90,
    initialValue: Float = 0F
): ArcScaleViewState {
    return remember(scaleMin, scaleMaxLength) {
        ArcScaleViewState(
            scaleMin = scaleMin,
            scaleMaxLength = scaleMaxLength,
            initialValue = initialValue
        )
    }
}

@Composable
fun rememberArcValueScaleState(
    scaleMin: Int = -45,
    scaleMaxLength: Int = 90,
    initialValue: Float = 0F
): ArcValueScaleState {
    return rememberArcScaleViewState(
        scaleMin = scaleMin,
        scaleMaxLength = scaleMaxLength,
        initialValue = initialValue
    )
}

/**
 * 通用弧形数值刻度盘。
 *
 * 旧的 ArcScaleView 继续保留，业务页面优先使用 ArcValueScale 这个更中性的命名。
 */
@Composable
fun ArcValueScale(
    state: ArcValueScaleState,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onValueChanged: (Float) -> Unit = {},
    onStartMove: () -> Unit = {},
    onEndMove: () -> Unit = {}
) {
    ArcScaleView(
        state = state,
        modifier = modifier,
        enabled = enabled,
        onValueChanged = onValueChanged,
        onStartMove = onStartMove,
        onEndMove = onEndMove
    )
}

/**
 * Compose 版弧形刻度尺。
 *
 * 该组件只负责绘制和手势选择，业务层通过回调接收角度变化，方便在裁剪页之外复用。
 */
@Composable
fun ArcScaleView(
    state: ArcScaleViewState,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onValueChanged: (Float) -> Unit = {},
    onStartMove: () -> Unit = {},
    onEndMove: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    var zeroSnapJob: Job? by remember { mutableStateOf(null) }
    val currentOnValueChanged by rememberUpdatedState(onValueChanged)
    val currentOnStartMove by rememberUpdatedState(onStartMove)
    val currentOnEndMove by rememberUpdatedState(onEndMove)
    // 旧 ScaleView 这里使用的是裸像素 30，而不是 dp；保持一致才能对齐原刻度密度和拖动手感。
    val eachScalePx = EACH_SCALE_PX

    LaunchedEffect(state.tideVersion) {
        while (state.hasTideTicks()) {
            withFrameNanos { }
            state.updateFrame(SystemClock.uptimeMillis())
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            zeroSnapJob?.cancel()
            state.clearTideEffect()
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            // 刻度尺视觉高度是组件规格，外部只负责摆放位置，避免不同页面接入时出现高度不一致。
            .requiredHeight(ARC_SCALE_VIEW_HEIGHT_DP.dp)
            .pointerInput(enabled, eachScalePx) {
                if (!enabled) {
                    return@pointerInput
                }
                detectDragGestures(
                    onDragStart = {
                        zeroSnapJob?.cancel()
                        state.startTideEffectForDrag()
                        currentOnStartMove()
                    },
                    onDragEnd = {
                        zeroSnapJob = scope.launch {
                            if (!state.snapToZeroIfNeeded(currentOnValueChanged, currentOnEndMove)) {
                                currentOnEndMove()
                            }
                        }
                    },
                    onDragCancel = {
                        zeroSnapJob = scope.launch {
                            if (!state.snapToZeroIfNeeded(currentOnValueChanged, currentOnEndMove)) {
                                currentOnEndMove()
                            }
                        }
                    }
                ) { change, dragAmount ->
                    val deltaX = if (change.positionChange().x == 0F) {
                        dragAmount.x
                    } else {
                        change.positionChange().x
                    }
                    change.consume()
                    state.dragBy(deltaX, eachScalePx, currentOnValueChanged)
                }
            }
    ) {
        // 读取 frameTick 触发潮汐动画逐帧重绘。
        state.frameTick

        val centerX = size.width / 2F
        val totalX = centerX + (state.scaleMin - state.currentValue) * eachScalePx
        val nowMs = SystemClock.uptimeMillis()
        drawTicks(
            state = state,
            totalX = totalX,
            eachScalePx = eachScalePx,
            nowMs = nowMs
        )
        drawFixedZeroIndicator(centerX)
        drawInitialScaleDot(state, totalX, eachScalePx, nowMs)
        drawMask()
    }
}

private fun DrawScope.drawTicks(
    state: ArcScaleViewState,
    totalX: Float,
    eachScalePx: Float,
    nowMs: Long
) {
    val tickWidth = DEFAULT_TICK_WIDTH_DP.dp.toPx()
    val defaultTickHeight = DEFAULT_TICK_HEIGHT_DP.dp.toPx()
    val tickRadius = TICK_RADIUS_DP.dp.toPx()

    for (index in 0..state.scaleMaxLength) {
        val tickCenterX = totalX + index * eachScalePx
        if (tickCenterX + tickWidth / 2F < 0F || tickCenterX - tickWidth / 2F > size.width) {
            continue
        }

        val tickValue = state.scaleMin + index
        val tickColor = when {
            tickValue == 0 -> MajorTickColor
            tickValue % 10 == 0 -> MajorTickColor
            else -> DefaultTickColor
        }
        val tickHeight = calculateTideTickHeight(
            state = state,
            tickValue = tickValue,
            defaultTickHeight = defaultTickHeight,
            nowMs = nowMs
        )

        drawRoundRect(
            color = tickColor,
            topLeft = Offset(tickCenterX - tickWidth / 2F, size.height - tickHeight),
            size = Size(tickWidth, tickHeight),
            cornerRadius = CornerRadius(tickRadius, tickRadius)
        )
    }
}

private fun DrawScope.drawFixedZeroIndicator(centerX: Float) {
    val tickWidth = DEFAULT_TICK_WIDTH_DP.dp.toPx()
    val tickHeight = ZERO_TICK_HEIGHT_DP.dp.toPx()
    val tickRadius = TICK_RADIUS_DP.dp.toPx()

    drawRoundRect(
        color = ZeroTickColor,
        topLeft = Offset(centerX - tickWidth / 2F, size.height - tickHeight),
        size = Size(tickWidth, tickHeight),
        cornerRadius = CornerRadius(tickRadius, tickRadius)
    )
}

private fun DrawScope.drawInitialScaleDot(
    state: ArcScaleViewState,
    totalX: Float,
    eachScalePx: Float,
    nowMs: Long
) {
    if (0 !in state.scaleMin..(state.scaleMin + state.scaleMaxLength)) {
        return
    }

    val zeroTickIndex = -state.scaleMin
    val dotCenterX = totalX + zeroTickIndex * eachScalePx
    val dotRadius = INITIAL_SCALE_DOT_SIZE_DP.dp.toPx() / 2F
    if (dotCenterX + dotRadius < 0F || dotCenterX - dotRadius > size.width) {
        return
    }

    val zeroTickHeight = calculateTideTickHeight(
        state = state,
        tickValue = 0,
        defaultTickHeight = DEFAULT_TICK_HEIGHT_DP.dp.toPx(),
        nowMs = nowMs
    )
    val dotGap = INITIAL_SCALE_DOT_GAP_DP.dp.toPx()
    val dotCenterY = size.height - zeroTickHeight - dotGap - dotRadius
    drawCircle(
        color = MajorTickColor,
        radius = dotRadius,
        center = Offset(dotCenterX, dotCenterY)
    )
}

private fun DrawScope.drawMask() {
    // 旧 View 的遮罩宽度也是裸像素 100，不能按密度放大。
    val maskWidth = MASK_GRADIENT_WIDTH_PX
    drawRect(
        brush = Brush.horizontalGradient(
            colors = listOf(Color(0x01000000), Color(0x11000000)),
            startX = 0F,
            endX = maskWidth
        ),
        topLeft = Offset.Zero,
        size = Size(maskWidth, size.height)
    )
    drawRect(
        brush = Brush.horizontalGradient(
            colors = listOf(Color(0x11000000), Color(0x01000000)),
            startX = size.width - maskWidth,
            endX = size.width
        ),
        topLeft = Offset(size.width - maskWidth, 0F),
        size = Size(maskWidth, size.height)
    )
}

private fun DrawScope.calculateTideTickHeight(
    state: ArcScaleViewState,
    tickValue: Int,
    defaultTickHeight: Float,
    nowMs: Long
): Float {
    val triggerTimeMs = state.tideTickStates[tickValue] ?: return defaultTickHeight
    val elapsedTime = nowMs - triggerTimeMs
    if (elapsedTime < 0L) {
        return defaultTickHeight + TIDE_TICK_EXTRA_HEIGHT_DP.dp.toPx()
    }

    val rawProgress = (elapsedTime.toFloat() / TIDE_RELEASE_DURATION_MS).coerceIn(0F, 1F)
    val releaseProgress = rawProgress * rawProgress * (3F - 2F * rawProgress)
    val extraHeight = TIDE_TICK_EXTRA_HEIGHT_DP.dp.toPx() * (1F - releaseProgress)
    return defaultTickHeight + extraHeight
}

private val DefaultTickColor = Color(0xFF666666)
private val MajorTickColor = Color(0xFFFEFEFE)
private val ZeroTickColor = Color(0xFFFFBC2C)

private const val EACH_SCALE_PX = 30F
private const val ARC_SCALE_VIEW_HEIGHT_DP = 38F
private const val DEFAULT_TICK_WIDTH_DP = 2F
private const val DEFAULT_TICK_HEIGHT_DP = 12F
private const val ZERO_TICK_HEIGHT_DP = 32F
private const val TIDE_TICK_EXTRA_HEIGHT_DP = 22F
private const val INITIAL_SCALE_DOT_SIZE_DP = 4F
private const val INITIAL_SCALE_DOT_GAP_DP = 6F
private const val TICK_RADIUS_DP = 4F
private const val MASK_GRADIENT_WIDTH_PX = 100F
private const val TIDE_RELEASE_DURATION_MS = 320L
