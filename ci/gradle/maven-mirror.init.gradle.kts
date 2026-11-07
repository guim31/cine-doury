// Cloud sessions only: Maven Central (repo.maven.apache.org) answers 429 after a few hundred requests.
// This init script points every Maven Central repository (settings, plugins, build-logic) at Google's
// mirror of Maven Central, and Robolectric's own downloads too. The CI does not use it.
// Install: mkdir -p ~/.gradle/init.d && cp ci/gradle/maven-mirror.init.gradle.kts ~/.gradle/init.d/
val mirror = "https://maven-central.storage-download.googleapis.com/maven2/"

fun RepositoryHandler.useMirror() {
    withType(MavenArtifactRepository::class.java).configureEach {
        val host = url.host.orEmpty()
        if (host == "repo.maven.apache.org" || host == "repo1.maven.org") setUrl(mirror)
    }
}

beforeSettings {
    pluginManagement.repositories.useMirror()
    dependencyResolutionManagement.repositories.useMirror()
}

allprojects {
    repositories.useMirror()
    buildscript.repositories.useMirror()
    tasks.withType(Test::class.java).configureEach {
        systemProperty("robolectric.dependency.repo.url", mirror)
    }
}
