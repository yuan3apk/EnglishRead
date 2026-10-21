// 依赖与插件统一走阿里云镜像（国内直连 google/mavenCentral 常常卡住）。
// 镜像按「先命中先返回」工作，官方源留作兜底，镜像缺构件时不至于整个构建失败。
pluginManagement {
    repositories {
        // AGP 与 androidx 都发布在 Google 仓库，阿里云的这个镜像同时覆盖两者
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/central")
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/central")
        google()
        mavenCentral()
    }
}

rootProject.name = "EnglishRead"
include(":app")
