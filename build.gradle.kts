plugins {
    kotlin("jvm") version "1.9.25"
    kotlin("plugin.spring") version "1.9.25"
    id("org.springframework.boot") version "3.5.8"
    id("io.spring.dependency-management") version "1.1.7"
    kotlin("plugin.jpa") version "1.9.25"
    id("com.gorylenko.gradle-git-properties") version "2.5.4"
}

group = "com.matchalab"

version = "0.0.1-SNAPSHOT"

description =
    "This is api server for web app \"Subscription Killer\". It supports Next.js frontend. This secure restful api backend manages multi-account Google authentication, provides real-time status updates via STOMP WebSockets, and processes email data using the Gmail API."

java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }

repositories {
    mavenCentral()
    maven { url = uri("https://repo.spring.io/milestone") }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("org.springframework.session:spring-session-jdbc")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.postgresql:postgresql")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.springframework.security:spring-security-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.springframework.boot:spring-boot-starter-webflux")


    // https://mvnrepository.com/artifact/org.assertj/assertj-core
    testImplementation("org.assertj:assertj-core:3.27.6")

    // https://mvnrepository.com/artifact/com.amazonaws.serverless/aws-serverless-java-container-springboot3
    // !Do not make this runtimeOnly() prevent to exclude from test environment: it prevents
    // compile.
    implementation("com.amazonaws.serverless:aws-serverless-java-container-springboot3:2.1.5")

    // https://docs.aws.amazon.com/lambda/latest/dg/java-package.html#java-package-libraries
    implementation("com.amazonaws:aws-lambda-java-core:1.4.0")
    implementation("com.amazonaws:aws-lambda-java-events:3.16.1")
    runtimeOnly("com.amazonaws:aws-lambda-java-log4j2:1.6.0")

    // https://mvnrepository.com/artifact/com.google.cloud/libraries-bom
    implementation(platform("com.google.cloud:libraries-bom:26.72.0"))

    implementation("com.google.api-client:google-api-client")

    // https://github.com/oshai/kotlin-logging?tab=readme-ov-file#gradle
    implementation("io.github.oshai:kotlin-logging-jvm:7.0.3")

    // Google Api Gmail
    implementation("com.google.api-client:google-api-client:2.0.0")
    implementation("com.google.oauth-client:google-oauth-client-jetty:1.34.1")
    implementation("com.google.apis:google-api-services-gmail:v1-rev20220404-2.0.0")
    implementation("com.google.auth:google-auth-library-oauth2-http")

    // kotlinx-coroutines-core
    // https://mvnrepository.com/artifact/org.jetbrains.kotlinx/kotlinx-coroutines-core
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

    // https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-test/
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")

    // https://mvnrepository.com/artifact/org.jetbrains.kotlinx/kotlinx-coroutines-reactor
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")

    // https://mvnrepository.com/artifact/io.mockk/mockk
    testImplementation("io.mockk:mockk:1.14.7")

    // https://mvnrepository.com/artifact/com.ninja-squad/springmockk
    testImplementation("com.ninja-squad:springmockk:4.0.2")

    // AI
    // https://mvnrepository.com/artifact/org.springframework.ai/spring-ai-bom
//    implementation(platform("org.springframework.ai:spring-ai-bom:2.0.0-M1"))
    // https://mvnrepository.com/artifact/org.springframework.ai/spring-ai-bom
    implementation(platform("org.springframework.ai:spring-ai-bom:1.1.2"))
    implementation("org.springframework.ai:spring-ai-starter-model-google-genai")

    // https://mvnrepository.com/artifact/io.projectreactor/reactor-test
    testImplementation("io.projectreactor:reactor-test:3.8.1")

    testImplementation("org.awaitility:awaitility:4.2.2")

    // Micrometer Observation
    implementation("org.springframework.boot:spring-boot-starter-aop")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-observation")
    implementation("io.micrometer:micrometer-tracing-bridge-brave")
    implementation("io.zipkin.reporter2:zipkin-reporter-brave")

}

kotlin { compilerOptions { freeCompilerArgs.addAll("-Xjsr305=strict") } }

allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

tasks.withType<Test> {
    useJUnitPlatform {
        // 'includeExternal' 파라미터가 없으면 해당 태그 제외
        if (!project.hasProperty("includeGcp")) {
            excludeTags("gcp")
        }
        if (!project.hasProperty("includeAi")) {
            excludeTags("ai")
        }
    }
    systemProperty("spring.profiles.active", System.getProperty("spring.profiles.active", "dev,test,gcp"))
}

tasks.register<Zip>("buildZip") {

    dependsOn("generateGitProperties")

    from(tasks.compileJava)
    from(tasks.processResources)

    into("lib") {
        from(configurations.runtimeClasspath) // Use runtimeClasspath for a runnable zip
    }
}

tasks.processResources {
    dependsOn("generateGitProperties")
}

tasks.withType<JavaExec> {
    jvmArgs("-Dfile.encoding=UTF-8")
}

tasks.build {
    dependsOn(tasks.getByName("buildZip"))
}

gitProperties {
    failOnNoGitDirectory = false
}

// TODO : Use layers for dependencies.
// https://docs.aws.amazon.com/lambda/latest/dg/java-package.html#java-package-layers
