plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kover)
    alias(libs.plugins.detekt)
}

kotlin {
    compilerOptions {
        allWarningsAsErrors = true
    }
}

// ktfmt はGradleプラグインを使わずCLI jarを直接実行する(依存とリポジトリを増やさないため)。
val ktfmtCli: Configuration = configurations.create("ktfmtCli")

val ktfmtSources =
    listOf(
        "src/main/kotlin",
        "src/test/kotlin",
        "src/androidTest/kotlin",
    )

tasks.register<JavaExec>("ktfmtFormat") {
    group = "formatting"
    description = "ktfmt でソースを整形する"
    classpath = ktfmtCli
    mainClass = "com.facebook.ktfmt.cli.Main"
    args = listOf("--kotlinlang-style") + ktfmtSources
}

val ktfmtCheck =
    tasks.register<JavaExec>("ktfmtCheck") {
        group = "verification"
        description = "ktfmt の整形結果と差分がないか検査する"
        classpath = ktfmtCli
        mainClass = "com.facebook.ktfmt.cli.Main"
        args = listOf("--kotlinlang-style", "--dry-run", "--set-exit-if-changed") + ktfmtSources
    }

tasks.named("check") { dependsOn(ktfmtCheck) }

kover {
    reports {
        // カバレッジの計測・ゲートとも logic 層(純粋ロジック)に限定する。
        // ui/data/player は薄いグルーコードで、数値を追うとテストが脆く高コストになるため
        // 計測対象から外す(Kover 0.9 の filters は reports 単位でのみ指定できる)。
        filters {
            includes { classes("dev.thinkami.podcastplayer.logic.*") }
            // ドメインモデルは判断を持たないデータ保持クラス。data class が生成する
            // equals/hashCode/copy の行を数えると、意味のないテストで数字を稼ぐ誘惑が生まれる。
            // ゲートは「判断」そのもの(ListeningRules / EpisodeFiltering / PlaybackQueue)に掛ける。
            excludes { classes("dev.thinkami.podcastplayer.logic.model.*") }
        }
        verify { rule("logic層の行カバレッジ") { bound { minValue = 90 } } }
    }
}

android {
    namespace = "dev.thinkami.podcastplayer"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.thinkami.podcastplayer"
        minSdk = 36
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // 計装テストは instrumented ビルドタイプで走らせる。applicationIdSuffix により
    // 本番アプリ(dev.thinkami.podcastplayer)とは別パッケージになり、テストが本番の
    // DB・購読データ・DLファイルに到達することを OS のサンドボックスが阻止する。
    // 実端末を普段使いしているための保護であり、この2箇所を削除してはならない。
    testBuildType = "instrumented"

    buildTypes {
        release {
            isMinifyEnabled = false
        }
        create("instrumented") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".instrumented"
        }
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        // ユニットテストのJVMを実機(Pixel 7 Pro)と同じ ja-JP ロケールで起動する。
        // ビルドマシンのロケール任せにすると、ロケール依存のバグ(英語月名の日付解釈など)を
        // JVMテストで検出できない。
        unitTests.all { it.jvmArgs("-Duser.language=ja", "-Duser.country=JP") }
    }

    lint {
        warningsAsErrors = true
        abortOnError = true
        // 「新しいバージョンが出ている」系の通知は無効化する。
        // 依存は libs.versions.toml で意図的に固定しており、更新は自分の意思で
        // リリースノートを読んでから行う。上流が publish しただけで CI が赤くなるのは誤り。
        // targetSdk 36 も端末(Pixel 7 Pro / Android 16)に合わせた意図的な選択。
        disable +=
            setOf(
                "GradleDependency",
                "NewerVersionAvailable",
                "AndroidGradlePluginVersion",
                "OldTargetApi",
            )
    }
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    ktfmtCli(libs.ktfmt.cli)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.navigation.compose)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.coroutines.android)

    debugImplementation(libs.compose.ui.tooling)
    // ui-test-manifest はテスト対象アプリ側に必要。testBuildType = instrumented のため
    // debugImplementation では計装テストから見えない
    "instrumentedImplementation"(libs.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.coroutines.test)
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.espresso.core)
}
