plugins {
    kotlin("jvm") version "1.9.24"
    id("org.jetbrains.compose") version "1.6.11"
}

group = "com.agnes.studio"
version = "1.0.0"

repositories {
    mavenCentral()
    google()
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.materialIconsExtended)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.json:json:20240303")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.1")
}

kotlin {
    jvmToolchain(17)
}

compose.desktop {
    application {
        mainClass = "com.agnes.studio.MainKt"

        nativeDistributions {
            packageName = "AgnesAI-Image-Studio"
            packageVersion = "1.0.0"
            description = "Agnes AI Image Studio"
            vendor = "Agnes AI"

            windows {
                iconFile.set(project.file("src/main/resources/app-icon.ico"))
            }
        }
    }
}
