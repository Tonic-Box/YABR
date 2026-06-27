plugins {
    id("com.github.johnrengelman.shadow") version "8.1.1" apply false
}

subprojects {
    apply(plugin = "java-library")

    group = "com.tonic"
    version = "1.0.1"

    repositories {
        mavenCentral()
    }

    configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(11))
        }
    }

    dependencies {
        "compileOnly"("org.projectlombok:lombok:1.18.30")
        "annotationProcessor"("org.projectlombok:lombok:1.18.30")
    }
}
