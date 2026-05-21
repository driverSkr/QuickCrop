package com.ethan.base

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.annotation.ColorInt
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Activity 基类，用来收敛所有页面通用的行为和工具方法。
 *
 * 当前基类保持轻量，只放和页面生命周期、跳转、系统 UI、输入法、Intent 参数读取等
 * 强相关的基础能力，避免把具体业务逻辑下沉到 base 模块。
 */
abstract class BaseActivity : AppCompatActivity {
    /**
     * 日志 Tag，默认使用当前 Activity 类名。
     *
     * 子类如果需要统一模块前缀或更短的日志名称，可以 override 这个属性。
     */
    protected open val logTag: String = javaClass.simpleName

    constructor() : super()

    constructor(@LayoutRes contentLayoutId: Int) : super(contentLayoutId)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onActivityCreated(savedInstanceState)
        logLifecycle("onCreate")
    }

    override fun onStart() {
        super.onStart()
        logLifecycle("onStart")
    }

    override fun onResume() {
        super.onResume()
        logLifecycle("onResume")
    }

    override fun onPause() {
        logLifecycle("onPause")
        super.onPause()
    }

    override fun onStop() {
        logLifecycle("onStop")
        super.onStop()
    }

    override fun onDestroy() {
        logLifecycle("onDestroy")
        super.onDestroy()
    }

    /**
     * onCreate 的模板方法。
     *
     * 子类如果只需要在 setContentView 之外追加初始化逻辑，可以 override 这里；
     * 如果页面初始化流程比较特殊，也可以继续直接 override onCreate。
     */
    protected open fun onActivityCreated(savedInstanceState: Bundle?) = Unit

    /**
     * 显示短提示。
     *
     * 统一封装 Toast，后续如果要替换成 Snackbar、全局 Toast 管理器或埋点提示，
     * 可以只改这一处。
     */
    protected fun showToast(message: CharSequence, duration: Int = Toast.LENGTH_SHORT) {
        Toast.makeText(this, message, duration).show()
    }

    /**
     * 安全启动 Activity。
     *
     * 启动失败时不会让页面崩溃，而是记录 warning 日志并返回 false。
     * 适合外部 Intent、系统页面、文件预览等不完全受当前 App 控制的跳转。
     */
    protected fun startActivitySafely(intent: Intent, options: Bundle? = null): Boolean {
        return runCatching {
            startActivity(intent, options)
        }.onFailure { throwable ->
            Log.w(logTag, "Unable to start activity.", throwable)
        }.isSuccess
    }

    /**
     * 泛型方式启动 App 内 Activity。
     *
     * 示例：
     * startActivity<DetailActivity> {
     *     putExtra("id", id)
     * }
     */
    protected inline fun <reified T : Activity> startActivity(
        options: Bundle? = null,
        configure: Intent.() -> Unit = {}
    ): Boolean {
        val intent = Intent(this, T::class.java).apply(configure)
        return startActivitySafely(intent, options)
    }

    /**
     * 设置页面返回结果并关闭当前页面。
     *
     * 用于 Activity Result 场景，避免子类重复写 setResult + finish。
     */
    protected fun finishWithResult(resultCode: Int = RESULT_OK, data: Intent? = null) {
        setResult(resultCode, data)
        finish()
    }

    /**
     * 读取 String 类型 Intent 参数。
     *
     * 当参数不存在时返回 defaultValue，适合可选参数。
     */
    protected fun getStringExtra(key: String, defaultValue: String? = null): String? {
        return intent?.getStringExtra(key) ?: defaultValue
    }

    /**
     * 读取必传的 String 类型 Intent 参数。
     *
     * 参数缺失时会抛出异常，适合明确依赖上游传参的页面入口。
     */
    protected fun requireStringExtra(key: String): String {
        return requireNotNull(getStringExtra(key)) {
            "Missing required intent extra: $key"
        }
    }

    /**
     * 兼容不同 Android 版本的 Parcelable 参数读取。
     *
     * Android 13 之后系统推荐使用带 Class 的 getParcelableExtra；
     * 这里统一封装，子类不需要关心版本分支。
     */
    protected inline fun <reified T : Parcelable> getParcelableExtraCompat(key: String): T? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(key, T::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(key)
        }
    }

    /**
     * 设置防重复点击监听。
     *
     * intervalMs 时间窗口内的重复点击会被忽略，适合跳转、提交、刷新等操作。
     */
    protected fun View.setDebouncedClickListener(
        intervalMs: Long = DEFAULT_CLICK_INTERVAL_MS,
        onClick: (View) -> Unit
    ) {
        setOnClickListener(object : View.OnClickListener {
            private var lastClickTime = 0L

            override fun onClick(view: View) {
                val now = System.currentTimeMillis()
                if (now - lastClickTime >= intervalMs) {
                    lastClickTime = now
                    onClick(view)
                }
            }
        })
    }

    /**
     * 隐藏软键盘并清除焦点。
     *
     * anchor 传入当前输入框时效果最准确；不传则使用 currentFocus，
     * 如果当前没有焦点则退回到 decorView。
     */
    protected fun hideKeyboard(anchor: View? = currentFocus) {
        val target = anchor ?: window.decorView
        WindowInsetsControllerCompat(window, target).hide(WindowInsetsCompat.Type.ime())
        val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        inputMethodManager?.hideSoftInputFromWindow(target.windowToken, 0)
        target.clearFocus()
    }

    /**
     * 配置状态栏、导航栏和 edge-to-edge 显示。
     *
     * edgeToEdge 为 true 时内容可以延伸到系统栏区域；
     * lightStatusBar/lightNavigationBar 控制系统栏图标颜色；
     * statusBarColor/navigationBarColor 可用于传统非沉浸式页面设置栏背景色。
     */
    protected fun configureSystemBars(
        edgeToEdge: Boolean = true,
        lightStatusBar: Boolean = false,
        lightNavigationBar: Boolean = false,
        @ColorInt statusBarColor: Int? = null,
        @ColorInt navigationBarColor: Int? = null
    ) {
        WindowCompat.setDecorFitsSystemWindows(window, !edgeToEdge)
        statusBarColor?.let { window.statusBarColor = it }
        navigationBarColor?.let { window.navigationBarColor = it }

        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = lightStatusBar
            isAppearanceLightNavigationBars = lightNavigationBar
        }
    }

    private fun logLifecycle(event: String) {
        if (BuildConfig.DEBUG) {
            Log.d(logTag, event)
        }
    }

    private companion object {
        const val DEFAULT_CLICK_INTERVAL_MS = 600L
    }
}
