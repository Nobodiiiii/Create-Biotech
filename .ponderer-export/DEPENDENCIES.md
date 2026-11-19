This host requires pondererlib >= 0.1.1 and < 0.2.0, plus upstream Ponder supplied by Create.
Development resolves the NeoForge Lib from Modrinth Maven; see gradle.properties and build.gradle.
The ordinary development and CurseForge JARs require a separately installed Lib in mods.
The Modrinth distribution embeds the same Lib through NeoForge JarJar; its classes are not merged into the host.
Handwritten custom steps belong in com.nobodiiiii.createbiotech.ponderext.PonderExtensions.
