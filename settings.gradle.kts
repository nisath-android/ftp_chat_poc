pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
        // We need to declare this repository to be able to use Liblinphone SDK
            maven {
                url = uri("https://linphone.org/maven_repository")
            }

    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven( url = "https://jitpack.io" )
        // We need to declare this repository to be able to use Liblinphone SDK
            maven {
                url = uri("https://linphone.org/maven_repository")
            }

    }
}

rootProject.name = "FTPClient"
include(":app")
//include(":ftpclient")
