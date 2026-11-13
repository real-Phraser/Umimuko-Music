pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        maven("https://maven.aliyun.com/repository/google") {
            content { includeGroupByRegex("androidx\\..*") }
        }
        google()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        maven("https://maven.aliyun.com/repository/google") {
            content { includeGroupByRegex("androidx\\..*") }
        }
        google()
    }
}

rootProject.name = "UmimukoMusic"
