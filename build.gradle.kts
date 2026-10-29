plugins {
    kotlin("jvm") version "2.4.21"
    `maven-publish`
    id("com.github.davidmc24.gradle.plugin.avro") version "1.9.1"
}

group = "com.cgf.contracts"
version = "2.4.0"


java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Not 1.12.2: it adds a ClassSecurityValidator that refuses to load generated classes outside a
    // trusted package list, so KafkaAvroDeserializer (specific.avro.reader=true) fails with
    // SecurityException on every contract class. 1.12.1 is what the services resolve through Confluent.
    // OrderStatusContractTest fails on 1.12.2, which is the guard. Moving past 1.12.1 needs
    // org.apache.avro.SERIALIZABLE_PACKAGES set for com.cgf in every consumer first.
    implementation("org.apache.avro:avro:1.12.1")
    testImplementation(kotlin("test"))
}

avro {
    setCreateSetters(true)
    fieldVisibility.set("PRIVATE")
    outputCharacterEncoding.set("UTF-8")
    stringType.set("String")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}


publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/cgf-studio/event-contracts")
            credentials {
                username = project.findProperty("gpr.user") as String? ?: System.getenv("USERNAME")
                password = project.findProperty("gpr.key") as String? ?: System.getenv("TOKEN")
            }
        }
    }
    publications {
        register<MavenPublication>("gpr") {
            from(components["java"])
        }
    }
}
