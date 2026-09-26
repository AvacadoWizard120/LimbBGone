plugins {
    // Quilt Loom supports Fabric targets and is also used by the Quilt node.
    // Using one pinned Loom implementation avoids Gradle classpath shadowing
    // between two forks and keeps the effective toolchain reproducible.
    id("org.quiltmc.loom")
}

fun prop(name: String): String = project.property(name).toString()

val minecraft = prop("deps.minecraft")
val modId = prop("mod.id")
val modName = prop("mod.name")
val modVersion = prop("mod.version")
val modGroup = prop("mod.group")
val modDescription = prop("mod.description")
val modAuthor = prop("mod.author")
val modOriginalAuthor = prop("mod.original_author")
val modPortAuthor = prop("mod.port_author")
val modLicense = prop("mod.license")
val modEntrypoint = prop("mod.entrypoint")
val modHomepage = prop("mod.homepage")
val modSources = prop("mod.sources")
val modIssues = prop("mod.issues")
val versionRange = prop("version_range")
val javaVersion = prop("java_version").toInt()
val mixinCompatibilityLevel = prop("mixin_compatibility_level")

group = modGroup
base.archivesName = modId
version = "$modVersion+$minecraft-fabric"

java {
    toolchain.languageVersion = JavaLanguageVersion.of(javaVersion)
    sourceCompatibility = JavaVersion.toVersion(javaVersion)
    targetCompatibility = JavaVersion.toVersion(javaVersion)
    withSourcesJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.release = javaVersion
    options.encoding = "UTF-8"
}

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net/")
    maven("https://maven.terraformersmc.com/releases/")
}

loom {
    val accessWidener = rootProject.file("src/main/resources/${modId}.accesswidener")
    if (accessWidener.exists()) {
        accessWidenerPath = accessWidener
    }
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraft")
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:${prop("deps.fabric-loader")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${prop("deps.fabric-api")}")
    modCompileOnly("com.terraformersmc:modmenu:${prop("deps.modmenu")}")
    modLocalRuntime("com.terraformersmc:modmenu:${prop("deps.modmenu")}")
}

tasks.processResources {
    val props = mapOf(
        "mod_id" to modId,
        "mod_name" to modName,
        "mod_version" to modVersion,
        "mod_description" to modDescription,
        "mod_author" to modAuthor,
        "mod_original_author" to modOriginalAuthor,
        "mod_port_author" to modPortAuthor,
        "mod_license" to modLicense,
        "mod_entrypoint" to modEntrypoint,
        "mod_homepage" to modHomepage,
        "mod_sources" to modSources,
        "mod_issues" to modIssues,
        "minecraft_version" to minecraft,
        "version_range" to versionRange,
        "fabric_loader_version_range" to prop("fabric_loader_version_range"),
        "mixin_compatibility_level" to mixinCompatibilityLevel
    )

    inputs.properties(props)
    filesMatching(listOf("fabric.mod.json", "${modId}.mixins.json")) {
        expand(props)
    }
    exclude("quilt.mod.json", "META-INF/mods.toml", "META-INF/neoforge.mods.toml")
}

tasks.named<Jar>("jar") {
    from(rootProject.file("COPYING")) { into("META-INF") }
    from(rootProject.file("COPYING.LESSER")) { into("META-INF") }
    from(rootProject.file("NOTICE")) { into("META-INF") }
}

tasks.register<Copy>("buildAndCollect") {
    group = "build"
    dependsOn(tasks.named("remapJar"))
    from(tasks.named("remapJar"))
    into(rootProject.layout.buildDirectory.dir("libs/$modVersion"))
}
