plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Pure Kotlin, no Android: the ELM327 protocol, OBD decoding, the Qashqai's fault-code and parts
// knowledge and the health checks. Tested on the JVM (`gradle :core:test`), locally and in CI.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.junit)
}
