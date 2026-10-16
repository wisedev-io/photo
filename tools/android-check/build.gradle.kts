// Compile check for the Android-specific code (host/, platform/, AppViewModel, Room, MainActivity).
//
// The real Android build needs Google Maven, which not every environment can reach. This project compiles the
// same sources on the JVM against: the real android.jar (framework APIs), the real Compose runtime/ui jars from
// Maven Central, the real :core module, and small hand-written stubs of CameraX / ML Kit / Room / lifecycle /
// activity-compose (stubs/). It catches syntax errors, wrong names, null-safety and type mistakes across files.
// It does NOT run anything, and a stub is only as accurate as its author - the Android build stays the judge.
//
//   tools/check-android.sh            # downloads android.jar once, then runs this project
plugins {
    kotlin("multiplatform") version "1.9.22"
    id("org.jetbrains.compose") version "1.5.12"
}

val androidJar = providers.gradleProperty("androidJar").orElse(providers.environmentVariable("ANDROID_JAR"))
val resDir = layout.projectDirectory.dir("../../app/src/main/res")
val generatedR = layout.buildDirectory.dir("generated/r")

val generateR = tasks.register("generateR") {
    val stringsXml = resDir.file("values/strings.xml").asFile
    val outDir = generatedR.get().asFile
    inputs.file(stringsXml)
    outputs.dir(outDir)
    doLast {
        val doc = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(stringsXml)
        val nodes = doc.getElementsByTagName("string")
        val names = (0 until nodes.length).map { nodes.item(it).attributes.getNamedItem("name").nodeValue }.sorted()
        val sb = StringBuilder("package app.dvphoto\n\nobject R {\n    object string {\n")
        names.forEachIndexed { i, n -> sb.append("        const val $n = ${i + 1}\n") }
        sb.append("    }\n}\n")
        outDir.mkdirs()
        File(outDir, "R.kt").writeText(sb.toString())
    }
}

kotlin {
    jvm("desktop")
    sourceSets {
        val desktopMain by getting {
            kotlin.srcDir("../../core/src/main/kotlin")
            kotlin.srcDir("../../app/src/main/kotlin")
            kotlin.srcDir("stubs")
            kotlin.srcDir(generatedR)
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(compose.material3)
                compileOnly(files(androidJar.get()))
            }
        }
    }
}

tasks.matching { it.name.startsWith("compileKotlinDesktop") }.configureEach { dependsOn(generateR) }
