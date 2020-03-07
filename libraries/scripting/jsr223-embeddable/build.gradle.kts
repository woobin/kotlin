
description = "Kotlin Scripting JSR-223 support"

plugins {
    java
}

dependencies {
    embedded(project(":kotlin-scripting-jsr223")) { isTransitive = false }
    runtime(project(":kotlin-script-runtime"))
    runtime(kotlinStdlib())
    runtime(project(":kotlin-scripting-common"))
    runtime(project(":kotlin-scripting-jvm"))
    runtime(project(":kotlin-scripting-jvm-host-embeddable"))
    runtime(project(":kotlin-compiler-embeddable"))
    runtime(project(":kotlin-scripting-compiler-embeddable"))
}

sourceSets {
    "main" {}
    "test" {}
}

publish()

runtimeJar(relocateDefaultJarToEmbeddableCompiler())
sourcesJar()
javadocJar()
