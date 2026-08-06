import org.gradle.api.tasks.compile.JavaCompile

dependencies {
    api(project(":lumin-graphics-render"))
    api(platform(libs.lwjgl.bom))
    api(libs.lwjgl.core)
    api(libs.lwjgl.stb)

    val lwjglNatives = when {
        System.getProperty("os.name").lowercase().contains("win") -> "natives-windows"
        System.getProperty("os.name").lowercase().contains("mac") -> "natives-macos"
        else -> "natives-linux"
    }
    runtimeOnly("org.lwjgl:lwjgl::$lwjglNatives")
    runtimeOnly("org.lwjgl:lwjgl-stb::$lwjglNatives")
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-Xlint:all")
}

tasks.test {
    testLogging.showStandardStreams = true
}
