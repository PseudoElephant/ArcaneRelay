plugins {
    idea
    java
    id("com.azuredoom.hytale-tools") version "1.+"
    id("com.azuredoom.hytalepublisher") version "1.+"
}


tasks.withType<Javadoc>().configureEach {
    (options as org.gradle.external.javadoc.StandardJavadocDocletOptions).addStringOption("Xdoclint:-missing", "-quiet")
}

group = project.property("group").toString()

tasks.named<Jar>("jar") {
  archiveBaseName.set(project.property("mod_name").toString())
  archiveVersion.set(project.property("version").toString())
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(property("java_version").toString().toInt()))
}

hytaleTools {
    javaVersion = property("java_version").toString().toInt()
    hytaleVersion = property("hytale_version").toString()
    manifestServerVersion = property("manifestServerVersion").toString()
    manifestGroup = property("manifest_group").toString()
    modId = property("mod_id").toString()
    modDescription = property("mod_description").toString()
    modUrl = property("mod_url").toString()
    mainClass = property("main_class").toString()
    modCredits = property("mod_author").toString()
    manifestDependencies = property("manifest_dependencies").toString()
    manifestOptionalDependencies = property("manifest_opt_dependencies").toString()
    curseforgeId = property("curseforgeID").toString()
    disabledByDefault = property("disabled_by_default").toString().toBoolean()
    includesPack = property("includes_pack").toString().toBoolean()
    injectServerJavadocsIntoSources = property("inject_server_javadocs_into_sources").toString().toBoolean()
    generateAssetsBinary = property("generateAssetsBinary").toString().toBoolean()
    patchline = property("patchline").toString()
}

repositories {
    mavenCentral()
}

idea {
    module {
        isDownloadSources = true
        isDownloadJavadoc = true
    }
}


hytalePublisher {
    modtale {
        enabled = true
        projectId = property("modtale_project_id").toString()
    }

    curseforge {
        enabled = true
        projectId = property("curseforge_project_id").toString()
    }

    modifold {
        enabled = true
        projectId = property("modifold_project_slug").toString()
    }
}
