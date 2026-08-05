import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.external.javadoc.StandardJavadocDocletOptions
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion

plugins {
    base
}

group = "com.github.slmpc.lumingraphics"
version = "1.2.0"

val prismGroup = "com.github.slmpc.prismrhi"
val prismVersion = "0.2.0"
extra["prismGroup"] = prismGroup
extra["prismVersion"] = prismVersion
val luminPublishedJavaModules = listOf(
    "lumin-graphics-core",
    "lumin-graphics-render",
    "lumin-graphics-text",
    "lumin-graphics-ui",
)
val luminPublishedModules = luminPublishedJavaModules + "lumin-graphics-bom"

subprojects {
    group = rootProject.group
    version = rootProject.version

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(17)
    }
    tasks.withType<Javadoc>().configureEach {
        (options as StandardJavadocDocletOptions).apply {
            encoding = "UTF-8"
            charSet = "UTF-8"
            docEncoding = "UTF-8"
        }
    }
    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        providers.systemProperty("lumin.epsilon.root").orNull?.let { epsilonRoot ->
            systemProperty("lumin.epsilon.root", epsilonRoot)
        }
    }
    tasks.withType<AbstractArchiveTask>().configureEach {
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
    }
}

configure(luminPublishedJavaModules.map(::project)) {
    apply(plugin = "java-library")
    apply(plugin = "maven-publish")

    extensions.configure<JavaPluginExtension> {
        toolchain.languageVersion.set(JavaLanguageVersion.of(17))
        withSourcesJar()
    }
    dependencies {
        add("testImplementation", platform("org.junit:junit-bom:5.11.4"))
        add("testImplementation", "org.junit.jupiter:junit-jupiter")
        add("testRuntimeOnly", "org.junit.platform:junit-platform-launcher")
    }
    extensions.configure<PublishingExtension> {
        publications {
            create<MavenPublication>("mavenJava") {
                from(components["java"])
                artifactId = project.name
            }
        }
    }
}

configure(luminPublishedModules.map(::project)) {
    pluginManager.withPlugin("maven-publish") {
        extensions.configure<PublishingExtension> {
            providers.gradleProperty("publishRepository").orNull?.let { target ->
                repositories {
                    maven {
                        name = "githubPages"
                        url = uri(target)
                    }
                }
            }
        }
    }
}

project(":lumin-graphics-demo") {
    apply(plugin = "application")
    extensions.configure<JavaPluginExtension> {
        toolchain.languageVersion.set(JavaLanguageVersion.of(17))
    }
}

tasks.register("verifyTopology") {
    group = "verification"
    description = "Verifies the standalone module, publication, and dependency topology."

    doLast {
        val expectedProjects = setOf(
            ":lumin-graphics-core",
            ":lumin-graphics-render",
            ":lumin-graphics-text",
            ":lumin-graphics-ui",
            ":lumin-graphics-bom",
            ":lumin-graphics-demo",
        )
        check(rootProject.subprojects.map { it.path }.toSet() == expectedProjects) {
            "Unexpected project topology: ${rootProject.subprojects.map { it.path }}"
        }
        check(gradle.includedBuilds.isEmpty()) { "Composite builds are forbidden" }
        check(rootProject.group.toString() == "com.github.slmpc.lumingraphics")
        check(rootProject.version.toString() == "1.2.0")

        luminPublishedJavaModules.forEach { moduleName ->
            val module = project(":$moduleName")
            check(module.plugins.hasPlugin("maven-publish")) { "$moduleName must be published" }
            check(module.extensions.getByType<JavaPluginExtension>().toolchain.languageVersion.get().asInt() == 17)
        }
        check(project(":lumin-graphics-bom").plugins.hasPlugin("maven-publish"))
        check(!project(":lumin-graphics-demo").plugins.hasPlugin("maven-publish")) {
            "lumin-graphics-demo must not be published"
        }

        fun projectApiEdges(moduleName: String): Set<String> =
            project(":$moduleName").configurations.getByName("api").dependencies
                .filterIsInstance<ProjectDependency>()
                .map { it.path }
                .toSet()

        check(projectApiEdges("lumin-graphics-core").isEmpty())
        check(projectApiEdges("lumin-graphics-render") == setOf(":lumin-graphics-core"))
        check(projectApiEdges("lumin-graphics-text") == setOf(":lumin-graphics-render"))
        check(projectApiEdges("lumin-graphics-ui") == setOf(":lumin-graphics-text"))

        val coreExternal = project(":lumin-graphics-core").configurations.getByName("api").dependencies
            .filterNot { it is ProjectDependency }
            .map { "${it.group}:${it.name}:${it.version}" }
        check(coreExternal == listOf("$prismGroup:prism-rhi-core:$prismVersion")) {
            "Core must have exactly the pinned Prism dependency: $coreExternal"
        }
        check(luminPublishedModules.toSet().size == 5)
    }
}

tasks.register("verifyPrismArtifact") {
    group = "verification"
    description = "Verifies that the required Prism core artifact resolves."

    doLast {
        val resolvedArtifact = project(":lumin-graphics-core")
            .configurations.getByName("compileClasspath")
            .resolvedConfiguration.resolvedArtifacts
            .single { artifact ->
                artifact.moduleVersion.id.group == prismGroup &&
                    artifact.name == "prism-rhi-core" &&
                    artifact.moduleVersion.id.version == prismVersion
            }
            .file
        println("Prism coordinate: $prismGroup:prism-rhi-core:$prismVersion")
        println("Resolved artifact: ${resolvedArtifact.absolutePath}")
    }
}

tasks.named("check") {
    dependsOn("verifyTopology")
}

project(":lumin-graphics-core") {
    tasks.named<Test>("test") {
        exclude("**/PublishedArchitectureTest.class")
    }
}

val architectureCheck by tasks.registering(Test::class) {
    group = "verification"
    description = "Checks the four published modules, their GAV graph, JAR namespaces, and migration ledgers."
    val coreSourceSets = project(":lumin-graphics-core").extensions.getByType<org.gradle.api.tasks.SourceSetContainer>()
    dependsOn(":lumin-graphics-core:testClasses")
    dependsOn(luminPublishedJavaModules.map { ":$it:jar" })
    testClassesDirs = coreSourceSets["test"].output.classesDirs
    classpath = coreSourceSets["test"].runtimeClasspath
    include("**/PublishedArchitectureTest.class")
    useJUnitPlatform()
    testLogging.showStandardStreams = true
    binaryResultsDirectory.set(layout.buildDirectory.dir("test-results/architectureCheck/binary"))
    reports.junitXml.outputLocation.set(layout.buildDirectory.dir("test-results/architectureCheck"))
    reports.html.outputLocation.set(layout.buildDirectory.dir("reports/tests/architectureCheck"))
    systemProperty("lumin.root", rootProject.projectDir.absolutePath)
    doLast {
        fun apiEdges(name: String) = project(":$name").configurations.getByName("api").dependencies
            .filterIsInstance<ProjectDependency>().map { it.path }.toSet()
        val expected = mapOf(
            "lumin-graphics-core" to emptySet(),
            "lumin-graphics-render" to setOf(":lumin-graphics-core"),
            "lumin-graphics-text" to setOf(":lumin-graphics-render"),
            "lumin-graphics-ui" to setOf(":lumin-graphics-text"),
        )
        check(expected.all { (name, edges) -> apiEdges(name) == edges }) {
            "published GAV graph is not one-way: ${expected.keys.associateWith(::apiEdges)}"
        }
        val external = luminPublishedJavaModules.flatMap { name ->
            project(":$name").configurations.getByName("api").dependencies.filterNot { it is ProjectDependency }
        }
        check(external.map { "${it.group}:${it.name}" }.toSet() == setOf(
            "$prismGroup:prism-rhi-core", "org.lwjgl:lwjgl-bom", "org.lwjgl:lwjgl", "org.lwjgl:lwjgl-stb",
        )) { "unexpected published external GAV graph: ${external.map { "${it.group}:${it.name}:${it.version}" }}" }
        check(external.single { it.group == prismGroup }.version == prismVersion)
        check(external.single { it.name == "lwjgl-bom" }.version == "3.4.1")
        check(external.filter { it.name == "lwjgl" || it.name == "lwjgl-stb" }.all { it.version == null })
        println("ARCH_LUMIN_DEPENDENCIES projectEdges=3 externalEdges=${external.size}")
    }
}

for (smoke in listOf("gl41Smoke", "gl46Smoke", "vulkanSmoke", "wrongContextSmoke", "missingShaderSmoke")) {
    tasks.register(smoke) {
        group = "verification"
        dependsOn(":lumin-graphics-demo:$smoke")
    }
}
