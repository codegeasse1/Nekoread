// Reader engine compiled as a library module. The Nekoread app hosts the engine's ReaderScreen
// via the HTML bridge in com.example.readerbridge (see app/src/main/java/com/example/readerbridge).
// This file wires the engine's build against the project toolchain (AGP built-in Kotlin, KSP)
// while keeping the engine's own dependency versions.
plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.hilt)
  alias(libs.plugins.serialization)
}

android {
  namespace = "io.aatricks.easyreader"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    minSdk = 24
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    buildConfigField("String", "GIT_COMMIT_SHA", "\"unknown\"")
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  sourceSets {
    getByName("main") {
      java.srcDir("src/standard/java")
    }
  }
  packaging {
    resources {
      excludes += "org/bouncycastle/pqc/crypto/**/*.properties"
      excludes += "com/itextpdf/io/font/cmap/*"
      excludes += "com/itextpdf/hyph/*"
      excludes += "META-INF/{AL2.0,LGPL2.1}"
      excludes += "META-INF/versions/**/module-info.class"
      excludes += "module-info.class"
    }
  }
}

ksp {
  arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
  // Core Android
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.core.splashscreen)
  implementation(libs.androidx.profileinstaller)
  implementation(libs.androidx.work.runtime.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)

  // Compose (the engine's own BOM so its code compiles against the APIs it was written for)
  implementation(platform(libs.androidx.compose.bom.engine))
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.material.icons.extended)

  // Hilt
  implementation(libs.hilt.android)
  ksp(libs.hilt.compiler)
  // Hilt 2.60's generated code references com.google.errorprone.annotations (now compileOnly in
  // Dagger), so the generated-Java compile needs these annotations on the classpath.
  compileOnly(libs.error.prone.annotations)
  implementation(libs.hilt.navigation.compose)
  implementation(libs.androidx.hilt.work)
  ksp(libs.androidx.hilt.compiler)

  // Room
  implementation(libs.room.runtime.engine)
  implementation(libs.room.ktx.engine)
  ksp(libs.room.compiler.engine)

  // Navigation
  implementation(libs.navigation.compose.engine)

  // Serialization
  implementation(libs.kotlinx.serialization.json.engine)

  // Ktor
  implementation(libs.ktor.client.core)
  implementation(libs.ktor.client.okhttp)
  implementation(libs.ktor.client.content.negotiation)
  implementation(libs.ktor.serialization.kotlinx.json)

  // Web Scraping - JSoup
  implementation(libs.jsoup)

  // Image Loading - Coil 3
  implementation(libs.coil3.compose)
  implementation(libs.coil3.network.okhttp)

  // PDF Parsing - iText7
  implementation(libs.itext7.core) {
    exclude(group = "org.bouncycastle")
  }
  implementation(libs.bouncycastle.bcprov)
  implementation(libs.bouncycastle.bcpkix)
  implementation(libs.bouncycastle.bcutil)

  // Networking - OkHttp
  implementation(libs.okhttp)
  implementation(libs.logging.interceptor)
  // Ktor's OkHttp engine pulls okhttp-sse in transitively at an older version than the rest
  // of the okhttp family; declaring it explicitly forces it to resolve at the same version so
  // its internals (e.g. RealEventSource) stay binary-compatible with okhttp itself.
  implementation(libs.okhttp.sse)
}
