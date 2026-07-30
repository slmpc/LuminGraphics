import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion
import java.security.MessageDigest

plugins {
    base
}

group = "com.github.slmpc.lumingraphics"
version = "0.1.0"

val prismGroup = "com.github.slmpc.prismrhi"
val prismVersion = "0.1.0"
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
    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
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
        check(rootProject.version.toString() == "0.1.0")

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
    description = "Verifies the resolved Prism core artifact against the explicit isolated repository."

    doLast {
        val configuredRepository = System.getProperty("maven.repo.local")
            ?.takeIf { it.isNotBlank() }
            ?: error("maven.repo.local must identify the isolated Prism repository")
        val sourceArtifact = file(configuredRepository).resolve(
            "com/github/slmpc/prismrhi/prism-rhi-core/$prismVersion/prism-rhi-core-$prismVersion.jar",
        )
        check(sourceArtifact.isFile) { "Expected Prism verifier artifact is missing: $sourceArtifact" }

        val resolvedArtifact = project(":lumin-graphics-core")
            .configurations.getByName("compileClasspath")
            .resolvedConfiguration.resolvedArtifacts
            .single { artifact ->
                artifact.moduleVersion.id.group == prismGroup &&
                    artifact.name == "prism-rhi-core" &&
                    artifact.moduleVersion.id.version == prismVersion
            }
            .file
        fun sha256(file: File): String = MessageDigest.getInstance("SHA-256")
            .digest(file.readBytes())
            .joinToString("") { byte -> "%02x".format(byte) }

        val resolvedHash = sha256(resolvedArtifact)
        val sourceHash = sha256(sourceArtifact)
        check(resolvedHash == sourceHash) {
            "Resolved Prism artifact does not match the isolated verifier repository"
        }
        println("Prism coordinate: $prismGroup:prism-rhi-core:$prismVersion")
        println("Resolved artifact: ${resolvedArtifact.absolutePath}")
        println("Verifier artifact: ${sourceArtifact.absolutePath}")
        println("SHA-256: $resolvedHash")
    }
}

tasks.named("check") {
    dependsOn("verifyTopology")
}

for (smoke in listOf("gl41Smoke", "glDsaSmoke", "vulkanSmoke", "wrongContextSmoke", "missingShaderSmoke")) {
    tasks.register(smoke) {
        group = "verification"
        dependsOn(":lumin-graphics-demo:$smoke")
    }
}
