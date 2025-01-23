import me.qoomon.gitversioning.commons.GitRefType
import java.util.*

plugins {
    `maven-publish`
    idea
    jacoco
    `java-library`
    signing
    id("org.sonarqube") version "6.0.1.5171"
    id("org.cadixdev.licenser") version "0.6.1"
    id("me.qoomon.git-versioning") version "6.4.4"
    id("io.freefair.javadoc-links") version "8.12"
    id("io.freefair.javadoc-utf-8") version "8.12"
    id("com.github.ben-manes.versions") version "0.51.0"
    id("io.freefair.maven-central.validate-poms") version "8.12"
    id("ru.vyarus.pom") version "3.0.0"
    id("io.codearte.nexus-staging") version "0.30.0"
}

repositories {
    mavenLocal()
    mavenCentral()
    maven(url = "https://jitpack.io")
}

group = "io.github.1c-syntax"
gitVersioning.apply {
    refs {
        considerTagsOnBranches = true
        tag("v(?<tagVersion>[0-9].*)") {
            version = "\${ref.tagVersion}\${dirty}"
        }
        branch(".+") {
            version = "\${ref}-\${commit.short}\${dirty}"
        }
    }

    rev {
        version = "\${commit.short}\${dirty}"
    }
}
val isSnapshot = gitVersioning.gitVersionDetails.refType != GitRefType.TAG

dependencies {
    implementation("org.antlr", "antlr4", "4.13.1")
    implementation("com.github.1c-syntax", "utils", "0.5.1")
    implementation("commons-io", "commons-io", "2.15.1")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    withSourcesJar()
    withJavadocJar()
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.test {
    useJUnitPlatform()

    testLogging {
        events("passed", "skipped", "failed", "standard_error")
    }

    reports {
        html.required.set(true)
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    reports {
        xml.required.set(true)
        xml.outputLocation.set(File("$buildDir/reports/jacoco/test/jacoco.xml"))
    }
}

license {
    header(rootProject.file("license/HEADER.txt"))
    newLine(false)
    ext["year"] = "2018-" + Calendar.getInstance().get(Calendar.YEAR)
    ext["name"] =
        "Alexey Sosnoviy <labotamy@gmail.com>, Nikita Fedkin <nixel2007@gmail.com>, Valery Maximov <maximovvalery@gmail.com>"
    ext["project"] = "BSL Parser Core"
    exclude("**/*.tokens")
    exclude("**/*.interp")
    exclude("**/*.g4")
    exclude("**/*.bsl")
    exclude("**/*.orig")
    exclude("**/*.gitkeep")
}

sonar {
    properties {
        property("sonar.sourceEncoding", "UTF-8")
        property("sonar.host.url", "https://sonarcloud.io")
        property("sonar.organization", "1c-syntax")
        property("sonar.projectKey", "1c-syntax_bsl-parser-core")
        property("sonar.projectName", "BSL Parser Core")
        property("sonar.scm.exclusions.disabled", "true")
        property("sonar.coverage.jacoco.xmlReportPaths", "$buildDir/reports/jacoco/test/jacoco.xml")
    }
}

artifacts {
    archives(tasks["jar"])
    archives(tasks["sourcesJar"])
    archives(tasks["javadocJar"])
}

signing {
    val signingInMemoryKey: String? by project      // env.ORG_GRADLE_PROJECT_signingInMemoryKey
    val signingInMemoryPassword: String? by project // env.ORG_GRADLE_PROJECT_signingInMemoryPassword
    if (signingInMemoryKey != null) {
        useInMemoryPgpKeys(signingInMemoryKey, signingInMemoryPassword)
        sign(publishing.publications)
    }
}

publishing {
    repositories {
        maven {
            name = "sonatype"
            url = if (isSnapshot)
                uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
            else
                uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")

            val sonatypeUsername: String? by project
            val sonatypePassword: String? by project

            credentials {
                username = sonatypeUsername // ORG_GRADLE_PROJECT_sonatypeUsername
                password = sonatypePassword // ORG_GRADLE_PROJECT_sonatypePassword
            }
        }
    }
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            if (isSnapshot && project.hasProperty("simplifyVersion")) {
                version = findProperty("git.ref.slug") as String + "-SNAPSHOT"
            }

            pom {
                description.set("Core library for Collection of parsers for Language 1C (BSL) in ANTLR4 format.")
                url.set("https://github.com/1c-syntax/bsl-parser-core")
                licenses {
                    license {
                        name.set("GNU LGPL 3")
                        url.set("https://www.gnu.org/licenses/lgpl-3.0.txt")
                        distribution.set("repo")
                    }
                }
                developers {
                    developer {
                        id.set("asosnoviy")
                        name.set("Alexey Sosnoviy")
                        email.set("labotamy@gmail.com")
                        url.set("https://github.com/asosnoviy")
                        organization.set("1c-syntax")
                        organizationUrl.set("https://github.com/1c-syntax")
                    }
                    developer {
                        id.set("nixel2007")
                        name.set("Nikita Fedkin")
                        email.set("nixel2007@gmail.com")
                        url.set("https://github.com/nixel2007")
                        organization.set("1c-syntax")
                        organizationUrl.set("https://github.com/1c-syntax")
                    }
                    developer {
                        id.set("theshadowco")
                        name.set("Valery Maximov")
                        email.set("maximovvalery@gmail.com")
                        url.set("https://github.com/theshadowco")
                        organization.set("1c-syntax")
                        organizationUrl.set("https://github.com/1c-syntax")
                    }
                }
                scm {
                    connection.set("scm:git:git://github.com/1c-syntax/bsl-parser-core.git")
                    developerConnection.set("scm:git:git@github.com:1c-syntax/bsl-parser-core.git")
                    url.set("https://github.com/1c-syntax/bsl-parser-core")
                }
            }
        }
    }
}

nexusStaging {
    serverUrl = "https://s01.oss.sonatype.org/service/local/"
    stagingProfileId = "15bd88b4d17915" // ./gradlew getStagingProfile
}
