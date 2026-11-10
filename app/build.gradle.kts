plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.rearcare.car"
    compileSdk {
        version = release(37)
    }
    // 주행 규제(CarUxRestrictionsManager)를 쓰기 위한 Android Automotive 라이브러리.
    // 컴파일할 때만 연결한다. 실제 차(에뮬레이터)에는 시스템에 이미 들어 있다
    useLibrary("android.car")

    // AI 모델 파일(.tflite, .task)은 APK 안에서 압축하지 않는다. MediaPipe가 모델을 그대로 읽을 수 있게 하기 위해서다
    androidResources {
        noCompress += listOf("tflite", "task")
    }

    defaultConfig {
        applicationId = "com.rearcare.car"
        minSdk = 28
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
        // 제출 APK 용량 때문에 에뮬레이터(x86_64)와 arm64용 파일만 넣는다
        ndk { abiFilters += listOf("x86_64", "arm64-v8a") }
    }

    buildTypes {
        release {
            // 앱 마켓에 올리지 않는 공모전 제출본이라 따로 서명 키를 만들지 않고 개발용 키로 서명한다
            signingConfig = signingConfigs.getByName("debug")
            // 코드 축소(R8)를 켜면 release 앱이 시작하자마자 꺼졌다(kotlin 클래스 접근 오류). 개발용과 같은 코드로 돌게 끈다
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    implementation(libs.pleos.vehicle)
    implementation(libs.pleos.tts)
    implementation(libs.pleos.navi)
    implementation(libs.mediapipe.tasks.audio)
    implementation(libs.mediapipe.tasks.vision)
    testImplementation(libs.junit)
}