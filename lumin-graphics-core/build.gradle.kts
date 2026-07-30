val prismGroup = rootProject.extra["prismGroup"] as String
val prismVersion = rootProject.extra["prismVersion"] as String

apply(plugin = "java-test-fixtures")

dependencies {
    api("$prismGroup:prism-rhi-core:$prismVersion")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.4.1")
    testRuntimeOnly(project(":lumin-graphics-render"))
    testRuntimeOnly(project(":lumin-graphics-text"))
    testRuntimeOnly(project(":lumin-graphics-ui"))
}
