plugins {
    id("dev.kikugie.stonecutter")
    id("org.quiltmc.loom") version "1.15.1" apply false
    id("net.minecraftforge.gradle") version "7.0.31" apply false
    id("net.neoforged.moddev") version "2.0.141" apply false
    id("net.neoforged.moddev.legacyforge") version "2.0.141" apply false
}

stonecutter active "1.21.1-fabric" /* [SC] DO NOT EDIT */

stonecutter parameters {
    constants.match(
        node.metadata.project.substringAfterLast('-'),
        "fabric",
        "quilt",
        "forge",
        "neoforge"
    )
}
