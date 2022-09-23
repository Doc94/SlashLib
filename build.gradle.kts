/*
 * This file was generated by the Gradle 'init' task.
 */

plugins {
    java
    signing
    `maven-publish`
}

repositories {
    mavenLocal()
    maven {
        url = uri("https://repo.maven.apache.org/maven2/")
    }
}

dependencies {
    implementation("com.discord4j:discord4j-core:3.2.3")
}

group = "net.exploitables"
version = "1.2.4"
description = "SlashLib"
java.sourceCompatibility = JavaVersion.VERSION_1_8

java {
    withSourcesJar()
    withJavadocJar()
}

publishing {
    publications.create<MavenPublication>("maven") {
        from(components["java"])
    }
    repositories {
        maven {
            @Suppress("SpellCheckingInspection")
            name = "ExploitablesReposiliteReleases"
            url = uri("https://reposilite.exploitables.net/releases")
            credentials(PasswordCredentials::class)
            authentication {
                create<BasicAuthentication>("basic")
            }
        }
    }
}

tasks.withType<JavaCompile>() {
    options.encoding = "UTF-8"
}
