package com.synapse.social.studioasinc.feature.inbox.inbox.voice

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

object VoiceWaveformExtractor {

    private const val DEFAULT_BAR_COUNT = 36
    private const val MIN_AMPLITUDE = 0.15f
    private const val MAX_AMPLITUDE = 1.0f

    suspend fun extractAmplitudes(
        filePath: String,
        barCount: Int = DEFAULT_BAR_COUNT,
        dispatcher: CoroutineDispatcher = Dispatchers.IO
    ): List<Float> = withContext(dispatcher) {
        try {
            val file = File(filePath)
            if (!file.exists() || file.length() == 0L) {
                return@withContext generateFallbackAmplitudes(filePath, barCount)
            }

            val bytes = file.readBytes()
            if (bytes.size < barCount) {
                return@withContext generateFallbackAmplitudes(filePath, barCount)
            }

            val chunkSize = bytes.size / barCount
            val rawAmplitudes = FloatArray(barCount)

            var maxRms = 0f

            for (i in 0 until barCount) {
                val start = i * chunkSize
                val end = if (i == barCount - 1) bytes.size else (i + 1) * chunkSize

                var sumSquares = 0.0
                val count = end - start
                for (j in start until end) {
                    val sample = bytes[j].toInt()
                    sumSquares += (sample * sample)
                }

                val rms = if (count > 0) sqrt(sumSquares / count).toFloat() else 0f
                rawAmplitudes[i] = rms
                if (rms > maxRms) {
                    maxRms = rms
                }
            }

            if (maxRms <= 0f) {
                return@withContext generateFallbackAmplitudes(filePath, barCount)
            }

            // Normalize values into [MIN_AMPLITUDE, MAX_AMPLITUDE]
            rawAmplitudes.map { rms ->
                val normalized = rms / maxRms
                (MIN_AMPLITUDE + normalized * (MAX_AMPLITUDE - MIN_AMPLITUDE)).coerceIn(MIN_AMPLITUDE, MAX_AMPLITUDE)
            }
        } catch (e: Exception) {
            generateFallbackAmplitudes(filePath, barCount)
        }
    }

    fun generateFallbackAmplitudes(seedStr: String, barCount: Int = DEFAULT_BAR_COUNT): List<Float> {
        val hash = seedStr.hashCode()
        return List(barCount) { index ->
            val angle = (index + (abs(hash) % 10)) * 0.35f
            val wave = (sin(angle.toDouble()).toFloat() * 0.4f + 0.6f)
            wave.coerceIn(MIN_AMPLITUDE, MAX_AMPLITUDE)
        }
    }
}
