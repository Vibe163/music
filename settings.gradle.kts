pluginManagement {
    repositories {
        // 国内阿里云镜像（放最前，优先命中，绕过网络代理直连不稳定问题）
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // 国内阿里云镜像（放最前，优先命中，加快依赖下载）
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://maven.aliyun.com/repository/central") }
        google()
        mavenCentral()
        // JAudioTagger Android 兼容版（AdrienPoupa fork）发布在 jitpack
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "LocalMusic"
include(":app")
