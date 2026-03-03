package com.cl.mt

import android.app.Application
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    private val vm: CountdownViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        runCatching {
            ContextCompat.startForegroundService(this, Intent(this, BackgroundRunService::class.java))
        }

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CountdownScreen(vm = vm)
                }
            }
        }
    }

    override fun onStop() {
        vm.persistNow()
        super.onStop()
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

enum class FeedbackMode(val label: String) {
    SoundOnly("纯音效"),
    VibrationOnly("纯振动"),
    SoundAndVibration("音效和振动")
}

enum class TickAlertOption(val label: String) {
    Off("关闭"),
    Last5Seconds("最后5秒"),
    Last3Seconds("最后3秒"),
    Custom("自定义")
}

enum class TapGestureMode(val label: String) {
    SinglePauseDoubleRestart("单击暂停 / 双击重开"),
    SingleRestartDoublePause("单击重开 / 双击暂停")
}

data class GlobalSettings(
    val feedbackMode: FeedbackMode = FeedbackMode.SoundOnly,
    val keepScreenOn: Boolean = false,
    val gestureMode: TapGestureMode = TapGestureMode.SinglePauseDoubleRestart
)

data class ManualRestartRecord(
    val epochMs: Long,
    val durationSeconds: Long
)

data class RestartStatsGroup(
    val title: String,
    val seconds: List<Long>
)

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

data class TimerRenderStyle(
    val panelColor: Color,
    val trackColor: Color,
    val progressColor: Color,
    val textColor: Color,
    val strokeDp: Float,
    val cap: StrokeCap
)

private fun TimerStyle.resolve(isRunning: Boolean): TimerRenderStyle {
    if (isRunning) {
        return TimerRenderStyle(
            panelColor = panelColor,
            trackColor = trackColor,
            progressColor = progressColor,
            textColor = textColor,
            strokeDp = strokeDp,
            cap = cap
        )
    }
    return TimerRenderStyle(
        panelColor = Color(0xFF171A20),
        trackColor = Color(0xFF2E3440),
        progressColor = Color(0xFF7C8596),
        textColor = Color(0xFFD7DCE6),
        strokeDp = 13f,
        cap = StrokeCap.Round
    )
}

data class CountdownItem(
    val id: Long,
    val totalMillis: Long,
    val remainingMillis: Long,
    val isRunning: Boolean,
    val tone: ToneOption,
    val style: TimerStyle,
    val tickAlert: TickAlertOption = TickAlertOption.Off,
    val customTickSeconds: Int = 5,
    val manualRestartAnchorEpochMs: Long = System.currentTimeMillis(),
    val manualRestartRecords: List<ManualRestartRecord> = emptyList()
)

class CountdownViewModel(application: Application) : AndroidViewModel(application) {
    val timers = mutableStateListOf<CountdownItem>()
    var globalSettings by mutableStateOf(GlobalSettings())
        private set

    private val jobs = mutableStateMapOf<Long, Job>()
    private val tickCueSecondCache = mutableStateMapOf<Long, Int>()
    private val toneMutex = Mutex()
    private val appContext = application.applicationContext
    private val prefs = application.getSharedPreferences("countdown_state", Context.MODE_PRIVATE)
    private val stateKey = "timers_state_v1"

    private var idSeed = 1L
    private var lastPersistMs = 0L
    private var pausedByStatsOverlay: Set<Long> = emptySet()

    init {
        restoreStateOrDefault()
    }

    fun updateGlobalSettings(
        feedbackMode: FeedbackMode = globalSettings.feedbackMode,
        keepScreenOn: Boolean = globalSettings.keepScreenOn,
        gestureMode: TapGestureMode = globalSettings.gestureMode
    ) {
        globalSettings = GlobalSettings(
            feedbackMode = feedbackMode,
            keepScreenOn = keepScreenOn,
            gestureMode = gestureMode
        )
        persistNow()
    }

    private fun CountdownItem.tickWindowSeconds(): Int {
        return when (tickAlert) {
            TickAlertOption.Off -> 0
            TickAlertOption.Last5Seconds -> 5
            TickAlertOption.Last3Seconds -> 3
            TickAlertOption.Custom -> customTickSeconds.coerceIn(1, 30)
        }
    }

    fun addTimer() {
        val next = idSeed.toInt()
        val tone = ToneOption.entries[(next - 1) % ToneOption.entries.size]
        val style = TimerStyle.entries[(next - 1) % TimerStyle.entries.size]
        addTimerWithDuration(1, 0, tone, style)
        persistNow()
    }

    fun removeTimer(timerId: Long) {
        if (timers.size <= 1) return
        jobs[timerId]?.cancel()
        jobs.remove(timerId)
        tickCueSecondCache.remove(timerId)
        timers.removeAll { it.id == timerId }
        persistNow()
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
                style = style,
                manualRestartAnchorEpochMs = System.currentTimeMillis()
            )
        )
        startLoop(id)
    }

    fun configureTimer(
        timerId: Long,
        minutesText: String,
        secondsText: String,
        tone: ToneOption,
        style: TimerStyle,
        tickAlert: TickAlertOption,
        customTickSeconds: Int
    ) {
        val minutes = max(0, minutesText.toIntOrNull() ?: 0)
        val seconds = (secondsText.toIntOrNull() ?: 0).coerceIn(0, 59)
        val total = (minutes * 60L + seconds) * 1000L
        jobs[timerId]?.cancel()
        tickCueSecondCache.remove(timerId)

        if (total <= 0L) {
            updateTimer(timerId) {
                it.copy(
                    totalMillis = 0L,
                    remainingMillis = 0L,
                    isRunning = false,
                    tone = tone,
                    style = style,
                    tickAlert = tickAlert,
                    customTickSeconds = customTickSeconds.coerceIn(1, 30),
                    manualRestartAnchorEpochMs = System.currentTimeMillis()
                )
            }
            persistNow()
            return
        }

        updateTimer(timerId) {
            it.copy(
                totalMillis = total,
                remainingMillis = total,
                isRunning = true,
                tone = tone,
                style = style,
                tickAlert = tickAlert,
                customTickSeconds = customTickSeconds.coerceIn(1, 30),
                manualRestartAnchorEpochMs = System.currentTimeMillis()
            )
        }
        startLoop(timerId)
        persistNow()
    }

    fun togglePauseResume(timerId: Long) {
        val timer = timers.firstOrNull { it.id == timerId } ?: return
        if (timer.isRunning) {
            jobs[timerId]?.cancel()
            updateTimer(timerId) { it.copy(isRunning = false) }
            playActionFeedback()
            persistNow()
        } else if (timer.totalMillis > 0L) {
            updateTimer(timerId) { it.copy(isRunning = true) }
            startLoop(timerId)
            playActionFeedback()
            persistNow()
        }
    }

    fun restartNow(timerId: Long) {
        val timer = timers.firstOrNull { it.id == timerId } ?: return
        if (timer.totalMillis <= 0L) return
        val now = System.currentTimeMillis()
        val durationSec = ((now - timer.manualRestartAnchorEpochMs).coerceAtLeast(0L)) / 1000L
        val nextRecords = (timer.manualRestartRecords + ManualRestartRecord(now, durationSec)).takeLast(600)
        updateTimer(timerId) {
            it.copy(
                remainingMillis = it.totalMillis,
                isRunning = true,
                manualRestartAnchorEpochMs = now,
                manualRestartRecords = nextRecords
            )
        }
        tickCueSecondCache.remove(timerId)
        startLoop(timerId)
        playActionFeedback()
        persistNow()
    }

    fun restartStats(timerId: Long): List<RestartStatsGroup> {
        val timer = timers.firstOrNull { it.id == timerId } ?: return emptyList()
        if (timer.manualRestartRecords.isEmpty()) return emptyList()

        val sorted = timer.manualRestartRecords.sortedBy { it.epochMs }
        val groups = mutableListOf<List<ManualRestartRecord>>()
        var current = mutableListOf(sorted.first())
        for (idx in 1 until sorted.size) {
            val prev = sorted[idx - 1]
            val rec = sorted[idx]
            if (rec.epochMs - prev.epochMs > 60L * 60_000L) {
                groups += current.toList()
                current = mutableListOf(rec)
            } else {
                current += rec
            }
        }
        groups += current.toList()

        val endTimeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        return groups.map { g ->
            val firstSec = g.first().durationSeconds
            val endTime = endTimeFmt.format(Date(g.last().epochMs))
            RestartStatsGroup(
                title = "$endTime",
                seconds = g.map { it.durationSeconds }
            )
        }.reversed()
    }

    fun onStatsDialogVisibilityChanged(visible: Boolean) {
        if (visible) {
            if (pausedByStatsOverlay.isNotEmpty()) return
            val runningIds = timers
                .filter { it.isRunning && it.totalMillis > 0L }
                .map { it.id }
                .toSet()
            pausedByStatsOverlay = runningIds
            runningIds.forEach { id ->
                jobs[id]?.cancel()
                updateTimer(id) { it.copy(isRunning = false) }
            }
            return
        }

        if (pausedByStatsOverlay.isEmpty()) return
        val toResume = pausedByStatsOverlay
        pausedByStatsOverlay = emptySet()
        toResume.forEach { id ->
            val timer = timers.firstOrNull { it.id == id } ?: return@forEach
            if (!timer.isRunning && timer.totalMillis > 0L) {
                updateTimer(id) { it.copy(isRunning = true) }
                startLoop(id)
            }
        }
    }

    fun exportAllRecords(): String {
        return runCatching {
            val dir = appContext.getExternalFilesDir(null) ?: appContext.filesDir
            val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val outFile = File(dir, "countdown_records_$ts.txt")
            val lineTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

            val content = buildString {
                appendLine("Multi Countdown Records Export")
                appendLine("Export Time: ${lineTime.format(Date())}")
                appendLine("FeedbackMode: ${globalSettings.feedbackMode.label}")
                appendLine("KeepScreenOn: ${globalSettings.keepScreenOn}")
                appendLine("GestureMode: ${globalSettings.gestureMode.label}")
                appendLine("================================")

                timers.forEach { timer ->
                    appendLine("Timer #${timer.id}")
                    appendLine("Records: ${timer.manualRestartRecords.size}")
                    if (timer.manualRestartRecords.isEmpty()) {
                        appendLine("  (none)")
                    } else {
                        timer.manualRestartRecords.sortedBy { it.epochMs }.forEach { rec ->
                            appendLine(
                                "  - ${lineTime.format(Date(rec.epochMs))} | ${rec.durationSeconds} sec"
                            )
                        }
                    }
                    val groups = restartStats(timer.id)
                    if (groups.isNotEmpty()) {
                        appendLine("  Groups:")
                        groups.forEach { group ->
                            appendLine("    * ${group.title}")
                            appendLine("      ${group.seconds.joinToString(", ") { \"${it}s\" }}")
                        }
                    }
                    appendLine("--------------------------------")
                }
            }

            outFile.writeText(content, Charsets.UTF_8)
            outFile.absolutePath
        }.getOrElse {
            "导出失败: ${it.message ?: "未知错误"}"
        }
    }

    private fun startLoop(timerId: Long) {
        val timer = timers.firstOrNull { it.id == timerId } ?: return
        if (timer.totalMillis <= 0L) return

        jobs[timerId]?.cancel()
        tickCueSecondCache.remove(timerId)
        jobs[timerId] = viewModelScope.launch {
            var lastTick = SystemClock.elapsedRealtime()
            while (true) {
                delay(16L)
                val current = timers.firstOrNull { it.id == timerId } ?: break
                if (!current.isRunning) break

                val now = SystemClock.elapsedRealtime()
                val delta = (now - lastTick).coerceAtLeast(1L)
                lastTick = now

                val nextRemain = (current.remainingMillis - delta).coerceAtLeast(0L)
                if (nextRemain > 0L) {
                    maybePlayFinalTickCue(current, nextRemain)
                    updateTimer(timerId) { it.copy(remainingMillis = nextRemain) }
                    maybePersist()
                    continue
                }

                playTone(current.tone)
                updateTimer(timerId) { it.copy(remainingMillis = it.totalMillis, isRunning = it.totalMillis > 0L) }
                tickCueSecondCache.remove(timerId)
                persistNow()
            }
        }
    }

    private fun playTone(tone: ToneOption) {
        viewModelScope.launch {
            toneMutex.withLock {
                if (shouldPlaySound()) {
                    repeat(tone.repeats) {
                        playSyntheticTone(tone.frequencyHz, tone.burstMs, tone.velocity)
                        delay(90L)
                    }
                }
                if (shouldVibrate()) {
                    vibrate(durationMs = 140L, amplitude = 200)
                }
            }
        }
    }

    // Unified feedback for pause/resume/manual restart, controlled by global mode.
    private fun playActionFeedback() {
        viewModelScope.launch {
            toneMutex.withLock {
                if (shouldPlaySound()) {
                    playSyntheticTone(frequencyHz = 392, durationMs = 85, velocity = 0.35)
                }
                if (shouldVibrate()) {
                    vibrate(durationMs = 55L, amplitude = 160)
                }
            }
        }
    }

    private fun maybePlayFinalTickCue(timer: CountdownItem, nextRemain: Long) {
        val tickWindow = timer.tickWindowSeconds()
        if (tickWindow <= 0) {
            tickCueSecondCache.remove(timer.id)
            return
        }

        val beforeSecond = ceil(timer.remainingMillis / 1000.0).toInt()
        val afterSecond = ceil(nextRemain / 1000.0).toInt()
        if (afterSecond >= beforeSecond) return

        val alertedSecond = tickCueSecondCache[timer.id]
        for (sec in (beforeSecond - 1) downTo afterSecond) {
            if (sec in 1..tickWindow && (alertedSecond == null || sec < alertedSecond)) {
                playTickCue()
                tickCueSecondCache[timer.id] = sec
            }
        }
    }

    private fun playTickCue() {
        viewModelScope.launch {
            toneMutex.withLock {
                if (shouldPlaySound()) {
                    playSyntheticTone(frequencyHz = 960, durationMs = 55, velocity = 0.30)
                }
                if (shouldVibrate()) {
                    vibrate(durationMs = 30L, amplitude = 120)
                }
            }
        }
    }

    private fun shouldPlaySound(): Boolean = globalSettings.feedbackMode != FeedbackMode.VibrationOnly

    private fun shouldVibrate(): Boolean = globalSettings.feedbackMode != FeedbackMode.SoundOnly

    private fun vibrate(durationMs: Long, amplitude: Int) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            appContext.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        } ?: return

        if (!vibrator.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                VibrationEffect.createOneShot(
                    durationMs,
                    amplitude.coerceIn(1, 255)
                )
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(durationMs)
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

    private fun restoreStateOrDefault() {
        val raw = prefs.getString(stateKey, null)
        if (raw.isNullOrBlank()) {
            addTimerWithDuration(1, 0, ToneOption.PianoC3, TimerStyle.Slate)
            addTimerWithDuration(2, 0, ToneOption.PianoE3, TimerStyle.Ocean)
            addTimerWithDuration(3, 0, ToneOption.PianoG3, TimerStyle.Forest)
            persistNow()
            return
        }

        runCatching {
            val root = JSONObject(raw)
            val savedAt = root.optLong("savedAtEpochMs", System.currentTimeMillis())
            val elapsed = (System.currentTimeMillis() - savedAt).coerceAtLeast(0L)
            val arr = root.optJSONArray("timers") ?: JSONArray()
            val globalObj = root.optJSONObject("global") ?: JSONObject()
            globalSettings = GlobalSettings(
                feedbackMode = FeedbackMode.entries.getOrElse(
                    globalObj.optInt("feedbackMode", FeedbackMode.SoundOnly.ordinal)
                ) { FeedbackMode.SoundOnly },
                keepScreenOn = globalObj.optBoolean("keepScreenOn", false),
                gestureMode = TapGestureMode.entries.getOrElse(
                    globalObj.optInt("gestureMode", TapGestureMode.SinglePauseDoubleRestart.ordinal)
                ) { TapGestureMode.SinglePauseDoubleRestart }
            )
            val restored = mutableListOf<CountdownItem>()
            var maxId = 0L

            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val id = obj.optLong("id", i + 1L)
                val total = obj.optLong("totalMillis", 0L).coerceAtLeast(0L)
                val remainSaved = obj.optLong("remainingMillis", total).coerceAtLeast(0L)
                val wasRunning = obj.optBoolean("isRunning", false)
                val tone = ToneOption.entries.getOrElse(obj.optInt("tone", 0)) { ToneOption.PianoC3 }
                val style = TimerStyle.entries.getOrElse(obj.optInt("style", 0)) { TimerStyle.Slate }
                val tickAlert = TickAlertOption.entries.getOrElse(
                    obj.optInt("tickAlert", TickAlertOption.Off.ordinal)
                ) { TickAlertOption.Off }
                val customTickSeconds = obj.optInt("customTickSeconds", 5).coerceIn(1, 30)
                val manualAnchor = obj.optLong("manualRestartAnchorEpochMs", savedAt)
                val recordsArr = obj.optJSONArray("manualRestartRecords") ?: JSONArray()
                val records = buildList {
                    for (j in 0 until recordsArr.length()) {
                        val r = recordsArr.optJSONObject(j) ?: continue
                        val sec = if (r.has("durationSeconds")) {
                            r.optLong("durationSeconds", 0L)
                        } else {
                            r.optLong("durationMinutes", 0L) * 60L
                        }
                        add(
                            ManualRestartRecord(
                                epochMs = r.optLong("epochMs", 0L),
                                durationSeconds = sec.coerceAtLeast(0L)
                            )
                        )
                    }
                }

                val remain = if (!wasRunning || total <= 0L) {
                    remainSaved.coerceIn(0L, total)
                } else {
                    val rawRemain = remainSaved - elapsed
                    if (rawRemain > 0L) rawRemain else {
                        val mod = (-rawRemain) % total
                        if (mod == 0L) total else total - mod
                    }
                }

                restored += CountdownItem(
                    id = id,
                    totalMillis = total,
                    remainingMillis = remain.coerceIn(0L, total),
                    isRunning = wasRunning && total > 0L,
                    tone = tone,
                    style = style,
                    tickAlert = tickAlert,
                    customTickSeconds = customTickSeconds,
                    manualRestartAnchorEpochMs = manualAnchor,
                    manualRestartRecords = records
                )
                if (id > maxId) maxId = id
            }

            if (restored.isEmpty()) error("empty")
            timers.clear()
            timers.addAll(restored)
            idSeed = max(root.optLong("idSeed", maxId + 1), maxId + 1)
            timers.filter { it.isRunning && it.totalMillis > 0L }.forEach { startLoop(it.id) }
        }.getOrElse {
            timers.clear()
            addTimerWithDuration(1, 0, ToneOption.PianoC3, TimerStyle.Slate)
            addTimerWithDuration(2, 0, ToneOption.PianoE3, TimerStyle.Ocean)
            addTimerWithDuration(3, 0, ToneOption.PianoG3, TimerStyle.Forest)
            persistNow()
        }
    }

    private fun maybePersist() {
        val now = System.currentTimeMillis()
        if (now - lastPersistMs >= 1000L) {
            persistNow()
        }
    }

    fun persistNow() {
        val now = System.currentTimeMillis()
        val arr = JSONArray()
        timers.forEach { t ->
            val records = JSONArray()
            t.manualRestartRecords.forEach { r ->
                records.put(
                    JSONObject()
                        .put("epochMs", r.epochMs)
                        .put("durationSeconds", r.durationSeconds)
                )
            }
            arr.put(
                JSONObject()
                    .put("id", t.id)
                    .put("totalMillis", t.totalMillis)
                    .put("remainingMillis", t.remainingMillis)
                    .put("isRunning", t.isRunning)
                    .put("tone", t.tone.ordinal)
                    .put("style", t.style.ordinal)
                    .put("tickAlert", t.tickAlert.ordinal)
                    .put("customTickSeconds", t.customTickSeconds)
                    .put("manualRestartAnchorEpochMs", t.manualRestartAnchorEpochMs)
                    .put("manualRestartRecords", records)
            )
        }
        val global = JSONObject()
            .put("feedbackMode", globalSettings.feedbackMode.ordinal)
            .put("keepScreenOn", globalSettings.keepScreenOn)
            .put("gestureMode", globalSettings.gestureMode.ordinal)
        val root = JSONObject()
            .put("idSeed", idSeed)
            .put("savedAtEpochMs", now)
            .put("global", global)
            .put("timers", arr)
        prefs.edit().putString(stateKey, root.toString()).apply()
        lastPersistMs = now
    }

    private fun updateTimer(timerId: Long, transform: (CountdownItem) -> CountdownItem) {
        val index = timers.indexOfFirst { it.id == timerId }
        if (index != -1) timers[index] = transform(timers[index])
    }

    override fun onCleared() {
        persistNow()
        jobs.values.forEach { it.cancel() }
        tickCueSecondCache.clear()
        super.onCleared()
    }
}

@Composable
fun CountdownScreen(vm: CountdownViewModel) {
    var configTimerId by remember { mutableStateOf<Long?>(null) }
    var statsTimerId by remember { mutableStateOf<Long?>(null) }
    var showGlobalSettings by remember { mutableStateOf(false) }
    var burnInStep by remember { mutableStateOf(0) }
    val view = LocalView.current
    val statsVisible = statsTimerId != null

    DisposableEffect(vm.globalSettings.keepScreenOn) {
        view.keepScreenOn = vm.globalSettings.keepScreenOn
        onDispose { view.keepScreenOn = false }
    }

    LaunchedEffect(vm.globalSettings.keepScreenOn) {
        burnInStep = 0
        if (!vm.globalSettings.keepScreenOn) return@LaunchedEffect
        while (true) {
            delay(28_000L)
            burnInStep = (burnInStep + 1) % 5
        }
    }

    LaunchedEffect(statsVisible) {
        vm.onStatsDialogVisibilityChanged(statsVisible)
    }

    val burnInOffsets = listOf(
        0.dp to 0.dp,
        2.dp to 1.dp,
        1.dp to 3.dp,
        3.dp to 2.dp,
        1.dp to 2.dp
    )
    val currentOffset = if (vm.globalSettings.keepScreenOn) burnInOffsets[burnInStep] else burnInOffsets[0]

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
                .offset(x = currentOffset.first, y = currentOffset.second)
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
                                onTap = {
                                    when (vm.globalSettings.gestureMode) {
                                        TapGestureMode.SinglePauseDoubleRestart -> vm.togglePauseResume(timer.id)
                                        TapGestureMode.SingleRestartDoublePause -> vm.restartNow(timer.id)
                                    }
                                },
                                onDoubleTap = {
                                    when (vm.globalSettings.gestureMode) {
                                        TapGestureMode.SinglePauseDoubleRestart -> vm.restartNow(timer.id)
                                        TapGestureMode.SingleRestartDoublePause -> vm.togglePauseResume(timer.id)
                                    }
                                },
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

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .size(48.dp)
                .alpha(0.72f)
                .background(Color(0xFF1B3A57).copy(alpha = 0.45f), CircleShape)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { vm.addTimer() },
                        onLongPress = { showGlobalSettings = true }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "+",
                color = Color(0xFFEAF2FF).copy(alpha = 0.82f),
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium
            )
        }

        val selected = vm.timers.firstOrNull { it.id == configTimerId }
        if (selected != null) {
            TimerConfigDialog(
                timer = selected,
                canDelete = vm.timers.size > 1,
                onDismiss = { configTimerId = null },
                onConfirm = { min, sec, tone, style, tickAlert, customTickSeconds ->
                    vm.configureTimer(
                        timerId = selected.id,
                        minutesText = min,
                        secondsText = sec,
                        tone = tone,
                        style = style,
                        tickAlert = tickAlert,
                        customTickSeconds = customTickSeconds
                    )
                    configTimerId = null
                },
                onDelete = {
                    vm.removeTimer(selected.id)
                    configTimerId = null
                },
                onShowStats = {
                    statsTimerId = selected.id
                }
            )
        }

        val statsTarget = statsTimerId
        if (statsTarget != null) {
            RestartStatsDialog(
                groups = vm.restartStats(statsTarget),
                onDismiss = { statsTimerId = null }
            )
        }

        if (showGlobalSettings) {
            GlobalSettingsDialog(
                settings = vm.globalSettings,
                onDismiss = { showGlobalSettings = false },
                onApply = { feedbackMode, keepScreenOn, gestureMode ->
                    vm.updateGlobalSettings(
                        feedbackMode = feedbackMode,
                        keepScreenOn = keepScreenOn,
                        gestureMode = gestureMode
                    )
                    showGlobalSettings = false
                },
                onExport = { vm.exportAllRecords() }
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
    val render = timer.style.resolve(timer.isRunning)
    Card(
        modifier = Modifier.fillMaxSize(),
        colors = CardDefaults.cardColors(containerColor = render.panelColor),
        shape = RoundedCornerShape(16.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
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
    val style = timer.style.resolve(timer.isRunning)
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
    canDelete: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, String, ToneOption, TimerStyle, TickAlertOption, Int) -> Unit,
    onDelete: () -> Unit,
    onShowStats: () -> Unit
) {
    var minuteInput by remember(timer.id, timer.totalMillis) {
        mutableStateOf((timer.totalMillis / 1000 / 60).toString())
    }
    var secondInput by remember(timer.id, timer.totalMillis) {
        mutableStateOf(((timer.totalMillis / 1000) % 60).toString())
    }
    var selectedTone by remember(timer.id, timer.tone) { mutableStateOf(timer.tone) }
    var selectedStyle by remember(timer.id, timer.style) { mutableStateOf(timer.style) }
    var selectedTickAlert by remember(timer.id, timer.tickAlert) { mutableStateOf(timer.tickAlert) }
    var customTickSecondsInput by remember(timer.id, timer.customTickSeconds) {
        mutableStateOf(timer.customTickSeconds.toString())
    }
    var toneMenuExpanded by remember(timer.id) { mutableStateOf(false) }
    var styleMenuExpanded by remember(timer.id) { mutableStateOf(false) }
    var tickMenuExpanded by remember(timer.id) { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("倒计时设置", color = Color(0xFFF3F7FF)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF102235))
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("时长设置", color = Color(0xFFE9F1FF), fontWeight = FontWeight.SemiBold)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = minuteInput,
                                onValueChange = { minuteInput = it.filter(Char::isDigit).take(3) },
                                label = { Text("分钟") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = secondInput,
                                onValueChange = { secondInput = it.filter(Char::isDigit).take(2) },
                                label = { Text("秒") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                Box {
                    PickerButton(
                        title = "结束音效",
                        value = selectedTone.label,
                        onClick = { toneMenuExpanded = true }
                    )
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
                    PickerButton(
                        title = "样式",
                        value = selectedStyle.label,
                        onClick = { styleMenuExpanded = true }
                    )
                    DropdownMenu(expanded = styleMenuExpanded, onDismissRequest = { styleMenuExpanded = false }) {
                        TimerStyle.entries.forEach { style ->
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        StylePreviewDot(style = style, isRunning = true)
                                        StylePreviewDot(style = style, isRunning = false)
                                        Text(style.label)
                                    }
                                },
                                onClick = {
                                    selectedStyle = style
                                    styleMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                Box {
                    PickerButton(
                        title = "最后滴答",
                        value = selectedTickAlert.label,
                        onClick = { tickMenuExpanded = true }
                    )
                    DropdownMenu(expanded = tickMenuExpanded, onDismissRequest = { tickMenuExpanded = false }) {
                        TickAlertOption.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                onClick = {
                                    selectedTickAlert = option
                                    tickMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                if (selectedTickAlert == TickAlertOption.Custom) {
                    OutlinedTextField(
                        value = customTickSecondsInput,
                        onValueChange = { customTickSecondsInput = it.filter(Char::isDigit).take(2) },
                        label = { Text("自定义秒数（1-30）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Button(
                    onClick = onShowStats,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF223B56),
                        contentColor = Color(0xFFE8F2FF)
                    )
                ) {
                    Text("显示手动重开统计")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val customTickSeconds = customTickSecondsInput.toIntOrNull()?.coerceIn(1, 30) ?: 5
                    onConfirm(
                        minuteInput,
                        secondInput,
                        selectedTone,
                        selectedStyle,
                        selectedTickAlert,
                        customTickSeconds
                    )
                }
            ) {
                Text("应用")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (canDelete) {
                    Button(onClick = onDelete) {
                        Text("删除")
                    }
                }
                Button(onClick = onDismiss) {
                    Text("取消")
                }
            }
        }
    )
}

@Composable
private fun PickerButton(
    title: String,
    value: String,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF1E3550),
            contentColor = Color(0xFFEAF3FF)
        )
    ) {
        Text("$title: $value")
    }
}

@Composable
private fun GlobalSettingsDialog(
    settings: GlobalSettings,
    onDismiss: () -> Unit,
    onApply: (FeedbackMode, Boolean, TapGestureMode) -> Unit,
    onExport: () -> String
) {
    var feedbackMode by remember(settings.feedbackMode) { mutableStateOf(settings.feedbackMode) }
    var keepScreenOn by remember(settings.keepScreenOn) { mutableStateOf(settings.keepScreenOn) }
    var gestureMode by remember(settings.gestureMode) { mutableStateOf(settings.gestureMode) }
    var modeMenuExpanded by remember { mutableStateOf(false) }
    var gestureMenuExpanded by remember { mutableStateOf(false) }
    var exportHint by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("全局设置", color = Color(0xFFF3F7FF)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box {
                    PickerButton(
                        title = "提醒模式",
                        value = feedbackMode.label,
                        onClick = { modeMenuExpanded = true }
                    )
                    DropdownMenu(
                        expanded = modeMenuExpanded,
                        onDismissRequest = { modeMenuExpanded = false }
                    ) {
                        FeedbackMode.entries.forEach { mode ->
                            DropdownMenuItem(
                                text = { Text(mode.label) },
                                onClick = {
                                    feedbackMode = mode
                                    modeMenuExpanded = false
                                }
                            )
                        }
                    }
                }
                Box {
                    PickerButton(
                        title = "手势映射",
                        value = gestureMode.label,
                        onClick = { gestureMenuExpanded = true }
                    )
                    DropdownMenu(
                        expanded = gestureMenuExpanded,
                        onDismissRequest = { gestureMenuExpanded = false }
                    ) {
                        TapGestureMode.entries.forEach { mode ->
                            DropdownMenuItem(
                                text = { Text(mode.label) },
                                onClick = {
                                    gestureMode = mode
                                    gestureMenuExpanded = false
                                }
                            )
                        }
                    }
                }
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF102235))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "屏幕常亮（防烧屏）",
                            color = Color(0xFFE4F0FF)
                        )
                        Switch(
                            checked = keepScreenOn,
                            onCheckedChange = { keepScreenOn = it }
                        )
                    }
                }
                Button(
                    onClick = { exportHint = onExport() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF22415F),
                        contentColor = Color(0xFFEAF3FF)
                    )
                ) {
                    Text("导出所有记录")
                }
                if (exportHint.isNotBlank()) {
                    Card(
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F2236))
                    ) {
                        Text(
                            text = exportHint,
                            color = Color(0xFFD6E7FF),
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onApply(feedbackMode, keepScreenOn, gestureMode) }) {
                Text("应用")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun RestartStatsDialog(
    groups: List<RestartStatsGroup>,
    onDismiss: () -> Unit
) {
    val scrollState = rememberScrollState()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("手动重开统计", color = Color(0xFFF3F7FF)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (groups.isEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF102235)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            "暂无手动重开记录",
                            color = Color(0xFFE3EEFF),
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                } else {
                    groups.forEach { group ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF102235)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(group.title, color = Color(0xFFF1F6FF), fontWeight = FontWeight.SemiBold)
                                group.seconds.chunked(5).forEach { row ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        row.forEach { sec ->
                                            Card(
                                                shape = RoundedCornerShape(10.dp),
                                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1F3853)),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text(
                                                    text = "${sec}",
                                                    color = Color(0xFFE5F0FF),
                                                    textAlign = TextAlign.Center,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 8.dp)
                                                )
                                            }
                                        }
                                        repeat(5 - row.size) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("关闭") }
        }
    )
}

@Composable
private fun StylePreviewDot(style: TimerStyle, isRunning: Boolean) {
    val render = style.resolve(isRunning)
    Canvas(modifier = Modifier.size(22.dp)) {
        drawSafeArc(render.trackColor, 360f, render.strokeDp.coerceAtMost(4f), render.cap)
        drawSafeArc(render.progressColor, 290f, render.strokeDp.coerceAtMost(4f), render.cap)
    }
}

private fun formatMillis(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
