import org.gradle.api.tasks.compile.JavaCompile

dependencies {
    api(project(":lumin-graphics-text"))
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}
