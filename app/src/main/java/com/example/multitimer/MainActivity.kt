package com.example.multitimer

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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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

data class CountdownItem(
    val id: Long,
    val totalMillis: Long,
    val remainingMillis: Long,
    val isRunning: Boolean,
    val tone: ToneOption
)

class CountdownViewModel : ViewModel() {
    val timers = mutableStateListOf<CountdownItem>()

    private val jobs = mutableStateMapOf<Long, Job>()
    private val toneMutex = Mutex()

    private var idSeed = 1L

    init {
        addTimerWithDuration(1, 0, ToneOption.PianoC3)
        addTimerWithDuration(2, 0, ToneOption.PianoE3)
        addTimerWithDuration(3, 0, ToneOption.PianoG3)
    }

    private fun addTimerWithDuration(minutes: Int, seconds: Int, tone: ToneOption) {
        val total = (minutes * 60L + seconds) * 1000L
        val id = idSeed++
        timers.add(
            CountdownItem(
                id = id,
                totalMillis = total,
                remainingMillis = total,
                isRunning = true,
                tone = tone
            )
        )
        startLoop(id)
    }

    fun configureTimer(timerId: Long, minutesText: String, secondsText: String, tone: ToneOption) {
        val minutes = max(0, minutesText.toIntOrNull() ?: 0)
        val seconds = (secondsText.toIntOrNull() ?: 0).coerceIn(0, 59)
        val total = (minutes * 60L + seconds) * 1000L

        jobs[timerId]?.cancel()

        if (total <= 0L) {
            updateTimer(timerId) {
                it.copy(totalMillis = 0L, remainingMillis = 0L, isRunning = false, tone = tone)
            }
            return
        }

        updateTimer(timerId) {
            it.copy(totalMillis = total, remainingMillis = total, isRunning = true, tone = tone)
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
                updateTimer(timerId) {
                    it.copy(remainingMillis = it.totalMillis, isRunning = it.totalMillis > 0L)
                }
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
                    i > sampleCount - releaseSamples ->
                        (sampleCount - i).coerceAtLeast(0) / releaseSamples.toDouble()
                    else -> 1.0
                }
                val mildLowPass = 1.0 - (i / sampleCount.toDouble()) * 0.15
                samples[i] =
                    (wave * envelope * mildLowPass * velocity * Short.MAX_VALUE * 0.42).toInt().toShort()
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
        if (index != -1) {
            timers[index] = transform(timers[index])
        }
    }

    override fun onCleared() {
        jobs.values.forEach { it.cancel() }
        super.onCleared()
    }
}

@Composable
fun CountdownScreen(vm: CountdownViewModel = viewModel()) {
    var configTimerId by remember { mutableStateOf<Long?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF08111C))
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            items(vm.timers, key = { it.id }) { timer ->
                CountdownOnlyCard(
                    timer = timer,
                    onTap = { vm.togglePauseResume(timer.id) },
                    onDoubleTap = { vm.restartNow(timer.id) },
                    onLongPress = { configTimerId = timer.id }
                )
            }
        }

        val selected = vm.timers.firstOrNull { it.id == configTimerId }
        if (selected != null) {
            TimerConfigDialog(
                timer = selected,
                onDismiss = { configTimerId = null },
                onConfirm = { min, sec, tone ->
                    vm.configureTimer(selected.id, min, sec, tone)
                    configTimerId = null
                }
            )
        }
    }
}

@Composable
private fun CountdownOnlyCard(
    timer: CountdownItem,
    onTap: () -> Unit,
    onDoubleTap: () -> Unit,
    onLongPress: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF122338))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            ProgressRing(
                timer = timer,
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
    onTap: () -> Unit,
    onDoubleTap: () -> Unit,
    onLongPress: () -> Unit
) {
    val progress = if (timer.totalMillis == 0L) 0f
    else timer.remainingMillis.toFloat() / timer.totalMillis.toFloat()

    Box(
        modifier = Modifier
            .size(160.dp)
            .clip(RoundedCornerShape(20.dp))
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
            drawArc(
                color = Color(0xFF2A3A4F),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = 14.dp.toPx(), cap = StrokeCap.Round)
            )
            drawArc(
                color = Color(0xFF4FC3F7),
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                style = Stroke(width = 14.dp.toPx(), cap = StrokeCap.Round)
            )
        }
        Text(
            text = formatMillis(timer.remainingMillis),
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun TimerConfigDialog(
    timer: CountdownItem,
    onDismiss: () -> Unit,
    onConfirm: (String, String, ToneOption) -> Unit
) {
    var minuteInput by remember(timer.id, timer.totalMillis) {
        mutableStateOf((timer.totalMillis / 1000 / 60).toString())
    }
    var secondInput by remember(timer.id, timer.totalMillis) {
        mutableStateOf(((timer.totalMillis / 1000) % 60).toString())
    }
    var selectedTone by remember(timer.id, timer.tone) { mutableStateOf(timer.tone) }
    var toneMenuExpanded by remember(timer.id) { mutableStateOf(false) }

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
                    DropdownMenu(
                        expanded = toneMenuExpanded,
                        onDismissRequest = { toneMenuExpanded = false }
                    ) {
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
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(minuteInput, secondInput, selectedTone) }) {
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
