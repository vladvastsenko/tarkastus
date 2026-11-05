plugins { id("com.android.application") }

dependencies {
    implementation("androidx.core:core:1.15.0")
}

android {
    namespace = "fi.tarkastus.app"
    compileSdk = 36
    defaultConfig {
        applicationId = "fi.tarkastus.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.3"
    }
}
