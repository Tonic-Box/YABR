plugins {
    id("com.github.johnrengelman.shadow")
    `maven-publish`
}

// Aggregates every layer into a single published artifact (com.tonic:YABR) so consumers keep
// one dependency. The module boundaries live in the build (the subproject DAG); the shipped jar
// is a flat merge of all of them.
dependencies {
    "api"(project(":core"))
    "api"(project(":bytecode"))
    "api"(project(":renamer"))
    "api"(project(":ssa"))
    "api"(project(":source"))
    "api"(project(":analyses"))
    "api"(project(":execution"))
    "api"(project(":query"))
}

tasks.shadowJar {
    archiveBaseName.set("YABR")
    archiveClassifier.set("")
}

tasks.named("assemble") {
    dependsOn(tasks.shadowJar)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "com.tonic"
            artifactId = "YABR"
            version = project.version.toString()
            artifact(tasks.shadowJar)
            pom {
                name.set("YABR")
                description.set("Yet Another Bytecode Reverser - Java bytecode analysis tool")
            }
        }
    }
}
