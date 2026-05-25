# QuickCrop

QuickCrop 是一个基于 Android Jetpack Compose 的音视频与图片处理示例项目，当前重点演示两类能力：

- 视频裁剪：选择本地视频，使用 ExoPlayer 预览，生成时间轴缩略图，并通过 Media3 Transformer 导出裁剪片段
- 图片裁剪：选择本地图片，支持缩放、平移和指定比例裁剪，并导出裁剪结果

这个项目更偏向“可运行的工程示例”，适合用来学习 Compose 页面组织、Media3 音视频处理，以及图片裁剪导出的实现方式。

## 功能概览

- 视频页面
  - 选择本地视频文件
  - 使用 Media3 ExoPlayer 进行预览
  - 从视频中提取多帧缩略图
  - 自定义时间轴，拖动左右手柄选择裁剪区间
  - 导出裁剪后的视频文件
- 图片页面
  - 选择本地图片文件
  - 支持缩放与平移预览
  - 支持 `1:1`、`4:5`、`16:9` 三种裁剪比例
  - 导出裁剪后的图片文件

## 技术栈

- Kotlin 2.0
- Android Gradle Plugin 8.x
- Jetpack Compose
- Material 3
- Media3 ExoPlayer
- Media3 Transformer

## 环境要求

- Android Studio 建议使用较新的稳定版本
- JDK 17
- Android SDK
- `minSdk = 23`
- `targetSdk = 36`

## 项目结构

```text
QuickCrop/
├── README.md
├── driverSkr/
│   └── QuickCrop/
│       ├── app/
│       ├── gradle/
│       ├── build.gradle.kts
│       ├── settings.gradle.kts
│       └── ...
└── ...
```

仓库中的 Android 工程实际位于 `driverSkr/QuickCrop` 目录下。

## 核心实现说明

### 视频裁剪

- `VideoEditorScreen` 负责视频导入、预览、时间轴交互和导出触发
- `VideoPreviewRepository` 使用 `MediaMetadataRetriever` 读取时长并提取缩略图
- `VideoTrimTimeline` 负责绘制时间轴、手柄拖动和高亮选区
- `VideoExportRepository` 使用 `Media3 Transformer` 按裁剪区间导出视频

### 图片裁剪

- `ImageCropScreen` 负责图片导入、缩放平移交互和导出触发
- `ImageCropRepository` 负责图片解码与裁剪结果输出
- `CropAspectRatio` 定义了当前支持的裁剪比例

### Compose 自定义绘制速查

图片裁剪框这类 UI 不一定适合完全用现成组件拼出来。比如半透明遮罩、挖空区域、九宫格辅助线、手柄、选区边框等效果，通常会用 Compose 的绘制 API 自己画。

#### Canvas

`Canvas` 是 Compose 提供的画布组件。它会创建一块可以绘制图形的区域，绘制代码写在 `Canvas { ... }` 代码块里。

作用：

- 绘制自定义图形，比如矩形、线条、圆形、路径、图片等
- 适合实现裁剪框、时间轴、波形图、进度条、标尺、涂鸦等自定义 UI
- 绘制逻辑会随着 Compose 状态变化自动重新执行

写法：

```kotlin
Canvas(
    modifier = Modifier.fillMaxSize()
) {
    drawRect(
        color = Color.Black.copy(alpha = 0.55f),
        size = size
    )
}
```

#### Rect

`Rect` 用来描述一个矩形区域，它本身不负责绘制，只保存矩形的边界坐标。

作用：

- 表示裁剪框、选区、碰撞区域、点击区域等矩形范围
- 可以通过 `left`、`top`、`right`、`bottom` 获取四条边的位置
- 可以通过 `width`、`height` 获取宽高
- 可以用 `translate()` 平移矩形，适合拖动裁剪框这类场景

写法：

```kotlin
var cropRect by remember {
    mutableStateOf(
        Rect(
            left = 100f,
            top = 200f,
            right = 700f,
            bottom = 800f
        )
    )
}

cropRect = cropRect.translate(dx, dy)
```

#### Offset

`Offset` 表示一个点的位置，也就是二维坐标里的 `(x, y)`。

作用：

- 指定矩形左上角
- 指定线条起点和终点
- 指定圆心、触摸点、拖动位置等

写法：

```kotlin
val topLeft = Offset(x = 100f, y = 200f)
```

#### Size

`Size` 表示一个尺寸，也就是宽度和高度。

作用：

- 指定矩形、椭圆、图片等绘制区域的大小
- `Canvas` 代码块里的 `size` 表示当前画布的完整尺寸

写法：

```kotlin
val rectSize = Size(
    width = cropRect.width,
    height = cropRect.height
)
```

#### drawRect

`drawRect` 用来绘制矩形。默认是填充整个矩形区域，如果传入 `style = Stroke(...)`，则只绘制边框。

作用：

- 绘制背景、遮罩、选区、裁剪框边框
- 配合 `BlendMode.Clear` 可以把某个区域清空，做出“挖空遮罩”的效果

填充矩形：

```kotlin
drawRect(
    color = Color.Black.copy(alpha = 0.55f),
    size = size
)
```

绘制边框矩形：

```kotlin
drawRect(
    color = Color.White,
    topLeft = Offset(cropRect.left, cropRect.top),
    size = Size(cropRect.width, cropRect.height),
    style = Stroke(width = 4f)
)
```

清空矩形区域：

```kotlin
drawRect(
    color = Color.Transparent,
    topLeft = Offset(cropRect.left, cropRect.top),
    size = Size(cropRect.width, cropRect.height),
    blendMode = BlendMode.Clear
)
```

#### drawLine

`drawLine` 用来绘制一条直线，需要指定起点、终点、颜色和线宽。

作用：

- 绘制九宫格辅助线
- 绘制时间轴刻度
- 绘制分割线、指示线、参考线等

写法：

```kotlin
drawLine(
    color = Color.White.copy(alpha = 0.6f),
    start = Offset(x, cropRect.top),
    end = Offset(x, cropRect.bottom),
    strokeWidth = 2f
)
```

#### 常用绘制函数

除了 `drawRect` 和 `drawLine`，Compose Canvas 还提供了很多绘制函数：

```kotlin
drawCircle()      // 绘制圆形
drawOval()        // 绘制椭圆
drawArc()         // 绘制圆弧或扇形
drawPath()        // 绘制自定义路径，适合复杂形状
drawPoints()      // 绘制多个点
drawImage()       // 绘制图片
drawRoundRect()   // 绘制圆角矩形
drawText()        // 绘制文字，通常需要配合 TextMeasurer
```

示例：

```kotlin
drawCircle(
    color = Color.Red,
    radius = 50f,
    center = Offset(100f, 100f)
)

drawRoundRect(
    color = Color.Blue,
    topLeft = Offset(50f, 50f),
    size = Size(200f, 100f),
    cornerRadius = CornerRadius(16f, 16f)
)
```

#### Modifier 绘制相关 API

除了直接使用 `Canvas` 组件，也可以通过 `Modifier` 在普通 Compose 组件上增加绘制逻辑。

```kotlin
Modifier.drawBehind {
    drawRect(Color.Red)
}
```

常用 API：

- `drawBehind { ... }`：在组件内容背后绘制，适合画背景、底线、装饰图形
- `drawWithContent { ... }`：可以控制“先画内容还是先画自定义图形”，适合遮罩、前景覆盖等效果
- `drawWithCache { ... }`：可以缓存复杂计算结果，适合路径、渐变、文字测量等开销较大的绘制

#### 自定义 View 一定要用这些函数吗

不一定。

如果是 Compose 页面里的普通 UI，优先使用现成组件，例如 `Box`、`Row`、`Column`、`Image`、`Text`、`Button` 等。只有当现成组件很难表达某种视觉效果时，才需要使用 `Canvas` 或绘制相关 `Modifier`。

如果是在传统 Android View 体系里自定义 View，通常是继承 `View` 并重写 `onDraw()`：

```kotlin
override fun onDraw(canvas: android.graphics.Canvas) {
    super.onDraw(canvas)
    canvas.drawRect(...)
    canvas.drawLine(...)
}
```

所以可以这样理解：

- Compose 自定义绘制：常用 `Canvas`、`drawRect`、`drawLine` 等 Compose 绘制 API
- 传统 Android 自定义 View：常用 `android.graphics.Canvas`、`Paint`、`onDraw()`
- 普通界面开发：优先用现成 Compose 组件，不需要手动画图

## 运行方式

1. 使用 Android Studio 打开仓库根目录
2. 等待 Gradle 同步完成
3. 进入 `driverSkr/QuickCrop` 工程目录
4. 连接 Android 设备或启动模拟器
5. 直接运行 `app` 模块

## 使用说明

### 视频裁剪

1. 进入“视频剪辑”页
2. 点击“选择视频”
3. 通过时间轴左右手柄调整裁剪区间
4. 点击“导出视频”
5. 导出结果会保存到应用外部文件目录的 `Movies` 目录下

### 图片裁剪

1. 进入“图片裁剪”页
2. 点击“选择图片”
3. 双指缩放或拖动图片，调整裁剪位置
4. 选择裁剪比例
5. 点击“导出裁剪结果”
6. 导出结果会保存到应用外部文件目录的 `Pictures` 目录下

## 输出文件位置

- 视频导出文件：`Android/data/com.ethan.quickcrop/files/Movies/`
- 图片导出文件：`Android/data/com.ethan.quickcrop/files/Pictures/`

文件名会自动带上时间戳，便于区分每次导出的结果。

## 注意事项

- 当前项目以示例演示为主，适合学习和二次开发
- 导出功能依赖设备可用的媒体能力，部分机型或格式可能存在兼容性差异
- 读取本地图片或视频时，系统会通过文件选择器申请访问权限

## 后续可以继续扩展的方向

- 增加更多视频剪辑能力，比如倍速、封面导出、音轨处理
- 为图片裁剪增加旋转、翻转、网格辅助线等能力
- 增加导出进度提示和结果预览
- 补充单元测试和 UI 测试
