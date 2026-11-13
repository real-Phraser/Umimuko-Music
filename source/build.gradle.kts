import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.compose.desktop.application.tasks.AbstractJPackageTask
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest

plugins {
    kotlin("jvm") version "2.2.20"
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.20"
    id("org.jetbrains.compose") version "1.9.1"
}

group = "com.lostf1sh.pixelplayeross"
version = "0.22.2"

val offlineVlcDir = layout.projectDirectory.dir("vlc")
val vlcVersion = "3.0.21"
val vlcSha256 = "a0b7ec02b50adf6417eed014fb8df50af39690505a4225b85b3dc2ed17d14843"
val standaloneVlcDir = layout.projectDirectory.dir(".gradle/vlc/vlc-$vlcVersion")
val vlcZipFile = layout.projectDirectory.file(".gradle/vlc/vlc-$vlcVersion-win64.zip")

val downloadBundledVlc by tasks.registering {
    group = "distribution"
    description = "Ensure standalone 64-bit VLC 3 is cached and ready, independent of any device-installed VLC."
    onlyIf {
        System.getProperty("os.name").startsWith("Windows", ignoreCase = true) &&
            !providers.gradleProperty("vlcDir").isPresent &&
            !providers.environmentVariable("UMIMUKO_VLC_DIR").isPresent &&
            !offlineVlcDir.file("libvlc.dll").asFile.isFile
    }
    val outputDir = standaloneVlcDir.asFile
    outputs.dir(outputDir)
    outputs.upToDateWhen {
        outputDir.resolve("libvlc.dll").isFile &&
            outputDir.resolve("libvlccore.dll").isFile &&
            outputDir.resolve("plugins").isDirectory
    }
    doLast {
        if (outputDir.resolve("libvlc.dll").isFile &&
            outputDir.resolve("libvlccore.dll").isFile &&
            outputDir.resolve("plugins").isDirectory) {
            return@doLast
        }
        val zipFile = vlcZipFile.asFile
        zipFile.parentFile.mkdirs()

        fun computeHash(file: File): String {
            val md = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { stream ->
                val buf = ByteArray(65536)
                var n: Int
                while (stream.read(buf).also { n = it } != -1) {
                    md.update(buf, 0, n)
                }
            }
            return md.digest().joinToString("") { "%02x".format(it) }
        }

        fun downloadUrl(targetUrl: String, dest: File) {
            var current = targetUrl
            for (redirect in 0..5) {
                val conn = URI.create(current).toURL().openConnection() as HttpURLConnection
                conn.instanceFollowRedirects = true
                conn.connectTimeout = 15000
                conn.readTimeout = 300000
                conn.connect()
                val code = conn.responseCode
                if (code in 300..399) {
                    val location = conn.getHeaderField("Location") ?: error("Redirect with no Location header")
                    current = if (location.startsWith("http://") || location.startsWith("https://")) location
                        else URI.create(current).resolve(location).toString()
                    conn.disconnect()
                    continue
                }
                if (code !in 200..299) {
                    conn.disconnect()
                    error("HTTP $code from $current")
                }
                conn.inputStream.use { input ->
                    dest.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                conn.disconnect()
                return
            }
            error("Too many redirects downloading $targetUrl")
        }

        val validZip = zipFile.isFile && computeHash(zipFile).equals(vlcSha256, ignoreCase = true)
        if (!validZip) {
            val urls = listOf(
                "https://get.videolan.org/vlc/$vlcVersion/win64/vlc-$vlcVersion-win64.zip",
                "https://download.videolan.org/pub/videolan/vlc/$vlcVersion/win64/vlc-$vlcVersion-win64.zip"
            )
            var downloaded = false
            var lastError: Exception? = null

            for (url in urls) {
                logger.lifecycle("Downloading standalone VLC $vlcVersion from $url...")
                try {
                    downloadUrl(url, zipFile)
                    if (zipFile.isFile) {
                        val hash = computeHash(zipFile)
                        if (hash.equals(vlcSha256, ignoreCase = true)) {
                            logger.lifecycle("Verified standalone VLC $vlcVersion archive (SHA-256: $hash).")
                            downloaded = true
                            break
                        } else {
                            logger.warn("Checksum mismatch for downloaded VLC: got $hash, expected $vlcSha256")
                            zipFile.delete()
                        }
                    }
                } catch (e: Exception) {
                    logger.warn("Failed downloading from $url: ${e.message}")
                    lastError = e
                    zipFile.delete()
                }
            }
            check(downloaded) {
                "Failed to download standalone VLC $vlcVersion. Check network or pass -PvlcDir=...: ${lastError?.message}"
            }
        }

        logger.lifecycle("Extracting standalone VLC to ${zipFile.parentFile}...")
        project.copy {
            from(zipTree(zipFile))
            into(zipFile.parentFile)
        }
        check(outputDir.resolve("libvlc.dll").isFile) {
            "Standalone VLC extraction did not produce libvlc.dll in $outputDir"
        }
        logger.lifecycle("Standalone VLC ready at $outputDir.")
    }
}

val vlcSource = providers.gradleProperty("vlcDir")
    .orElse(providers.environmentVariable("UMIMUKO_VLC_DIR"))
    .orElse(providers.provider {
        val offline = offlineVlcDir.asFile
        if (offline.resolve("libvlc.dll").isFile) {
            return@provider offline.absolutePath
        }
        val standalone = standaloneVlcDir.asFile
        if (standalone.resolve("libvlc.dll").isFile) {
            return@provider standalone.absolutePath
        }
        val defaultDir = file("C:/Program Files/VideoLAN/VLC")
        if (defaultDir.resolve("libvlc.dll").exists()) {
            return@provider defaultDir.absolutePath
        }
        offline.absolutePath
    })
val bundledResources = layout.buildDirectory.dir("generated/bundled-resources")
val windowsAppImage = layout.buildDirectory.dir("compose/binaries/main/app/Umimuko Music")
val validateBundledVlc by tasks.registering {
    group = "distribution"
    description = "Bundle a complete 64-bit VLC 3 installation with the Windows app."
    dependsOn(downloadBundledVlc)
    onlyIf { System.getProperty("os.name").startsWith("Windows", ignoreCase = true) }
    doLast {
        check(System.getProperty("os.arch") in listOf("amd64", "x86_64")) {
            "Build Umimuko Music using a 64-bit Windows JDK 17."
        }
        val source = file(vlcSource.get())
        listOf("libvlc.dll", "libvlccore.dll", "plugins").forEach {
            check(source.resolve(it).exists()) {
                "Missing VLC file: $it in $source"
            }
        }
        RandomAccessFile(source.resolve("libvlc.dll"), "r").use {
            it.seek(0x3c)
            val pe = Integer.reverseBytes(it.readInt()).toLong()
            it.seek(pe)
            val signature = Integer.reverseBytes(it.readInt())
            val machine = it.readUnsignedByte() or (it.readUnsignedByte() shl 8)
            check(signature == 0x4550 && machine == 0x8664) {
                "VLC must be the 64-bit Windows version."
            }
        }
    }
}
val prepareBundledVlc by tasks.registering(Sync::class) {
    dependsOn(validateBundledVlc)
    onlyIf { System.getProperty("os.name").startsWith("Windows", ignoreCase = true) }
    from(vlcSource) { into("vlc") }
    into(bundledResources)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation("uk.co.caprica:vlcj:4.8.3")
    implementation(files("libs/jaudiotagger-3.0.1.jar"))
    implementation(files("libs/jna-5.14.0.jar"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
    testImplementation(kotlin("test"))
}

compose.desktop {
    application {
        dependsOn("prepareBundledVlc")
        mainClass = "com.lostf1sh.pixelplayeross.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Exe, TargetFormat.Msi)
            packageName = "Umimuko Music"
            packageVersion = "0.22.2"
            description = "Umimuko Music desktop music player"
            vendor = "Umimuko Music contributors"
            licenseFile.set(project.file("../LICENSE"))
            includeAllModules = true
            windows {
                iconFile.set(project.file("packaging/umimuko.ico"))
                menuGroup = "Umimuko Music"
                shortcut = true
                upgradeUuid = "48f05696-9b27-4bdf-905b-5cb0a842d923"
            }
        }
    }
}

// Put the native engine into the actual app image before an installer consumes
// it. This also handles the first build, when generated resource directories
// do not exist yet during Compose configuration.
tasks.matching { it.name == "createDistributable" }.configureEach {
    dependsOn(prepareBundledVlc)
    inputs.dir(bundledResources)
    val image = windowsAppImage
    outputs.upToDateWhen {
        !System.getProperty("os.name").startsWith("Windows", ignoreCase = true) ||
            image.get().file("vlc/libvlc.dll").asFile.isFile
    }
    doLast {
        if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
            val app = image.get().asFile
            check(app.resolve("Umimuko Music.exe").isFile) { "App image not found: $app" }
            project.copy {
                from(bundledResources.get().dir("vlc"))
                into(app.resolve("vlc"))
            }
            check(app.resolve("vlc/libvlc.dll").isFile &&
                app.resolve("vlc/libvlccore.dll").isFile &&
                app.resolve("vlc/plugins").walkTopDown().any { it.isFile && it.extension.equals("dll", ignoreCase = true) }) {
                "App image is missing bundled VLC: $app"
            }
        }
    }
}

val verifyWindowsAppImage by tasks.registering {
    group = "distribution"
    description = "Verify the complete portable image before making Windows installers."
    dependsOn("createDistributable")
    onlyIf { System.getProperty("os.name").startsWith("Windows", ignoreCase = true) }
    doLast {
        val image = windowsAppImage.get().asFile
        listOf("Umimuko Music.exe", "app/Umimuko Music.cfg", "runtime/bin/java.dll",
            "vlc/libvlc.dll", "vlc/libvlccore.dll").forEach { relative ->
            check(image.resolve(relative).isFile) { "Installer input is missing $relative in $image" }
        }
        check(image.resolve("vlc/plugins").walkTopDown().any {
            it.isFile && it.extension.equals("dll", ignoreCase = true)
        }) { "Installer input has no VLC plugins: $image" }
        logger.lifecycle("Installer source verified: $image (embedded Java and VLC)")
    }
}

// Compose 1.9.1 builds Windows installers independently by default. Explicitly
// use the already assembled image, including the VLC folder added above.
tasks.withType<AbstractJPackageTask>().configureEach {
    if (name in listOf("packageExe", "packageMsi") &&
        System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
        dependsOn(verifyWindowsAppImage)
        appImage.set(windowsAppImage)
    }
}

tasks.register<JavaExec>("checkDataModels") {
    group = "verification"
    description = "Check editable queue, Home persistence and existing library compatibility."
    dependsOn("testClasses")
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.lostf1sh.pixelplayeross.desktop.ModelChecksKt")
}

tasks.withType<JavaExec>().configureEach {
    if (name == "run") {
        dependsOn(prepareBundledVlc)
        systemProperty("compose.application.resources.dir", bundledResources.get().asFile.absolutePath)
    }
}
