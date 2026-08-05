import java.time.Duration

dependencies {
    api(project(":lumin-graphics-core"))
}

val prismGroup: String by rootProject.extra
val prismVersion: String by rootProject.extra
val lwjglNatives = when {
    System.getProperty("os.name").lowercase().contains("win") -> "natives-windows"
    System.getProperty("os.name").lowercase().contains("mac") &&
        System.getProperty("os.arch").lowercase().contains("aarch64") -> "natives-macos-arm64"
    System.getProperty("os.name").lowercase().contains("mac") -> "natives-macos"
    System.getProperty("os.name").lowercase().contains("linux") -> "natives-linux"
    else -> error("Unsupported LWJGL native platform")
}

val shaderCompiler by sourceSets.creating
val generatedShaderResources = layout.buildDirectory.dir("generated/resources/shaders")

configurations.named(shaderCompiler.implementationConfigurationName) {
    extendsFrom(configurations.compileOnly.get())
}

dependencies {
    add(shaderCompiler.implementationConfigurationName, "$prismGroup:prism-rhi-shaderc:$prismVersion")
    testImplementation(shaderCompiler.output)
    testRuntimeOnly("$prismGroup:prism-rhi-shaderc:$prismVersion")
    testImplementation(platform("org.lwjgl:lwjgl-bom:3.4.1"))
    testImplementation("org.lwjgl:lwjgl")
    testImplementation("org.lwjgl:lwjgl-glfw")
    testImplementation("org.lwjgl:lwjgl-opengl")
    testRuntimeOnly("org.lwjgl:lwjgl::$lwjglNatives")
    testRuntimeOnly("org.lwjgl:lwjgl-glfw::$lwjglNatives")
    testRuntimeOnly("org.lwjgl:lwjgl-opengl::$lwjglNatives")
}

val compileShaders by tasks.registering(JavaExec::class) {
    group = "build"
    description = "Compiles all retained GLSL sources to deterministic Vulkan 1.3 SPIR-V 1.6."
    dependsOn(tasks.named(shaderCompiler.classesTaskName))
    classpath = shaderCompiler.runtimeClasspath
    mainClass.set("com.github.slmpc.lumingraphics.tooling.ShaderCompilerTool")
    args(
        layout.projectDirectory.dir("src/main/resources/assets/lumin_graphics/shaders").asFile.absolutePath,
        generatedShaderResources.get().asFile.absolutePath,
    )
    inputs.files(fileTree("src/main/resources/assets/lumin_graphics/shaders") {
        include("**/*.vsh", "**/*.fsh")
    })
    inputs.property("compiler", "$prismGroup:prism-rhi-shaderc:$prismVersion")
    inputs.property("target", "Vulkan 1.3 / SPIR-V 1.6 / optimization NONE / LUMIN_VULKAN=1")
    outputs.dir(generatedShaderResources)
    outputs.file(generatedShaderResources.map { it.file("generation-complete.properties") })
}

sourceSets.main {
    resources.srcDir(generatedShaderResources)
}

tasks.named("processResources") {
    dependsOn(compileShaders)
}

tasks.named("sourcesJar") {
    dependsOn(compileShaders)
}

tasks.register<Test>("shaderCompileTest") {
    group = "verification"
    description = "Verifies the generated shader artifact contract."
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform()
    filter.includeTestsMatching("*ShaderArtifactTest")
    filter.includeTestsMatching("*ShaderCompilerTransactionTest")
    dependsOn(compileShaders)
}

tasks.withType<Test>().configureEach {
    systemProperty("lumin.shader.testClasspath", sourceSets.test.get().runtimeClasspath.asPath)
}

fun registerGlShaderTest(name: String, major: Int, minor: Int) = tasks.register<Test>(name) {
    group = "verification"
    description = "Compiles and links the shader catalog in a hidden OpenGL $major.$minor context."
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform()
    filter.includeTestsMatching("*ShaderGlContextTest")
    systemProperty("lumin.gl.major", major)
    systemProperty("lumin.gl.minor", minor)
    systemProperty("lumin.shader.evidenceDir",
        rootProject.layout.projectDirectory.dir(".omo/start-work/attempts/lumin-graphics-prism-rhi-migration-20260730").asFile.absolutePath)
    timeout.set(Duration.ofMinutes(2))
}

registerGlShaderTest("shaderGl41Test", 4, 1)
registerGlShaderTest("shaderGl46Test", 4, 6)

tasks.named<Test>("test") {
    exclude("**/ShaderGlContextTest.class")
}

tasks.withType<org.gradle.api.tasks.compile.JavaCompile>().configureEach {
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}
