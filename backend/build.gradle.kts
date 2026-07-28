plugins {
	kotlin("jvm") version "2.3.21"
	kotlin("plugin.spring") version "2.3.21"
	id("org.springframework.boot") version "4.1.0"
	id("io.spring.dependency-management") version "1.1.7"
	kotlin("plugin.jpa") version "2.3.21"
}

group = "com.storepilot"
version = "0.0.1-SNAPSHOT"
description = "StorePilot marketplace API"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
}

dependencyManagement {
	imports {
		mavenBom("software.amazon.awssdk:bom:2.29.52")
	}
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-flyway")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	implementation("org.flywaydb:flyway-database-postgresql")
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation("tools.jackson.module:jackson-module-kotlin")
	implementation("com.stripe:stripe-java:29.2.0")
	// aws profile only (S3ReceiptStorageService, SesEmailService) — the
	// default/dev profile never touches these classes, so no local AWS
	// credentials are needed to build or run locally.
	implementation("software.amazon.awssdk:s3")
	implementation("software.amazon.awssdk:ses")
	// NOT aws-profile-gated: Cognito is the authorization server in every
	// environment (local dev points at a real, free Cognito user pool —
	// there's no local/mock auth implementation, unlike S3/SES).
	implementation("software.amazon.awssdk:cognitoidentityprovider")
	// Never called directly — the SDK's ProfileCredentialsProvider needs
	// this on the classpath to resolve a role_arn/source_profile ("assume
	// role") AWS CLI profile, e.g. local dev's AWS_PROFILE=storepilot-dev.
	runtimeOnly("software.amazon.awssdk:sts")
	developmentOnly("org.springframework.boot:spring-boot-devtools")
	runtimeOnly("org.postgresql:postgresql")
	testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
	testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
	testImplementation("org.springframework.boot:spring-boot-starter-flyway-test")
	testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
	compilerOptions {
		freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
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

// config.yml (gitignored local secrets, see application.yml's
// spring.config.import) lives in src/main/resources for easy reference but
// must never end up in build/resources or the packaged jar — it's loaded
// directly off disk, not the classpath, so excluding it here is pure
// defense-in-depth, not required for it to work.
tasks.processResources {
	exclude("config.yml")
}
