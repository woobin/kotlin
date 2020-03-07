
plugins {
    java
}

description = "Kotlin Scripting Compiler Plugin for embeddable compiler"

dependencies {
    embedded(project(":kotlin-scripting-compiler")) { isTransitive = false }
    runtime(project(":kotlin-scripting-compiler-impl-embeddable"))
    runtime(kotlinStdlib())
}

publish()

runtimeJar(relocateDefaultJarToEmbeddableCompiler())

sourcesJar()
javadocJar()
