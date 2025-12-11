plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
    application
}

group = "com.smartcard"
version = "1.0.0"

application {
    mainClass.set("com.smartcard.ApplicationKt")
    
    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

repositories {
    mavenCentral()
}

dependencies {
    // Ktor Server
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.config.yaml)
    implementation(libs.ktor.server.call.logging)
    
    // Database - Exposed ORM
    implementation(libs.exposed.core)
    implementation(libs.exposed.dao)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.java.time)
    implementation(libs.mysql.connector)
    implementation("com.zaxxer:HikariCP:5.1.0")
    
    // Security
    implementation(libs.bcrypt)
    implementation("org.bouncycastle:bcprov-jdk15on:1.70")
    
    // Logging
    implementation(libs.logback.classic)
    
    // Serialization
    implementation(libs.kotlinx.serialization.json)
    
    // Testing
    testImplementation(libs.ktor.server.tests)
    testImplementation(libs.kotlin.test)
}

kotlin {
    jvmToolchain(17)
}
