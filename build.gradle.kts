plugins {
  kotlin("jvm") version "1.9.25"
  kotlin("plugin.spring") version "1.9.25"
  id("org.springframework.boot") version "3.5.8"
  id("io.spring.dependency-management") version "1.1.7"
  kotlin("plugin.jpa") version "1.9.25"
}

group = "com.matchalab"
version = "0.0.1-SNAPSHOT"
description = "This is api server for web app \"Subscription Killer\". It supports Next.js frontend. This secure restful api backend manages multi-account Google authentication, provides real-time status updates via STOMP WebSockets, and processes email data using the Gmail API."

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("org.springframework.boot:spring-boot-starter-websocket")
	implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation("org.postgresql:postgresql")
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
	testImplementation("org.springframework.security:spring-security-test")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // https://mvnrepository.com/artifact/com.amazonaws.serverless/aws-serverless-java-container-springboot3
    // !Do not make this runtimeOnly() prevent to exclude from test environment: it prevents compile. 
    implementation("com.amazonaws.serverless:aws-serverless-java-container-springboot3:2.1.5")

    // https://docs.aws.amazon.com/lambda/latest/dg/java-package.html#java-package-libraries
    implementation("com.amazonaws:aws-lambda-java-core:1.4.0")
    implementation("com.amazonaws:aws-lambda-java-events:3.16.1")
    runtimeOnly("com.amazonaws:aws-lambda-java-log4j2:1.6.0")    
}

kotlin {
	compilerOptions {
		freeCompilerArgs.addAll("-Xjsr305=strict")
	}
}


allOpen {
	annotation("jakarta.persistence.Entity")
	annotation("jakarta.persistence.MappedSuperclass")
	annotation("jakarta.persistence.Embeddable")
}

tasks.withType<Test> {
	useJUnitPlatform()
}

tasks.register<Zip>("buildZip") {
    from(tasks.compileJava)
    from(tasks.processResources)

    into("lib") {
        from(configurations.compileClasspath)
    }
}

tasks.build {
    dependsOn(tasks.getByName("buildZip"))
}

// TODO : Use layers for dependencies. https://docs.aws.amazon.com/lambda/latest/dg/java-package.html#java-package-layers