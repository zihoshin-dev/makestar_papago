plugins {
    id("org.springframework.boot") version "3.4.2"
    id("io.spring.dependency-management") version "1.1.7"
    kotlin("jvm") version "1.9.25"
    kotlin("plugin.spring") version "1.9.25"
    kotlin("plugin.jpa") version "1.9.25"
    id("io.gitlab.arturbosch.detekt") version "1.23.7"
    id("org.jlleitschuh.gradle.ktlint") version "12.1.2"
}

group = "ai.makestar"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    runtimeOnly("com.h2database:h2")
    runtimeOnly("org.postgresql:postgresql")
    // Testing - Kotest
    testImplementation("io.kotest:kotest-runner-junit5:5.9.1")
    testImplementation("io.kotest:kotest-assertions-core:5.9.1")
    testImplementation("io.kotest:kotest-property:5.9.1")

    // Testing - MockK
    testImplementation("io.mockk:mockk:1.13.14")
    testImplementation("com.ninja-squad:springmockk:4.0.2")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // OpenAPI Documentation
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.0")

    // Error Tracking
    implementation("io.sentry:sentry-spring-boot-starter:8.2.0")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// Detekt Configuration
detekt {
    config.setFrom(files("${rootProject.projectDir}/detekt.yml"))
    buildUponDefaultConfig = true
    allRules = false
}

// ktlint Configuration
ktlint {
    version.set("1.5.0")
    android.set(false)
    outputToConsole.set(true)
}
