package com.luna.music.ui

import android.content.Context
import android.content.ContextWrapper
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Direction the glass highlight comes from, each axis in -1..1 (x right, y down).
 * `null` means no sensor is wired in and surfaces use [DefaultGlassLight].
 *
 * The sensor-driven specular idea is borrowed from QWEA0/Liquid-Glass-Android (MIT);
 * that library targets the View system, so only the concept is ported to Compose here.
 */
val LocalLunaGlassLight = staticCompositionLocalOf<State<Offset>?> { null }

val DefaultGlassLight = Offset(-0.6f, -0.8f)

/**
 * Tracks device tilt with the gravity sensor and exposes a smoothed light direction.
 * Registers only while the activity is resumed, and stays static when the user has
 * turned animations off or the device has no suitable sensor.
 */
@Composable
fun rememberLunaGlassLight(): State<Offset> {
    val context = LocalContext.current
    val light = remember { mutableStateOf(DefaultGlassLight) }
    DisposableEffect(context) {
        val animationsEnabled = runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
        }.getOrDefault(true)
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val owner = context.findLifecycleOwner()
        if (!animationsEnabled || sensorManager == null || sensor == null || owner == null) {
            return@DisposableEffect onDispose { }
        }

        var smoothX = DefaultGlassLight.x
        var smoothY = DefaultGlassLight.y
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val gx = event.values[0]
                val gz = event.values[2]
                val targetX = (-gx / SensorManager.GRAVITY_EARTH * 1.4f).coerceIn(-1f, 1f)
                // Phones are usually held tilted back; centre the vertical axis around that pose.
                val targetY = (-(gz / SensorManager.GRAVITY_EARTH - 0.55f) * 2.5f).coerceIn(-1f, 1f)
                smoothX += (targetX - smoothX) * 0.12f
                smoothY += (targetY - smoothY) * 0.12f
                val current = light.value
                if (abs(smoothX - current.x) > 0.012f || abs(smoothY - current.y) > 0.012f) {
                    light.value = Offset(smoothX, smoothY)
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        var registered = false
        fun register() {
            if (!registered) {
                registered = sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
            }
        }
        fun unregister() {
            if (registered) {
                sensorManager.unregisterListener(listener)
                registered = false
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> register()
                Lifecycle.Event.ON_PAUSE -> unregister()
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            unregister()
        }
    }
    return light
}

private fun Context.findLifecycleOwner(): LifecycleOwner? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is LifecycleOwner) return current
        current = current.baseContext
    }
    return null
}

/**
 * Draws a light-facing specular rim and a soft highlight bloom that slide as the
 * device tilts. Replaces a static border so glass keeps catching the light.
 */
fun Modifier.lunaSpecularRim(shape: Shape, accent: Color): Modifier = composed {
    val light = LocalLunaGlassLight.current
    drawWithContent {
        val direction = light?.value ?: DefaultGlassLight
        val length = sqrt(direction.x * direction.x + direction.y * direction.y)
        val unit = if (length < 0.001f) Offset(DefaultGlassLight.x, DefaultGlassLight.y) else direction / length
        val reach = max(size.width, size.height) * 0.75f
        val center = Offset(size.width / 2f, size.height / 2f)

        // Soft bloom under the content so text keeps its contrast.
        drawRect(
            Brush.radialGradient(
                colors = listOf(Color.White.copy(alpha = 0.09f), Color.Transparent),
                center = center + Offset(direction.x * size.width * 0.38f, direction.y * size.height * 0.38f),
                radius = max(size.width, size.height) * 0.6f
            )
        )
        drawContent()

        val outline = shape.createOutline(size, layoutDirection, this)
        drawOutline(
            outline = outline,
            brush = Brush.linearGradient(
                colorStops = arrayOf(
                    0f to Color.White.copy(alpha = 0.62f),
                    0.42f to Color.White.copy(alpha = 0.10f),
                    0.72f to accent.copy(alpha = 0.22f),
                    1f to Color.White.copy(alpha = 0.16f)
                ),
                start = center + unit * reach,
                end = center - unit * reach
            ),
            style = Stroke(width = 1.2.dp.toPx())
        )
    }
}
