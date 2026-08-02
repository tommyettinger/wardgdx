import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.zip.ZipFile
import org.gradle.api.publish.maven.MavenPublication

plugins {
    `java-library`
    `maven-publish`
}

group = project.property("group")!!
version = project.property("version")!!

val gdxVersion = project.property("gdxVersion") as String
val forkName = project.property("forkName") as String

val glueSrcDir = file("src/glue/java")
val mainSrcDir = file("src/main/java")
val mainResDir = file("src/main/resources")

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
    withSourcesJar()
}

base {
    archivesName.set(forkName)
}

sourceSets {
    main {
        java {
            srcDir(glueSrcDir)
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
        }
    }
}

repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    api("com.badlogicgames.gdx:gdx-jnigen-loader:2.5.2")
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

fun getUpstreamVersion(): String {
    return project.findProperty("gdxVersion") as? String
        ?: throw GradleException("gdxVersion is missing in gradle.properties.")
}

fun getGlueClasses(): Set<String> {
    val manifest = project.file("config/glue-classes.md")
    val classes = mutableSetOf<String>()
    if (manifest.exists()) {
        manifest.forEachLine { line ->
            val parts = line.split("|").map { it.trim() }
            if (parts.size > 1 && parts[1].startsWith("com.badlogic.gdx")) {
                val pkg = parts[1]
                val cls = parts[2]
                classes.add("$pkg.$cls")
            }
        }
    }
    return classes
}

fun calculateGlueChecksum(): String {
    val digest = MessageDigest.getInstance("SHA-256")

    digest.update(gdxVersion.toByteArray())
    glueSrcDir.walkTopDown()
        .filter { it.isFile && it.extension == "java" }
        .sortedBy { it.absolutePath }
        .forEach { file ->
            // step around the CRLF issues on Windows git
            val content = file.readText()
                .replace("\r", "")
                .replace("\n", "")
            digest.update(content.toByteArray())
        }

    return digest.digest().joinToString("") { "%02x".format(it) }
}

tasks.register("fetchUpstream") {
    group = forkName
    description =
        "Downloads upstream LibGDX sources and binaries, and sorts them between glue and main sourcesets."

    doLast {
        val version = getUpstreamVersion()
        val glueClasses = getGlueClasses()

        val gdxDependency = "com.badlogicgames.gdx:gdx:$version"
        val jarFile = configurations.detachedConfiguration(dependencies.create(gdxDependency)).files.find {
            it.name.endsWith(".jar") && !it.name.contains("sources")
        }!!
        val sourcesFile =
            configurations.detachedConfiguration(dependencies.create("$gdxDependency:sources")).files.find {
                it.name.contains("sources")
            }!!

        println("Using sources from: ${sourcesFile.absolutePath}")



        println("Purging existing sources and resources...")
        glueSrcDir.deleteRecursively()

        val mainFilesBefore = if (mainSrcDir.exists()) {
            mainSrcDir.walkTopDown().filter { it.isFile }.map { it.relativeTo(mainSrcDir).path }.toSet()
        } else emptySet()

        val mainResBefore = if (mainResDir.exists()) {
            mainResDir.walkTopDown().filter { it.isFile }.map { it.relativeTo(mainResDir).path }.toSet()
        } else emptySet()

        glueSrcDir.mkdirs()
        mainSrcDir.mkdirs()
        mainResDir.mkdirs()

        println("Distributing sources from LibGDX $version...")
        val filesDistributedToMain = mutableSetOf<String>()
        val resourcesDistributed = mutableSetOf<String>()
        ZipFile(sourcesFile).use { zip ->
            zip.entries().asSequence().forEach { entry ->
                if (!entry.isDirectory && entry.name.endsWith(".java")) {
                    val className = entry.name.removeSuffix(".java").replace("/", ".")
                    val targetDir = if (glueClasses.contains(className)) glueSrcDir else mainSrcDir

                    if (targetDir == mainSrcDir) {
                        filesDistributedToMain.add(entry.name)
                    }

                    val targetFile = targetDir.resolve(entry.name)
                    targetFile.parentFile.mkdirs()
                    zip.getInputStream(entry).use { input ->
                        Files.copy(input, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    }
                }
            }
        }

        ZipFile(jarFile).use { zip ->
            zip.entries().asSequence().forEach { entry ->
                if (!entry.isDirectory && !entry.name.endsWith(".class") && entry.name != "META-INF/MANIFEST.MF") {
                    resourcesDistributed.add(entry.name)
                    val targetFile = mainResDir.resolve(entry.name)
                    targetFile.parentFile.mkdirs()
                    zip.getInputStream(entry).use { input ->
                        Files.copy(input, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    }
                }
            }
        }

        val exclusiveInForkSources = mainFilesBefore.filter { !filesDistributedToMain.contains(it) }
        val exclusiveInForkResources = mainResBefore.filter { !resourcesDistributed.contains(it) }

        val reportDir = layout.buildDirectory.dir("reports/$forkName").get().asFile
        reportDir.mkdirs()
        val reportFile = reportDir.resolve("exclusive-in-fork.txt")

        if (exclusiveInForkSources.isNotEmpty() || exclusiveInForkResources.isNotEmpty()) {
            val reportContent = StringBuilder()
            if (exclusiveInForkSources.isNotEmpty()) {
                reportContent.append("The following files in 'main/java' were NOT found in the upstream source:\n")
                exclusiveInForkSources.sorted().forEach { rogue ->
                    println("  - [SRC] $rogue")
                    reportContent.append("  - [SRC] $rogue\n")
                }
            }
            if (exclusiveInForkResources.isNotEmpty()) {
                reportContent.append("\nThe following files in 'main/resources' were NOT found in the upstream source:\n")
                exclusiveInForkResources.sorted().forEach { rogue ->
                    println("  - [RES] $rogue")
                    reportContent.append("  - [RES] $rogue\n")
                }
            }
            reportFile.writeText(reportContent.toString())
        }

        println("Generating glue checksum...")
        val checksumFile = file("src/glue/checksum/CHECKSUM")
        checksumFile.parentFile.mkdirs()
        checksumFile.writeText(calculateGlueChecksum())

        println("Updated successfully for LibGDX version $version")
    }
}

tasks.register("verifyGlueIntegrity") {
    group = forkName
    description = "Verifies that no class exists in both glue and main sourcesets and checks glue checksum."

    doLast {

        val glueFiles = glueSrcDir.walkTopDown().filter { it.isFile && it.extension == "java" }
            .map { it.relativeTo(glueSrcDir).path }.toSet()
        val mainFiles = mainSrcDir.walkTopDown().filter { it.isFile && it.extension == "java" }
            .map { it.relativeTo(mainSrcDir).path }.toSet()

        val intersection = glueFiles.intersect(mainFiles)
        if (intersection.isNotEmpty()) {
            throw GradleException("Duplicate files exist in glue and main: $intersection")
        } 

        val checksumFile = file("src/glue/checksum/CHECKSUM")

        val expectedChecksum = checksumFile.readText().trim()
        val actualChecksum = calculateGlueChecksum()

        if (expectedChecksum != actualChecksum) {
            throw GradleException("Glue checksum mismatch. Glue files must remain identical to upstream.")
        }
    }
}

tasks.test {
    useJUnitPlatform()
}

tasks.named("check") {
    dependsOn("verifyGlueIntegrity")
}