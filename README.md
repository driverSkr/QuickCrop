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
