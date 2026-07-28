import java.time.LocalDate
import java.time.format.DateTimeFormatter

plugins {
    `java-library`
    id("com.vanniktech.maven.publish") version "0.36.0"
}

description = "Combat for Minestom"
group = "rocks.minestom"

java.toolchain.languageVersion = JavaLanguageVersion.of(25)

val minestomVersion = "2026.07.22-26.2"

mavenPublishing {
    val mcVersion = minestomVersion.split("-")[1]
    val date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy.MM.dd"))
    val version = "$date-$mcVersion"
    coordinates(project.group.toString(), project.name, version)

    publishToMavenCentral()

    if (project.hasProperty("signing.keyId") || project.hasProperty("signingInMemoryKey")) {
        signAllPublications()
    }

    pom {
        name = project.name
        description = project.description
        url = "https://github.com/vibenilla/pvp"

        licenses {
            license {
                name = "Apache-2.0"
                url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
            }
        }

        developers {
            developer {
                name = "mudkip"
                id = "mudkipdev"
                email = "mudkip@mudkip.dev"
                url = "https://mudkip.dev"
            }
        }

        scm {
            url = "https://github.com/vibenilla/pvp"
            connection = "scm:git:git://github.com/vibenilla/pvp.git"
            developerConnection = "scm:git:ssh://git@github.com/vibenilla/pvp.git"
        }
    }
}

repositories {
    mavenCentral()
}

dependencies {
    compileOnly("net.minestom:minestom:$minestomVersion")
    compileOnly("it.unimi.dsi:fastutil:8.5.12")
    testImplementation("net.minestom:minestom:$minestomVersion")
    testImplementation("net.minestom:testing:$minestomVersion")
    testImplementation("org.junit.jupiter:junit-jupiter:5.13.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    systemProperty("minestom.inside-test", "true")
    failOnNoDiscoveredTests = false
}
