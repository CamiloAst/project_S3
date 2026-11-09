import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Configuración del backend leída de local.properties (no versionado) o de -P en la línea de comandos:
//   care.apiBaseUrl.debug=http://192.168.1.50:3000/   (por defecto: emulador 10.0.2.2)
//   care.apiBaseUrl.release=https://tu-servidor/
//   care.apiToken=<mismo valor que API_ACCESS_TOKEN del backend>
// providers.fileContents registra el archivo como entrada del configuration cache:
// al cambiar local.properties se vuelve a generar BuildConfig con los valores nuevos.
val localProperties = Properties().apply {
    providers.fileContents(rootProject.layout.projectDirectory.file("local.properties"))
        .asText.orNull?.let { load(it.reader()) }
}

val releaseBaseUrlPlaceholder = "https://configura-care-apiBaseUrl-release.invalid/"

fun careProperty(name: String, default: String): String =
    (localProperties.getProperty(name) ?: providers.gradleProperty(name).orNull ?: default).trim()

fun asBuildConfigString(value: String): String =
    "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "uq.seminario.projects3"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "uq.seminario.projects3"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "CARE_API_TOKEN", asBuildConfigString(careProperty("care.apiToken", "")))
    }

    buildTypes {
        debug {
            buildConfigField(
                "String",
                "CARE_API_BASE_URL",
                asBuildConfigString(careProperty("care.apiBaseUrl.debug", "http://10.0.2.2:3000/"))
            )
        }
        release {
            // Release solo permite HTTPS (usesCleartextTraffic=false en el manifest principal)
            buildConfigField(
                "String",
                "CARE_API_BASE_URL",
                asBuildConfigString(careProperty("care.apiBaseUrl.release", releaseBaseUrlPlaceholder))
            )
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
// Un release sin servidor configurado fallaría en silencio (siempre respaldo local): se detiene el build.
val releaseBaseUrl = careProperty("care.apiBaseUrl.release", releaseBaseUrlPlaceholder)
tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    // Copias locales: la acción no debe capturar el script (configuration cache)
    val url = releaseBaseUrl
    val placeholder = releaseBaseUrlPlaceholder
    doFirst {
        check(url != placeholder && url.startsWith("https://")) {
            "Define care.apiBaseUrl.release=https://... en local.properties antes de generar un release."
        }
    }
}

// EmergencyDetectorTest y ContextImageTest leen datos compartidos con el backend: se declaran
// como entrada para que las pruebas vuelvan a ejecutarse cuando cambien.
tasks.withType<Test>().configureEach {
    inputs.file(rootProject.layout.projectDirectory.file("backend/test/emergency-cases.json"))
        .withPropertyName("sharedEmergencyCases")
    inputs.file(rootProject.layout.projectDirectory.file("backend/test/image-keys.json"))
        .withPropertyName("sharedImageKeys")
}
