plugins {
    kotlin("jvm") version "1.9.22"
    `java-library`
    `maven-publish`
}

group = "com.github.godwinjk"
version = "1.0.1"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}

java {
    withSourcesJar()
    withJavadocJar()
}

tasks.jar {
    manifest {
        attributes(
            "Implementation-Title" to "JsonAutoRepair",
            "Implementation-Version" to project.version,
            "Implementation-Vendor" to "godwin"
        )
    }
}

// **Key fix for JitPack**
publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            // Use the correct component
            from(components["kotlin"])
            artifactId = "JsonAutoRepair"
        }
    }
}