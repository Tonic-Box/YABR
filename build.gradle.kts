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
}

evaluationDependsOnChildren()

// The modules the published artifact aggregates; tests and examples stay out of the public docsite.
val docSiteModules = listOf("core", "bytecode", "renamer", "ssa", "source", "analyses", "execution", "query")

fun docSiteRepoUrl(): String? = try {
    val process = ProcessBuilder("git", "remote", "get-url", "origin").start()
    val url = process.inputStream.bufferedReader().readText().trim()
    if (process.waitFor() == 0 && url.isNotEmpty()) {
        url.removeSuffix(".git").replace(Regex("^git@([^:]+):"), "https://$1/")
    } else {
        null
    }
} catch (ignored: Exception) {
    null
}

fun docSiteLinksBar(template: File, projectName: String): String {
    val repoUrl = docSiteRepoUrl()
    return template.readText(Charsets.UTF_8)
        .lines()
        .filterNot { repoUrl == null && it.contains("@REPO_URL@") }
        .joinToString("\n")
        .replace("@REPO_URL@", repoUrl ?: "")
        .replace("@PROJECT_NAME@", projectName)
        .trim()
}

tasks.register<Javadoc>("docSite") {
    group = "documentation"
    description = "Generates the public API docsite into docs/"

    val apiProjects = docSiteModules.map { project(":$it") }
    apiProjects.forEach { dependsOn("${it.path}:compileJava") }

    val mainSourceSets = apiProjects.map { it.the<SourceSetContainer>()["main"] }
    setSource(mainSourceSets.map { it.allJava })
    classpath = files(mainSourceSets.map { it.output.classesDirs }, mainSourceSets.map { it.compileClasspath })

    setDestinationDir(file("docs"))
    outputs.upToDateWhen { false }

    (options as StandardJavadocDocletOptions).apply {
        memberLevel = JavadocMemberLevel.PUBLIC
        windowTitle = "YABR"
        docTitle = "YABR ${project(":core").version}"
        encoding = "UTF-8"
        charSet = "UTF-8"
        links("https://docs.oracle.com/en/java/javase/11/docs/api/")
    }

    doFirst {
        delete("docs")
    }

    doLast {
        val docsDir = file("docs")
        copy {
            from("doc-assets/javadoc-dark.css")
            into(docsDir)
        }

        val linksBar = docSiteLinksBar(file("doc-assets/links-bar.html"), rootProject.name.lowercase())
        val barInjection = "<script type=\"text/javascript\">if (window == top) document.documentElement.className += \" withSiteBar\";</script>\n$linksBar"
        val bodyTag = Regex("<body[^>]*>")

        docsDir.walkTopDown().filter { it.isFile && it.extension == "html" }.forEach { page ->
            var content = page.readText(Charsets.UTF_8)
            if (content.contains("</head>") && !content.contains("javadoc-dark.css")) {
                val depth = generateSequence(page.parentFile) { it.parentFile }
                    .takeWhile { it != docsDir }
                    .count()
                val href = "../".repeat(depth) + "javadoc-dark.css"
                content = content.replace("</head>", "<link rel=\"stylesheet\" type=\"text/css\" href=\"$href\">\n</head>")
            }
            if (!content.contains("class=\"siteBar\"")) {
                content = bodyTag.replaceFirst(content, "$0\n" + Regex.escapeReplacement(barInjection))
            }
            page.writeText(content, Charsets.UTF_8)
        }
    }
}
