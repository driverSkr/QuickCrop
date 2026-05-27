package com.ethan.quickcrop.extension

import android.app.Activity
import android.content.Context
import android.util.Log

private const val TAG = "ContextExt"

fun Context.finishActivity() {
    // Context 可能来自预览或包装类，安全转换后再关闭页面，避免非 Activity 场景崩溃。
    val activity = this as? Activity
    if (activity != null) {
        activity.finish()
    } else {
        Log.w(TAG, "当前 Context 不是 Activity，无法关闭页面")
    }
}
