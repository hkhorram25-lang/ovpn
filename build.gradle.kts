@file:Suppress("UnstableApiUsage")

plugins {
    // Version catalogs could be used, but keeping minimal root build file
}

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}

