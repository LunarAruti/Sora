plugins {
    id("java")
    application
}

group = "sora"
version = "1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("net.dv8tion:JDA:5.0.0-beta.24")
    implementation("org.slf4j:slf4j-simple:2.0.13")
    implementation("org.json:json:20240303")
}

application {
    mainClass.set("sora.main.Main")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}
