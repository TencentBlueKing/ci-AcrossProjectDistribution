plugins {
    kotlin("jvm") version "1.6.10"
    id("com.github.johnrengelman.shadow") version "7.1.2"
}

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation("com.tencent.devops.ci-plugins:java-plugin-sdk:1.1.8")
    implementation("com.tencent.bk.repo:api-repository:1.0.0") {
        exclude("io.swagger")
        exclude("org.apache.commons")
    }
    implementation("com.tencent.bk.repo:api-generic:1.0.0")
}

tasks.shadowJar {
    archiveBaseName.set("AcrossProjectDistribution")
    archiveClassifier.set("")
    archiveVersion.set("")
}

tasks.register<Copy>("copyTaskZhJson") {
    from("task.json")
    into("$buildDir/libs")
}

tasks.register<Copy>("copyTaskEnJson") {
    from("task_en.json")
    into("$buildDir/libs")
    rename("task_en.json", "task.json")
}

tasks.register<Copy>("copyZhResource") {
    from("docs/desc.md")
    from("images/logo.png")
    into("$buildDir/libs/file")
    rename("desc.md", "README.md")
}

tasks.register<Copy>("copyEnResource") {
    from("docs/desc_en.md")
    from("images/logo.png")
    into("$buildDir/libs/file")
    rename("desc_en.md", "README.md")
}

tasks.register<Zip>("packageCN") {
    dependsOn(tasks.clean)
    dependsOn(tasks.shadowJar)
    dependsOn(tasks.named("copyTaskZhJson"))
    dependsOn(tasks.named("copyZhResource"))
    into("AcrossProjectDistribution")
    destinationDirectory.set(layout.buildDirectory.dir("out"))
    archiveFileName.set("AcrossProjectDistribution.zip")
    from(layout.buildDirectory.dir("libs"))
}

tasks.register<Zip>("packageEN") {
    dependsOn(tasks.clean)
    dependsOn(tasks.shadowJar)
    dependsOn(tasks.named("copyTaskEnJson"))
    dependsOn(tasks.named("copyEnResource"))
    into("AcrossProjectDistribution")
    destinationDirectory.set(layout.buildDirectory.dir("out"))
    archiveFileName.set("AcrossProjectDistribution_en.zip")
    from(layout.buildDirectory.dir("libs"))
}

tasks.register("package") {
    dependsOn(tasks.named("packageCN"))
    dependsOn(tasks.named("packageEN"))
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "com.tencent.bk.devops.atom.AtomRunner"
    }
}
