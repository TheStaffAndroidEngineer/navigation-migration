plugins { alias(libs.plugins.kotlinJvm) }
kotlin {
    jvmToolchain(27)
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_26) }
}
dependencies {
    testImplementation(libs.junitJupiter)
    testRuntimeOnly(libs.junitPlatformLauncher)
}
tasks.test { useJUnitPlatform { excludeTags("exercise") } }
tasks.register<Test>("exerciseTest") {
    description = "Run the intentionally defective report-picker starter"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform { includeTags("exercise") }
}

java {
    sourceCompatibility = JavaVersion.VERSION_26
    targetCompatibility = JavaVersion.VERSION_26
}
tasks.withType<JavaCompile>().configureEach { options.release.set(26) }
