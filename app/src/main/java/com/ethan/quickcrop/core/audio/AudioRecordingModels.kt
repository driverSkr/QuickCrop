package com.ethan.quickcrop.core.audio

import java.io.File

enum class AudioRecordingStatus {
    Idle,
    Recording,
    Paused,
    Completed,
    Error
}

data class AudioRecordingMarker(
    val positionMs: Long,
    val label: String
)

data class AudioRecordingState(
    val status: AudioRecordingStatus = AudioRecordingStatus.Idle,
    val elapsedMs: Long = 0L,
    val peakDb: Float = MIN_AUDIO_DB,
    val liveWaveform: List<Float> = List(LIVE_WAVEFORM_BAR_COUNT) { 0.08F },
    val recordedWaveform: List<Float> = emptyList(),
    val markers: List<AudioRecordingMarker> = emptyList(),
    val outputFile: File? = null,
    val displayName: String = "",
    val errorMessage: String? = null
) {
    val hasRecording: Boolean
        get() = outputFile?.exists() == true && elapsedMs > 0L
}

const val AUDIO_SAMPLE_RATE = 48_000
const val AUDIO_CHANNEL_COUNT = 1
const val AUDIO_BITS_PER_SAMPLE = 16
const val AUDIO_WAV_HEADER_SIZE = 44L
const val AUDIO_MAX_DURATION_MS = 60L * 60L * 1_000L
const val LIVE_WAVEFORM_BAR_COUNT = 18
const val MIN_AUDIO_DB = -60F

val AUDIO_BYTES_PER_SECOND: Long
    get() = AUDIO_SAMPLE_RATE.toLong() * AUDIO_CHANNEL_COUNT * (AUDIO_BITS_PER_SAMPLE / 8)
