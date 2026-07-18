plugins {
    jacoco
}

// The whole test suite lives here and depends on every production module. Architectural layering
// is enforced by the production subprojects' own dependencies, not by the tests; keeping the suite
// aggregated avoids distributing ~360 cross-cutting test classes across modules.
dependencies {
    "testImplementation"(project(":core"))
    "testImplementation"(project(":bytecode"))
    "testImplementation"(project(":renamer"))
    "testImplementation"(project(":ssa"))
    "testImplementation"(project(":source"))
    "testImplementation"(project(":analyses"))
    "testImplementation"(project(":execution"))
    "testImplementation"(project(":query"))

    "testImplementation"(platform("org.junit:junit-bom:5.9.1"))
    "testImplementation"("org.junit.jupiter:junit-jupiter")
    "testImplementation"("org.mockito:mockito-core:5.3.1")
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    maxParallelForks = 1
    jvmArgs("-Xss2m", "-Xmx1g")
    systemProperty("junit.jupiter.execution.parallel.enabled", "false")
    // Forward the opt-in jar path for VerifySweepTest to the forked test JVM (unset -> the test skips).
    System.getProperty("verify.sweep.jar")?.let { systemProperty("verify.sweep.jar", it) }
    finalizedBy(tasks.named("jacocoTestReport"))
}

tasks.named<JacocoReport>("jacocoTestReport") {
    dependsOn(tasks.named("test"))
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}
