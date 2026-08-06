import org.gradle.api.initialization.resolve.RepositoriesMode

rootProject.name = "lumin-graphics"

include(
    "lumin-graphics-core",
    "lumin-graphics-render",
    "lumin-graphics-text",
    "lumin-graphics-ui",
    "lumin-graphics-bom",
    "lumin-graphics-demo",
)

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenLocal()
        maven("https://slmpc.github.io/maven-repository/")
        mavenCentral {
            content {
                excludeGroup("com.github.slmpc.prismrhi")
            }
        }
    }
}
