// Top-level build file. Plugins are declared here and applied in :app.
plugins {
    // Pinned to the newest AGP this Android Studio accepts; a newer one is refused
    // outright at sync with "incompatible version of the Android Gradle plugin".
    id("com.android.application") version "9.4.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
    // miuix-nav keeps its back stack in rememberSaveable, which needs a
    // serializer for the route types. Version tracks the Kotlin plugin above.
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.21" apply false
}
