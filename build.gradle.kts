plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21" apply false
    id("com.google.devtools.ksp") version "2.0.21-1.0.28" apply false
}

// 项目约定：日常构建/安装必须用 release 版（debug 构建的 Compose 有 2-5 倍性能损耗，
// 会导致滑动列表卡顿掉帧，详见 app/build.gradle.kts 中 buildTypes.release 的注释）。
// 无任务参数运行 gradlew 时默认执行 assembleRelease，避免误装 debug APK。
defaultTasks("assembleRelease")
