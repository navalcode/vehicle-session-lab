plugins {
    kotlin("jvm") version "2.2.10"
    application
}
kotlin { jvmToolchain(17) }
application { mainClass.set("dev.navalcode.vehicle.DemoKt") }
dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}
