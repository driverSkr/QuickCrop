# QuickCrop

QuickCrop 是一个基于 Android Jetpack Compose 的本地图片选择与编辑示例项目。当前主流程聚焦在图片处理链路：

- 自定义相册：读取本地图片，支持最近项目、相册分组、网格预览和大图预览
- 图片导入：校验图片格式与尺寸，支持 JPG、PNG、WebP，并将 HEIC/HEIF 转换为 JPG
- 图片编辑：支持裁剪、镜像、90 度旋转、角度微调、滤镜、亮度/对比度/饱和度/色温/清晰度调节
- 结果预览：编辑完成后保存到相册，并进入独立结果页查看最终图片

这个项目更偏向“可运行的工程示例”，适合用来学习 Compose 页面组织、MediaStore 相册读取、图片导入校验、Canvas 自定义裁剪框，以及基于原图像素的编辑结果导出。

## 功能概览

- 自定义相册页面
  - 请求并处理 Android 不同版本的图片访问权限
  - 从 MediaStore 读取本地图片
  - 按最近项目和相册分组展示图片
  - 支持图片网格、相册列表和全屏预览
  - 支持选择图片后进入裁剪流程
- 图片导入链路
  - 支持 JPG、PNG、WebP、HEIC、HEIF
  - 限制最大分辨率与最长边，避免导入超大图片导致内存风险
  - 将选中的相册图片复制到应用缓存目录
  - 对 HEIC/HEIF 图片执行兼容性转换
- 图片编辑页面
  - 使用 Compose `Image` 展示待裁剪图片
  - 使用自定义 `Canvas` 绘制半透明遮罩、九宫格辅助线、裁剪框和四角控制柄
  - 支持拖动裁剪框和四角缩放
  - 支持自由、原始、`1:1`、`16:9`、`9:16`、`4:3` 等比例，并可切换横向/纵向约束
  - 支持旋转刻度盘、镜像、90 度旋转、滤镜和基础调节
  - 基于原图重新解码并导出编辑结果，避免预览图质量影响输出
- 编辑结果页
  - 读取已保存到相册的编辑结果
  - 展示导出成功状态
  - 支持返回首页或继续选择图片编辑

## 技术栈

- Kotlin 2.0.21
- Android Gradle Plugin 8.13.0
- JDK 17
- Jetpack Compose
- Material 3
- AndroidX Activity / Lifecycle
- MediaStore
- Coil
- Media3 依赖已接入，视频裁剪 Activity 当前仍是预留壳

## 环境要求

- Android Studio 建议使用较新的稳定版本
- JDK 17
- Android SDK
- `minSdk = 23`
- `targetSdk = 36`
- `compileSdk = 36`

## 项目结构

```text
QuickCrop/
├── README.md
├── settings.gradle.kts
├── build.gradle.kts
├── gradle/
├── base/
│   └── src/main/java/com/ethan/base/
└── app/
    ├── build.gradle.kts
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/com/ethan/quickcrop/
        │   ├── MainActivity.kt
        │   ├── core/
        │   ├── extension/
        │   ├── ui/media/
        │   └── ui/edit/image/
        └── res/
```

仓库根目录就是 Android 工程根目录，可以直接用 Android Studio 打开 `QuickCrop`。

## 核心实现说明

### 自定义相册

- `MainActivity` 是应用入口，当前直接进入自定义相册选择流程
- `MediaPickActivity` 承载相册选择页面，并在导入成功后跳转到图片裁剪页
- `MediaPickPage` 负责权限请求、相册数据加载、图片预览、导入校验和缓存准备
- `MediaPickModels` 定义相册和图片列表使用的数据模型
- `PhotoGrid`、`AlbumList`、`MediaPreviewPage` 等组件拆分相册列表、图片网格和预览层

### 图片导入校验

- `requiredMediaPhotoPermissions()` 根据系统版本返回需要申请的图片读取权限
- `hasMediaPhotoPermission()` 兼容 Android 13 的图片权限和 Android 14 的有限照片访问权限
- `validateAndPrepareImport()` 统一处理格式识别、尺寸校验和缓存文件准备
- `resolveImportImageFormat()` 判断图片格式，并决定是否需要转码
- `copyToImportCache()` 将无需转码的图片复制到应用缓存
- `convertToJpeg()` 将 HEIC/HEIF 图片解码并压缩为 JPG

### 图片编辑

- `EditImageActivity` 负责接收图片 Uri 并加载编辑页面
- `EditImagePage` 负责图片预览、裁剪比例选择、旋转/镜像/滤镜/调节和结果页跳转
- `ResizableCropBox` 负责绘制裁剪框，并处理移动、四角缩放和固定比例约束
- `ImageEditSaveProcessor` 负责重新读取原图，根据裁剪框和编辑参数输出最终图片并保存到相册
- `EditImageResultActivity` 与 `EditImageResultPage` 负责展示编辑后的最终图片

### Compose 自定义绘制速查

图片裁剪框这类 UI 不一定适合完全用现成组件拼出来。半透明遮罩、挖空区域、九宫格辅助线、手柄、选区边框等效果，通常会使用 Compose 的绘制 API 自己完成。

#### Canvas

`Canvas` 是 Compose 提供的画布组件。它会创建一块可以绘制图形的区域，绘制代码写在 `Canvas { ... }` 代码块里。

作用：

- 绘制自定义图形，比如矩形、线条、圆形、路径、图片等
- 适合实现裁剪框、时间轴、波形图、进度条、标尺等自定义 UI
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
- 可以通过 `translate()` 平移矩形，适合拖动裁剪框这类场景

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
- 绘制刻度线、分割线、参考线等

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
- `drawWithContent { ... }`：可以控制先画内容还是先画自定义图形，适合遮罩、前景覆盖等效果
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

可以这样理解：

- Compose 自定义绘制：常用 `Canvas`、`drawRect`、`drawLine` 等 Compose 绘制 API
- 传统 Android 自定义 View：常用 `android.graphics.Canvas`、`Paint`、`onDraw()`
- 普通界面开发：优先使用现成 Compose 组件，不需要手动画图

## 运行方式

1. 使用 Android Studio 打开仓库根目录
2. 等待 Gradle 同步完成
3. 连接 Android 设备或启动模拟器
4. 直接运行 `app` 模块

也可以在命令行编译：

```bash
./gradlew :app:compileDebugKotlin
```

Windows PowerShell 下可以使用：

```powershell
.\gradlew.bat :app:compileDebugKotlin
```

## 使用说明

### 图片选择与裁剪

1. 启动应用，点击入口按钮打开自定义相册
2. 首次进入时授予图片访问权限
3. 在最近项目或相册列表中选择图片
4. 可点击图片右下角入口进入预览，也可直接点击图片进入裁剪流程
5. 在裁剪页拖动裁剪框或四角控制柄调整裁剪区域
6. 选择需要的裁剪比例
7. 点击“裁剪”
8. 裁剪成功后进入结果预览页，可缩放和平移查看结果

## 输出文件位置

- 导入缓存：应用外部缓存目录下的 `export` 目录
- 裁剪结果缓存：应用缓存目录下的 `crop_result` 目录

文件名会自动带上时间戳，避免同名文件互相覆盖。当前裁剪结果先保存为应用缓存文件，并用于结果预览；如需保存到系统相册，可以在后续版本接入 MediaStore 写入流程。

## 当前代码状态

- `:app:compileDebugKotlin` 已通过
- 图片选择、导入校验、裁剪导出和结果预览是当前主链路
- `CropVideoActivity` 当前为空实现，视频裁剪功能仍处于预留状态
- `Media3` 相关依赖已经接入，但暂未形成可运行的视频裁剪流程

## 注意事项

- 当前项目以示例演示为主，适合学习和二次开发
- 图片裁剪导出会重新解码原图，超大图片可能带来内存压力，当前已在导入阶段做基础尺寸限制
- 裁剪结果保存在缓存目录，应用缓存被清理后结果文件也会被删除
- 读取本地图片时，系统会根据 Android 版本请求对应的媒体访问权限
- Android 14 的有限照片访问场景已做基础兼容，但具体系统 ROM 行为可能存在差异

## 后续可以继续扩展的方向

- 将裁剪结果保存到系统相册
- 增加图片旋转、翻转、滤镜、贴纸、撤销重做等编辑能力
- 为裁剪导出增加进度提示和失败重试
- 完善视频裁剪页面、时间轴预览和 Media3 Transformer 导出流程
- 补充单元测试和 UI 测试
