dependencies {
    api(project(":lumin-graphics-core"))
}

val shaderCompiler by sourceSets.creating
val generatedShaderResources = layout.buildDirectory.dir("generated/resources/shaders")

configurations.named(shaderCompiler.implementationConfigurationName) {
    extendsFrom(configurations.compileOnly.get())
}

dependencies {
    add(shaderCompiler.implementationConfigurationName, libs.prism.rhi.shaderc)
    testImplementation(shaderCompiler.output)
    testRuntimeOnly(libs.prism.rhi.shaderc)
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
    inputs.property("compiler", libs.prism.rhi.shaderc.get().toString())
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
    useJUnitPlatform()
    filter.includeTestsMatching("*ShaderArtifactTest")
    filter.includeTestsMatching("*ShaderCompilerTransactionTest")
    dependsOn(compileShaders)
    doFirst {
        val runtimeClasspath = sourceSets.test.get().runtimeClasspath
        classpath = runtimeClasspath
        systemProperty("lumin.shader.testClasspath", runtimeClasspath.asPath)
    }
}

tasks.named<Test>("test") {
    exclude("**/ShaderArtifactTest.class")
    exclude("**/ShaderCompilerTransactionTest.class")
}

tasks.withType<org.gradle.api.tasks.compile.JavaCompile>().configureEach {
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}
