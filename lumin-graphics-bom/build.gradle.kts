plugins {
    `java-platform`
    `maven-publish`
}

val prismGroup = rootProject.extra["prismGroup"] as String
val prismVersion = rootProject.extra["prismVersion"] as String

javaPlatform {
    allowDependencies()
}

dependencies {
    constraints {
        api(project(":lumin-graphics-core"))
        api(project(":lumin-graphics-render"))
        api(project(":lumin-graphics-text"))
        api(project(":lumin-graphics-ui"))

        api("$prismGroup:prism-rhi-core:$prismVersion")
        api("$prismGroup:prism-rhi-backend-opengl-common:$prismVersion")
        api("$prismGroup:prism-rhi-backend-opengl41:$prismVersion")
        api("$prismGroup:prism-rhi-backend-opengl46:$prismVersion")
        api("$prismGroup:prism-rhi-backend-vulkan:$prismVersion")
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
