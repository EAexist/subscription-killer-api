//https://kotlinlang.org/docs/kapt.html#annotation-processor-arguments

import groovy.json.JsonSlurper
import org.springframework.boot.gradle.tasks.bundling.BootBuildImage
import java.net.HttpURLConnection
import java.net.URI

abstract class DownloadTestDataTask : DefaultTask() {
    @get:Input
    @get:Optional
    abstract val hfToken: Property<String>

    @get:Input
    @get:Optional
    abstract val hfBase: Property<String>

    @get:Input
    @get:Optional
    abstract val owner: Property<String>

    @get:Input
    @get:Optional
    abstract val repo: Property<String>

    @get:OutputDirectory
    @get:Optional
    abstract val outputDir: DirectoryProperty

    @get:Input
    @get:Optional
    abstract val enabledByProperty: Property<Boolean?>

    @TaskAction
    fun download() {
        if (!(enabledByProperty.getOrElse(false) ?: false)) {
            logger.lifecycle("⏭️ Skipping download (Property 'syncDataset' not found).")
            return
        }

        if (!hfToken.isPresent) {
            logger.lifecycle("⏭️ Skipping download (Property 'hfToken' not found).")
            return
        }

        val token = hfToken.get()
        val base = "${hfBase.get()}/${owner.get()}/${repo.get()}/resolve/main/data"
        val root = outputDir.get().asFile.apply { mkdirs() }

        logger.lifecycle("🚀 Flag detected. Starting dataset sync...")

        val companiesUrl = "$base/reference/companies.json"
        val companiesFile = File(root, "reference/companies.json").apply { parentFile.mkdirs() }
        downloadFile(companiesUrl, companiesFile, token)

        val datasets = listOf("emails", "templates")

        datasets.forEach { folder ->
            // Download pointer to check for updates
            val targetDir = File(root, folder).apply { mkdirs() }
            val pointerUrl = "$base/$folder/latest.json"
            val pointerFile = File(targetDir, "$folder-latest.json")
            downloadFile(pointerUrl, pointerFile, token)

            // Parse the pointer
            val json = JsonSlurper().parseText(pointerFile.readText()) as Map<*, *>
            val relativePath = json["relative_path"] as String
            val fileName = relativePath.substringAfterLast("/")

            val dataFile = File(targetDir, fileName)

            // Always download fresh data (override existing)
            logger.info("Downloading fresh data for $folder. Version: $relativePath")
            val dataUrl = "$base/$folder/$relativePath"
            downloadFile(dataUrl, dataFile, token)
        }
    }

    private fun downloadFile(urlString: String, dest: File, token: String) {
        val url = URI.create(urlString).toURL()
        val conn = url.openConnection() as HttpURLConnection
        try {
            token?.let { conn.setRequestProperty("Authorization", "Bearer $it") }

            val code = conn.responseCode
            if (code != 200) {
                // Read the error stream to see the actual message from HF
                val errorBody = conn.errorStream?.bufferedReader()?.readText() ?: "No error body"
                throw GradleException(
                    "Download failed for $urlString\n" +
                            "Status: $code ${conn.responseMessage}\n" +
                            "Reason: $errorBody"
                )
            }

            conn.inputStream.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
        } finally {
            conn.disconnect()
        }
    }
}

plugins {
    kotlin("jvm") version "1.9.25"
    kotlin("plugin.spring") version "1.9.25"
    id("org.springframework.boot") version "3.5.8"
    id("io.spring.dependency-management") version "1.1.7"
    kotlin("plugin.jpa") version "1.9.25"
    id("com.gorylenko.gradle-git-properties") version "2.5.4"
    kotlin("kapt") version "1.9.25"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("maven-publish")
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
    implementation("org.springframework.boot:spring-boot-starter-web") {
        exclude(group = "org.springframework.boot", module = "spring-boot-starter-tomcat")
    }
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("org.springframework.session:spring-session-jdbc")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.postgresql:postgresql")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.springframework.security:spring-security-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.springframework.boot:spring-boot-starter-webflux")


    // https://mvnrepository.com/artifact/org.assertj/assertj-core
    testImplementation("org.assertj:assertj-core:3.27.6")

    // Deprecated: aws api gateway & aws-serverless -> aws lambda web adapter. Why? To stream sse (api gateway timeouts at 30 seconds)
    // https://mvnrepository.com/artifact/com.amazonaws.serverless/aws-serverless-java-container-springboot3
    // !Do not make this runtimeOnly() prevent to exclude from test environment: it prevents
    // compile.
//    implementation("com.amazonaws.serverless:aws-serverless-java-container-springboot3:2.1.5")

    // Processes Java files
    annotationProcessor("org.springframework:spring-context-indexer:7.0.2")
    // Processes Kotlin files
    kapt("org.springframework:spring-context-indexer:7.0.2")


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

    // Source: https://mvnrepository.com/artifact/com.knuddels/jtokkit
    implementation("com.knuddels:jtokkit:1.1.0")

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

    testImplementation("org.awaitility:awaitility-kotlin:4.2.2")

    /* Observations */
    /* Micrometer*/
    implementation("org.springframework.boot:spring-boot-starter-aop")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-observation")
    implementation("io.micrometer:micrometer-commons")
    implementation("io.opentelemetry:opentelemetry-extension-kotlin")
    // Source: https://mvnrepository.com/artifact/io.micrometer/micrometer-observation-test
    testImplementation("io.micrometer:micrometer-observation-test")


//    implementation("io.zipkin.reporter2:zipkin-reporter-brave")
//    runtimeOnly("io.micrometer:micrometer-registry-prometheus")

    /* langfuse */
    implementation("io.micrometer:micrometer-tracing-bridge-otel")
    implementation("io.opentelemetry:opentelemetry-exporter-otlp")

    /* TestContainer */
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")

    /* Flyway */
    // https://mvnrepository.com/artifact/org.flywaydb/flyway-core
    implementation("org.flywaydb:flyway-core:11.17.0")
    // https://mvnrepository.com/artifact/org.flywaydb/flyway-database-postgresql
    runtimeOnly("org.flywaydb:flyway-database-postgresql:11.17.0")

    /* Context Propagation */
    implementation("io.micrometer:context-propagation")
}

kotlin { compilerOptions { freeCompilerArgs.addAll("-Xjsr305=strict") } }

allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

tasks.withType<Test> {

    /* Live Logging */
    testLogging {
        showStandardStreams = true
        events("started", "skipped", "failed", "passed")
    }

    environment(System.getenv())

    val activeProfile = project.findProperty("spring.profiles.active")?.toString()
        ?: System.getenv("SPRING_PROFILES_ACTIVE")
        ?: "test" // fallback

    environment("SPRING_PROFILES_ACTIVE", activeProfile)

    val tagsProperty = project.findProperty("includeTags") as String?
    useJUnitPlatform {
        if (!tagsProperty.isNullOrBlank()) {
            tagsProperty.split(",").forEach { tag ->
                includeTags(tag.trim())
            }
        } else {
            excludeTags("gmail", "ai", "prompt-eval")
        }
    }

    doFirst {
        logger.lifecycle("Running tests with Profile: [$activeProfile] and Tags: [$tagsProperty]")
    }
}

tasks.register<Zip>("buildLambdaWebAdapterZip") {
    dependsOn("generateGitProperties")
    dependsOn("bootJar")

    into("lib") {
        from(tasks.bootJar)
//        from(tasks.jar)
//        from(configurations.runtimeClasspath)
    }

    from("$projectDir/scripts/run.sh") {
        filePermissions {
            // This is the clean, type-safe way to set 755
            user {
                read = true
                write = true
                execute = true
            }
            group {
                read = true
                execute = true
            }
            other {
                read = true
                execute = true
            }
        }
    }
}

tasks.jar {
    enabled = true
    archiveClassifier.set("")
}

tasks.build {
    dependsOn(tasks.getByName("buildLambdaWebAdapterZip"))
}

tasks.withType<JavaExec> {
    jvmArgs("-Dfile.encoding=UTF-8")
}

//gitProperties {
//    failOnNoGitDirectory = false
//    customProperty("git.commit.id", project.findProperty("GIT_COMMIT")?.toString() ?: "unknown")
//    customProperty("git.branch", project.findProperty("GIT_TAG")?.toString() ?: "unknown")
//}

// TODO : Use layers for dependencies.
// https://docs.aws.amazon.com/lambda/latest/dg/java-package.html#java-package-layers

/* BootBuildIamge */
tasks.withType<BootBuildImage>().configureEach {

    publish.set(true)

    imageName.set(
        project.findProperty("imageName")?.toString()
            ?: "ghcr.io/eaexist/subscription-killer-api:${project.version}"
    )

    environment.set(
        mapOf(
            "BP_OCI_REVISION" to (project.findProperty("GIT_COMMIT")?.toString() ?: "unknown"),
            "BP_OCI_REF_NAME" to (project.findProperty("GIT_TAG")?.toString() ?: "untagged")
        )
    )

    // fix local daemon usage error in github workflow environment.
    docker {
        publishRegistry {
            url.set("https://ghcr.io")
            username.set(project.findProperty("docker.publish.username")?.toString() ?: "")
            password.set(project.findProperty("docker.publish.password")?.toString() ?: "")
        }
    }
}

/* Maven Publish To Custom Repo (build/repo).
*  Command: ./gradlew publish */
publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])

            groupId = project.group.toString()
            artifactId = "subscription-killer-api"
            version = project.version.toString()

            pom {
                name.set("subscription-killer-api")
            }
        }
    }

    repositories {
        maven {
            name = "workspace-repo"
            url = rootProject.layout.buildDirectory.dir("repo").get().asFile.toURI()
        }
    }
}

tasks.withType<org.gradle.api.publish.maven.tasks.AbstractPublishToMaven>().configureEach {
    notCompatibleWithConfigurationCache("maven-publish is not fully CC compatible")
}
tasks.withType<org.gradle.api.publish.maven.tasks.GenerateMavenPom>().configureEach {
    notCompatibleWithConfigurationCache("maven-publish uses Project internals")
}

/*
 * Dataset
 */

//tasks.withType<BootRun> {
//    val activeProfiles = System.getenv("SPRING_PROFILES_ACTIVE") ?: ""
//    if (activeProfiles.contains("dev")) {
//        val dataset = sourceSets["dataset"]
//        classpath += dataset.output + dataset.runtimeClasspath
//    }
//}

tasks.register<JavaExec>("benchmark") {
    group = "application"
    mainClass.set("com.matchalab.subscription_killer_api")
    classpath = sourceSets["main"].runtimeClasspath + sourceSets["test"].runtimeClasspath
    args("--spring.profiles.active=benchmark")
}
//
//val env = Properties().apply {
//    val envFile = project.file(".env.dev")
//    if (envFile.exists()) {
//        envFile.inputStream().use { load(it) }
//    }
//
//    // Clean up quotes
//    stringPropertyNames().forEach { name ->
//        val value = getProperty(name)
//        if (value != null) {
//            // Removes leading/trailing double or single quotes
//            val cleanValue = value.trim().removeSurrounding("\"").removeSurrounding("'")
//            setProperty(name, cleanValue)
//        }
//    }
//}

val syncDatasetProvider = providers.gradleProperty("syncDataset")

val downloadTestData = tasks.register<DownloadTestDataTask>("downloadTestData") {
    group = "verification"

    outputDir.set(layout.buildDirectory.dir("generated/dataset/data"))

    hfToken.set(providers.environmentVariable("HF_TOKEN"))
    hfBase.set(providers.environmentVariable("HF_BASE_URL"))
    owner.set(providers.environmentVariable("HF_OWNER"))
    repo.set(providers.environmentVariable("HF_REPO"))
    enabledByProperty.set(providers.gradleProperty("syncDataset").isPresent)
}

sourceSets {
    main {
        resources {
            srcDir(layout.buildDirectory.dir("generated/dataset"))
        }
    }
}

tasks.processResources {
    dependsOn(downloadTestData)
    dependsOn("generateGitProperties")
}

tasks.test {
    maxHeapSize = "1024m"
}

kapt {
    useBuildCache = true
}


