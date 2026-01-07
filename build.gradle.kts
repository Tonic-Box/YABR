plugins {
    id("java")
    id("maven-publish")
    id("jacoco")
}

group = "com.tonic"
version = "1.0.0"

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
    testImplementation("org.mockito:mockito-core:5.3.1")

    implementation("org.projectlombok:lombok:1.18.30")
    annotationProcessor("org.projectlombok:lombok:1.18.30")
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)

    maxParallelForks = 1
    jvmArgs("-Xss2m", "-Xmx1g")

    systemProperty("junit.jupiter.execution.parallel.enabled", "false")
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
    doLast {
        val reportPath = reports.html.outputLocation.get().asFile.resolve("index.html")
        println("Coverage report: $reportPath")
    }
}

// Maven publishing configuration
publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])

            pom {
                name.set("YABR")
                description.set("Yet Another Bytecode Reverser - Java bytecode analysis tool")
            }
        }
    }
}

// Custom task: clean -> build -> publishToMavenLocal
tasks.register("publishLocal") {
    group = "publishing"
    description = "Cleans, builds, and publishes to Maven local repository"

    dependsOn("clean")
    dependsOn("build")
    dependsOn("publishToMavenLocal")

    // Ensure proper task ordering
    tasks.findByName("build")?.mustRunAfter("clean")
    tasks.findByName("publishToMavenLocal")?.mustRunAfter("build")
}