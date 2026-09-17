package xyz.qiaosheng.bilibili.ui.video.danmaku

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.Player
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import xyz.qiaosheng.bilibili.model.danmaku.DanmakuItem

/** Transparent, touch-through overlay. Place player controls after this composable in the same Box. */
@Composable
fun DanmakuOverlay(
    player: Player,
    items: List<DanmakuItem>,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current.density
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val paint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            strokeJoin = Paint.Join.ROUND
        }
    }
    val fontMetrics = remember { Paint.FontMetrics() }
    val engine = remember(items) {
        DanmakuLayoutEngine(items, measureText = { text, fontSize ->
            paint.textSize = fontSize
            paint.measureText(text)
        })
    }
    // This state is read only in Canvas's draw phase. Frames never recompose the player/page.
    val clock = remember(player) { mutableStateOf<DanmakuClock?>(null) }

    LaunchedEffect(player, enabled, lifecycle, engine) {
        clock.value = null
        engine.clear()
        if (!enabled || items.isEmpty()) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            val signals = Channel<Unit>(Channel.CONFLATED)
            var seekGeneration = 0L
            val listener = object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) {
                    if (events.contains(Player.EVENT_POSITION_DISCONTINUITY) ||
                        events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)
                    ) {
                        seekGeneration++
                        engine.clear()
                    }
                    signals.trySend(Unit)
                }
            }
            player.addListener(listener)
            try {
                while (isActive) {
                    clock.value = DanmakuClock(player.currentPosition.coerceAtLeast(0), seekGeneration)
                    if (player.isPlaying) {
                        withFrameNanos { /* Sample actual media position on the next display frame. */ }
                    } else {
                        // Pauses, buffering and suppressed playback freeze without a polling loop.
                        signals.receive()
                    }
                }
            } finally {
                player.removeListener(listener)
                signals.close()
                clock.value = null
                engine.clear()
            }
        }
    }

    Canvas(modifier = modifier) {
        val frame = clock.value
        if (enabled && frame != null) {
            val comments = engine.frame(
                frame.positionMs,
                DanmakuViewport(size.width, size.height, density),
            )
            clipRect {
                val canvas = drawContext.canvas.nativeCanvas
                comments.forEach { comment ->
                    paint.textSize = comment.fontSizePx
                    paint.getFontMetrics(fontMetrics)
                    val baseline = comment.top +
                        (comment.height - fontMetrics.descent + fontMetrics.ascent) / 2 - fontMetrics.ascent
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 2.5f * density
                    paint.color = 0xD9000000.toInt()
                    canvas.drawText(comment.text, comment.x, baseline, paint)
                    paint.style = Paint.Style.FILL
                    paint.color = comment.color or 0xFF000000.toInt()
                    canvas.drawText(comment.text, comment.x, baseline, paint)
                }
            }
        }
    }
}

private data class DanmakuClock(val positionMs: Long, val seekGeneration: Long)
