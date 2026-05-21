package com.ethan.quickcrop.core.model

data class TrimRange(
    val startMs: Long,
    val endMs: Long
) {
    init {
        require(startMs >= 0) { "开始时间不能小于 0" }
        require(endMs >= startMs) { "结束时间不能早于开始时间" }
    }

    val durationMs: Long
        get() = endMs - startMs
}

