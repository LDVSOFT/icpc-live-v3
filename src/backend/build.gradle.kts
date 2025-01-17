plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktor)
}

group = "org.icpclive"
version = rootProject.findProperty("build_version")!!
application {
    mainClass.set("org.icpclive.ApplicationKt")
}

ktor {
    fatJar {
        archiveFileName.set("${rootProject.name}-${project.version}.jar")
    }
}

kotlin {
    sourceSets {
        all {
            languageSettings.optIn("kotlinx.serialization.ExperimentalSerializationApi")
        }
    }
}

tasks {
    shadowJar {
        mergeServiceFiles()
    }
    named<JavaExec>("run") {
        this.args = listOfNotNull(
            "--no-auth",
            project.properties["live.dev.credsFile"]?.let { "--creds=$it"},
            project.properties["live.dev.widgetPositionsFile"]?.let { "--widget-positions=$it"},
            project.properties["live.dev.contest"]?.let { "--config-directory=$it" },
            project.properties["live.dev.analyticsTemplatesFile"]?.let { "--analytics-template=$it" },
        )
        this.workingDir(rootDir.resolve("config"))
    }
    task<Copy>("release") {
        from(shadowJar)
        destinationDir = rootProject.rootDir.resolve("artifacts")
    }
    val jsBuildPath = project.buildDir.resolve("js")
    val schemasBuildPath = project.buildDir.resolve("schemas")
    val copyJsAdmin = register<Copy>("copyJsAdmin") {
        from(project(":frontend").tasks["npm_run_buildAdmin"])
        destinationDir = jsBuildPath.resolve("admin")
    }
    val copyJsOverlay = register<Copy>("copyJsOverlay") {
        from(project(":frontend").tasks["npm_run_buildOverlay"])
        destinationDir = jsBuildPath.resolve("overlay")
    }
    val copySchema = register<Copy>("copySchema") {
        val genTask = project(":schema-generator").tasks["gen"]
        from(genTask.dependsOn)
        destinationDir = schemasBuildPath.resolve("schemas")
    }
    register("buildJs") {
        dependsOn(copyJsAdmin, copyJsOverlay)
        outputs.dir(jsBuildPath)
    }
    register("buildSchema") {
        dependsOn(copySchema)
        outputs.dir(schemasBuildPath)
    }
}



sourceSets {
    main {
        resources {
            if (project.properties["live.dev.embedFrontend"] == "true") {
                srcDirs(tasks["buildJs"].outputs)
            }
            srcDirs(tasks["buildSchema"].outputs)
        }
    }
}

repositories {
    mavenCentral()
}


dependencies {
    implementation(libs.logback)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.server.autoHeadResponse)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.callLogging)
    implementation(libs.ktor.server.contentNegotiation)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.defaultHeaders)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.statusPages)
    implementation(libs.ktor.server.websockets)
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.cli)
    implementation(projects.cds)
    implementation(projects.common)
    testImplementation(libs.kotlin.junit)
    testImplementation(libs.ktor.server.tests)
}
