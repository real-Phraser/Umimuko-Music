pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://maven.aliyun.com/repository/google") {
            content { includeGroupByRegex("androidx\\..*") }
        }
        google()
    }
}

dependencyResolutionManagement {
    repositories {
        // The JDK probe is needed by checkRuntime even when the app already builds.
        // Resolve this exact tool locally so packaging works without Maven DNS.
        exclusiveContent {
            forRepository {
                maven {
                    name = "bundledComposeJdkProbe"
                    url = uri("local-maven")
                    metadataSources {
                        mavenPom()
                        ignoreGradleMetadataRedirection()
                    }
                }
            }
            filter {
                includeModule("org.jetbrains.compose", "gradle-plugin-internal-jdk-version-probe")
            }
        }
        mavenCentral()
        maven("https://maven.aliyun.com/repository/google") {
            content { includeGroupByRegex("androidx\\..*") }
        }
        google()
    }
}

rootProject.name = "UmimukoMusic"
