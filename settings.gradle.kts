pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // Vendored copy of io.github.libxposed:*:102.0.0. The artifacts are
        // not in the local Gradle cache and pulling them from Maven Central
        // would make every clean build depend on the network.
        maven {
            url = uri(rootDir.resolve(".tools/maven"))
            metadataSources {
                mavenPom()
                artifact()
            }
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "HonorDuo"
include(":app")
