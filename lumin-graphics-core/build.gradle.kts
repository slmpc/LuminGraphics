val prismGroup = rootProject.extra["prismGroup"] as String
val prismVersion = rootProject.extra["prismVersion"] as String

dependencies {
    api("$prismGroup:prism-rhi-core:$prismVersion")
}
