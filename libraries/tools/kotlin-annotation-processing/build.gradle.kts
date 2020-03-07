description = "Kapt - Annotation processing for Kotlin"

plugins {
    kotlin("jvm")
}

dependencies {
    compile(kotlinStdlib())
    embedded(project(":kotlin-annotation-processing")) { isTransitive = false }
    runtime(projectRuntimeJar(":kotlin-compiler-embeddable"))
}

projectTest(parallel = true) {
    workingDir = projectDir
}

publish()

runtimeJar(relocateDefaultJarToEmbeddableCompiler())

sourcesJar()
javadocJar()
