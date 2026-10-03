import java.io.File
import java.util.Properties

plugins { id("com.android.application"); id("org.jetbrains.kotlin.android"); id("org.jetbrains.kotlin.plugin.compose"); id("org.jetbrains.kotlin.kapt") }
android {
 namespace = "dev.usbdroid"
 compileSdk = 35
 ndkVersion = "28.2.13676358"
 defaultConfig { applicationId = "dev.usbdroid"; minSdk = 26; targetSdk = 35; versionCode = 1; versionName = "1.0.0"; ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") } }
 defaultConfig { testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
 buildFeatures { compose = true; buildConfig = true }
 val releaseStore = providers.environmentVariable("USBDROID_STORE_FILE").orNull
 signingConfigs {
  if(releaseStore != null) create("release") {
   storeFile = file(releaseStore)
   storePassword = providers.environmentVariable("USBDROID_STORE_PASSWORD").get()
   keyAlias = providers.environmentVariable("USBDROID_KEY_ALIAS").get()
   keyPassword = providers.environmentVariable("USBDROID_KEY_PASSWORD").get()
  }
 }
 buildTypes { release { if(releaseStore != null) signingConfig = signingConfigs.getByName("release"); isMinifyEnabled = true; proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro") } }
 packaging.resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
 packaging.resources.merges += "META-INF/services/*"
 packaging.jniLibs.useLegacyPackaging = true
 packaging.jniLibs.keepDebugSymbols += "**/libisohybrid.so"
}
dependencies {
 implementation(platform("androidx.compose:compose-bom:2025.04.01"))
 implementation("androidx.core:core-ktx:1.15.0")
 implementation("androidx.activity:activity-compose:1.10.0")
 implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
 implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
 implementation("androidx.compose.ui:ui")
 implementation("androidx.compose.material3:material3")
 implementation("androidx.compose.material:material-icons-extended")
 implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
 implementation("com.squareup.okhttp3:okhttp:4.12.0")
 implementation("androidx.appcompat:appcompat:1.7.0")
 implementation("androidx.room:room-runtime:2.6.1")
 implementation("androidx.room:room-ktx:2.6.1")
 kapt("androidx.room:room-compiler:2.6.1")
 implementation("androidx.datastore:datastore-preferences:1.1.1")
 implementation("androidx.work:work-runtime-ktx:2.10.0")
 implementation("androidx.documentfile:documentfile:1.0.1")
 testImplementation("junit:junit:4.13.2")
 testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
 testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
 testImplementation("org.json:json:20240303")
 androidTestImplementation(platform("androidx.compose:compose-bom:2025.04.01"))
 androidTestImplementation("androidx.compose.ui:ui-test-junit4")
 androidTestImplementation("androidx.test:runner:1.7.0")
 androidTestImplementation("androidx.test.ext:junit:1.3.0")
 androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
 debugImplementation("androidx.compose.ui:ui-tooling")
 debugImplementation("androidx.compose.ui:ui-test-manifest")
 debugImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}

val prepareNative by tasks.registering {
 val source = rootProject.file("native/src")
 val bootSource = rootProject.file("native/testboot")
 val nativeOutput = layout.projectDirectory.dir("src/main/jniLibs").asFile
 val bootOutput = layout.projectDirectory.dir("src/main/assets/testboot").asFile
 inputs.dir(source)
 inputs.dir(bootSource)
 outputs.dir(nativeOutput)
 outputs.dir(bootOutput)
 doLast {
  val properties = Properties()
  rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { properties.load(it) }
  val sdk = properties.getProperty("sdk.dir") ?: System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT") ?: error("Android SDK not configured")
  val os = System.getProperty("os.name").lowercase()
  val host = when { os.contains("windows") -> "windows-x86_64"; os.contains("mac") -> "darwin-x86_64"; else -> "linux-x86_64" }
  val executable = if(os.contains("windows")) ".exe" else ""
  val toolchain = File(sdk, "ndk/28.2.13676358/toolchains/llvm/prebuilt/$host")
  fun tool(name: String) = File(toolchain, "bin/$name$executable").absolutePath
  fun run(vararg command: String) {
   val process = providers.exec { commandLine(command.toList()); isIgnoreExitValue = true }
   val stdout = process.standardOutput.asText.get()
   val stderr = process.standardError.asText.get()
   if(stdout.isNotBlank()) logger.lifecycle(stdout.trim())
   if(stderr.isNotBlank()) logger.lifecycle(stderr.trim())
   check(process.result.get().exitValue == 0) { "Native command failed: ${command.first()}" }
  }
  val intermediate = layout.buildDirectory.dir("native").get().asFile.apply { mkdirs() }
  val variants = listOf("isohdpfx", "isohdpfx_f", "isohdpfx_c", "isohdppx", "isohdppx_f", "isohdppx_c")
  val arrays = variants.map { variant ->
   val assembly = if(variant.startsWith("isohdppx")) "isohdppx.S" else "isohdpfx.S"
   val definition = if(variant.endsWith("_f")) "-DFORCE_80" else if(variant.endsWith("_c")) "-DCTRL_80" else "-DUSBDROID_BUILD"
   run(tool("clang"), "--target=i386-linux-gnu", "-m32", definition, "-c", File(source, "mbr/$assembly").path, "-o", "$intermediate/$variant.o")
   run(tool("ld.lld"), "-m", "elf_i386", "-T", File(source, "mbr/mbr.ld").path, "-e", "_start", "-o", "$intermediate/$variant.elf", "$intermediate/$variant.o")
   run(tool("llvm-objcopy"), "-O", "binary", "$intermediate/$variant.elf", "$intermediate/$variant.bin")
   val bytes = File(intermediate, "$variant.bin").readBytes()
   check(bytes.size <= 432) { "Unexpected MBR size" }
   "{" + bytes.copyOf(432).joinToString(",") { "0x%02x".format(it.toInt() and 255) } + "}"
  }
  val generated = File(intermediate, "isohdpfx.c")
  generated.writeText("#include \"isohybrid.h\"\nunsigned char isohdpfx[][MBRSIZE] = {" + arrays.joinToString(",") + "};\n")
  mapOf("arm64-v8a" to "aarch64-linux-android26", "armeabi-v7a" to "armv7a-linux-androideabi26", "x86_64" to "x86_64-linux-android26").forEach { (abi, target) ->
   val output = File(nativeOutput, abi).apply { mkdirs() }
   run(tool("clang"), "--target=$target", "--sysroot=${File(toolchain, "sysroot")}", "-O2", "-fPIE", "-pie", "-D_GNU_SOURCE", "-Wl,-z,max-page-size=16384", "-I", source.path, File(source, "isohybrid.c").path, generated.path, "-o", File(output, "libisohybrid.so").path)
  }
  bootOutput.mkdirs()
  run(tool("clang"), "--target=i386-linux-gnu", "-c", File(bootSource, "bios.S").path, "-o", "$intermediate/bios.o")
  run(tool("ld.lld"), "-m", "elf_i386", "-Ttext=0x7c3e", "--oformat=binary", "-o", File(bootOutput, "bios.bin").path, "$intermediate/bios.o")
  run(tool("clang"), "--target=x86_64-pc-windows-msvc", "-ffreestanding", "-fshort-wchar", "-fno-stack-protector", "-c", File(bootSource, "uefi.c").path, "-o", "$intermediate/uefi.obj")
  run(tool("ld.lld"), "-flavor", "link", "/subsystem:efi_application", "/entry:efi_main", "/nodefaultlib", "/machine:x64", "/timestamp:0", "/out:${File(bootOutput, "bootx64.efi")}", "$intermediate/uefi.obj")
 }
}
tasks.named("preBuild") { dependsOn(prepareNative) }
