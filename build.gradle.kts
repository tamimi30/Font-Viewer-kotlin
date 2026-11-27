buildscript {
    repositories {
        google()
        maven("https://maven.aliyun.com/repository/public")
        maven("https://maven.aliyun.com/repository/google")
        maven("https://jitpack.io")
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:9.5.3")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.21")
        classpath("com.google.devtools.ksp:symbol-processing-gradle-plugin:2.3.14")
    }
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
