plugins {
    kotlin("jvm") version "1.6.10"
    id("com.github.johnrengelman.shadow") version "7.1.2"
}

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation("com.tencent.devops.ci-plugins:java-plugin-sdk:1.1.3")
    implementation("com.tencent.bk.repo:api-repository:1.0.0") {
        exclude("io.swagger")
        exclude("org.apache.commons")
    }
}

tasks.shadowJar {
    archiveBaseName.set("AcrossProjectDistribution")
    archiveClassifier.set("")
    archiveVersion.set("")
}

tasks.register<Copy>("copyTaskJson") {
    from("task.json")
    into("$buildDir/libs")
}

tasks.register<Zip>("package") {
    dependsOn(tasks.shadowJar)
    dependsOn(tasks.named("copyTaskJson"))

    destinationDirectory.set(layout.buildDirectory.dir("out"))
    archiveFileName.set("AcrossProjectDistribution.zip")
    from(layout.buildDirectory.dir("libs"))
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "com.tencent.bk.devops.atom.AtomRunner"
    }
}
