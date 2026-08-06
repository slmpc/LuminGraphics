apply(plugin = "java-test-fixtures")

dependencies {
    api(libs.prism.rhi.core)
    testImplementation(libs.archunit.junit5)
    testRuntimeOnly(project(":lumin-graphics-render"))
    testRuntimeOnly(project(":lumin-graphics-text"))
    testRuntimeOnly(project(":lumin-graphics-ui"))
}
