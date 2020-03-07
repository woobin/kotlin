
description = "Kotlin Daemon (for using with embeddable compiler)"

plugins {
    java
}

dependencies {
    embedded(project(":kotlin-daemon")) { isTransitive = false }
}

publish()

runtimeJar(relocateDefaultJarToEmbeddableCompiler())

sourcesJar()
javadocJar()
