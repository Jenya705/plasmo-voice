import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.matthewprenger.cursegradle.CurseArtifact
import com.matthewprenger.cursegradle.CurseProject
import com.matthewprenger.cursegradle.CurseRelation
import com.matthewprenger.cursegradle.Options
import com.modrinth.minotaur.TaskModrinthUpload
import com.modrinth.minotaur.request.VersionType
import net.fabricmc.loom.task.RemapJarTask

val minecraftVersion: String by rootProject
val fabricLoaderVersion: String by rootProject
val fabricVersion: String by rootProject
val modVersion: String by rootProject
val mavenGroup: String by rootProject

val curseProjectId: String by rootProject
val curseFabricRelease: String by rootProject
val curseDisplayVersion: String by rootProject
val curseSupportedVersions: String by rootProject

val modrinthVersionType: String by rootProject
val modrinthSupportedVersions: String by rootProject
val modrinthProjectId: String by rootProject

configurations {
    create("shadowCommon")
}

architectury {
    platformSetupLoomIde()
    fabric()
}

base {
    archivesBaseName = "plasmovoice"
}

project.group = mavenGroup
project.version = modVersion

dependencies {
    minecraft("com.mojang:minecraft:${minecraftVersion}")
    mappings(loom.officialMojangMappings())

    compileOnly(project(":common", "dev")) {
        isTransitive = false
    }
    project.configurations.getByName("developmentFabric")(project(":common", "dev")) {
        isTransitive = false
    }
    "shadowCommon"(project(":common", "transformProductionFabric")) {
        isTransitive = false
    }

    modApi("net.fabricmc:fabric-loader:${fabricLoaderVersion}")

    // Fabric API jar-in-jar
    include(fabricApi.module("fabric-api-base", fabricVersion))?.let { modImplementation(it) }
    include(fabricApi.module("fabric-command-api-v1", fabricVersion))?.let { modImplementation(it) }
    include(fabricApi.module("fabric-key-binding-api-v1", fabricVersion))?.let { modImplementation(it) }
    include(fabricApi.module("fabric-lifecycle-events-v1", fabricVersion))?.let { modImplementation(it) }
    include(fabricApi.module("fabric-networking-api-v1", fabricVersion))?.let { modImplementation(it) }
    include(fabricApi.module("fabric-rendering-v1", fabricVersion))?.let { modImplementation(it) }
    include(fabricApi.module("fabric-resource-loader-v0", fabricVersion))?.let { modImplementation(it) }

    // Plasmo Voice protocol
    implementation("su.plo.voice:common:1.0.0")
    "shadowCommon"("su.plo.voice:common:1.0.0")

    // YAML for server config
    implementation("org.yaml:snakeyaml:1.29")
    "shadowCommon"("org.yaml:snakeyaml:1.29")

    // Opus
    implementation("su.plo.voice:opus:1.1.2")
    "shadowCommon"("su.plo.voice:opus:1.1.2")

    // RNNoise
    implementation("su.plo.voice:rnnoise:1.0.0")
    "shadowCommon"("su.plo.voice:rnnoise:1.0.0")

    compileOnly("org.projectlombok:lombok:1.18.20")
    annotationProcessor("org.projectlombok:lombok:1.18.20")

    testCompileOnly("org.projectlombok:lombok:1.18.20")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.20")
}

repositories {
    maven {
        url = uri("https://repo.plo.su")
    }
    mavenCentral()
    mavenLocal()
}

tasks {
    jar {
        classifier = "dev"
    }

    processResources {
        filesMatching("fabric.mod.json") {
            expand(
                mutableMapOf(
                    "version" to modVersion,
                    "loader_version" to fabricLoaderVersion,
                    "fabric_version" to fabricVersion
                )
            )
        }
    }

    shadowJar {
        configurations = listOf(project.configurations.getByName("shadowCommon"))
        classifier = "dev-shadow"

        dependencies {
            exclude(dependency("net.java.dev.jna:jna"))
            exclude(dependency("org.slf4j:slf4j-api"))
        }
    }

    remapJar {
        dependsOn(getByName<ShadowJar>("shadowJar"))
        input.set(shadowJar.get().archiveFile)
        archiveBaseName.set("plasmovoice-fabric-${minecraftVersion}")
    }

    build {
        doLast {
            shadowJar.get().archiveFile.get().asFile.delete()
        }
    }
}

val remapJar = tasks.getByName<RemapJarTask>("remapJar")

tasks.register<TaskModrinthUpload>("publishModrinth") {
    token = if (file("${rootDir}/modrinth_key.txt").exists()) {
        file("${rootDir}/modrinth_key.txt").readText()
    } else {
        ""
    }

    projectId = modrinthProjectId

    versionNumber = "fabric-$curseDisplayVersion-$version"
    versionName = "[Fabric ${curseDisplayVersion}] Plasmo Voice $version"
    versionType = VersionType.valueOf(modrinthVersionType)

    modrinthSupportedVersions.split(",").forEach {
        addGameVersion(it)
    }
    changelog = file("${rootDir}/changelog.md").readText()
    addLoader("fabric")
    uploadFile = file("${project.buildDir}/libs/${remapJar.archiveBaseName.get()}-${version}.jar")
}

curseforge {
    apiKey = if (file("${rootDir}/curseforge_key.txt").exists()) {
        file("${rootDir}/curseforge_key.txt").readText()
    } else {
        ""
    }

    project(closureOf<CurseProject> {
        id = curseProjectId
        changelog = file("${rootDir}/changelog.txt")
        releaseType = curseFabricRelease
        curseSupportedVersions.split(",").forEach {
            addGameVersion(it)
        }
        addGameVersion("Fabric")

        mainArtifact(
            file("${project.buildDir}/libs/${remapJar.archiveBaseName.get()}-${version}.jar"),
            closureOf<CurseArtifact> {
                displayName = "[Fabric ${curseDisplayVersion}] Plasmo Voice $version"

                relations(closureOf<CurseRelation> {
                    optionalDependency("sound-physics-fabric")
                    optionalDependency("sound-physics-remastered")
                })
            })
        afterEvaluate {
            uploadTask.dependsOn(remapJar)
        }
    })

    options(closureOf<Options> {
        forgeGradleIntegration = false
    })
}
