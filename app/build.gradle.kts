import java.math.BigDecimal
import javax.xml.parsers.DocumentBuilderFactory
import org.gradle.api.tasks.Exec
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification
import org.gradle.testing.jacoco.tasks.JacocoReport

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.hilt)
  alias(libs.plugins.ksp)
  alias(libs.plugins.room)
  alias(libs.plugins.screenshot)
  jacoco
}

android {
    namespace = "com.musicapp.player"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.musicapp.player"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "com.musicapp.player.testing.MusicTestRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
      compose = true
      aidl = false
      buildConfig = false
      shaders = false
    }

    testOptions {
      animationsDisabled = true
      unitTests.isIncludeAndroidResources = true
    }

    lint {
      abortOnError = true
      checkReleaseBuilds = true
      htmlReport = true
      xmlReport = true
    }

    experimentalProperties["android.experimental.enableScreenshotTest"] = true

    packaging {
      resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
      }
    }
}

room {
  schemaDirectory("$projectDir/schemas")
}

hilt {
  enableAggregatingTask = true
}

jacoco {
  toolVersion = "0.8.14"
}

kotlin {
    jvmToolchain(17)
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.lifecycle.process)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.kotlinx.coroutines.android)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)

  // Navigation
  implementation(libs.androidx.navigation3.ui)
  implementation(libs.androidx.navigation3.runtime)

  // Dependency injection and platform foundations
  implementation(libs.hilt.android)
  ksp(libs.hilt.compiler)
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.room.ktx)
  ksp(libs.androidx.room.compiler)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.media3.common)
  implementation(libs.androidx.media3.exoplayer)
  implementation(libs.androidx.media3.session)
  implementation(libs.coil.compose)
  implementation(libs.androidx.profileinstaller)

  // Local tests
  testImplementation(libs.junit4)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.turbine)
  testImplementation(libs.robolectric)
  testImplementation(libs.hilt.android.testing)
  kspTest(libs.hilt.compiler)

  // Device and Compose tests
  androidTestImplementation(composeBom)
  androidTestImplementation(libs.junit4)
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.rules)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.test.uiautomator)
  androidTestImplementation(libs.androidx.room.testing)
  androidTestImplementation(libs.hilt.android.testing)
  kspAndroidTest(libs.hilt.compiler)
  kspAndroidTest(libs.androidx.room.compiler)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Host-side Compose Preview screenshot tests
  screenshotTestImplementation(composeBom)
  screenshotTestImplementation(libs.screenshot.validation.api)
  screenshotTestImplementation(libs.androidx.compose.ui.tooling)
}

val coverageIncludes =
  listOf(
    "com/musicapp/player/core/**",
    "com/musicapp/player/data/**",
    "com/musicapp/player/media/**",
    "com/musicapp/player/feature/**/*ViewModel*",
    "com/musicapp/player/feature/**/*Reducer*",
    "com/musicapp/player/feature/**/*Parser*",
  )
val coverageExcludes =
  listOf(
    "**/R.class",
    "**/R$*.class",
    "**/BuildConfig.*",
    "**/*Preview*.*",
    "**/*_Factory*.*",
    "**/*Hilt*.*",
    "**/*_Impl*.*",
    "**/package-info*.*",
    "com/musicapp/player/core/system/Default*.*",
  )
val debugClassDirectories =
  files(
    fileTree(layout.buildDirectory.dir("intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes")) {
      include(coverageIncludes)
      exclude(coverageExcludes)
    },
    fileTree(layout.buildDirectory.dir("intermediates/javac/debug/compileDebugJavaWithJavac/classes")) {
      include(coverageIncludes)
      exclude(coverageExcludes)
    },
  )
val debugCoverageData =
  fileTree(layout.buildDirectory) {
    include("jacoco/testDebugUnitTest.exec")
    include("outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec")
  }

tasks.register<JacocoReport>("jacocoDebugUnitTestReport") {
  group = "verification"
  description = "Generates the frozen Wave 0 debug unit-test coverage report."
  dependsOn("testDebugUnitTest")
  classDirectories.setFrom(debugClassDirectories)
  sourceDirectories.setFrom(files("src/main/java", "src/main/kotlin"))
  executionData.setFrom(debugCoverageData)
  reports {
    html.required.set(true)
    html.outputLocation.set(layout.buildDirectory.dir("reports/jacoco/jacocoDebugUnitTestReport/html"))
    xml.required.set(true)
    xml.outputLocation.set(layout.buildDirectory.file("reports/jacoco/jacocoDebugUnitTestReport/jacocoDebugUnitTestReport.xml"))
    csv.required.set(false)
  }
}

tasks.register<JacocoCoverageVerification>("verifyDebugCoverage") {
  group = "verification"
  description = "Enforces 80% line and 70% branch coverage for Wave business-code scopes."
  dependsOn("testDebugUnitTest")
  classDirectories.setFrom(debugClassDirectories)
  sourceDirectories.setFrom(files("src/main/java", "src/main/kotlin"))
  executionData.setFrom(debugCoverageData)
  violationRules {
    rule {
      limit {
        counter = "LINE"
        value = "COVEREDRATIO"
        minimum = BigDecimal("0.80")
      }
      limit {
        counter = "BRANCH"
        value = "COVEREDRATIO"
        minimum = BigDecimal("0.70")
      }
    }
  }
}

val qualityReportDirectory = layout.buildDirectory.dir("reports/quality")

tasks.register("verifyStringResources") {
  group = "verification"
  description = "Checks that English and Simplified Chinese resource keys stay identical."
  val defaultStrings = layout.projectDirectory.file("src/main/res/values/strings.xml")
  val chineseStrings = layout.projectDirectory.file("src/main/res/values-zh-rCN/strings.xml")
  val report = qualityReportDirectory.map { it.file("strings.txt") }
  inputs.files(defaultStrings, chineseStrings)
  outputs.file(report)

  doLast {
    val namePattern = Regex("""<(string|string-array|plurals)\s+[^>]*name\s*=\s*\"([^\"]+)\"""")
    fun keys(file: File): Set<String> =
      namePattern.findAll(file.readText()).map { it.groupValues[2] }.toSortedSet()

    val defaultKeys = keys(defaultStrings.asFile)
    val chineseKeys = keys(chineseStrings.asFile)
    val missingChinese = defaultKeys - chineseKeys
    val extraChinese = chineseKeys - defaultKeys
    check(missingChinese.isEmpty() && extraChinese.isEmpty()) {
      "String resources differ: missing zh-rCN=$missingChinese, extra zh-rCN=$extraChinese"
    }
    report.get().asFile.apply {
      parentFile.mkdirs()
      writeText("PASS: ${defaultKeys.size} localized resource keys match.\n")
    }
  }
}

tasks.register("verifyArchitecture") {
  group = "verification"
  description = "Rejects UI access to platform data/player APIs and hard-coded design dimensions."
  val sourceFiles = fileTree("src/main/java") { include("**/*.kt") }
  val moduleDirectory = layout.projectDirectory.asFile
  val report = qualityReportDirectory.map { it.file("architecture.txt") }
  inputs.files(sourceFiles)
  outputs.file(report)

  doLast {
    val violations = mutableListOf<String>()
    val forbiddenPlatformReferences =
      listOf(
        "android.provider.MediaStore",
        "androidx.datastore",
        "androidx.media3.common.Player",
        "androidx.media3.exoplayer",
      )
    sourceFiles.files.sorted().forEach { source ->
      val relativePath = source.relativeTo(moduleDirectory).invariantSeparatorsPath
      val isUiSource = "/ui/" in relativePath || "/feature/" in relativePath
      if (!isUiSource) return@forEach
      val content = source.readText()
      forbiddenPlatformReferences.filter(content::contains).forEach { reference ->
        violations += "$relativePath directly references $reference"
      }
      Regex("""\b\d+(\.\d+)?\.(dp|sp)\b|RoundedCornerShape\s*\(""")
        .findAll(content)
        .forEach { match -> violations += "$relativePath bypasses design tokens with ${match.value}" }
    }
    check(violations.isEmpty()) { violations.joinToString(prefix = "Architecture violations:\n", separator = "\n") }
    report.get().asFile.apply {
      parentFile.mkdirs()
      writeText("PASS: ${sourceFiles.files.size} Kotlin sources checked.\n")
    }
  }
}

tasks.register("verifyManifestSecurity") {
  group = "verification"
  description = "Checks merged release permissions and exported component policy."
  dependsOn("processReleaseMainManifest")
  val mergedManifest =
    layout.buildDirectory.file("intermediates/merged_manifest/release/processReleaseMainManifest/AndroidManifest.xml")
  val report = qualityReportDirectory.map { it.file("manifest.txt") }
  inputs.file(mergedManifest)
  outputs.file(report)

  doLast {
    val androidNamespace = "http://schemas.android.com/apk/res/android"
    val document =
      DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder()
        .parse(mergedManifest.get().asFile)
    val forbiddenPermissions = setOf("android.permission.INTERNET", "android.permission.POST_NOTIFICATIONS")
    val declaredPermissions =
      document.getElementsByTagName("uses-permission").let { nodes ->
        (0 until nodes.length)
          .map { nodes.item(it).attributes.getNamedItemNS(androidNamespace, "name").nodeValue }
          .toSet()
      }
    check((declaredPermissions intersect forbiddenPermissions).isEmpty()) {
      "Forbidden permissions declared: ${declaredPermissions intersect forbiddenPermissions}"
    }

    val exportedComponents = mutableListOf<String>()
    listOf("activity", "service", "receiver", "provider").forEach { tag ->
      val nodes = document.getElementsByTagName(tag)
      (0 until nodes.length).forEach { index ->
        val attributes = nodes.item(index).attributes
        if (attributes.getNamedItemNS(androidNamespace, "exported")?.nodeValue == "true") {
          val name = attributes.getNamedItemNS(androidNamespace, "name").nodeValue
          exportedComponents += name
        }
      }
    }
    val invalidExports =
      exportedComponents.filterNot { component ->
        component == "com.musicapp.player.MainActivity" ||
          component == ".MainActivity"
      }
    check(invalidExports.isEmpty()) { "Unexpected exported components: $invalidExports" }
    check(exportedComponents.any { it.endsWith("MainActivity") }) { "Launcher MainActivity is not exported" }

    report.get().asFile.apply {
      parentFile.mkdirs()
      writeText(
        "PASS: forbidden permissions absent; exported components=${exportedComponents.sorted()}.\n",
      )
    }
  }
}

val screenshotWrapper = rootProject.layout.projectDirectory.file("tools/quality/preview_screenshot_ascii.sh")
val screenshotInputs =
  files(
    rootProject.layout.projectDirectory.file("gradle/libs.versions.toml"),
    rootProject.layout.projectDirectory.file("gradle.properties"),
    rootProject.layout.projectDirectory.file("settings.gradle.kts"),
    rootProject.layout.projectDirectory.file("build.gradle.kts"),
    layout.projectDirectory.file("build.gradle.kts"),
    fileTree("src/main"),
    fileTree("src/screenshotTest"),
    fileTree("src/screenshotTestDebug/reference"),
  )

tasks.register<Exec>("updateDebugScreenshotReferences") {
  group = "verification"
  description = "Updates debug screenshot references through an ASCII temporary checkout."
  inputs.files(screenshotInputs, screenshotWrapper)
  outputs.dir(layout.projectDirectory.dir("src/screenshotTestDebug/reference"))
  commandLine(
    "bash",
    screenshotWrapper.asFile.absolutePath,
    rootProject.layout.projectDirectory.asFile.absolutePath,
    "update",
  )
}

tasks.register<Exec>("verifyDebugScreenshots") {
  group = "verification"
  description = "Validates debug screenshots through an ASCII temporary checkout."
  inputs.files(screenshotInputs, screenshotWrapper)
  outputs.dir(layout.buildDirectory.dir("reports/screenshotTest/preview/debug"))
  commandLine(
    "bash",
    screenshotWrapper.asFile.absolutePath,
    rootProject.layout.projectDirectory.asFile.absolutePath,
    "validate",
  )
}

tasks.register("wave0HostQualityGate") {
  group = "verification"
  description = "Runs all host-side Wave 0 quality checks; device tests remain a separate gate."
  dependsOn(
    "jacocoDebugUnitTestReport",
    "verifyDebugCoverage",
    "verifyStringResources",
    "verifyArchitecture",
    "verifyManifestSecurity",
    "verifyDebugScreenshots",
    "lintDebug",
  )
}
