import com.vanniktech.maven.publish.JavaLibrary
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.SonatypeHost

plugins {
    `java-library`
    alias(libs.plugins.maven.publish)
}

group = "com.crypto-chief"
version = "0.1.0"

description = "Java SDK for the Crypto Chief crypto-processing API."

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
    withSourcesJar()
    withJavadocJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-serial", "-Werror"))
}

tasks.withType<Javadoc>().configureEach {
    (options as StandardJavadocDocletOptions).apply {
        addStringOption("Xdoclint:none", "-quiet")
        encoding = "UTF-8"
        charSet = "UTF-8"
    }
}

dependencies {
    api(libs.okhttp)
    api(libs.jackson.databind)
    api(libs.jackson.annotations)
    implementation(libs.slf4j.api)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.mockwebserver)
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "failed", "skipped")
    }
}

tasks.withType<Jar>().configureEach {
    manifest {
        attributes(
            "Implementation-Title" to project.name,
            "Implementation-Version" to project.version,
        )
    }
}

mavenPublishing {
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
    signAllPublications()

    configure(JavaLibrary(javadocJar = JavadocJar.Empty(), sourcesJar = true))

    coordinates(group.toString(), "cryptochief-crypto-processing-java", version.toString())

    pom {
        name.set("Crypto Chief Processing SDK for Java")
        description.set(project.description)
        url.set("https://github.com/crypto-chiefs/cryptochief-crypto-processing-java")
        inceptionYear.set("2026")

        licenses {
            license {
                name.set("MIT License")
                url.set("https://opensource.org/licenses/MIT")
                distribution.set("repo")
            }
        }
        developers {
            developer {
                id.set("crypto-chiefs")
                name.set("Crypto Chief")
                email.set("dev@crypto-chief.com")
                organization.set("Crypto Chief")
                organizationUrl.set("https://crypto-chief.com")
            }
        }
        scm {
            url.set("https://github.com/crypto-chiefs/cryptochief-crypto-processing-java")
            connection.set("scm:git:git://github.com/crypto-chiefs/cryptochief-crypto-processing-java.git")
            developerConnection.set("scm:git:ssh://git@github.com/crypto-chiefs/cryptochief-crypto-processing-java.git")
        }
        issueManagement {
            system.set("GitHub")
            url.set("https://github.com/crypto-chiefs/cryptochief-crypto-processing-java/issues")
        }
    }
}
