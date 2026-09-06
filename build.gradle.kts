import java.net.URI
import java.security.MessageDigest

plugins { java }

group = "com.mira"
version = "0.2.15"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
}


val miraShopVersion = "0.1.12"
val miraShopSha256 = "a2b2299c5282b64f32c72df1f721cd90234b0171650c9ea475f242f3046453a3"
val miraShopJar = layout.projectDirectory.file("libs/MiraShop-$miraShopVersion.jar").asFile

fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    return digest.digest(file.readBytes()).joinToString("") { "%02x".format(it) }
}

fun downloadVerified(url: String, target: File, expectedSha256: String) {
    if (target.exists() && sha256(target) == expectedSha256) return
    target.parentFile.mkdirs()
    URI(url).toURL().openStream().use { input ->
        target.outputStream().use { output -> input.copyTo(output) }
    }
    check(sha256(target) == expectedSha256) {
        "Downloaded dependency failed SHA-256 verification: ${target.name}"
    }
}

val downloadMiraShopApi by tasks.registering {
    doLast {
        downloadVerified(
            "https://github.com/FiveSOCE/Mira-shop/releases/download/v$miraShopVersion/MiraShop-$miraShopVersion.jar",
            miraShopJar,
            miraShopSha256
        )
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1")
    compileOnly("me.clip:placeholderapi:2.11.6")
    compileOnly(files(miraShopJar))
}

java { toolchain.languageVersion.set(JavaLanguageVersion.of(21)) }

tasks.withType<JavaCompile>().configureEach {
    dependsOn(downloadMiraShopApi)
    options.encoding = "UTF-8"
    options.release.set(21)
}

tasks.jar { archiveFileName.set("MiraFactions-${project.version}.jar") }

tasks.processResources {
    filesMatching("plugin.yml") { expand("version" to project.version) }
}
