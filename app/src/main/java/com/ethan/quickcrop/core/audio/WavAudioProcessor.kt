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

object WavAudioProcessor {
    private const val TAG = "WavAudioProcessor"
    private const val COPY_BUFFER_SIZE = 32 * 1024

    fun writeEmptyHeader(output: RandomAccessFile) {
        output.seek(0L)
        writeHeader(output = output, pcmDataSize = 0L)
    }

    fun finalizeHeader(output: RandomAccessFile, pcmDataSize: Long) {
        output.seek(0L)
        writeHeader(output = output, pcmDataSize = pcmDataSize.coerceAtLeast(0L))
    }

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
            val safeStartMs = startMs.coerceIn(0L, (sourceDurationMs - 1L).coerceAtLeast(0L))
            val safeEndMs = endMs.coerceIn(safeStartMs + 1L, sourceDurationMs)
            val frameSize = AUDIO_CHANNEL_COUNT * (AUDIO_BITS_PER_SAMPLE / 8)
            val startByte = ((safeStartMs / 1_000.0) * AUDIO_BYTES_PER_SECOND)
                .roundToLong()
                .alignDown(frameSize)
                .coerceIn(0L, sourcePcmSize)
            val endByte = ((safeEndMs / 1_000.0) * AUDIO_BYTES_PER_SECOND)
                .roundToLong()
                .alignDown(frameSize)
                .coerceIn(startByte + frameSize, sourcePcmSize)
            val selectedPcmSize = endByte - startByte

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
                input.seek(AUDIO_WAV_HEADER_SIZE + startByte)

                val buffer = ByteArray(COPY_BUFFER_SIZE)
                var remaining = selectedPcmSize
                while (remaining > 0L) {
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
                put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/QuickCrop")
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
        }

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
                values.clear()
                values.put(MediaStore.Audio.Media.IS_PENDING, 0)
                resolver.update(outputUri, values, null, null)
            }
            completed = true
            Log.d(TAG, "音频已保存到系统音乐目录: $outputUri")
            return outputUri
        } finally {
            if (!completed) {
                runCatching { resolver.delete(outputUri, null, null) }
                    .onFailure { throwable -> Log.w(TAG, "清理导出失败音频条目失败", throwable) }
            }
        }
    }

    private fun writeHeader(output: RandomAccessFile, pcmDataSize: Long) {
        val byteRate = AUDIO_BYTES_PER_SECOND
        val blockAlign = AUDIO_CHANNEL_COUNT * (AUDIO_BITS_PER_SAMPLE / 8)
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

    private fun RandomAccessFile.writeAscii(value: String) {
        write(value.toByteArray(Charsets.US_ASCII))
    }

    private fun RandomAccessFile.writeLittleEndianInt(value: Int) {
        write(value and 0xFF)
        write(value shr 8 and 0xFF)
        write(value shr 16 and 0xFF)
        write(value shr 24 and 0xFF)
    }

    private fun RandomAccessFile.writeLittleEndianShort(value: Int) {
        write(value and 0xFF)
        write(value shr 8 and 0xFF)
    }

    private fun Long.alignDown(frameSize: Int): Long {
        return this - this % frameSize
    }

    private fun String.ensureWavExtension(): String {
        return if (endsWith(".wav", ignoreCase = true)) this else "$this.wav"
    }
}
