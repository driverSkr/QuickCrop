package com.ethan.quickcrop.core.audio

import java.io.File

/**
 * 录音流程状态，用于驱动页面展示和服务内部状态机。
 */
enum class AudioRecordingStatus {
    /** 未开始录音，页面展示准备状态。 */
    Idle,

    /** 正在读取麦克风数据并写入缓存 WAV 文件。 */
    Recording,

    /** 录音暂时停止读取，但保留已录制内容和录音器实例。 */
    Paused,

    /** 录音已结束，WAV 文件头已经回写完成，可用于播放、裁剪和导出。 */
    Completed,

    /** 录音启动或录制过程中发生错误，需要向用户展示失败原因。 */
    Error
}

/**
 * 录音标记点，记录用户在录音过程中点击标记时的时间位置和展示文案。
 */
data class AudioRecordingMarker(
    /** 标记点在录音中的毫秒位置。 */
    val positionMs: Long,

    /** 标记点展示名称，如“标记 1”。 */
    val label: String
)

/**
 * 录音服务暴露给页面的完整状态快照，页面通过 StateFlow 订阅它刷新 UI。
 */
data class AudioRecordingState(
    /** 当前录音状态。 */
    val status: AudioRecordingStatus = AudioRecordingStatus.Idle,

    /** 已录制的有效音频时长，单位毫秒。 */
    val elapsedMs: Long = 0L,

    /** 当前输入峰值分贝，用于提示音量过载。 */
    val peakDb: Float = MIN_AUDIO_DB,

    /** 实时波形柱数据，只保留最近一次读取缓冲区的展示结果。 */
    val liveWaveform: List<Float> = List(LIVE_WAVEFORM_BAR_COUNT) { 0.08F },

    /** 整段录音的抽样波形，用于完成后的裁剪界面展示。 */
    val recordedWaveform: List<Float> = emptyList(),

    /** 用户在录音过程中添加的标记点。 */
    val markers: List<AudioRecordingMarker> = emptyList(),

    /** 当前录音缓存文件，完成前后都保存在应用缓存目录。 */
    val outputFile: File? = null,

    /** 导出时使用的默认文件名。 */
    val displayName: String = "",

    /** 一次性错误或提示文案，页面读取后通过 Toast 展示。 */
    val errorMessage: String? = null
) {
    /** 是否已经生成可用录音文件。 */
    val hasRecording: Boolean
        get() = outputFile?.exists() == true && elapsedMs > 0L
}

/** WAV 录音采样率，48kHz 兼顾质量和通用播放器兼容性。 */
const val AUDIO_SAMPLE_RATE = 48_000

/** 单声道录音，减少文件体积并简化 PCM 帧对齐计算。 */
const val AUDIO_CHANNEL_COUNT = 1

/** PCM 16-bit 采样位深，对应 Android AudioRecord 的 ENCODING_PCM_16BIT。 */
const val AUDIO_BITS_PER_SAMPLE = 16

/** 标准 PCM WAV 文件头大小。 */
const val AUDIO_WAV_HEADER_SIZE = 44L

/** 单次录音最长 60 分钟，避免缓存文件无限增长。 */
const val AUDIO_MAX_DURATION_MS = 60L * 60L * 1_000L

/** 实时波形固定柱数，和录音页面的实时波形面板保持一致。 */
const val LIVE_WAVEFORM_BAR_COUNT = 18

/** 无输入或暂停时使用的最低分贝值。 */
const val MIN_AUDIO_DB = -60F

/** 每秒 PCM 字节数，用于在字节偏移和毫秒时长之间换算。 */
val AUDIO_BYTES_PER_SECOND: Long
    get() = AUDIO_SAMPLE_RATE.toLong() * AUDIO_CHANNEL_COUNT * (AUDIO_BITS_PER_SAMPLE / 8)
