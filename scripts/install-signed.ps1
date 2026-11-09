# Builds the release APK, signs it with the release key and installs it over ADB.
# Usage:
#   ./scripts/install-signed.ps1 [-NoBuild] [-Keystore key.jks] [-Alias wellness] [-Reinstall]
# apksigner asks for the keystore password in the console. -NoBuild signs and installs the APK
# already built. -Reinstall uninstalls the app first (needed once when switching from a
# differently signed build; it deletes app data).
param(
  [string]$Keystore = "$PSScriptRoot/../key.jks",
  [string]$Alias = 'wellness',
  [switch]$NoBuild,
  [switch]$Reinstall
)
$ErrorActionPreference = 'Stop'
$root = Resolve-Path "$PSScriptRoot/.."

# Toolchain for this machine; on other machines the existing environment is kept.
# Gradle needs JDK 17+, so an older JAVA_HOME (e.g. Java 8) is overridden.
function Use($name, $path) { if (Test-Path $path) { Set-Item "env:$name" $path } }
Use JAVA_HOME 'D:\Dev\utils\Android\jdk21'
Use ANDROID_HOME 'D:\Dev\utils\Android\SDK'
Use GRADLE_USER_HOME 'D:\Dev\utils\gradle-home'
$Sdk = $env:ANDROID_HOME
if (-not $Sdk) { throw 'Set ANDROID_HOME to the Android SDK' }

if (-not $NoBuild) {
  Push-Location $root
  try { & ./gradlew.bat assembleRelease } finally { Pop-Location }
  if ($LASTEXITCODE) { throw 'Build failed' }
}

$bt = Get-ChildItem "$Sdk/build-tools" | Sort-Object { [version]$_.Name } | Select-Object -Last 1
$adb = "$Sdk/platform-tools/adb.exe"
$version = [regex]::Match((Get-Content "$root/app/build.gradle.kts" -Raw), 'versionName = "([^"]+)"').Groups[1].Value
$dir = "$root/app/build/outputs/apk/release"
# Unsigned unless keystore.properties exists (then Gradle has signed it already).
$src = if (Test-Path "$dir/app-release-unsigned.apk") { "$dir/app-release-unsigned.apk" } else { "$dir/app-release.apk" }
$out = "$dir/wellness-$version.apk"
$tmp = "$out.aligned"

& "$($bt.FullName)/zipalign.exe" -f -p 4 $src $tmp
& "$($bt.FullName)/apksigner.bat" sign --ks $Keystore --ks-key-alias $Alias --out $out $tmp
if ($LASTEXITCODE) { throw 'Signing failed' }
Remove-Item $tmp
& "$($bt.FullName)/apksigner.bat" verify $out
Write-Host "Signed: $out"

if ($Reinstall) { & $adb uninstall io.github.timmylich.wellness }
& $adb install -r $out
