import org.gradle.api.tasks.JavaExec
import java.time.Duration

val prismGroup = rootProject.extra["prismGroup"] as String
val prismVersion = rootProject.extra["prismVersion"] as String
val lwjglVersion = "3.4.1"
val lwjglNatives = when {
    System.getProperty("os.name").lowercase().contains("win") -> "natives-windows"
    System.getProperty("os.name").lowercase().contains("mac") &&
        System.getProperty("os.arch").lowercase().contains("aarch64") -> "natives-macos-arm64"
    System.getProperty("os.name").lowercase().contains("mac") -> "natives-macos"
    else -> "natives-linux"
}

dependencies {
    implementation(project(":lumin-graphics-ui"))
    implementation("$prismGroup:prism-rhi-backend-opengl41:$prismVersion")
    implementation("$prismGroup:prism-rhi-backend-opengl46:$prismVersion")
    implementation("$prismGroup:prism-rhi-backend-vulkan:$prismVersion")
    implementation(platform("org.lwjgl:lwjgl-bom:$lwjglVersion"))
    implementation("org.lwjgl:lwjgl")
    implementation("org.lwjgl:lwjgl-glfw")
    implementation("org.lwjgl:lwjgl-opengl")
    implementation("org.lwjgl:lwjgl-vulkan")
    runtimeOnly("org.lwjgl:lwjgl::$lwjglNatives")
    runtimeOnly("org.lwjgl:lwjgl-glfw::$lwjglNatives")
    runtimeOnly("org.lwjgl:lwjgl-opengl::$lwjglNatives")
}

application {
    mainClass.set("com.github.slmpc.lumingraphics.demo.StandaloneSmoke")
}

fun registerSmoke(name: String, mode: String) = tasks.register<JavaExec>(name) {
    group = "verification"
    description = "Runs the caller-owned $mode standalone smoke."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set(application.mainClass)
    args(mode)
    systemProperty("lumin.evidenceDir", providers.systemProperty("lumin.evidenceDir").orElse(
        rootProject.layout.projectDirectory.dir(".omo/start-work/attempts/lumin-graphics-prism-rhi-migration-20260730").asFile.absolutePath
    ).get())
    timeout.set(Duration.ofMinutes(2))
}

registerSmoke("gl41Smoke", "gl41")
registerSmoke("gl46Smoke", "gl46")
registerSmoke("vulkanSmoke", "vulkan")
registerSmoke("wrongContextSmoke", "wrong-context")
registerSmoke("missingShaderSmoke", "missing-shader")
