# Signs the locally built release APK with the release key and installs it over ADB.
# Usage (after `cd android; ./gradlew assembleRelease`):
#   ./scripts/install-signed.ps1 [-Keystore key.jks] [-Alias wellness] [-Reinstall]
# apksigner asks for the keystore password in the console. -Reinstall uninstalls the app
# first (needed once when switching from a differently signed build; it deletes app data).
param(
  [string]$Keystore = "$PSScriptRoot/../key.jks",
  [string]$Alias = 'wellness',
  [string]$Sdk = $(if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { 'D:\Dev\utils\Android\SDK' }),
  [switch]$Reinstall
)
$ErrorActionPreference = 'Stop'
if (-not $env:JAVA_HOME) { $env:JAVA_HOME = 'D:\Dev\utils\Android\jdk21' }

$bt = Get-ChildItem "$Sdk/build-tools" | Sort-Object { [version]$_.Name } | Select-Object -Last 1
$adb = "$Sdk/platform-tools/adb.exe"
$version = (Get-Content "$PSScriptRoot/../app.json" -Raw | ConvertFrom-Json).expo.version
$src = "$PSScriptRoot/../android/app/build/outputs/apk/release/app-release.apk"
$out = "$PSScriptRoot/../android/app/build/outputs/apk/release/wellness-$version.apk"
$tmp = "$out.aligned"

& "$($bt.FullName)/zipalign.exe" -f -p 4 $src $tmp
& "$($bt.FullName)/apksigner.bat" sign --ks $Keystore --ks-key-alias $Alias --out $out $tmp
if ($LASTEXITCODE) { throw 'Signing failed' }
Remove-Item $tmp
& "$($bt.FullName)/apksigner.bat" verify $out
Write-Host "Signed: $out"

if ($Reinstall) { & $adb uninstall io.github.timmylich.wellness }
& $adb install -r $out
