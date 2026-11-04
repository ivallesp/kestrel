# Android toolchain, latest stable library versions (as of 2026-11-04) and a proven headless Compose build plus JVM screenshot test on this Linux desktop

## Summary
A headless Compose build works on this machine and is proven end to end. The stack is AGP 9.5.0 with Gradle 9.8.2, Kotlin 2.4.21 and KSP 2.3.13, built with JDK 21, compileSdk 37.2, targetSdk 37 and minSdk 31. The working project is /var/tmp/sniper-toolchain-probe. `./gradlew assembleDebug` builds a debug APK. `./gradlew recordRoborazziDebug` writes a 1078x2422 PNG at Pixel 9a size; I opened it and it renders correctly. `verifyRoborazziDebug` passes after `clean` and fails when the UI changes, so the comparison is real. With warm caches, `clean assembleDebug verifyRoborazziDebug spotlessCheck lintDebug --no-build-cache` takes 41 s. The cold first build took 134 s. The full version catalog builds together in one APK: Room with KSP, Navigation 3, DataStore, WorkManager, webkit, Glance, Coil 3, OkHttp, kotlinx-serialization, coroutines and datetime. Its tests (Turbine, MockK, coroutines-test) pass. Three changes were needed for SDK 37 on JDK 21: a Robolectric `--add-opens=java.base/jdk.internal.access=ALL-UNNAMED` JVM flag, forcing Espresso 3.7.0 (the 3.5.x that comes in transitively calls a method SDK 37 removed), and keeping Roborazzi goldens outside build/. In cmdline-tools 23.0, sdkmanager is deprecated and forwards to a new `android` CLI, and `--licenses` does nothing; `android sdk install` writes the license file itself. Material 3 Expressive is not in any stable release: stable material3 1.4.0 keeps MaterialExpressiveTheme and MotionScheme internal. In 1.5.0-beta02 the theme, ButtonGroup and FloatingToolbar are public without opt-in, but LoadingIndicator and shape morphing are still experimental; beta02 compiled and rendered with stable Compose 1.12.1. Roborazzi 1.77.0 with Robolectric 4.17 is the stable JVM screenshot option. Paparazzi's only stable release (1.3.5) fails on AGP 9; only 2.0.0-alpha05.1 works. material-icons-extended stopped at 1.7.8 (Feb 2025), and Google's docs call it no longer maintained.

## Findings
- **TC1** [high] Latest stable Android Gradle Plugin is 9.5.0 (POM Last-Modified 2026-10-28); 9.6.0-alpha02 is the newest pre-release. AGP 9.5 needs Gradle 9.6.0 or newer, JDK 17 or newer, SDK Build Tools 36.0.0 or newer, and supports API level 37 at most.
  - evidence: https://dl.google.com/dl/android/maven2/com/android/tools/build/gradle/maven-metadata.xml (read 2026-11-04); https://developer.android.com/build/releases/gradle-plugin compatibility table: 'The maximum API level that Android Gradle plugin 9.5 supports is API level 37', Gradle min 9.6.0, Build Tools 36.0.0, JDK 17.
  - implication: Use agp = 9.5.0 with the Gradle 9.8.2 wrapper. Proven in /var/tmp/sniper-toolchain-probe.
- **TC2** [high] Latest stable Gradle is 9.8.2 (buildTime 2026-10-19). 9.9.0-rc-1 (2026-10-30) is the active release candidate. The wrapper was generated with distributionSha256Sum 21bd5da2de49b642904077f38c0efdc7778e92b3ed0889392dca61b97e389607, and the wrapper jar sha256 3b8a2577...ce68e98 matches the published checksum.
  - evidence: https://services.gradle.org/versions/current and /versions/all (read 2026-11-04); sha256sum of the downloaded zip and of gradle/wrapper/gradle-wrapper.jar.
  - implication: Copy gradlew, gradlew.bat and gradle/wrapper/* from /var/tmp/sniper-toolchain-probe; no system Gradle is needed.
- **TC3** [high] Latest stable Kotlin, and therefore the Compose compiler plugin org.jetbrains.kotlin.plugin.compose, is 2.4.21 (2026-10-08); 2.5.0-Beta2 (2026-10-21) is a pre-release. KSP is 2.3.13 (2026-10-30). Since 2.3.0, KSP versions no longer carry the Kotlin version.
  - evidence: repo1.maven.org kotlin-gradle-plugin and compose-compiler-gradle-plugin metadata; plugins.gradle.org org.jetbrains.kotlin.plugin.compose marker; repo1.maven.org com/google/devtools/ksp/symbol-processing-gradle-plugin metadata (last 2.3.x: 2.3.0 ... 2.3.13).
  - implication: Pin kotlin = 2.4.21 and ksp = 2.3.13. Both built Room code generation successfully.
- **TC4** [high] Kotlin's own compatibility table lists KGP 2.4.20-2.4.21 as tested only up to Gradle 9.7.0 and AGP 9.3.1. The combination AGP 9.5.0 + Gradle 9.8.2 + KGP 2.4.21 is past that tested range, yet it built with zero Kotlin or Gradle warnings.
  - evidence: https://kotlinlang.org/docs/gradle-configure-project.html (page dated 06 October 2026) row '2.4.20-2.4.21 | 7.6.3-9.7.0 | 8.5.2-9.3.1'; build logs /var/tmp/sniper-toolchain-dl/build-logs/40-final-all.log (no 'w:' lines).
  - implication: Keep the latest versions. If an odd KGP failure appears, the documented fallback is AGP 9.3.x with Gradle 9.7.x.
- **TC5** [high] AGP 9 has built-in Kotlin, so the org.jetbrains.kotlin.android plugin must not be applied. AGP 9.5.0 depends on KGP 2.2.10 at runtime. Declaring the Compose compiler plugin in the root plugins block (apply false) puts kotlin-gradle-plugin 2.4.21 on the build classpath, because compose-compiler-gradle-plugin 2.4.21 declares a compile dependency on it.
  - evidence: agp-9.5.0.pom lists org.jetbrains.kotlin:kotlin-gradle-plugin 2.2.10; developer.android.com/build/migrate-to-built-in-kotlin; `./gradlew buildEnvironment` printed 'org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.10 -> 2.4.21'; debugRuntimeClasspath resolves kotlin-stdlib 2.4.21.
  - implication: Copy the root build.gradle.kts pattern. Do not add kotlin-android or kapt. Use kotlin { compilerOptions {} } instead of android.kotlinOptions.
- **TC6** [high] In Android SDK cmdline-tools 23.0 (commandlinetools-linux-16111833_latest.zip, 2026-08-19), sdkmanager is deprecated and forwards to a new 'android' CLI (version 1.1.16717390). `yes | sdkmanager --licenses` prints 'Warning: The --licenses option is no longer needed.' and creates no licenses directory. `android sdk install` uses slash package names and writes licenses/android-sdk-license with hash 24333f8a63b6825ea9c5514f83c2829b004d1fee.
  - evidence: Console output on this desktop on 2026-11-04: 'WARNING: The SDK Manager CLI tool (sdkmanager) is deprecated. Android CLI will be used instead.'; https://dl.google.com/android/repository/repository2-3.xml entry cmdline-tools;23.0 with sha1 e025545c62a8e64c7559119566a569fb1dec5f60 (verified).
  - implication: Install SDK packages with `android sdk install platform-tools build-tools/37.0.0 platforms/android-37.2`, which took 15.6 s.
- **TC7** [high] The latest stable SDK pieces are platforms;android-37.2 (api-level 37.2, extension 24, no codename, r02 published 2026-10-28), build-tools 37.0.0 (2026-03-26) and platform-tools 37.0.1 (2026-07-30). AGP selects platform 37.2 through `compileSdk { version = release(37) { minorApiLevel = 2 } }`.
  - evidence: repository2-3.xml (channel-0 = stable), Last-Modified headers of platform-37.2_r02.zip, build-tools_r37_linux.zip and platform-tools_r37.0.1-linux.zip; gradle-common-api-9.5.0-sources.jar com/android/build/api/dsl/CompileSdkSpec.kt and CompileSdkReleaseSpec.kt (minorApiLevel); successful build with only platforms/android-37.2 installed.
  - implication: Use that compileSdk block with targetSdk = 37. Inferred: a plain `compileSdk = 37` would look for a different platform directory, android-37.0.
- **TC8** [high] Unless buildToolsVersion is set, AGP 9.5 auto-installs its default Build-Tools 36.0.0 on the first build, and this succeeded because the license file existed. Setting buildToolsVersion = "37.0.0" uses the installed 37.0.0.
  - evidence: 01-assembleDebug.log: 'Installing Android SDK Build-Tools 36 in ~/android-sdk/build-tools/36.0.0'; `android sdk list` now shows build-tools/36.0.0 and 37.0.0.
  - implication: Keep buildToolsVersion = "37.0.0" in app/build.gradle.kts (it is in the final file), or accept the extra download.
- **TC9** [high] Several current AARs declare minCompileSdk=37, and the AndroidX ones also require minAndroidGradlePluginVersion=9.1.0: core 1.19.1, compose ui 1.12.1, navigation3 1.2.0, lifecycle-viewmodel-navigation3 2.11.0, adaptive-navigation3 1.3.0, material3 1.5.0-beta02 and coil-compose 3.6.3. Coil declares minCompileSdk=37 but minAndroidGradlePluginVersion=1.0.0.
  - evidence: META-INF/com/android/build/gradle/aar-metadata.properties read from the cached AARs under ~/.gradle/caches/modules-2/files-2.1 after the build.
  - implication: compileSdk 37 or higher and AGP 9.1 or newer are mandatory for this library set; older toolchains fail checkDebugAarMetadata.
- **TC10** [high] The latest stable Compose BOM is 2026.09.00 (2026-09-09). It maps Compose ui/foundation/runtime to 1.12.1, material3 to 1.4.0, material3-adaptive and adaptive-navigation3 to 1.3.0, and material-icons-* to 1.7.8. The pre-release BOMs compose-bom-alpha and compose-bom-beta are at 2026.10.01, with ui/foundation 1.13.0-beta02 and material3 1.5.0-beta02.
  - evidence: https://dl.google.com/dl/android/maven2/androidx/compose/compose-bom/2026.09.00/compose-bom-2026.09.00.pom; compose-bom-beta-2026.10.01.pom.
  - implication: Use platform(compose-bom:2026.09.00) and declare Compose artifacts without versions.
- **TC11** [high] Stable material3 1.4.0 (2025-09-24) has no public Material 3 Expressive APIs. MaterialExpressiveTheme, the MotionScheme interface, expressiveLightColorScheme and FlexibleBottomAppBar are declared `internal`. LoadingIndicator, ButtonGroup, HorizontalFloatingToolbar, MaterialShapes and RoundedPolygon.toShape are absent. ShortNavigationBar and WideNavigationRail are public.
  - evidence: material3-android-1.4.0-sources.jar, e.g. commonMain/androidx/compose/material3/MaterialTheme.kt:185 'internal fun MaterialExpressiveTheme(' and MotionScheme.kt:42 'internal interface MotionScheme'.
  - implication: Expressive design needs material3 1.5.0-beta02 (pre-release). With stable 1.4.0 the app gets only the classic M3 components.
- **TC12** [high] In material3 1.5.0-beta02 (2026-10-21) these Expressive APIs are public with no @ExperimentalMaterial3ExpressiveApi: MaterialExpressiveTheme, MotionScheme with MotionScheme.expressive() and standard(), expressiveLightColorScheme, ButtonGroup with ButtonGroupDefaults.OverflowIndicator, HorizontalFloatingToolbar, FlexibleBottomAppBar, SplitButtonLayout, ToggleButton, FloatingActionButtonMenu, LinearWavyProgressIndicator, ShortNavigationBar and WideNavigationRail. Still experimental (15 annotation sites): LoadingIndicator, ContainedLoadingIndicator, LoadingIndicatorDefaults, MaterialShapes, RoundedPolygon.toShape and toPath, Morph.toPath (shape morphing), and two DropdownMenuItem overloads.
  - evidence: material3-android-1.5.0-beta02-sources.jar: `grep -rn '@ExperimentalMaterial3ExpressiveApi'` gives 15 hits, all in LoadingIndicator.kt, MaterialShapes.kt and Menu.kt. ExpressiveProbeTheme calls MaterialExpressiveTheme, expressiveLightColorScheme and MotionScheme.expressive() with no @OptIn and compiled. The annotation is a plain @RequiresOptIn, whose default level is ERROR.
  - implication: With 1.5.0-beta02, opt in only around LoadingIndicator and MaterialShapes or shape-morph code.
- **TC13** [high] material3 1.5.0-beta02 works on top of the stable BOM 2026.09.00, because its POM depends on Compose foundation and ui 1.12.0. An Expressive screen (MaterialExpressiveTheme, a Cookie9Sided shape, a wavy progress bar, ButtonGroup toggles, LoadingIndicator and HorizontalFloatingToolbar) compiled and rendered under Robolectric 4.17 with Roborazzi 1.77.0. The infinite LoadingIndicator animation needs composeRule.mainClock.autoAdvance = false plus advanceTimeBy(700) before capture.
  - evidence: /var/tmp/sniper-toolchain-probe-m3e (material3 overridden to 1.5.0-beta02; dependencies show material3:1.5.0-beta02 and ui:1.12.1); PNG /var/tmp/sniper-toolchain-probe-m3e/app/src/test/screenshots/com.example.sniperprobe.ExpressiveScreenshotTest.expressiveSnipeScreen.png, opened and checked.
  - implication: If the design calls for Expressive, override only material3 to 1.5.0-beta02 and keep the rest stable.
- **TC14** [high] material-icons-extended (and material-icons-core) stopped at 1.7.8, published 2025-02-12. Google's Compose docs say the Material Icons artifact 'is no longer maintained or recommended for use in your apps, as it contains an older look and feel and can also increase the build time of your apps significantly' and recommend downloading Material Symbols XML from fonts.google.com/icons.
  - evidence: dl.google.com maven-metadata for androidx.compose.material:material-icons-extended; https://developer.android.com/develop/ui/compose/graphics/images/material (fetched 2026-11-04).
  - implication: Do not add material-icons-extended. Ship Material Symbols vector drawables and draw them with Icon(painterResource(...)).
- **TC15** [high] Two navigation libraries are current: classic navigation-compose 2.10.2 (2026-09-23, no newer pre-release) and Navigation 3 navigation3-runtime/ui 1.2.0 (2026-09-23; 1.3.0-alpha03 is the pre-release). Navigation 3 pairs with lifecycle-viewmodel-navigation3 2.11.0 and adaptive-navigation3 1.3.0. This Nav3 1.2.0 code compiled: rememberNavBackStack(SnipeListKey), NavDisplay(backStack, onBack, entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator()), entryProvider = entryProvider { entry<SnipeListKey> { ... } }). Older NavDisplay overloads that take sceneStrategy are DeprecationLevel.HIDDEN.
  - evidence: maven-metadata for each artifact; navigation3-ui-android-1.2.0-sources.jar NavDisplay.kt lines 244-251 and 335-350; /var/tmp/sniper-toolchain-probe/app/src/main/kotlin/com/example/sniperprobe/AppNavigation.kt compiled. android/nowinandroid HEAD 2026-10-20 merged 'Integrate Navigation 3 deep links'.
  - implication: Use Navigation 3 for this Compose-only app. Annotate keys with @Serializable and implement NavKey.
- **TC16** [high] Room 2.8.5 (2026-09-09) works with KSP 2.3.13 and AGP 9.5 built-in Kotlin: kspDebugKotlin generated SnipeDao_Impl.kt and SnipeDatabase_Impl.kt, and copyRoomSchemas wrote app/schemas/.../1.json. suspend and Flow DAO functions compiled with room-runtime alone (no room-ktx). Room 3.0.3 (group androidx.room3, plugin id androidx.room3; stable since 3.0.0 on 2026-07-01) also compiled with sqlite-bundled 2.7.1's BundledSQLiteDriver.
  - evidence: 06-full-assembleDebug.log and 50-room3-assemble.log; https://developer.android.com/jetpack/androidx/releases/room3 ('Kotlin Symbol Processing (KSP) is required', 'A SQLiteDriver is now required to build a RoomDatabase').
  - implication: Either Room works. The proven catalog uses Room 2.8.5. Room 3 needs a driver, suspend-only DAO calls and androidx.room3 imports.
- **TC17** [high] Other latest stable versions all compiled together into one APK: activity-compose 1.13.0 (2026-03-11), lifecycle 2.11.0 (2026-06-17), DataStore 1.3.0 (2026-10-21), WorkManager 2.12.0 (2026-09-23), androidx.webkit 1.18.0 (2026-10-21), core-ktx 1.19.1 (2026-09-23), core-splashscreen 1.2.0 (2025-11-05), Glance 1.2.0 (2026-08-26), Coil 3.6.3 (2026-09-18), OkHttp 5.5.1 (2026-10-22), kotlinx-serialization-json 1.12.0 (2026-10-19), kotlinx-coroutines 1.11.0 (2026-05-07) and kotlinx-datetime 0.8.0 (2026-05-07).
  - evidence: maven-metadata plus POM Last-Modified dates (/var/tmp/sniper-toolchain-meta/results*.json); /var/tmp/sniper-toolchain-probe builds a 40.8 MB debug APK using all of them.
  - implication: The final libs.versions.toml in the report is a proven baseline for the real app.
- **TC18** [high] Robolectric 4.17 (2026-09-10) supports SDK 37 and downloads android-all-instrumented 17-robolectric-15733970-i7 (239 MB, cached in ~/.m2). On JDK 21 an SDK 37 test fails at startup: 'java.lang.RuntimeException: Failed to interact with raw FileDescriptor internals; perhaps JRE has changed?' caused by 'module java.base does not export jdk.internal.access to unnamed module'. The fix is the add-opens flag list from robolectric.org, which includes --add-opens=java.base/jdk.internal.access=ALL-UNNAMED.
  - evidence: 02-recordRoborazziDebug.log and its test XML (at com.android.internal.os.ApplicationSharedMemory.create); GitHub release robolectric-4.17 'supports SDK 37'; https://robolectric.org/getting-started/ flag list.
  - implication: Keep testOptions.unitTests.all { it.jvmArgs(...) } exactly as in the final app/build.gradle.kts.
- **TC19** [high] Roborazzi 1.77.0 and Compose ui-test 1.12.1 pull Espresso 3.5.1 transitively. On SDK 37 it fails with 'java.lang.NoSuchMethodException: android.hardware.input.InputManager.getInstance()', thrown from InputManagerEventInjectionStrategy. Espresso 3.7.0 (2025-07-30) calls getSystemService(InputManager.class) on SDK 23 and newer, and forcing testImplementation espresso-core:3.7.0 fixes it.
  - evidence: 03-recordRoborazziDebug.log; `dependencyInsight` shows espresso-core 3.5.1 coming from roborazzi and ui-test-android; espresso-core 3.5.1 vs 3.7.0 sources of androidx/test/espresso/base/InputManagerEventInjectionStrategy.java.
  - implication: Keep libs.androidx.test.espresso.core (3.7.0) in testImplementation while targetSdk is 37.
- **TC20** [high] Roborazzi 1.77.0 (2026-10-22) works with AGP 9.5; its plugin uses the androidComponents API and has explicit 'AGP 9.0: unitTest is now on HasUnitTest' handling. `./gradlew recordRoborazziDebug` wrote a 1078x2422 RGBA PNG using RobolectricDeviceQualifiers.Pixel9a = 'w411dp-h923dp-normal-long-notround-any-420dpi-keyshidden-nonav'. verifyRoborazziDebug passes, and fails with '... is changed. See the compare image at ..._compare.png' after a text change.
  - evidence: Roborazzi repo HEAD 4c9b207 (2026-10-22, tag 1.77.0), roborazzi/src/main/java/com/github/takahirom/roborazzi/RobolectricDeviceQualifiers.kt line 53; build logs 04, 08, 09 and 10; PNG app/src/test/screenshots/com.example.sniperprobe.SnipeScreenScreenshotTest.snipeScreen.png (opened and checked).
  - implication: Use Roborazzi for JVM screenshot tests at the user's Pixel 9a size.
- **TC21** [high] Roborazzi's default golden directory is build/outputs/roborazzi, so `clean` deleted the golden and the next verify failed with 'The original file(...) was not found'. Setting roborazzi { outputDir.set(file("src/test/screenshots")) } fixed it.
  - evidence: 05-verify.log failure; 08-clean-verify.log success after the change; Roborazzi README section 'Gradle DSL Options'.
  - implication: Commit app/src/test/screenshots; record with recordRoborazziDebug and check with verifyRoborazziDebug.
- **TC22** [high] Paparazzi's latest stable 1.3.5 (2024-11-07) fails on AGP 9.5 at configuration: 'Extension of type 'BaseExtension' does not exist'. Paparazzi 2.0.0-alpha05.1 (2026-09-28; release notes list 'Android Gradle Plugin 9.0.0', LayoutLib 16.2.3) recorded a snapshot (461x1000, downscaled) with recordPaparazziDebug in 63 s.
  - evidence: /var/tmp/sniper-toolchain-probe-paparazzi; logs 30-paparazzi-alpha-record.log and 31-paparazzi-1.3.5-record.log; GitHub releases of cashapp/paparazzi.
  - implication: Paparazzi has no stable release that works with the latest AGP; use Roborazzi.
- **TC23** [high] Compose 1.12 deprecates androidx.compose.ui.test.junit4.createComposeRule. The compiler warning says to use androidx.compose.ui.test.junit4.v2.createComposeRule, which uses StandardTestDispatcher instead of UnconfinedTestDispatcher. The v2 rule works with Roborazzi captureRoboImage().
  - evidence: 02-recordRoborazziDebug.log warning text; the final SnipeScreenScreenshotTest uses the v2 import and passes.
  - implication: Write new Compose tests with the v2 rule.
- **TC24** [high] Spotless 8.11.0 with ktlint 1.8.0 and compose-rules 0.6.8 works with the configuration cache. Spotless presets ktlint_code_style=intellij_idea, which overrides .editorconfig; the style must be set with .editorConfigOverride(mapOf("ktlint_code_style" to "ktlint_official")). The compose-rules vm-injection autocorrect moves viewModel() into a parameter of the enclosing composable. Inside a Nav3 entryProvider that would change which nav entry owns the ViewModel, so a separate SnipeListRoute(viewModel = viewModel()) composable is used instead.
  - evidence: Spotless plugin-gradle README: 'intellij_idea is the default style we preset in Spotless'; logs 11-16 (spotlessCheck diffs, then spotlessApply and a passing check).
  - implication: Run ./gradlew spotlessApply before committing. Keep the editorConfigOverride in the root build file.
- **TC25** [medium] The new Android CLI renders Compose @Preview functions headlessly: `android preview list :app` and `android preview render :app --fqn=com.example.sniperprobe.SnipeScreenKt.SnipeScreenPreview --output=DIR`, run from the project directory, produced a 1080x2400 PNG in 24 s through AGP task generateDebugComposePreviewRunfiles. Passing an absolute path as the reference failed with 'Invalid relative Gradle project path'. It caches about 1.1 GB in ~/.android, and analytics.settings has hasOptedIn false.
  - evidence: logs 22-24; PNG /var/tmp/sniper-toolchain-dl/preview-out/com/example/sniperprobe/SnipeScreenKt/SnipeScreenPreview_0.png (opened). Its AssistChip rendered with a filled background, while Roborazzi rendered it outlined.
  - implication: Useful for quick design iteration without tests. Keep Roborazzi as the golden source because the two renderers differ.
- **TC26** [high] Build times on this 64-core desktop with JDK 21: cold first assembleDebug 134 s (includes the Gradle distribution, all dependencies and the build-tools 36 auto-install). With warm caches: clean assembleDebug --no-build-cache 15.8 s, no-op assembleDebug 1.5 s, one-composable edit then assembleDebug 3.8 s, verifyRoborazziDebug --no-build-cache 23.3 s, lintDebug 36 s first run, and the combined clean assembleDebug verifyRoborazziDebug spotlessCheck lintDebug --no-build-cache 41 s. The first Robolectric test in a JVM takes about 11.5-13 s (SDK 37 sandbox start); later screenshots take about 0.5 s each.
  - evidence: /var/tmp/sniper-toolchain-dl/build-logs/01, 17-*, 21 and 40 logs; testsuite time attributes in app/build/test-results/testDebugUnitTest/*.xml.
  - implication: A tight loop of edit, record or verify screenshots, then open the PNG is practical: about 25 s per full verify.
- **TC27** [high] Java ignores the TMPDIR variable. Exporting JAVA_TOOL_OPTIONS=-Djava.io.tmpdir=/var/tmp moves the temp directory of every JVM Gradle starts (client, daemon, Kotlin daemon, test workers) off the RAM-backed /tmp. The only side effect is a 'Picked up JAVA_TOOL_OPTIONS' line on stderr.
  - evidence: /var/tmp/sniper-toolchain-tools/env.sh, used for every build in this report.
  - implication: Run `source /var/tmp/sniper-toolchain-tools/env.sh` before ./gradlew.
- **TC28** [high] Test libraries: Robolectric and Roborazzi run on JUnit 4.13.2 (published 2021, still the latest 4.x). JUnit Jupiter 6.1.3 (2026-08-07) exists but is not needed. Turbine 1.2.1 (2025-06-11), MockK 1.14.11 (2026-05-29) and kotlinx-coroutines-test 1.11.0 passed a ViewModel StateFlow test on JDK 21. MockK's agent prints a harmless 'Sharing is only supported for boot loader classes' JVM warning.
  - evidence: SnipeViewModelTest passed in 40-final-all.log; maven-metadata dates.
  - implication: Keep JUnit 4 for the Android unit and screenshot test source set.
- **TC29** [medium] Android lint runs with compileSdk 37.2 and reports 0 errors and 2 warnings (DataExtractionRules, MissingApplicationIcon). It did not flag any dependency as having a newer version.
  - evidence: app/build/reports/lint-results-debug.txt (21-lintDebug.log, 40-final-all.log).
  - implication: The real app needs an icon and data_extraction_rules.xml (or allowBackup false plus rules) to get a clean lint.

## Open questions
- Material 3 Expressive needs material3 1.5.0-beta02, a pre-release: is that acceptable for this app, or should it stay on stable 1.4.0 (proven baseline) and add Expressive later? Both build and render here.
- Room 2.8.5 (proven baseline) or Room 3.0.3 (stable since 2026-07-01; package androidx.room3; needs a SQLiteDriver such as BundledSQLiteDriver and suspend-only DAO calls; compiled in the scratch variant)?
- AGP 9.5.0 + Gradle 9.8.2 is past the range KGP 2.4.21 is documented as tested with (Gradle up to 9.7.0, AGP up to 9.3.1). It built with zero warnings; if issues appear later, the fallback is AGP 9.3.x with Gradle 9.7.x.
- The release build with R8 (AGP 9 strict full mode and optimized resource shrinking on by default) was not tested; keep rules for kotlinx-serialization and Room need checking when assembleRelease is set up.
- No device or emulator here: on-device behaviour on the Pixel 9a (assumed, not checked, to run Android 17 / API 37), WebView sign-in, exact alarms and notifications must be checked after sideloading the APK.

## Report
# Android toolchain report: versions and a proven headless build (2026-11-04)

Machine: a Linux build host without KVM (no emulator), JDK 21.0.13. No Gradle, Kotlin, `~/.gradle` or `~/.m2` existed beforehand. No eBay host was contacted. Network hosts used: dl.google.com, repo1.maven.org, plugins.gradle.org, services.gradle.org, github.com/api.github.com (via gh), developer.android.com, kotlinlang.org, robolectric.org.

## 0. Verdict

The working project is `/var/tmp/sniper-toolchain-probe`. It uses AGP 9.5.0, Gradle 9.8.2, Kotlin 2.4.21 (built-in Kotlin), KSP 2.3.13, compileSdk 37.2, targetSdk 37, minSdk 31, Compose BOM 2026.09.00, Robolectric 4.17 and Roborazzi 1.77.0. It builds a debug APK and a JVM screenshot PNG at Pixel 9a size. The PNG was opened and checked. The full catalog compiles in one APK, and all tests, Spotless, ktlint and lint pass.

```
source /var/tmp/sniper-toolchain-tools/env.sh
cd /var/tmp/sniper-toolchain-probe
./gradlew clean assembleDebug verifyRoborazziDebug spotlessCheck lintDebug --no-build-cache
# BUILD SUCCESSFUL in 40s, 65 actionable tasks: 65 executed (warm caches)
```

## 1. SDK install (exact commands and results)

```
mkdir -p /var/tmp/sniper-toolchain-dl ~/android-sdk/cmdline-tools
curl -sS -o /var/tmp/sniper-toolchain-dl/commandlinetools-linux-16111833_latest.zip \
  https://dl.google.com/android/repository/commandlinetools-linux-16111833_latest.zip
sha1sum ...zip   # e025545c62a8e64c7559119566a569fb1dec5f60 = value in repository2-3.xml (cmdline-tools;23.0)
unzip -q ...zip -d unz && mv unz/cmdline-tools ~/android-sdk/cmdline-tools/latest
export ANDROID_HOME=~/android-sdk JAVA_HOME=/path/to/jdk-21 TMPDIR=/var/tmp
export PATH=$ANDROID_HOME/cmdline-tools/latest/bin:$JAVA_HOME/bin:$PATH
yes | sdkmanager --sdk_root=$ANDROID_HOME --licenses
#  WARNING: The SDK Manager CLI tool (sdkmanager) is deprecated. Android CLI will be used instead.
#  The 'android' binary can also be found in the cmdline-tools directory, and 'android sdk' is the replacement for 'sdkmanager'.
#  Warning: The --licenses option is no longer needed.      (exit 0, no licenses/ dir created)
android sdk install platform-tools build-tools/37.0.0 platforms/android-37.2    # 15.6 s, exit 0
# creates ~/android-sdk/licenses/android-sdk-license = 24333f8a63b6825ea9c5514f83c2829b004d1fee
```

The SDK manifest is `https://dl.google.com/android/repository/repository2-3.xml`, where channel-0 is stable. Release dates below are Last-Modified headers of the zips:

| Package | Version | Date | Notes |
|---|---|---|---|
| cmdline-tools | 23.0 (`commandlinetools-linux-16111833_latest.zip`) | 2026-08-19 | ships `android` CLI 1.1.16717390; `sdkmanager` forwards to it |
| platforms | `platforms;android-37.2` r02 (`api-level 37.2`, `extension-level 24`, no codename) | 2026-10-28 | also stable: android-37.1 (2026-07-01), android-37.0 r02 (2026-06-04), android-36.1; android-37.2-beta1..3 and CANARY exist |
| build-tools | 37.0.0 | 2026-03-26 | 37.0.0-rc1/rc2 older |
| platform-tools | 37.0.1 (adb `Version 37.0.1-15733141`) | 2026-07-30 | |

New CLI syntax uses slashes: `android sdk list --all`, `android sdk install build-tools/37.0.0`. The final installed set from `android sdk list` is `build-tools/36.0.0` (auto-installed by AGP, see P2), `build-tools/37.0.0`, `cmdline-tools/latest 23.0.0`, `platform-tools 37.0.1` and `platforms/android-37.2`. Disk use: `~/android-sdk` about 0.7 GB, `~/.gradle` 5.2 GB (includes the variant projects), `~/.m2` 228 MB (Robolectric android-all jar), `~/.android` 1.1 GB (Android CLI and preview packs).

Android 17 (API 37) shipped 2026-06-16 (androidcentral.com, "Android 17 is finally here"). Inferred: the user's Pixel 9a runs Android 17 by now; this was not checked on the device.

## 2. Latest stable versions (read from maven-metadata.xml on 2026-11-04)

Dates are the HTTP Last-Modified of each version's POM. "Newer pre-release" means a pre-release newer than the stable. The raw metadata is in `/var/tmp/sniper-toolchain-meta/mm/` and the parsed results in `/var/tmp/sniper-toolchain-meta/results{1,2,3,4}.json`. The script is `/var/tmp/sniper-toolchain-tools/mavenmeta.py`.

| Component | Coordinates / source | Latest stable | Date | Newer pre-release |
|---|---|---|---|---|
| Android Gradle Plugin | google `com.android.tools.build:gradle` | **9.5.0** | 2026-10-28 | 9.6.0-alpha02 (2026-10-28) |
| Gradle | services.gradle.org/versions/current | **9.8.2** (buildTime 20261019) | 2026-10-19 | 9.9.0-rc-1 (2026-10-30) |
| Kotlin (KGP) | central `org.jetbrains.kotlin:kotlin-gradle-plugin` | **2.4.21** | 2026-10-08 | 2.5.0-Beta2 (2026-10-21) |
| Compose compiler plugin | portal `org.jetbrains.kotlin.plugin.compose` (artifact `org.jetbrains.kotlin:compose-compiler-gradle-plugin`) | **2.4.21** (same as Kotlin) | 2026-10-08 | 2.5.0-Beta2 |
| KSP | central `com.google.devtools.ksp:symbol-processing-gradle-plugin`, portal id `com.google.devtools.ksp` | **2.3.13** | 2026-10-30 | none |
| Compose BOM | google `androidx.compose:compose-bom` | **2026.09.00** | 2026-09-09 | `compose-bom-beta` / `compose-bom-alpha` 2026.10.01 (2026-10-21) |
| Compose ui/foundation/runtime | google `androidx.compose.ui:ui` | **1.12.1** (BOM) | 2026-09-09 | 1.13.0-beta02 (2026-10-21) |
| material3 | google `androidx.compose.material3:material3` | **1.4.0** (BOM) | 2025-09-24 | 1.5.0-beta02 (2026-10-21), after 1.5.0-alpha01..alpha29, beta01 |
| material-icons-extended / -core | google `androidx.compose.material:material-icons-extended` | **1.7.8** (frozen) | 2025-02-12 | none |
| activity-compose | google `androidx.activity:activity-compose` | **1.13.0** | 2026-03-11 | 1.14.0-alpha03 (2026-09-23) |
| lifecycle (runtime-compose, viewmodel-compose, viewmodel-navigation3) | google `androidx.lifecycle:*` | **2.11.0** | 2026-06-17 | 2.12.0-alpha05 (2026-10-21) |
| navigation-compose (classic) | google `androidx.navigation:navigation-compose` | **2.10.2** | 2026-09-23 | none |
| Navigation 3 | google `androidx.navigation3:navigation3-runtime`, `navigation3-ui` | **1.2.0** | 2026-09-23 | 1.3.0-alpha03 (2026-10-21) |
| M3 adaptive navigation3 | google `androidx.compose.material3.adaptive:adaptive-navigation3` | **1.3.0** (in BOM) | 2026-08-12 | 1.4.0-alpha03 (2026-10-07) |
| Room 2 | google `androidx.room:room-runtime` / `room-compiler`, plugin `androidx.room` | **2.8.5** | 2026-09-09 | none |
| Room 3 | google `androidx.room3:room3-runtime` / `room3-compiler`, plugin `androidx.room3` | **3.0.3** (3.0.0 stable 2026-07-01) | 2026-09-09 | 3.1.0-alpha03 (2026-10-21) |
| sqlite-bundled (Room 3 driver) | google `androidx.sqlite:sqlite-bundled` | **2.7.1** | 2026-09-09 | 2.8.0-alpha03 |
| DataStore | google `androidx.datastore:datastore-preferences` | **1.3.0** | 2026-10-21 | none |
| WorkManager | google `androidx.work:work-runtime-ktx` | **2.12.0** | 2026-09-23 | none |
| androidx.webkit | google `androidx.webkit:webkit` | **1.18.0** | 2026-10-21 | none |
| core-ktx | google `androidx.core:core-ktx` | **1.19.1** | 2026-09-23 | none |
| core-splashscreen | google `androidx.core:core-splashscreen` | **1.2.0** | 2025-11-05 | none |
| Glance | google `androidx.glance:glance-appwidget`, `glance-material3` | **1.2.0** | 2026-08-26 | 1.3.0-alpha02 (2026-07-01) |
| Coil 3 | central `io.coil-kt.coil3:coil-compose`, `coil-network-okhttp` | **3.6.3** | 2026-09-18 | none |
| OkHttp | central `com.squareup.okhttp3:okhttp` (also `mockwebserver3`) | **5.5.1** | 2026-10-22 | none |
| kotlinx-serialization | central `org.jetbrains.kotlinx:kotlinx-serialization-json` | **1.12.0** | 2026-10-19 | none |
| kotlinx-coroutines | central `org.jetbrains.kotlinx:kotlinx-coroutines-*` | **1.11.0** | 2026-05-07 | none |
| kotlinx-datetime | central `org.jetbrains.kotlinx:kotlinx-datetime` | **0.8.0** (`0.8.0-0.6.x-compat` is a compat build) | 2026-05-07 | none |
| Robolectric | central `org.robolectric:robolectric` | **4.17** ("supports SDK 37") | 2026-09-10 | none; android-all-instrumented `17-robolectric-15733970-i7` |
| Roborazzi | central `io.github.takahirom.roborazzi:*`, plugin `io.github.takahirom.roborazzi` | **1.77.0** | 2026-10-22 | none |
| Paparazzi | central `app.cash.paparazzi:paparazzi`, plugin `app.cash.paparazzi` | **1.3.5** (fails on AGP 9) | 2024-11-07 | 2.0.0-alpha05.1 (2026-09-28), works |
| JUnit 4 | central `junit:junit` | **4.13.2** | 2021-02-13 | none |
| JUnit Jupiter | central `org.junit.jupiter:junit-jupiter` | 6.1.3 (not used) | 2026-08-07 | none |
| androidx.test core / ext.junit / espresso-core | google | **1.7.0 / 1.3.0 / 3.7.0** | 2025-07-30 | 1.8.0-alpha03 / 1.4.0-alpha03 / 3.8.0-alpha03 (2026-10-29) |
| Turbine | central `app.cash.turbine:turbine` | **1.2.1** | 2025-06-11 | none |
| MockK | central `io.mockk:mockk` | **1.14.11** | 2026-05-29 | none |
| Spotless | portal `com.diffplug.spotless` | **8.11.0** | 2026-11-02 | none |
| ktlint | central `com.pinterest.ktlint:ktlint-cli` | **1.8.0** | 2025-11-14 | none |
| Compose ktlint rules | central `io.nlopez.compose.rules:ktlint` | **0.6.8** | 2026-10-09 | none |
| ktlint Gradle plugin (alternative) | portal `org.jlleitschuh.gradle.ktlint` | 14.2.0 (not used) | 2026-03-12 | |
| ktfmt (alternative) | central `com.facebook:ktfmt` | 0.64 (not used) | 2026-06-24 | |
| MDC (XML themes, not needed) | google `com.google.android.material:material` | 1.14.0 (not used) | 2026-05-13 | |

Compatibility facts read from sources:
- **AGP 9.5** (developer.android.com/build/releases/gradle-plugin, fetched 2026-11-04): "The maximum API level that Android Gradle plugin 9.5 supports is API level 37." Gradle min/default 9.6.0, SDK Build Tools min/default 36.0.0, NDK default 28.2.13676358, JDK 17.
- **AGP 9.0 release notes** (developer.android.com/build/releases/agp-9-0-0-release-notes): built-in Kotlin is on by default ("you no longer have to apply the `org.jetbrains.kotlin.android` (or `kotlin-android`) plugin"). `org.jetbrains.kotlin.android` "is not compatible with the new DSL". AGP has a "runtime dependency on Kotlin Gradle plugin (KGP) 2.2.10". `kotlin-kapt` is incompatible; use KSP or `com.android.legacy-kapt`. Changed defaults include `android.newDsl` and `android.builtInKotlin` true, `android.sdk.defaultTargetSdkToCompileSdkIfUnset` true, `android.r8.strictFullModeForKeepRules` true, `android.r8.optimizedResourceShrinking` true and `android.onlyEnableUnitTestForTheTestedBuildType` true. Default Java source/target compatibility changed from 8 to 11 (issue 369246556). "The ability to opt-out will be removed in AGP 10.0 (mid-2026)" (AGP 10 is not out; the newest is 9.6.0-alpha02).
- **Built-in Kotlin migration** (developer.android.com/build/migrate-to-built-in-kotlin): replace `android.kotlinOptions {}` with `kotlin { compilerOptions { ... } }`. "With built-in Kotlin, you don't need to set `kotlin.compilerOptions.jvmTarget` because its value defaults to `android.compileOptions.targetCompatibility`". Extra Kotlin source directories go only in `android.sourceSets.named("main") { kotlin.directories += ... }`.
- **AGP 9.5.0 POM** (`gradle-9.5.0.pom`) depends on `org.jetbrains.kotlin:kotlin-gradle-plugin 2.2.10` and lists `com.google.devtools.ksp:symbol-processing-gradle-plugin 2.2.10-2.0.2`. The `compose-compiler-gradle-plugin-2.4.21.pom` has a compile dependency on `kotlin-gradle-plugin 2.4.21`. That is why the root `plugins { alias(libs.plugins.kotlin.compose) apply false }` lifts KGP. Verified: `./gradlew buildEnvironment` printed `org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.10 -> 2.4.21`.
- **KGP compatibility** (kotlinlang.org/docs/gradle-configure-project.html, page date 06 October 2026): `2.4.20-2.4.21 | Gradle 7.6.3-9.7.0 | AGP 8.5.2-9.3.1`. The chosen AGP 9.5 + Gradle 9.8.2 is past that tested range but built with zero warnings.
- **compileSdk with a minor level** (gradle-common-api-9.5.0-sources.jar `CompileSdkSpec.kt`): `compileSdk { version = release(33) { minorApiLevel = 1; sdkExtension = 18 } }`; `CompileSdkReleaseSpec` has `var minorApiLevel: Int?`. CommonExtension also exposes `compileSdkMinor`, and `compileSdkVersion(...)` is "Deprecated ... Will be removed in AGP 10.0".
- **AAR metadata of the libraries used** (from `META-INF/com/android/build/gradle/aar-metadata.properties` in cached AARs):
  - minCompileSdk=37 and minAndroidGradlePluginVersion=9.1.0: core 1.19.1, compose ui-android 1.12.1, material3 1.5.0-beta02, navigation3-runtime/ui 1.2.0, lifecycle-viewmodel-navigation3 2.11.0, adaptive-navigation3 1.3.0.
  - minCompileSdk=37 with AGP min 1.0.0: coil-compose 3.6.3.
  - minCompileSdk=36 (AGP 8.9.1): activity 1.13.0.
  - minCompileSdk=35 (AGP 8.6.0): material3 1.4.0, work-runtime 2.12.0, glance-appwidget 1.2.0, core-splashscreen 1.2.0.
  - minCompileSdk=34: room-runtime 2.8.5, datastore 1.3.0, lifecycle-runtime 2.11.0.
  - minCompileSdk=33: webkit 1.18.0.

## 3. Material 3 Expressive status (read from sources jars, then compiled and rendered)

- **Stable material3 1.4.0** (2025-09-24, the version in BOM 2026.09.00) has no public Expressive API. In `material3-android-1.4.0-sources.jar`:
  - `MaterialTheme.kt:185 internal fun MaterialExpressiveTheme(`
  - `MotionScheme.kt:42 internal interface MotionScheme`
  - `ColorScheme.kt:1138 internal fun expressiveLightColorScheme()`
  - `AppBar.kt:1287 internal fun FlexibleBottomAppBar(`
  - LoadingIndicator, ButtonGroup, HorizontalFloatingToolbar, MaterialShapes, `RoundedPolygon.toShape`, SplitButtonLayout, ToggleButton, FloatingActionButtonMenu and wavy progress are absent.
  - Public in 1.4.0: `ShortNavigationBar`, `WideNavigationRail`. There are 0 uses of the `@ExperimentalMaterial3ExpressiveApi` annotation on public API.
- **material3 1.5.0-beta02** (2026-10-21; pre-release) makes these public with no opt-in:
  - `public fun MaterialExpressiveTheme(colorScheme: ColorScheme? = null, motionScheme: MotionScheme? = null, shapes: Shapes? = null, typography: Typography? = null, content)`
  - `public interface MotionScheme` with `MotionScheme.expressive()` / `standard()` in its companion
  - `expressiveLightColorScheme()`
  - `ButtonGroup(overflowIndicator = { ButtonGroupDefaults.OverflowIndicator(it) }) { clickableItem(...); toggleableItem(...); customItem(...) }`
  - `HorizontalFloatingToolbar(expanded, ...)`, `FlexibleBottomAppBar`, `SplitButtonLayout`, `ToggleButton`, `FloatingActionButtonMenu`, `LinearWavyProgressIndicator`
- **Still `@ExperimentalMaterial3ExpressiveApi` in 1.5.0-beta02** (all 15 annotation sites): `LoadingIndicator` (2 overloads), `ContainedLoadingIndicator` (2), `LoadingIndicatorDefaults`, `MaterialShapes` (sealed class plus companion), `Morph.toPath`, `RoundedPolygon.toPath`, `RoundedPolygon.toShape` (that is, **shape morphing**), and 2 `DropdownMenuItem` overloads in Menu.kt. The annotation is a plain `@RequiresOptIn(...)`, whose default level is ERROR.
- Empirical check in `/var/tmp/sniper-toolchain-probe-m3e`. This is a copy of the probe with only material3 overridden to `1.5.0-beta02`; material3 1.5.0-beta02's POM needs Compose 1.12.0, so the stable BOM's 1.12.1 stays.
  - `ExpressiveProbeTheme` (MaterialExpressiveTheme + expressiveLightColorScheme + MotionScheme.expressive()) compiled **without** `@OptIn`.
  - A screen with `MaterialShapes.Cookie9Sided.toShape()`, `LinearWavyProgressIndicator`, `ButtonGroup` toggles, `LoadingIndicator()` and `HorizontalFloatingToolbar` rendered under Robolectric 4.17 + Roborazzi 1.77.0. The PNG was checked: `app/src/test/screenshots/com.example.sniperprobe.ExpressiveScreenshotTest.expressiveSnipeScreen.png`, 1078x2422.
  - The infinite LoadingIndicator animation needs a manual clock:

```kotlin
// LoadingIndicator animates forever; drive the clock by hand so the test can go idle.
composeRule.mainClock.autoAdvance = false
composeRule.setContent {
    ExpressiveProbeTheme { ExpressiveSnipeScreen(state = SnipeUiState.sample) }
}
composeRule.mainClock.advanceTimeBy(700)
composeRule.onRoot().captureRoboImage()
```

To opt in, add the following to the catalog and give `androidx-compose-material3` `version.ref = "material3Expressive"`:

```toml
material3Expressive = "1.5.0-beta02"
```

**Icons.** `material-icons-extended` stopped at 1.7.8 (2025-02-12). developer.android.com/develop/ui/compose/graphics/images/material says: "this artifact is no longer maintained or recommended for use in your apps, as it contains an older look and feel and can also increase the build time of your apps *significantly*. Instead, we recommend using Google Font Icons and download the XML file from the Android Tab to create an up-to-date Material Symbols style Icon." Use `Icon(painter = painterResource(R.drawable.x), contentDescription = ...)`.

## 4. Build proof: commands, timings, outputs

Wrapper generation was a one-off and is already done; `gradlew`, `gradlew.bat` and `gradle/wrapper/*` are in the probe:

```
curl -sSL -o gradle-9.8.2-bin.zip https://services.gradle.org/distributions/gradle-9.8.2-bin.zip   # sha256 21bd5da2...7607 OK
unzip -q gradle-9.8.2-bin.zip -d gradle-dist
cd /var/tmp/sniper-toolchain-dl/wrapper-gen && echo 'rootProject.name = "wrapper-gen"' > settings.gradle.kts
/var/tmp/sniper-toolchain-dl/gradle-dist/gradle-9.8.2/bin/gradle --no-daemon -q wrapper --gradle-version 9.8.2 \
  --distribution-type bin --gradle-distribution-sha256-sum 21bd5da2de49b642904077f38c0efdc7778e92b3ed0889392dca61b97e389607
# gradle-wrapper.jar sha256 3b8a25775a69158b5ad2b1d17a80a88dc7b40a352a73eb27d06c612a7ce68e98 = services.gradle.org wrapperChecksum
```

Environment script `/var/tmp/sniper-toolchain-tools/env.sh` (verbatim):

```sh
# Source this before running ./gradlew in the probe or the app.
export JAVA_HOME=/path/to/jdk-21
export ANDROID_HOME="$HOME/android-sdk"
export TMPDIR=/var/tmp
# Java ignores TMPDIR; this moves java.io.tmpdir for every JVM Gradle starts
# (client, daemon, Kotlin daemon, test workers) off the RAM-backed /tmp.
export JAVA_TOOL_OPTIONS="-Djava.io.tmpdir=/var/tmp"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
```

`./gradlew --version` reports Gradle 9.8.2 (build 2026-10-19 16:29:26 UTC), embedded Kotlin 2.4.10 for build scripts, and launcher and daemon JVM 21.0.13 ("no Daemon JVM specified, using current Java home").

Run log (all logs are in `/var/tmp/sniper-toolchain-dl/build-logs/`):

| Step | Command | Result | Time |
|---|---|---|---|
| 01 | `./gradlew assembleDebug` (minimal app, cold: Gradle dist, all deps, auto-install build-tools 36) | SUCCESS, 36 tasks, 29.6 MB APK | 134 s wall ("2m 12s") |
| 02 | `./gradlew recordRoborazziDebug` | FAIL: Robolectric IllegalAccessException (P3) | 81 s |
| 03 | same, after the add-opens fix | FAIL: Espresso NoSuchMethodException (P4) | 19 s |
| 04 | same, after forcing Espresso 3.7.0 | SUCCESS; PNG 1078x2422 (includes 239 MB android-all download) | 62 s |
| 06 | `./gradlew assembleDebug` with the full catalog (Room/KSP, Nav3, ...) | SUCCESS, 40.8 MB APK; `kspDebugKotlin`, `copyRoomSchemas` ran | 73 s |
| 07 | `./gradlew recordRoborazziDebug` (goldens in src/test/screenshots) | SUCCESS, 2 test classes pass | 47 s |
| 08 | `./gradlew clean verifyRoborazziDebug` | SUCCESS (testDebugUnitTest executed, not cached) | 16 s |
| 09 | UI text changed, then `verifyRoborazziDebug` | FAIL as expected: "...snipeScreen.png is changed. See the compare image at ..._compare.png" | 17 s |
| 17 | `clean` then `assembleDebug --no-build-cache` | SUCCESS, 37 tasks | 15.8 s |
| 17 | `assembleDebug` no-op | 37 up-to-date | 1.5 s |
| 17 | edit one string in a composable, then `assembleDebug` | 5 tasks executed | 3.8 s |
| 17 | `verifyRoborazziDebug --no-build-cache` | SUCCESS | 23.3 s |
| 21 | `./gradlew lintDebug` | 0 errors, 2 warnings (DataExtractionRules, MissingApplicationIcon) | 36 s |
| 15/16 | `./gradlew spotlessApply` then `spotlessCheck` | SUCCESS | 1 s / 3 s |
| 40 | `./gradlew clean assembleDebug verifyRoborazziDebug spotlessCheck lintDebug --no-build-cache` | SUCCESS, 65 tasks | 41 s |

Per-test times: the first Robolectric test in a JVM takes 11.5-13.1 s (SDK 37 sandbox start). A second screenshot in the same JVM took 0.465 s. The plain JVM ViewModel test takes about 2 s.

Outputs:
- Golden PNG: `/var/tmp/sniper-toolchain-probe/app/src/test/screenshots/com.example.sniperprobe.SnipeScreenScreenshotTest.snipeScreen.png`, 1078x2422 RGBA. Opened and checked: Roboto text, £ glyphs, M3 card, linear progress with stop dot, assist chip, filled button.
- First PNG (before the outputDir change): `app/build/outputs/roborazzi/...` (since deleted by clean). HTML report: `app/build/reports/roborazzi/debug/index.html`.
- APK: `app/build/outputs/apk/debug/app-debug.apk`; Room schema: `app/schemas/com.example.sniperprobe.data.SnipeDatabase/1.json`.

## 5. Problems and fixes

- **P1. sdkmanager is deprecated** (cmdline-tools 23.0) and `--licenses` is a no-op. Use `android sdk install <pkg>`; it accepts the license and writes `licenses/android-sdk-license`.
- **P2. AGP auto-installs Build-Tools 36.0.0**, its default: "Installing Android SDK Build-Tools 36 in .../build-tools/36.0.0". Fix: `buildToolsVersion = "37.0.0"` (in the final file).
- **P3. Robolectric 4.17 + SDK 37 + JDK 21** fails with `java.lang.RuntimeException: Failed to interact with raw FileDescriptor internals; perhaps JRE has changed?` at `org.robolectric.interceptors.AndroidInterceptors$FileDescriptorInterceptor.setInt(AndroidInterceptors.java:88)` / `com.android.internal.os.ApplicationSharedMemory.create(ApplicationSharedMemory.java:106)`. The cause is `IllegalAccessException: ... cannot access class jdk.internal.access.SharedSecrets (in module java.base) because module java.base does not export jdk.internal.access to unnamed module`. Fix: the 9 `--add-opens` flags from https://robolectric.org/getting-started/ in `testOptions.unitTests.all { it.jvmArgs(...) }`.
- **P4. Espresso 3.5.1** (pulled by `roborazzi:1.77.0` and `androidx.compose.ui:ui-test-android:1.12.1`) fails on SDK 37 with `java.lang.NoSuchMethodException: android.hardware.input.InputManager.getInstance()` at `androidx.test.espresso.base.InputManagerEventInjectionStrategy.initialize`. Espresso 3.7.0 uses `getContext().getSystemService(InputManager.class)` for SDK 23 and newer. Fix: `testImplementation(libs.androidx.test.espresso.core)` = 3.7.0.
- **P5. Compose 1.12 deprecation**: `'fun createComposeRule(effectContext: CoroutineContext = ...): ComposeContentTestRule' is deprecated. Use androidx.compose.ui.test.junit4.v2.createComposeRule instead.` Fix: import `androidx.compose.ui.test.junit4.v2.createComposeRule`.
- **P6. Goldens deleted by `clean`**: `Roborazzi: The original file(.../build/outputs/roborazzi/...png) was not found.` Fix: `roborazzi { outputDir.set(file("src/test/screenshots")) }`.
- **P7. Spotless ignores `ktlint_code_style` in .editorconfig.** Spotless presets intellij_idea through `editorConfigOverride`. Fix: `.editorConfigOverride(mapOf("ktlint_code_style" to "ktlint_official"))`. Other .editorconfig keys (`ktlint_function_naming_ignore_when_annotated_with = Composable`, `max_line_length`) are still read.
- **P8. compose-rules `vm-injection` autocorrect conflicts with Navigation 3.** It rewrote `fun AppNavigation()` to take `viewModel: SnipeViewModel = viewModel()`, which would create the ViewModel outside the nav entry. Fix: a separate `@Composable private fun SnipeListRoute(viewModel: SnipeViewModel = viewModel())` called inside `entry<SnipeListKey> { SnipeListRoute() }`.
- **P9. Infinite animations** (LoadingIndicator) in screenshot tests: set `mainClock.autoAdvance = false`, then `advanceTimeBy(...)`.
- **P10. Java ignores TMPDIR.** Fix: `JAVA_TOOL_OPTIONS=-Djava.io.tmpdir=/var/tmp` (env.sh).
- **P11. Harmless output**: `Unable to strip the following libraries, packaging them as they are: libandroidx.graphics.path.so, libdatastore_shared_counter.so` (no NDK installed). `OpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended` (MockK agent). `11-04 ... I HWUI : Using FreeType backend` (Robolectric native graphics).
- **P12. Paparazzi 1.3.5 + AGP 9.5**: `Could not create task ':app:preparePaparazziDebugResources'. > Extension of type 'BaseExtension' does not exist.` Use Roborazzi, or Paparazzi 2.0.0-alpha05.1, which works.

## 6. Final working files (verbatim from /var/tmp/sniper-toolchain-probe)

### gradle/libs.versions.toml

```toml
[versions]
# Build toolchain
agp = "9.5.0"
kotlin = "2.4.21"
ksp = "2.3.13"
spotless = "8.11.0"
ktlint = "1.8.0"
composeRulesKtlint = "0.6.8"

# AndroidX / Compose
composeBom = "2026.09.00"
activityCompose = "1.13.0"
coreKtx = "1.19.1"
coreSplashscreen = "1.2.0"
lifecycle = "2.11.0"
navigationCompose = "2.10.2"
navigation3 = "1.2.0"
room = "2.8.5"
datastore = "1.3.0"
work = "2.12.0"
webkit = "1.18.0"
glance = "1.2.0"

# Kotlin / third party
kotlinxCoroutines = "1.11.0"
kotlinxSerialization = "1.12.0"
kotlinxDatetime = "0.8.0"
coil = "3.6.3"
okhttp = "5.5.1"

# Tests
junit4 = "4.13.2"
androidxTestCore = "1.7.0"
androidxTestExtJunit = "1.3.0"
espresso = "3.7.0"
robolectric = "4.17"
roborazzi = "1.77.0"
turbine = "1.2.1"
mockk = "1.14.11"

[libraries]
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-compose-ui = { group = "androidx.compose.ui", name = "ui" }
androidx-compose-ui-graphics = { group = "androidx.compose.ui", name = "ui-graphics" }
androidx-compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
androidx-compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
androidx-compose-ui-test-junit4 = { group = "androidx.compose.ui", name = "ui-test-junit4" }
androidx-compose-ui-test-manifest = { group = "androidx.compose.ui", name = "ui-test-manifest" }
androidx-compose-material3 = { group = "androidx.compose.material3", name = "material3" }
androidx-compose-material3-adaptive-navigation3 = { group = "androidx.compose.material3.adaptive", name = "adaptive-navigation3" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
androidx-core-splashscreen = { group = "androidx.core", name = "core-splashscreen", version.ref = "coreSplashscreen" }
androidx-lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycle" }
androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }
androidx-lifecycle-viewmodel-navigation3 = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-navigation3", version.ref = "lifecycle" }
androidx-navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigationCompose" }
androidx-navigation3-runtime = { group = "androidx.navigation3", name = "navigation3-runtime", version.ref = "navigation3" }
androidx-navigation3-ui = { group = "androidx.navigation3", name = "navigation3-ui", version.ref = "navigation3" }
androidx-room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
androidx-room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
androidx-room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
androidx-datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }
androidx-work-runtime-ktx = { group = "androidx.work", name = "work-runtime-ktx", version.ref = "work" }
androidx-work-testing = { group = "androidx.work", name = "work-testing", version.ref = "work" }
androidx-webkit = { group = "androidx.webkit", name = "webkit", version.ref = "webkit" }
androidx-glance-appwidget = { group = "androidx.glance", name = "glance-appwidget", version.ref = "glance" }
androidx-glance-material3 = { group = "androidx.glance", name = "glance-material3", version.ref = "glance" }
kotlinx-coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "kotlinxCoroutines" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "kotlinxCoroutines" }
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "kotlinxSerialization" }
kotlinx-datetime = { group = "org.jetbrains.kotlinx", name = "kotlinx-datetime", version.ref = "kotlinxDatetime" }
coil-compose = { group = "io.coil-kt.coil3", name = "coil-compose", version.ref = "coil" }
coil-network-okhttp = { group = "io.coil-kt.coil3", name = "coil-network-okhttp", version.ref = "coil" }
okhttp = { group = "com.squareup.okhttp3", name = "okhttp", version.ref = "okhttp" }
okhttp-mockwebserver3 = { group = "com.squareup.okhttp3", name = "mockwebserver3", version.ref = "okhttp" }
junit4 = { group = "junit", name = "junit", version.ref = "junit4" }
androidx-test-core = { group = "androidx.test", name = "core", version.ref = "androidxTestCore" }
androidx-test-ext-junit = { group = "androidx.test.ext", name = "junit", version.ref = "androidxTestExtJunit" }
androidx-test-espresso-core = { group = "androidx.test.espresso", name = "espresso-core", version.ref = "espresso" }
robolectric = { group = "org.robolectric", name = "robolectric", version.ref = "robolectric" }
roborazzi = { group = "io.github.takahirom.roborazzi", name = "roborazzi", version.ref = "roborazzi" }
roborazzi-compose = { group = "io.github.takahirom.roborazzi", name = "roborazzi-compose", version.ref = "roborazzi" }
roborazzi-junit-rule = { group = "io.github.takahirom.roborazzi", name = "roborazzi-junit-rule", version.ref = "roborazzi" }
turbine = { group = "app.cash.turbine", name = "turbine", version.ref = "turbine" }
mockk = { group = "io.mockk", name = "mockk", version.ref = "mockk" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
room = { id = "androidx.room", version.ref = "room" }
roborazzi = { id = "io.github.takahirom.roborazzi", version.ref = "roborazzi" }
spotless = { id = "com.diffplug.spotless", version.ref = "spotless" }
```

The catalog defines four entries that the probe does not use: `androidx-compose-ui-graphics`, `androidx-navigation-compose`, `androidx-room-ktx` and `androidx-test-core`. Room 2.8 runtime already supports suspend and Flow DAOs.

### settings.gradle.kts

```kotlin
pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "sniper-toolchain-probe"
include(":app")
```

### build.gradle.kts (root)

```kotlin
// Declaring every plugin here (apply false) keeps one shared build classpath.
// The Compose compiler plugin pulls kotlin-gradle-plugin at the same version,
// which lifts AGP 9's bundled KGP (2.2.10) to the catalog's Kotlin version.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.roborazzi) apply false
    alias(libs.plugins.spotless)
}

// Spotless presets ktlint_code_style=intellij_idea and that preset beats .editorconfig,
// so the code style is set here.
val ktlintStyle = mapOf("ktlint_code_style" to "ktlint_official")

spotless {
    kotlin {
        target("**/*.kt")
        targetExclude("**/build/**")
        ktlint(libs.versions.ktlint.get())
            .editorConfigOverride(ktlintStyle)
            .customRuleSets(listOf("io.nlopez.compose.rules:ktlint:${libs.versions.composeRulesKtlint.get()}"))
    }
    kotlinGradle {
        target("**/*.gradle.kts")
        targetExclude("**/build/**")
        ktlint(libs.versions.ktlint.get()).editorConfigOverride(ktlintStyle)
    }
}
```

### app/build.gradle.kts (after spotlessApply)

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "com.example.sniperprobe"
    compileSdk {
        version =
            release(37) {
                minorApiLevel = 2
            }
    }
    buildToolsVersion = "37.0.0"

    defaultConfig {
        applicationId = "com.example.sniperprobe"
        minSdk = 31
        targetSdk = 37
        versionCode = 1
        versionName = "0.1"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                // Robolectric 4.17 on JDK 17+ (robolectric.org/getting-started).
                // SDK 37 needs jdk.internal.access at startup (ApplicationSharedMemory).
                it.jvmArgs(
                    "--add-opens=java.base/java.lang=ALL-UNNAMED",
                    "--add-opens=java.base/java.util=ALL-UNNAMED",
                    "--add-opens=java.base/java.io=ALL-UNNAMED",
                    "--add-opens=java.base/java.net=ALL-UNNAMED",
                    "--add-opens=java.base/java.security=ALL-UNNAMED",
                    "--add-opens=java.base/java.text=ALL-UNNAMED",
                    "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",
                    "--add-opens=java.desktop/java.awt.font=ALL-UNNAMED",
                    "--add-opens=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
                )
            }
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

roborazzi {
    // Goldens live outside build/ so `clean` keeps them; commit this folder.
    outputDir.set(file("src/test/screenshots"))
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive.navigation3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.webkit)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.okhttp)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.test.ext.junit)
    // Roborazzi 1.77.0 and Compose ui-test 1.12.1 pull Espresso 3.5.x, which reflects
    // InputManager.getInstance(); SDK 37 removed it. Espresso 3.7.0 uses getSystemService.
    testImplementation(libs.androidx.test.espresso.core)
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.mockk)
    testImplementation(libs.okhttp.mockwebserver3)
    testImplementation(libs.androidx.work.testing)
}
```

Minimal subset for the screenshot proof (steps 01 to 04): the plugins `android.application`, `kotlin.compose` and `roborazzi`, plus the `android {}` block. Dependencies: compose-bom, compose ui, ui-tooling-preview, material3, activity-compose and core-ktx; debug ui-tooling and ui-test-manifest; test compose-bom, ui-test-junit4, test-ext-junit, espresso-core 3.7.0, junit4, robolectric, roborazzi, roborazzi-compose and roborazzi-junit-rule.

### gradle.properties

```properties
org.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8
org.gradle.parallel=true
org.gradle.caching=true
org.gradle.configuration-cache=true
kotlin.code.style=official
```

AGP 9 defaults already set `android.useAndroidX=true`, `android.builtInKotlin=true` and `android.newDsl=true`.

### .editorconfig

```
root = true

[*.{kt,kts}]
ktlint_function_naming_ignore_when_annotated_with = Composable
max_line_length = 120
```

### gradle/wrapper/gradle-wrapper.properties

```properties
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionSha256Sum=21bd5da2de49b642904077f38c0efdc7778e92b3ed0889392dca61b97e389607
distributionUrl=https\://services.gradle.org/distributions/gradle-9.8.2-bin.zip
networkTimeout=10000
retries=0
retryBackOffMs=500
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
```

`local.properties` contains `sdk.dir=~/android-sdk`; `ANDROID_HOME` from env.sh also works.

### app/src/test/kotlin/com/example/sniperprobe/SnipeScreenScreenshotTest.kt

```kotlin
package com.example.sniperprobe

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel9a)
class SnipeScreenScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun snipeScreen() {
        composeRule.setContent {
            ProbeTheme { SnipeScreen(state = SnipeUiState.sample) }
        }
        composeRule.onRoot().captureRoboImage()
    }
}
```

`RobolectricDeviceQualifiers.Pixel9a = "w411dp-h923dp-normal-long-notround-any-420dpi-keyshidden-nonav"` (roborazzi repo, RobolectricDeviceQualifiers.kt:53, tag 1.77.0, 2026-10-22).

Other probe sources (all compile; spotless-formatted) are in `app/src/main/kotlin/com/example/sniperprobe/`:
- `SnipeScreen.kt`: M3 Scaffold, Card, LinearProgressIndicator, AssistChip, Button and a `@Preview`.
- `MainActivity.kt`: `installSplashScreen()`, `enableEdgeToEdge()`, `setContent { ProbeTheme { AppNavigation() } }`.
- `AppNavigation.kt`: Nav3 `NavDisplay` with `rememberViewModelStoreNavEntryDecorator()`.
- `SnipeViewModel.kt`: StateFlow `stateIn(viewModelScope, ...)`.
- `data/SnipeDatabase.kt`: Room `@Entity` / `@Dao` with `@Upsert suspend` and a `Flow` query / `@Database(exportSchema = true)`.
- `ItemRef.kt`: `@Serializable`, `Json`, `kotlinx.datetime.TimeZone.of("Europe/London")`.
- `Platform.kt`: `preferencesDataStore`, `OkHttpClient`, `WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)` and Coil `AsyncImage`.
- `MonitorWorker.kt`: CoroutineWorker. `SnipeWidget.kt`: GlanceAppWidget.

Test `SnipeViewModelTest.kt` uses Turbine, MockK and `runTest`. The theme is `Theme.SniperProbe` (parent `android:Theme.Material.Light.NoActionBar`, no MDC dependency) plus `Theme.SniperProbe.Starting` (parent `Theme.SplashScreen`, `postSplashScreenTheme`).

## 7. Variant experiments (scratch copies, not the baseline)

- **V1 Expressive and Room 3** (`/var/tmp/sniper-toolchain-probe-m3e`): material3 1.5.0-beta02 on BOM 2026.09.00; Expressive screenshot passes (section 3). The same copy was then switched to Room 3.0.3: catalog `room3 = "3.0.3"`, `sqlite = "2.7.1"`; plugin `androidx.room3`; `room3 { schemaDirectory("$projectDir/schemas") }`; `ksp("androidx.room3:room3-compiler")`; imports `androidx.room3.*`; builder `Room.databaseBuilder<SnipeDatabase>(context, "snipes.db").setDriver(BundledSQLiteDriver()).build()`. `assembleDebug` passed in 58 s, and KSP generated `SnipeDao_Impl.kt` and the schema JSON. No `@ConstructedBy` was needed on Android-only.
- **V2 Paparazzi** (`/var/tmp/sniper-toolchain-probe-paparazzi`): plugin `app.cash.paparazzi` 2.0.0-alpha05.1, test `@get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5, theme = "android:Theme.Material.Light.NoActionBar")` with `paparazzi.snapshot { ... }`. `./gradlew recordPaparazziDebug` passed in 63 s and wrote `app/src/test/snapshots/images/com.example.sniperprobe_SnipeScreenPaparazziTest_snipeScreen.png` (461x1000, layoutlib 16.2.3). With 1.3.5 it failed at configuration (P12).
- **V3 Android CLI preview**, run from the probe directory: `android preview list :app` prints `com.example.sniperprobe.SnipeScreenKt.SnipeScreenPreview`. `android preview render :app --fqn=com.example.sniperprobe.SnipeScreenKt.SnipeScreenPreview --output=/var/tmp/sniper-toolchain-dl/preview-out` took 24 s and wrote `.../SnipeScreenKt/SnipeScreenPreview_0.png` (1080x2400). It drives the AGP task `generateDebugComposePreviewRunfiles` and downloads a "preview pack" into `~/.android/preview` (335 MB). `--fqn` is required, and an absolute path as the reference fails with "Invalid relative Gradle project path". The AssistChip rendered filled grey in this renderer and outlined in Roborazzi, so keep Roborazzi as the source of truth.

## 8. Other evidence and source dates

- Roborazzi repo clone `/var/tmp/sniper-research/src/takahirom_roborazzi`, HEAD `4c9b207f0a212a2f7839160398fe7fdd95b2e7e6` (2026-10-22, "1.77.0"). The plugin uses the `ApplicationAndroidComponentsExtension` / `AndroidComponentsExtension` APIs with the comment "AGP 9.0: unitTest is now on HasUnitTest interface, not Variant". Its own build still pins AGP 8.13.2 and compose compiler 2.2.21 (gradle/libs.versions.toml comment about `ComposeAgpMappingFile` / `TypeNotPresentException` with AGP 8.13.2). That issue did not occur here.
- Roborazzi release notes: 1.77.0 (2026-10-22) fixes configuration-cache reuse for verify/compare; 1.75.0 (2026-09-21) makes generated preview tests 1.6x faster; 1.72.0 (2026-08-13) adds the `roborazzi.record.namingStrategy` options.
- Robolectric release robolectric-4.17 (2026-09-10): "Robolectric 4.17 supports SDK 37 ... Please note you may need to add jvmFlags configuration when using JDKs >= 17".
- Paparazzi releases: 2.0.0-alpha05.1 (2026-09-28) "[Gradle Plugin] Android Gradle Plugin 9.0.0 (#2198)", LayoutLib v16.2.3. 2.0.0-alpha05 (2026-05-20) "supports pre-AGP 9.0 consumers". 2.0.0-alpha04 (2026-01-20) "consumers must build on Java 21+". Repo HEAD 2026-11-03.
- android/nowinandroid HEAD 2982e63 (2026-10-20) uses AGP 9.3.2, Kotlin 2.3.0, KSP 2.3.4, Roborazzi 1.56.0, Robolectric 4.16, navigation3 1.2.0 and spotless 8.3.0. Its root plugins list includes `org.jetbrains.kotlin.plugin.compose`, `kotlin.jvm`, `ksp`, `room`, `roborazzi` and `spotless` (all apply false). It is a reference, not a source of latest versions.
- Material3 sources inspected: `/var/tmp/sniper-toolchain-meta/m3src-1.4.0/x`, `/var/tmp/sniper-toolchain-meta/m3src-1.5.0-beta02/x`. Nav3 sources: `/var/tmp/sniper-toolchain-meta/nav3-*`. AGP API sources: `/var/tmp/sniper-toolchain-meta/agp-*-9.5.0`.

## 9. Not tested

- `assembleRelease` with R8. AGP 9 turns on `android.r8.strictFullModeForKeepRules` and optimized resource shrinking by default; keep rules for kotlinx-serialization and Room must be checked when the release build is set up.
- Install and run on the Pixel 9a. There is no device or emulator here. Inferred: copy the APK through ~/Exchange and sideload it with `adb install -r app-debug.apk` from the laptop.
- Real WebView rendering in JVM tests. Inferred: Robolectric provides a shadow WebView, so eBay sign-in pages cannot be screenshot-tested on the JVM; only Compose UI can.
- Gradle daemon JVM pinning (`gradle/gradle-daemon-jvm.properties`). It is not configured; the daemon uses `JAVA_HOME` (JDK 21).