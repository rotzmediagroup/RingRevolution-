plugins {
    id("org.jetbrains.kotlin.jvm")
    application
}
kotlin { jvmToolchain(21) }
dependencies {
    implementation(project(":core"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
}
application { mainClass.set("com.shiostudios.dumplingrings.tools.MainKt"); applicationDefaultJvmArgs = listOf("-Xmx3g") }
