// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
    id("jacoco")
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}

// JaCoCo configuration for test coverage
tasks.register<JacocoReport>("jacocoFullReport") {
    dependsOn(subprojects.map { it.tasks.matching { task -> task.name == "testDebugUnitTest" } })

    reports {
        xml.required.set(true)
        html.required.set(true)
    }

    val fileFilter = listOf(
        "**/R.class",
        "**/R\$*.class",
        "**/BuildConfig.*",
        "**/Manifest*.*",
        "**/*Test*.*",
        "android/**/*.*",
        "**/*_Hilt*.*",
        "**/Hilt_*.*",
        "**/*_Factory.*",
        "**/*_MembersInjector.*",
        "**/di/*Module*.*",
        "**/*Dao_Impl*.*",
        "**/*Database_Impl*.*"
    )

    val javaClasses = subprojects.map { project ->
        fileTree("${project.layout.buildDirectory.get()}/intermediates/javac/debug") {
            exclude(fileFilter)
        }
    }

    val kotlinClasses = subprojects.map { project ->
        fileTree("${project.layout.buildDirectory.get()}/tmp/kotlin-classes/debug") {
            exclude(fileFilter)
        }
    }

    classDirectories.setFrom(files(javaClasses + kotlinClasses))

    val sources = subprojects.map { project ->
        "${project.projectDir}/src/main/java"
    }
    sourceDirectories.setFrom(files(sources))

    val execData = subprojects.map { project ->
        fileTree(project.layout.buildDirectory.get()) {
            include("jacoco/testDebugUnitTest.exec")
        }
    }
    executionData.setFrom(files(execData))
}
