plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    id("maven-publish")
}

android {
    namespace = "com.naminfo.ftpclient"
    compileSdk = 34

    defaultConfig {
        minSdk = 29
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

publishing {
    publications {
        register<MavenPublication>("release") {
            afterEvaluate { from(components["release"]) }

            groupId = "com.github.nisath-android"
            artifactId = "ftp-library"
            version = "1.0.0"

            pom {
                name.set("FTP Library")
                description.set("A simple FTP client library for Android.")
                url.set("https://github.com/nisath-android/ftp-library")

                licenses {
                    license {
                        name.set("Apache License 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.html")
                    }
                }

                developers {
                    developer {
                        id.set("nisath-android")
                        name.set("Nisath")
                        email.set("naminfo.chennai@gmail.com")
                    }
                }

                scm {
                    connection.set("scm:git:git://github.com/nisath-android/ftp-library.git")
                    developerConnection.set("scm:git:ssh://github.com/nisath-android/ftp-library.git")
                    url.set("https://github.com/nisath-android/ftp-library")
                }
            }
        }
    }
}

dependencies {
    implementation("commons-net:commons-net:3.11.1")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
