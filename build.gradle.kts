plugins {
	java
	id("org.springframework.boot") version "3.5.0"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "com.houkago"
version = "0.0.1-SNAPSHOT"
description = "Houkago backend API and content sync service"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml")
	implementation("org.flywaydb:flyway-core")
	implementation("org.flywaydb:flyway-mysql")
	runtimeOnly("com.mysql:mysql-connector-j")
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.springframework.boot:spring-boot-testcontainers")
	testImplementation("org.testcontainers:testcontainers:2.0.5")
	testImplementation("org.testcontainers:testcontainers-junit-jupiter:2.0.5")
	testImplementation("org.testcontainers:testcontainers-mysql:2.0.5")
	testImplementation("com.github.docker-java:docker-java-api:3.7.1")
	testImplementation("com.github.docker-java:docker-java-transport-zerodep:3.7.1")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
	useJUnitPlatform()
}
