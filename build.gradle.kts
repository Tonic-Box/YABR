plugins {
    id("java")
}

group = "com.tonic"
version = "1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(11))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")

    implementation("org.projectlombok:lombok:1.18.30")
    annotationProcessor("org.projectlombok:lombok:1.18.30")

    // Name inference system dependencies
    implementation("com.github.javaparser:javaparser-core:3.25.5")
    implementation("org.xerial:sqlite-jdbc:3.44.1.0")
}

tasks.test {
    useJUnitPlatform()
}