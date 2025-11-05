plugins {
    id("java")
    id("application")
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "de.professorsam.lecred"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.squareup.okhttp3:okhttp:5.2.1")
    implementation("org.json:json:20250517")
    implementation("io.javalin:javalin:6.7.0")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.8.1")
    testImplementation("org.testcontainers:junit-jupiter:1.20.2")
    testImplementation("org.testcontainers:testcontainers:1.20.2")
    testImplementation("org.testcontainers:mockserver:1.20.2")
    testImplementation("org.wiremock.integrations.testcontainers:wiremock-testcontainers-module:1.0-alpha-15")
    testImplementation("com.github.lookfirst:sardine:5.13")
}

tasks.test {
    useJUnitPlatform()
    dependsOn("buildDockerImage")
}

tasks.compileJava {
    sourceCompatibility = JavaVersion.VERSION_21.toString()
    targetCompatibility = JavaVersion.VERSION_21.toString()
    options.encoding = "UTF-8"
}

tasks.register("buildDockerImage") {
    dependsOn("shadowJar")
    group = "docker"
    doLast {
        exec {
            commandLine("docker", "build", ".", "-t", "ghcr.io/professorsam/lecrec:1.2")
        }
    }
}


application {
    mainClass = "de.professorsam.lecrec.LecRec"
}
