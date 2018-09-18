import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    java
    kotlin("jvm") version "1.2.70"
    id("org.jetbrains.kotlin.plugin.jpa") version "1.2.70"
}

group = "uriagassi"
version = "1.0-SNAPSHOT"

repositories {
    mavenLocal()
    mavenCentral()

}

dependencies {
    compile(kotlin("stdlib-jdk8"))
    compile("no.tornado:tornadofx:1.7.17")
    compile("org.hibernate:hibernate-core:5.3.6.Final")

    compile("com.enigmabridge:hibernate4-sqlite-dialect:0.1.2")
    compile("org.xerial:sqlite-jdbc:3.23.1")
    compile("stax:stax:+")
    testCompile("junit", "junit", "4.12")
}

configure<JavaPluginConvention> {
    sourceCompatibility = JavaVersion.VERSION_1_8
}
tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}