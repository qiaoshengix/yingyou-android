package xyz.qiaosheng.bilibili.ui.video.danmaku

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.FlagSet
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import xyz.qiaosheng.bilibili.model.danmaku.DanmakuItem

@androidx.annotation.OptIn(UnstableApi::class)
class DanmakuOverlayTest {
    @get:Rule val compose = createComposeRule()

    @Test fun switchClearsDrawingAndReenableRestoresWithoutConsumingTouches() {
        val player = OverlayPlayer()
        val enabled = mutableStateOf(true)
        var clicks = 0
        compose.setContent {
            Box(
                Modifier.size(300.dp, 180.dp).background(Color.Black)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { clicks++ }.testTag("surface"),
            ) {
                DanmakuOverlay(player.player, COMMENTS, enabled.value, Modifier.fillMaxSize().testTag("overlay"))
            }
        }
        compose.waitForIdle()
        assertTrue(inkPixels() > 0)
        compose.onNodeWithTag("surface").performTouchInput { click() }
        compose.runOnIdle { assertEquals(1, clicks); enabled.value = false }
        compose.waitForIdle()
        assertEquals(0, inkPixels())
        compose.runOnIdle { assertEquals(0, player.listeners.size); enabled.value = true }
        compose.waitForIdle()
        assertTrue(inkPixels() > 0)
    }

    @Test fun pauseAndStoppedLifecycleHaveNoFramePollingAndSeekDoesNotRecomposeThePage() {
        val player = OverlayPlayer()
        lateinit var owner: OverlayLifecycleOwner
        var compositions = 0
        compose.runOnUiThread {
            owner = OverlayLifecycleOwner()
            owner.registry.currentState = Lifecycle.State.RESUMED
        }
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                SideEffect { compositions++ }
                Box(Modifier.size(300.dp, 180.dp).background(Color.Black)) {
                    DanmakuOverlay(player.player, COMMENTS, true, Modifier.fillMaxSize().testTag("overlay"))
                }
            }
        }
        compose.waitForIdle()
        val initialCompositions = compositions
        val pausedReads = player.positionReads
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        assertEquals(pausedReads, player.positionReads)
        assertTrue(inkPixels() > 0)
        compose.runOnIdle { player.seekTo(10_000) }
        compose.waitForIdle()
        assertEquals(0, inkPixels())
        compose.runOnIdle { player.seekTo(2_000) }
        compose.waitForIdle()
        assertTrue(inkPixels() > 0)
        assertEquals(initialCompositions, compositions)

        compose.runOnUiThread { owner.registry.currentState = Lifecycle.State.CREATED }
        compose.waitForIdle()
        assertEquals(0, inkPixels())
        assertEquals(0, player.listeners.size)
        val stoppedReads = player.positionReads
        compose.mainClock.advanceTimeBy(1_000)
        assertEquals(stoppedReads, player.positionReads)
        compose.runOnUiThread { owner.registry.currentState = Lifecycle.State.RESUMED }
        compose.waitForIdle()
        assertTrue(inkPixels() > 0)
        assertEquals(1, player.listeners.size)
    }

    private fun inkPixels(): Int {
        val pixels = compose.onNodeWithTag("overlay", useUnmergedTree = true).captureToImage().toPixelMap()
        var count = 0
        for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
            if (pixels[x, y].red > 0.1f || pixels[x, y].green > 0.1f || pixels[x, y].blue > 0.1f) count++
        }
        return count
    }

    private class OverlayLifecycleOwner : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
    }

    /** The overlay needs only these Player calls, so tests do not depend on decoding or networking. */
    private class OverlayPlayer {
        var positionMs = 2_000L
        var positionReads = 0
        val listeners = linkedSetOf<Player.Listener>()
        val player: Player = Proxy.newProxyInstance(
            Player::class.java.classLoader, arrayOf(Player::class.java),
        ) { proxy, method, arguments ->
            when (method.name) {
                "getCurrentPosition" -> { positionReads++; positionMs }
                "isPlaying" -> false
                "addListener" -> { listeners.add(arguments!![0] as Player.Listener); null }
                "removeListener" -> { listeners.remove(arguments!![0] as Player.Listener); null }
                "equals" -> proxy === arguments?.get(0)
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "OverlayPlayer"
                else -> error("Unexpected Player call: ${method.name}")
            }
        } as Player

        fun seekTo(positionMs: Long) {
            this.positionMs = positionMs
            val events = Player.Events(FlagSet.Builder().add(Player.EVENT_POSITION_DISCONTINUITY).build())
            listeners.toList().forEach { it.onEvents(player, events) }
        }
    }

    private companion object {
        val COMMENTS = listOf(
            DanmakuItem(id = "visible", timeMs = 0, text = "弹幕 rendering", mode = 1, fontSize = 25, color = 0xFFFFFF),
        )
    }
}
