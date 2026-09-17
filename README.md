<p align="center">
  <img src="artwork/yingyou-icon-1024.png" width="120" alt="映游图标" />
</p>

<h1 align="center">映游 · YingYou</h1>

<p align="center">使用 Kotlin 与 Jetpack Compose 构建的 Android 视频客户端</p>

<p align="center">视频浏览 · 弹幕播放 · 后台听 · 离线缓存</p>

## 项目简介

映游是一款接入哔哩哔哩服务的非官方 Android 客户端，使用 Material 3 构建界面，围绕视频发现、搜索、播放和个人资料库提供完整的数据与交互流程。

项目采用单 Activity、单 `app` 模块，以功能组织页面，通过 ViewModel、StateFlow、Repository 以及本地与远程数据源连接界面与数据。播放器由 MediaSessionService 管理，支持页面播放与后台媒体会话。

## 功能

| 模块 | 已实现能力 |
| --- | --- |
| 首页 | 推荐视频分页、下拉刷新、加载状态与失败重试 |
| 搜索 | 视频搜索；最近 20 条搜索历史、去重置顶、单条删除与清空确认 |
| 账号 | 二维码登录、会话恢复、退出登录与个人资料展示 |
| 动态 | 全部 / 视频分区、游标分页、视频 / 图文 / 转发内容展示 |
| 详情与互动 | 视频详情、UP 主信息、评论分页、发送评论、点赞与收藏 |
| 播放器 | Media3 DASH 音视频播放、清晰度切换、自定义控制栏、沉浸式全屏、后台听与媒体通知 |
| 弹幕 | 分段 Protobuf 弹幕、滚动 / 固定 / 反向绘制、暂停和跳转同步、开关持久化 |
| 观看记录 | 本地播放进度、按分 P 继续观看、网络历史分页与同步、记录删除 |
| 个人资料库 | 收藏夹、收藏与点赞列表、本地副本、账号隔离、持久同步队列 |
| 离线缓存 | 音视频双轨下载、进度展示、暂停 / 恢复 / 重试 / 删除、完整缓存的离线播放 |
| 外观 | 浅色 / 深色 / 跟随系统，多种主题色及动态取色，偏好持久化 |
| 分享 | 系统分享面板 |

### 当前边界

- 投币和关注操作尚未实现。
- 点赞列表合并服务端最近点赞与本机记录，不能枚举全部历史点赞。
- 网络内容与账号操作依赖服务端接口、登录状态和账号权限；游客可保留本地观看记录。
- 播放通知的点击续播、上下集操作及退出页面后的播放策略仍有待完善。

## 技术栈

| 方向 | 技术 |
| --- | --- |
| 语言与界面 | Kotlin、Jetpack Compose、Material 3 |
| 状态与导航 | ViewModel、StateFlow、Coroutines、Navigation Compose |
| 依赖注入 | Hilt、KSP |
| 网络 | Retrofit、OkHttp、Gson |
| 本地存储 | Room、DataStore |
| 分页与同步 | Paging 3、WorkManager |
| 播放与下载 | Media3 ExoPlayer、MediaSessionService、DownloadService |
| 弹幕协议 | Protobuf Lite |
| 图片与二维码 | Coil、ZXing |
| 测试 | JUnit、Coroutines Test、MockWebServer、Compose UI Test、Espresso |

## 项目结构

```text
yingyou-android/
├── app/
│   └── src/
│       ├── main/
│       │   ├── java/xyz/qiaosheng/bilibili/
│       │   │   ├── ui/              页面、ViewModel、状态与 UI 组件
│       │   │   ├── model/           业务模型
│       │   │   ├── data/            网络、本地存储、仓库、分页与同步
│       │   │   ├── core/            异常处理、扩展与共享组件
│       │   │   ├── di/              Hilt 依赖配置
│       │   │   ├── playback/        播放服务与媒体会话
│       │   │   ├── offline/         下载、缓存及离线媒体源
│       │   │   ├── provider/        搜索历史 ContentProvider
│       │   │   └── service/         Service 示例
│       │   ├── proto/              弹幕协议定义
│       │   └── res/                图片、主题和其他 Android 资源
│       ├── test/                   JVM 单元测试
│       └── androidTest/            设备与 UI 测试
├── artwork/                        应用图标原稿与导出图
├── gradle/                         Wrapper 与依赖版本目录
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

源码阅读入口：[MainActivity](app/src/main/java/xyz/qiaosheng/bilibili/MainActivity.kt) → [AppNavHost](app/src/main/java/xyz/qiaosheng/bilibili/ui/navigation/AppNavHost.kt)。

Gradle 工程名为 `YingYou`，应用显示名称为「映游」，应用 ID 和源码包名为 `xyz.qiaosheng.bilibili`。

## 开始使用

### 环境要求

以下版本来自仓库中的构建配置：

| 配置 | 版本 |
| --- | --- |
| 最低运行系统 | Android 12（API 31） |
| compileSdk / targetSdk | 37 / 37 |
| Java 编译目标 | 17 |
| Gradle Daemon JDK | 25（由 `gradle/gradle-daemon-jvm.properties` 指定） |
| Gradle / Android Gradle Plugin | 9.7.1 / 9.4.0 |
| Kotlin / Compose BOM | 2.4.10 / 2026.08.00 |
| Media3 | 1.11.0 |

1. 克隆仓库，并使用支持上述 AGP 版本的 Android Studio 打开项目根目录。
2. 安装 Android SDK 37，并选择与 Gradle / AGP 兼容的 Gradle JDK。
3. 在 Android Studio 中配置 SDK 路径，生成本机 `local.properties`。
4. 完成 Gradle Sync，在 API 31 或更高版本的设备或模拟器上运行 `app`。

首次同步需要联网下载 Gradle、插件与依赖。Java 编译目标不等同于运行 Gradle 所需的 JDK 版本。

### 命令行构建

Windows PowerShell：

```powershell
.\gradlew.bat :app:assembleDebug
```

macOS / Linux：

```bash
chmod +x gradlew
./gradlew :app:assembleDebug
```

Debug APK 输出路径：`app/build/outputs/apk/debug/app-debug.apk`。

### 测试与静态检查

```powershell
# JVM 单元测试
.\gradlew.bat :app:testDebugUnitTest

# Android Lint
.\gradlew.bat :app:lintDebug

# 需要连接设备或启动模拟器
.\gradlew.bat :app:connectedDebugAndroidTest
```

macOS / Linux 将 `.\gradlew.bat` 替换为 `./gradlew`。

测试报告位于 `app/build/reports/tests/` 和 `app/build/reports/androidTests/`。
