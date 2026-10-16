// Headless Compose Desktop harness: compiles the app's pure-Compose screens (app/src/main/kotlin/app/dvphoto/ui)
// together with the rules engine, renders them off-screen to PNG in several languages and checks them.
// It exists because the Android build needs Google Maven; this one only needs Maven Central.
plugins {
    kotlin("multiplatform") version "1.9.22"
    id("org.jetbrains.compose") version "1.5.12"
}

val coreSrc = layout.projectDirectory.dir("../core/src/main/kotlin")
val uiSrc = layout.projectDirectory.dir("../app/src/main/kotlin/app/dvphoto/ui")
val resDir = layout.projectDirectory.dir("../app/src/main/res")
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
        val sb = StringBuilder("package app.dvphoto\n\n// Generated from app/src/main/res/values/strings.xml - mirrors the Android R class for the harness.\nobject R {\n    object string {\n")
        names.forEachIndexed { i, n -> sb.append("        const val $n = ${i + 1}\n") }
        sb.append("    }\n\n    val names: List<String> = listOf(\n")
        names.forEach { sb.append("        \"$it\",\n") }
        sb.append("    )\n}\n")
        outDir.mkdirs()
        File(outDir, "R.kt").writeText(sb.toString())
    }
}

kotlin {
    jvm("desktop")
    sourceSets {
        val desktopMain by getting {
            kotlin.srcDir(coreSrc)
            kotlin.srcDir(uiSrc)
            kotlin.srcDir(generatedR)
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(compose.material3)
            }
        }
        val desktopTest by getting {
            dependencies { implementation("junit:junit:4.13.2") }
        }
    }
}

tasks.matching { it.name.startsWith("compileKotlinDesktop") }.configureEach { dependsOn(generateR) }
tasks.withType<Test> {
    jvmArgs("-Djava.awt.headless=true")
    systemProperty("res.dir", resDir.asFile.absolutePath)
    systemProperty("shots.dir", layout.buildDirectory.dir("screenshots").get().asFile.absolutePath)
    testLogging { showStandardStreams = true }
}
