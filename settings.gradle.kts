import org.gradle.api.initialization.resolve.RepositoriesMode
import java.io.File

rootProject.name = "lumin-graphics"

include(
    "lumin-graphics-core",
    "lumin-graphics-render",
    "lumin-graphics-text",
    "lumin-graphics-ui",
    "lumin-graphics-bom",
    "lumin-graphics-demo",
)

val configuredPrismRepository = providers.systemProperty("maven.repo.local").orNull
val prismRepositoryDirectory = when {
    configuredPrismRepository == null -> rootDir.resolve(".missing-prism-repository")
    configuredPrismRepository.isBlank() -> error("maven.repo.local must not be blank")
    !File(configuredPrismRepository).isAbsolute -> error("maven.repo.local must be an absolute path")
    else -> File(configuredPrismRepository)
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        exclusiveContent {
            forRepository {
                maven {
                    name = "isolatedPrism"
                    url = prismRepositoryDirectory.toURI()
                    metadataSources {
                        mavenPom()
                        artifact()
                    }
                }
            }
            filter {
                includeGroup("com.github.slmpc.prismrhi")
            }
        }
        mavenCentral {
            content {
                excludeGroup("com.github.slmpc.prismrhi")
            }
        }
    }
}
