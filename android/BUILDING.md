# Android 构建与打包

本文档中的命令均从项目的 `android` 目录执行。

## 环境要求

- JDK 17
- Android SDK Platform 36
- Android SDK Build-Tools
- Android NDK `27.3.13750724`
- 真机安装或设备测试时需要 Android Platform-Tools（`adb`）

可以先检查 Java 和 Gradle 环境：

### Windows PowerShell

```powershell
cd D:\ai_projects\aibook\android
java -version
.\gradlew.bat --version
```

Android SDK 默认位于 `%LOCALAPPDATA%\Android\Sdk`。如果 Gradle 找不到 SDK，在 `android/local.properties` 中配置：

```properties
sdk.dir=C\:\\Users\\你的用户名\\AppData\\Local\\Android\\Sdk
```

### macOS

```bash
cd /path/to/aibook/android
chmod +x gradlew
java -version
./gradlew --version
```

Android SDK 默认位于 `$HOME/Library/Android/sdk`。如果 Gradle 找不到 SDK，在 `android/local.properties` 中配置：

```properties
sdk.dir=/Users/你的用户名/Library/Android/sdk
```

## Windows 构建命令

```powershell
# 清理构建目录
.\gradlew.bat clean

# 只检查 App Kotlin 编译
.\gradlew.bat :app:compileDebugKotlin

# 运行全部单元测试
.\gradlew.bat test

# 运行 App 单元测试
.\gradlew.bat :app:testDebugUnitTest

# 运行 Debug 静态检查
.\gradlew.bat lintDebug

# 打包可直接安装的 Debug APK
.\gradlew.bat :app:assembleDebug

# 打包 Release APK（当前项目未配置 Release 签名）
.\gradlew.bat :app:assembleRelease

# 打包 Release AAB（上传应用商店前必须签名）
.\gradlew.bat :app:bundleRelease

# 连接真机或模拟器后直接安装 Debug 版本
.\gradlew.bat :app:installDebug

# 运行设备端测试
.\gradlew.bat connectedDebugAndroidTest
```

## macOS 构建命令

```bash
# 清理构建目录
./gradlew clean

# 只检查 App Kotlin 编译
./gradlew :app:compileDebugKotlin

# 运行全部单元测试
./gradlew test

# 运行 App 单元测试
./gradlew :app:testDebugUnitTest

# 运行 Debug 静态检查
./gradlew lintDebug

# 打包可直接安装的 Debug APK
./gradlew :app:assembleDebug

# 打包 Release APK（当前项目未配置 Release 签名）
./gradlew :app:assembleRelease

# 打包 Release AAB（上传应用商店前必须签名）
./gradlew :app:bundleRelease

# 连接真机或模拟器后直接安装 Debug 版本
./gradlew :app:installDebug

# 运行设备端测试
./gradlew connectedDebugAndroidTest
```

## 构建产物

| 产物 | 路径 | 说明 |
|---|---|---|
| Debug APK | `app/build/outputs/apk/debug/app-debug.apk` | Gradle 自动使用 Debug 密钥签名，可直接安装 |
| Release APK | `app/build/outputs/apk/release/app-release-unsigned.apk` | 当前默认未签名，不可直接发布 |
| Release AAB | `app/build/outputs/bundle/release/app-release.aab` | 发布前需要使用 Release 密钥签名 |
| Lint 报告 | `app/build/reports/lint-results-debug.html` | Debug 静态检查报告 |
| 单元测试报告 | `app/build/reports/tests/testDebugUnitTest/index.html` | App 单元测试报告 |

## 安装与日志

### Windows PowerShell

```powershell
adb devices
adb install -r .\app\build\outputs\apk\debug\app-debug.apk

# 查看应用相关日志
adb logcat | Select-String "AiBook|OpdsViewModel"
```

### macOS

```bash
adb devices
adb install -r ./app/build/outputs/apk/debug/app-debug.apk

# 查看应用相关日志
adb logcat | grep -E 'AiBook|OpdsViewModel'
```

如果安装时出现 `INSTALL_FAILED_UPDATE_INCOMPATIBLE`，说明设备上已有使用其他密钥签名的同包名应用。执行下面的命令会同时删除该应用的本地数据，请先确认数据可以丢弃：

```bash
adb uninstall com.aibook.android
```

## Release 签名

项目当前未在 Gradle 中保存 Release 密钥配置，避免把密钥和密码提交到仓库。首次发布时先生成并妥善备份密钥：

### Windows PowerShell

```powershell
keytool -genkeypair -v `
  -keystore "$env:USERPROFILE\.android\aibook-release.jks" `
  -alias aibook `
  -keyalg RSA -keysize 4096 -validity 10000
```

签名 Release APK：

```powershell
$sdk = "$env:LOCALAPPDATA\Android\Sdk"
$buildTools = Get-ChildItem "$sdk\build-tools" -Directory | Sort-Object Name -Descending | Select-Object -First 1
$unsignedApk = ".\app\build\outputs\apk\release\app-release-unsigned.apk"
$alignedApk = ".\app\build\outputs\apk\release\app-release-aligned.apk"
$signedApk = ".\app\build\outputs\apk\release\aibook-release.apk"

& "$($buildTools.FullName)\zipalign.exe" -f -p 4 $unsignedApk $alignedApk
& "$($buildTools.FullName)\apksigner.bat" sign --ks "$env:USERPROFILE\.android\aibook-release.jks" --ks-key-alias aibook --out $signedApk $alignedApk
& "$($buildTools.FullName)\apksigner.bat" verify --verbose $signedApk
```

### macOS

```bash
keytool -genkeypair -v \
  -keystore "$HOME/.android/aibook-release.jks" \
  -alias aibook \
  -keyalg RSA -keysize 4096 -validity 10000
```

签名 Release APK：

```bash
SDK="$HOME/Library/Android/sdk"
BUILD_TOOLS="$(find "$SDK/build-tools" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -n 1)"
UNSIGNED_APK="app/build/outputs/apk/release/app-release-unsigned.apk"
ALIGNED_APK="app/build/outputs/apk/release/app-release-aligned.apk"
SIGNED_APK="app/build/outputs/apk/release/aibook-release.apk"

"$BUILD_TOOLS/zipalign" -f -p 4 "$UNSIGNED_APK" "$ALIGNED_APK"
"$BUILD_TOOLS/apksigner" sign --ks "$HOME/.android/aibook-release.jks" --ks-key-alias aibook --out "$SIGNED_APK" "$ALIGNED_APK"
"$BUILD_TOOLS/apksigner" verify --verbose "$SIGNED_APK"
```

Release AAB 可以使用同一密钥签名：

```bash
jarsigner -verbose -sigalg SHA256withRSA -digestalg SHA-256 \
  -keystore /path/to/aibook-release.jks \
  app/build/outputs/bundle/release/app-release.aab aibook
```

Windows PowerShell 使用相同的 `jarsigner` 参数，将续行符 `\` 改为反引号 `` ` ``，并替换密钥路径即可。不要把 `.jks` 文件、密钥密码或签名后的私有配置提交到 Git。

## 常见问题

- `SDK location not found`：检查 `local.properties` 中的 `sdk.dir`。
- 找不到 NDK：在 Android Studio 的 SDK Manager 中安装 NDK `27.3.13750724`。
- `JAVA_HOME` 或 Java 版本错误：确保 Gradle 使用 JDK 17。
- 真机无法连接本机 OPDS：真机使用电脑或 NAS 的局域网 IP，模拟器访问宿主机使用 `10.0.2.2`。
- 依赖下载失败：检查 Gradle 代理、Maven 仓库连接和网络环境后重试。
