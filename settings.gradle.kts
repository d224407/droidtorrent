pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://dl.frostwire.com/maven"); content { includeGroup("com.frostwire") } }
    }
}
rootProject.name = "DroidTorrent"
include(":app")
