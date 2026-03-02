package com.cl.mt

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CountdownScreen()
                }
            }
        }
    }
}

enum class ToneOption(
    val label: String,
    val frequencyHz: Int,
    val burstMs: Int,
    val velocity: Double,
    val repeats: Int
) {
    PianoC3("Piano C3", 131, 520, 0.55, 1),
    PianoD3("Piano D3", 147, 520, 0.55, 1),
    PianoE3("Piano E3", 165, 500, 0.54, 1),
    PianoF3("Piano F3", 175, 500, 0.54, 1),
    PianoG3("Piano G3", 196, 480, 0.52, 1),
    PianoA3("Piano A3", 220, 460, 0.50, 1),
    PianoB3("Piano B3", 247, 440, 0.48, 1),
    PianoC4("Piano C4", 262, 420, 0.46, 1),
    WarmChord("Warm Chord", 175, 620, 0.50, 1),
    DeepPluck("Deep Pluck", 139, 380, 0.58, 2)
}

enum class TimerStyle(
    val label: String,
    val panelColor: Color,
    val trackColor: Color,
    val progressColor: Color,
    val textColor: Color,
    val strokeDp: Float,
    val cap: StrokeCap
) {
    Slate("Slate", Color(0xFF1A2633), Color(0xFF2B3B4D), Color(0xFF74B3FF), Color(0xFFEFF6FF), 14f, StrokeCap.Round),
    Ocean("Ocean", Color(0xFF10293A), Color(0xFF21445F), Color(0xFF55C7FF), Color(0xFFE5F7FF), 14f, StrokeCap.Round),
    Forest("Forest", Color(0xFF172A22), Color(0xFF2C4A3D), Color(0xFF8FD6A8), Color(0xFFEFFFF3), 14f, StrokeCap.Round),
    Sand("Sand", Color(0xFF302A22), Color(0xFF4A4034), Color(0xFFF2C57A), Color(0xFFFFF6E8), 13f, StrokeCap.Round),
    Ember("Ember", Color(0xFF321F1F), Color(0xFF4D2F2F), Color(0xFFFF9D7A), Color(0xFFFFEFEA), 14f, StrokeCap.Round),
    Copper("Copper", Color(0xFF2E2320), Color(0xFF463631), Color(0xFFDEA685), Color(0xFFFFF1E9), 13f, StrokeCap.Butt),
    Ice("Ice", Color(0xFF1A2D34), Color(0xFF314A55), Color(0xFF9BE7FF), Color(0xFFF0FDFF), 12f, StrokeCap.Round),
    Mint("Mint", Color(0xFF152D29), Color(0xFF2A4A43), Color(0xFF89E2C7), Color(0xFFEFFFF9), 12f, StrokeCap.Round),
    Mono("Mono", Color(0xFF222222), Color(0xFF3B3B3B), Color(0xFFDADADA), Color(0xFFFFFFFF), 15f, StrokeCap.Square),
    NightBlue("Night Blue", Color(0xFF121D32), Color(0xFF243552), Color(0xFF7AA2FF), Color(0xFFEAF0FF), 14f, StrokeCap.Round)
}

data class CountdownItem(
    val id: Long,
    val totalMillis: Long,
    val remainingMillis: Long,
    val isRunning: Boolean,
    val tone: ToneOption,
    val style: TimerStyle
)

class CountdownViewModel : ViewModel() {
    val timers = mutableStateListOf<CountdownItem>()
    private val jobs = mutableStateMapOf<Long, Job>()
    private val toneMutex = Mutex()
    private var idSeed = 1L

    init {
        addTimerWithDuration(1, 0, ToneOption.PianoC3, TimerStyle.Slate)
        addTimerWithDuration(2, 0, ToneOption.PianoE3, TimerStyle.Ocean)
        addTimerWithDuration(3, 0, ToneOption.PianoG3, TimerStyle.Forest)
    }

    fun addTimer() {
        val next = idSeed.toInt()
        val tone = ToneOption.entries[(next - 1) % ToneOption.entries.size]
        val style = TimerStyle.entries[(next - 1) % TimerStyle.entries.size]
        addTimerWithDuration(1, 0, tone, style)
    }

    private fun addTimerWithDuration(minutes: Int, seconds: Int, tone: ToneOption, style: TimerStyle) {
        val total = (minutes * 60L + seconds) * 1000L
        val id = idSeed++
        timers.add(
            CountdownItem(
                id = id,
                totalMillis = total,
                remainingMillis = total,
                isRunning = true,
                tone = tone,
                style = style
            )
        )
        startLoop(id)
    }

    fun configureTimer(
        timerId: Long,
        minutesText: String,
        secondsText: String,
        tone: ToneOption,
        style: TimerStyle
    ) {
        val minutes = max(0, minutesText.toIntOrNull() ?: 0)
        val seconds = (secondsText.toIntOrNull() ?: 0).coerceIn(0, 59)
        val total = (minutes * 60L + seconds) * 1000L
        jobs[timerId]?.cancel()

        if (total <= 0L) {
            updateTimer(timerId) {
                it.copy(totalMillis = 0L, remainingMillis = 0L, isRunning = false, tone = tone, style = style)
            }
            return
        }

        updateTimer(timerId) {
            it.copy(totalMillis = total, remainingMillis = total, isRunning = true, tone = tone, style = style)
        }
        startLoop(timerId)
    }

    fun togglePauseResume(timerId: Long) {
        val timer = timers.firstOrNull { it.id == timerId } ?: return
        if (timer.isRunning) {
            jobs[timerId]?.cancel()
            updateTimer(timerId) { it.copy(isRunning = false) }
        } else if (timer.totalMillis > 0L) {
            updateTimer(timerId) { it.copy(isRunning = true) }
            startLoop(timerId)
        }
    }

    fun restartNow(timerId: Long) {
        val timer = timers.firstOrNull { it.id == timerId } ?: return
        if (timer.totalMillis <= 0L) return
        updateTimer(timerId) { it.copy(remainingMillis = it.totalMillis, isRunning = true) }
        startLoop(timerId)
    }

    private fun startLoop(timerId: Long) {
        val timer = timers.firstOrNull { it.id == timerId } ?: return
        if (timer.totalMillis <= 0L) return

        jobs[timerId]?.cancel()
        jobs[timerId] = viewModelScope.launch {
            while (true) {
                delay(100L)
                val current = timers.firstOrNull { it.id == timerId } ?: break
                if (!current.isRunning) break

                val nextRemain = (current.remainingMillis - 100L).coerceAtLeast(0L)
                if (nextRemain > 0L) {
                    updateTimer(timerId) { it.copy(remainingMillis = nextRemain) }
                    continue
                }

                playTone(current.tone)
                updateTimer(timerId) { it.copy(remainingMillis = it.totalMillis, isRunning = it.totalMillis > 0L) }
            }
        }
    }

    private fun playTone(tone: ToneOption) {
        viewModelScope.launch {
            toneMutex.withLock {
                repeat(tone.repeats) {
                    playSyntheticTone(tone.frequencyHz, tone.burstMs, tone.velocity)
                    delay(90L)
                }
            }
        }
    }

    private suspend fun playSyntheticTone(frequencyHz: Int, durationMs: Int, velocity: Double) {
        withContext(Dispatchers.Default) {
            val sampleRate = 44_100
            val sampleCount = (sampleRate * durationMs / 1000f).toInt().coerceAtLeast(1)
            val samples = ShortArray(sampleCount)
            val attackSamples = (sampleRate * 0.03f).toInt().coerceAtLeast(1)
            val releaseSamples = (sampleRate * 0.22f).toInt().coerceAtLeast(1)
            val sustainStart = attackSamples
            val sustainLength = (sampleCount - attackSamples - releaseSamples).coerceAtLeast(1)

            for (i in 0 until sampleCount) {
                val t = i / sampleRate.toDouble()
                val f = frequencyHz.toDouble()
                val w1 = sin(2.0 * Math.PI * f * t)
                val w2 = sin(2.0 * Math.PI * (f * 2.0) * t)
                val w3 = sin(2.0 * Math.PI * (f * 3.0) * t)
                val wave = (w1 * 0.78) + (w2 * 0.17) + (w3 * 0.05)
                val envelope = when {
                    i < attackSamples -> i / attackSamples.toDouble()
                    i < sustainStart + sustainLength -> 1.0 - ((i - sustainStart) / sustainLength.toDouble()) * 0.35
                    i > sampleCount - releaseSamples -> (sampleCount - i).coerceAtLeast(0) / releaseSamples.toDouble()
                    else -> 1.0
                }
                val mildLowPass = 1.0 - (i / sampleCount.toDouble()) * 0.15
                samples[i] = (wave * envelope * mildLowPass * velocity * Short.MAX_VALUE * 0.42).toInt().toShort()
            }

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(samples.size * 2)
                .build()

            try {
                track.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
                track.play()
                delay(durationMs.toLong() + 20L)
            } finally {
                runCatching { track.stop() }
                track.release()
            }
        }
    }

    private fun updateTimer(timerId: Long, transform: (CountdownItem) -> CountdownItem) {
        val index = timers.indexOfFirst { it.id == timerId }
        if (index != -1) timers[index] = transform(timers[index])
    }

    override fun onCleared() {
        jobs.values.forEach { it.cancel() }
        super.onCleared()
    }
}

@Composable
fun CountdownScreen(vm: CountdownViewModel = viewModel()) {
    var configTimerId by remember { mutableStateOf<Long?>(null) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF08111C))
    ) {
        val count = vm.timers.size.coerceAtLeast(1)
        val screenPadding = 12.dp
        val gridGap = 10.dp
        val fabReserve = 86.dp

        val usableWidth = maxWidth - (screenPadding * 2)
        val usableHeightBase = maxHeight - (screenPadding * 2) - fabReserve
        val usableHeight = if (usableHeightBase < 100.dp) 100.dp else usableHeightBase

        var bestColumns = 1
        var bestRingSize = 0.dp
        for (cols in 1..count) {
            val rows = ceil(count / cols.toDouble()).toInt()
            val cellWidth = (usableWidth - gridGap * (cols - 1)) / cols
            val cellHeight = (usableHeight - gridGap * (rows - 1)) / rows
            val candidate = if (cellWidth < cellHeight) cellWidth else cellHeight
            if (candidate > bestRingSize) {
                bestRingSize = candidate
                bestColumns = cols
            }
        }

        val rows = vm.timers.chunked(bestColumns)
        val ringSize = (bestRingSize - 18.dp).coerceAtLeast(38.dp)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(screenPadding),
            verticalArrangement = Arrangement.spacedBy(gridGap)
        ) {
            rows.forEach { rowTimers ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(gridGap)
                ) {
                    rowTimers.forEach { timer ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                        ) {
                            CountdownOnlyCard(
                                timer = timer,
                                ringSize = ringSize,
                                onTap = { vm.togglePauseResume(timer.id) },
                                onDoubleTap = { vm.restartNow(timer.id) },
                                onLongPress = { configTimerId = timer.id }
                            )
                        }
                    }
                    repeat(bestColumns - rowTimers.size) {
                        Spacer(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                        )
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { vm.addTimer() },
            containerColor = Color(0xFF1B3A57),
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
        ) {
            Text("+", fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
        }

        val selected = vm.timers.firstOrNull { it.id == configTimerId }
        if (selected != null) {
            TimerConfigDialog(
                timer = selected,
                onDismiss = { configTimerId = null },
                onConfirm = { min, sec, tone, style ->
                    vm.configureTimer(selected.id, min, sec, tone, style)
                    configTimerId = null
                }
            )
        }
    }
}

@Composable
private fun CountdownOnlyCard(
    timer: CountdownItem,
    ringSize: Dp,
    onTap: () -> Unit,
    onDoubleTap: () -> Unit,
    onLongPress: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxSize(),
        colors = CardDefaults.cardColors(containerColor = timer.style.panelColor),
        shape = RoundedCornerShape(16.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 6.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            ProgressRing(
                timer = timer,
                ringSize = ringSize,
                onTap = onTap,
                onDoubleTap = onDoubleTap,
                onLongPress = onLongPress
            )
        }
    }
}

@Composable
private fun ProgressRing(
    timer: CountdownItem,
    ringSize: Dp,
    onTap: () -> Unit,
    onDoubleTap: () -> Unit,
    onLongPress: () -> Unit
) {
    val progress = if (timer.totalMillis == 0L) 0f else timer.remainingMillis.toFloat() / timer.totalMillis.toFloat()
    val style = timer.style
    val timeFont = (ringSize.value * 0.20f).coerceIn(10f, 34f).sp

    Box(
        modifier = Modifier
            .size(ringSize)
            .padding(4.dp)
            .pointerInput(timer.id) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { onDoubleTap() },
                    onLongPress = { onLongPress() }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawSafeArc(style.trackColor, 360f, style.strokeDp, style.cap)
            drawSafeArc(style.progressColor, 360f * progress, style.strokeDp, style.cap)
        }
        Text(
            text = formatMillis(timer.remainingMillis),
            color = style.textColor,
            fontSize = timeFont,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}

private fun DrawScope.drawSafeArc(
    color: Color,
    sweepAngle: Float,
    strokeDp: Float,
    cap: StrokeCap
) {
    val strokePx = strokeDp.dp.toPx()
    val inset = (strokePx / 2f) + 1.dp.toPx()
    val drawSize = Size(size.width - inset * 2f, size.height - inset * 2f)
    if (drawSize.width <= 0f || drawSize.height <= 0f) return

    drawArc(
        color = color,
        startAngle = -90f,
        sweepAngle = sweepAngle,
        useCenter = false,
        topLeft = Offset(inset, inset),
        size = drawSize,
        style = Stroke(width = strokePx, cap = cap)
    )
}

@Composable
private fun TimerConfigDialog(
    timer: CountdownItem,
    onDismiss: () -> Unit,
    onConfirm: (String, String, ToneOption, TimerStyle) -> Unit
) {
    var minuteInput by remember(timer.id, timer.totalMillis) {
        mutableStateOf((timer.totalMillis / 1000 / 60).toString())
    }
    var secondInput by remember(timer.id, timer.totalMillis) {
        mutableStateOf(((timer.totalMillis / 1000) % 60).toString())
    }
    var selectedTone by remember(timer.id, timer.tone) { mutableStateOf(timer.tone) }
    var selectedStyle by remember(timer.id, timer.style) { mutableStateOf(timer.style) }
    var toneMenuExpanded by remember(timer.id) { mutableStateOf(false) }
    var styleMenuExpanded by remember(timer.id) { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Configure") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = minuteInput,
                    onValueChange = { minuteInput = it.filter(Char::isDigit).take(3) },
                    label = { Text("Min") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = secondInput,
                    onValueChange = { secondInput = it.filter(Char::isDigit).take(2) },
                    label = { Text("Sec") },
                    singleLine = true
                )
                Box {
                    Button(onClick = { toneMenuExpanded = true }) {
                        Text("Tone: ${selectedTone.label}")
                    }
                    DropdownMenu(expanded = toneMenuExpanded, onDismissRequest = { toneMenuExpanded = false }) {
                        ToneOption.entries.forEach { tone ->
                            DropdownMenuItem(
                                text = { Text(tone.label) },
                                onClick = {
                                    selectedTone = tone
                                    toneMenuExpanded = false
                                }
                            )
                        }
                    }
                }
                Box {
                    Button(onClick = { styleMenuExpanded = true }) {
                        Text("Style: ${selectedStyle.label}")
                    }
                    DropdownMenu(expanded = styleMenuExpanded, onDismissRequest = { styleMenuExpanded = false }) {
                        TimerStyle.entries.forEach { style ->
                            DropdownMenuItem(
                                text = { Text(style.label) },
                                onClick = {
                                    selectedStyle = style
                                    styleMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(minuteInput, secondInput, selectedTone, selectedStyle) }) {
                Text("Apply")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun formatMillis(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
