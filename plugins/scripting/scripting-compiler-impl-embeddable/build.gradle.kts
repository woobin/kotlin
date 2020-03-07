
plugins {
    java
}

description = "Kotlin Compiler Infrastructure for Scripting for embeddable compiler"

dependencies {
    embedded(project(":kotlin-scripting-compiler-impl")) { isTransitive = false }
    runtime(project(":kotlin-scripting-common"))
    runtime(project(":kotlin-scripting-jvm"))
    runtime(kotlinStdlib())
    runtime(commonDep("org.jetbrains.kotlinx", "kotlinx-coroutines-core")) { isTransitive = false }
}

publish()

runtimeJar(relocateDefaultJarToEmbeddableCompiler())
sourcesJar()
javadocJar()
