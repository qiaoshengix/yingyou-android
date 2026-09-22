plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.protobuf)
}

android {
    namespace = "xyz.qiaosheng.bilibili"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "xyz.qiaosheng.bilibili"
        minSdk = 31
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}

// 编译器与移动端 Lite 运行库保持同版本；生成代码属于 build/，只维护 src/main/proto。
protobuf {
    protoc { artifact = "com.google.protobuf:protoc:${libs.versions.protobuf.get()}" }
    generateProtoTasks {
        all().configureEach {
            builtins {
                maybeCreate("java").option("lite")
            }
        }
    }
}

dependencies {
    // Compose 界面、图标与 Activity 宿主。
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.text)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.core.ktx)

    // 生命周期、ViewModel 与页面导航。
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.kotlinx.serialization.core)

    // 远程请求与 JSON 解析。
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.protobuf.javalite)
    implementation(libs.okhttp.logging.interceptor)

    // 依赖注入及其编译期代码生成。
    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    ksp(libs.hilt.compiler)

    // 本地数据库、偏好存储与分页。
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.paging.compose)

    // 本地写入队列在联网后继续提交，进程退出后仍由系统调度。
    implementation(libs.androidx.work.runtime.ktx)

    // 图片加载与登录二维码。
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    implementation(libs.zxing.core)

    // 播放器、DASH 数据源及后台媒体会话。
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.exoplayer.dash)

    implementation(libs.androidx.media3.datasource.okhttp)

    implementation(libs.androidx.media3.session)

    // JVM / 设备测试与仅 Debug 使用的 Compose 调试工具。
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.paging)

}
