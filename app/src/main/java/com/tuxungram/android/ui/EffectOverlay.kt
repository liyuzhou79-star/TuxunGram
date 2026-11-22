package com.tuxungram.android.ui

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import kotlin.math.sin
import kotlin.random.Random

/**
 * Received message-effect playback (v0.9.30, W3): when a
 * message carrying a send effect first scrolls into view,
 * its effect emoji bursts once over the chat — about two
 * dozen copies of the character drifting up and fading,
 * ~1.5 s, tap anywhere to skip. Deliberately procedural
 * (the effect's own emoji is the particle; no animation
 * assets exist to play) and deliberately restrained: one
 * play per message per process, nothing for effects whose
 * emoji is unknown, and nothing at all when the system
 * animator scale is 0 (reduced motion).
 */
@Composable
fun EffectParticlesOverlay(
    emoji: String,
    nonce: Int,
    onDone: () -> Unit
) {
    var progress by remember(nonce) { mutableFloatStateOf(0f) }
    LaunchedEffect(nonce) {
        animate(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = Motion.ms(1500)
            )
        ) { value, _ -> progress = value }
        onDone()
    }
    val particles = remember(nonce, emoji) {
        val random = Random(nonce * 7919 + emoji.hashCode())
        List(26) {
            Particle(
                xFraction = 0.12f + random.nextFloat() * 0.76f,
                startYFraction = 0.55f + random.nextFloat() * 0.4f,
                riseFraction = 0.35f + random.nextFloat() * 0.3f,
                swayPhase = random.nextFloat() * 6.28f,
                swayAmount = 10f + random.nextFloat() * 26f,
                sizePx = 44f + random.nextFloat() * 40f,
                delay = random.nextFloat() * 0.25f
            )
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember {
                    MutableInteractionSource()
                },
                indication = null
            ) { onDone() }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val paint = android.graphics.Paint().apply {
                isAntiAlias = true
                textAlign = android.graphics.Paint.Align.CENTER
            }
            for (particle in particles) {
                val local = ((progress - particle.delay) /
                    (1f - particle.delay)).coerceIn(0f, 1f)
                if (local <= 0f || local >= 1f) continue
                val fade = if (local < 0.15f) {
                    local / 0.15f
                } else {
                    1f - (local - 0.15f) / 0.85f
                }
                paint.textSize = particle.sizePx
                paint.alpha = (fade * 235).toInt().coerceIn(0, 255)
                val x = particle.xFraction * size.width +
                    sin(
                        particle.swayPhase + local * 4.2f
                    ) * particle.swayAmount
                val y = (particle.startYFraction -
                    particle.riseFraction * local) * size.height
                drawContext.canvas.nativeCanvas.drawText(
                    emoji, x, y, paint
                )
            }
        }
    }
}

private data class Particle(
    val xFraction: Float,
    val startYFraction: Float,
    val riseFraction: Float,
    val swayPhase: Float,
    val swayAmount: Float,
    val sizePx: Float,
    val delay: Float
)

/** Process-lifetime record of messages whose effect has
 *  already played in this process (v0.9.30, W3): the brief
 *  asks for once-per-message, in-process bookkeeping is
 *  enough — after a restart an effect may play once more,
 *  which matches how fleeting the moment is. */
object EffectPlayMemory {
    private val played = HashSet<Long>()

    @Synchronized
    fun markIfNew(chatId: Long, messageId: Long): Boolean {
        val key = chatId * 1_000_000_007L + messageId
        return played.add(key)
    }
}
