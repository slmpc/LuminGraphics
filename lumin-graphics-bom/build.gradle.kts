plugins {
    `java-platform`
    `maven-publish`
}

javaPlatform {
    allowDependencies()
}

dependencies {
    constraints {
        api(project(":lumin-graphics-core"))
        api(project(":lumin-graphics-render"))
        api(project(":lumin-graphics-text"))
        api(project(":lumin-graphics-ui"))

        api(libs.prism.rhi.core)
        api(libs.prism.rhi.backend.opengl.common)
        api(libs.prism.rhi.backend.opengl41)
        api(libs.prism.rhi.backend.opengl46)
        api(libs.prism.rhi.backend.vulkan)
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["javaPlatform"])
            artifactId = project.name
        }
    }
}
