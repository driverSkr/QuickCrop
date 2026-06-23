package com.ethan.quickcrop.core.audio

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import kotlin.math.roundToLong

/**
 * WAV 音频处理工具，负责写入 WAV 文件头、裁剪 PCM 数据并导出到系统音乐目录。
 */
object WavAudioProcessor {
    private const val TAG = "WavAudioProcessor"

    /** 文件复制缓冲区大小，兼顾内存占用和导出速度。 */
    private const val COPY_BUFFER_SIZE = 32 * 1024

    /**
     * 写入占位 WAV 文件头，录音开始时还不知道最终 PCM 数据长度。
     */
    fun writeEmptyHeader(output: RandomAccessFile) {
        output.seek(0L)
        writeHeader(output = output, pcmDataSize = 0L)
    }

    /**
     * 录音结束后回写真实 WAV 文件头，使文件可被播放器正确识别。
     */
    fun finalizeHeader(output: RandomAccessFile, pcmDataSize: Long) {
        output.seek(0L)
        writeHeader(output = output, pcmDataSize = pcmDataSize.coerceAtLeast(0L))
    }

    /**
     * 将源 WAV 的指定时间区间导出到系统 Music/QuickCrop 目录。
     */
    fun exportSelectionToMusic(
        context: Context,
        sourceFile: File,
        displayName: String,
        startMs: Long,
        endMs: Long
    ): Result<Uri> {
        return runCatching {
            require(sourceFile.exists()) { "录音文件不存在" }
            require(sourceFile.length() > AUDIO_WAV_HEADER_SIZE) { "录音文件内容为空" }

            val sourcePcmSize = sourceFile.length() - AUDIO_WAV_HEADER_SIZE
            val sourceDurationMs = sourcePcmSize * 1_000L / AUDIO_BYTES_PER_SECOND
            require(sourceDurationMs > 0L) { "录音时长无效" }
            // 先把用户裁剪时间约束到源音频范围内，避免越界读取 PCM 数据。
            val safeStartMs = startMs.coerceIn(0L, (sourceDurationMs - 1L).coerceAtLeast(0L))
            val safeEndMs = endMs.coerceIn(safeStartMs + 1L, sourceDurationMs)
            val frameSize = AUDIO_CHANNEL_COUNT * (AUDIO_BITS_PER_SAMPLE / 8)
            // WAV PCM 必须按完整采样帧裁剪，防止从半个 sample 开始导致噪声或文件损坏。
            val startByte = ((safeStartMs / 1_000.0) * AUDIO_BYTES_PER_SECOND)
                .roundToLong()
                .alignDown(frameSize)
                .coerceIn(0L, sourcePcmSize)
            val endByte = ((safeEndMs / 1_000.0) * AUDIO_BYTES_PER_SECOND)
                .roundToLong()
                .alignDown(frameSize)
                .coerceIn(startByte + frameSize, sourcePcmSize)
            val selectedPcmSize = endByte - startByte

            // 先生成临时裁剪 WAV，写入 MediaStore 成功或失败后都会清理。
            val tempFile = File.createTempFile("quickcrop_audio_", ".wav", context.cacheDir)
            try {
                createSelectionFile(
                    sourceFile = sourceFile,
                    outputFile = tempFile,
                    startByte = startByte,
                    selectedPcmSize = selectedPcmSize
                )
                saveWavToMusic(
                    context = context,
                    wavFile = tempFile,
                    displayName = displayName.ensureWavExtension()
                )
            } finally {
                if (!tempFile.delete()) {
                    Log.w(TAG, "临时裁剪音频删除失败: ${tempFile.absolutePath}")
                }
            }
        }.onFailure { throwable ->
            Log.e(TAG, "导出 WAV 音频失败", throwable)
        }
    }

    /**
     * 从源 WAV 的 PCM 数据区复制指定字节范围，生成新的临时 WAV 文件。
     */
    private fun createSelectionFile(
        sourceFile: File,
        outputFile: File,
        startByte: Long,
        selectedPcmSize: Long
    ) {
        RandomAccessFile(sourceFile, "r").use { input ->
            RandomAccessFile(outputFile, "rw").use { output ->
                output.setLength(0L)
                writeHeader(output = output, pcmDataSize = selectedPcmSize)
                // 跳过源文件 44 字节 WAV 头，只复制用户选择的 PCM 数据段。
                input.seek(AUDIO_WAV_HEADER_SIZE + startByte)

                val buffer = ByteArray(COPY_BUFFER_SIZE)
                var remaining = selectedPcmSize
                while (remaining > 0L) {
                    // 每轮最多读取剩余字节数，确保不会把裁剪范围之后的数据写入成品。
                    val readSize = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                    if (readSize <= 0) {
                        error("读取录音数据失败，剩余字节数: $remaining")
                    }
                    output.write(buffer, 0, readSize)
                    remaining -= readSize
                }
            }
        }
    }

    /**
     * 通过 MediaStore 将 WAV 文件写入系统音乐库，返回系统媒体 Uri。
     */
    private fun saveWavToMusic(
        context: Context,
        wavFile: File,
        displayName: String
    ): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/wav")
            put(MediaStore.Audio.Media.DURATION, (wavFile.length() - AUDIO_WAV_HEADER_SIZE) * 1_000L / AUDIO_BYTES_PER_SECOND)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ 使用相对路径和 pending 状态，写完后再公开给媒体库。
                put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/QuickCrop")
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
        }

        // Android 10+ 优先写入主外部存储卷，旧系统继续使用兼容的外部音频 URI。
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        val outputUri = requireNotNull(resolver.insert(collection, values)) {
            "创建系统音乐文件失败"
        }
        var completed = false
        try {
            resolver.openOutputStream(outputUri)?.use { outputStream ->
                FileInputStream(wavFile).use { inputStream ->
                    inputStream.copyTo(outputStream, COPY_BUFFER_SIZE)
                }
            } ?: error("打开系统音乐输出流失败")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // 写入完成后取消 pending，让系统音乐库和其他 App 可以看到该音频。
                values.clear()
                values.put(MediaStore.Audio.Media.IS_PENDING, 0)
                resolver.update(outputUri, values, null, null)
            }
            completed = true
            Log.d(TAG, "音频已保存到系统音乐目录: $outputUri")
            return outputUri
        } finally {
            if (!completed) {
                // 导出中途失败时删除已创建的媒体库条目，避免系统相册/音乐库留下坏文件。
                runCatching { resolver.delete(outputUri, null, null) }
                    .onFailure { throwable -> Log.w(TAG, "清理导出失败音频条目失败", throwable) }
            }
        }
    }

    /**
     * 按 PCM WAV 规范写入 44 字节文件头。
     */
    private fun writeHeader(output: RandomAccessFile, pcmDataSize: Long) {
        val byteRate = AUDIO_BYTES_PER_SECOND
        val blockAlign = AUDIO_CHANNEL_COUNT * (AUDIO_BITS_PER_SAMPLE / 8)
        // RIFF Chunk 描述整个 WAV 文件大小，data Chunk 描述后续 PCM 数据大小。
        output.writeAscii("RIFF")
        output.writeLittleEndianInt((36L + pcmDataSize).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
        output.writeAscii("WAVE")
        output.writeAscii("fmt ")
        output.writeLittleEndianInt(16)
        output.writeLittleEndianShort(1)
        output.writeLittleEndianShort(AUDIO_CHANNEL_COUNT)
        output.writeLittleEndianInt(AUDIO_SAMPLE_RATE)
        output.writeLittleEndianInt(byteRate.toInt())
        output.writeLittleEndianShort(blockAlign)
        output.writeLittleEndianShort(AUDIO_BITS_PER_SAMPLE)
        output.writeAscii("data")
        output.writeLittleEndianInt(pcmDataSize.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
    }

    /**
     * 写入 WAV 头中的 ASCII 标识，如 RIFF、WAVE、fmt 和 data。
     */
    private fun RandomAccessFile.writeAscii(value: String) {
        write(value.toByteArray(Charsets.US_ASCII))
    }

    /**
     * 以小端序写入 32 位整数，符合 WAV 文件格式要求。
     */
    private fun RandomAccessFile.writeLittleEndianInt(value: Int) {
        write(value and 0xFF)
        write(value shr 8 and 0xFF)
        write(value shr 16 and 0xFF)
        write(value shr 24 and 0xFF)
    }

    /**
     * 以小端序写入 16 位整数，符合 WAV 文件格式要求。
     */
    private fun RandomAccessFile.writeLittleEndianShort(value: Int) {
        write(value and 0xFF)
        write(value shr 8 and 0xFF)
    }

    /**
     * 将字节偏移向下对齐到完整 PCM 帧边界。
     */
    private fun Long.alignDown(frameSize: Int): Long {
        return this - this % frameSize
    }

    /**
     * 确保导出文件名带有 .wav 后缀。
     */
    private fun String.ensureWavExtension(): String {
        return if (endsWith(".wav", ignoreCase = true)) this else "$this.wav"
    }
}
