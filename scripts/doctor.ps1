# Copyright 2026 Daily 3 Minutes contributors. SPDX-License-Identifier: Apache-2.0
param([switch]$Device)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$checks = [Collections.Generic.List[object]]::new()
function Add-Check($Name, $Passed, $Detail) {
    $checks.Add([pscustomobject]@{ Check = $Name; Passed = [bool]$Passed; Detail = $Detail })
}
$jdk = $env:JAVA_HOME
if (-not $jdk) {
    $candidate = Get-ChildItem -LiteralPath 'D:/Android/daily3minutes-jdk' -Directory -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($candidate) { $jdk = $candidate.FullName }
}
Add-Check 'JDK' ($jdk -and (Test-Path -LiteralPath (Join-Path $jdk 'bin/java.exe'))) 'JDK 17 is required; no system settings are changed.'
$sdk = $env:ANDROID_HOME
if (-not $sdk -and (Test-Path -LiteralPath 'D:/Android/daily3minutes-sdk')) { $sdk = 'D:/Android/daily3minutes-sdk' }
Add-Check 'Android SDK' ($sdk -and (Test-Path -LiteralPath (Join-Path $sdk 'platforms/android-36/android.jar'))) 'Compile SDK 36'
Add-Check 'Gradle wrapper' (Test-Path -LiteralPath (Join-Path $root 'gradle/wrapper/gradle-wrapper.jar')) 'Use the checked-in Gradle wrapper.'
Add-Check 'Git' ([bool](Get-Command git -ErrorAction SilentlyContinue)) 'Git is used for source and releases; credentials are never printed.'
if ($Device -and $sdk) {
    $adb = Join-Path $sdk 'platform-tools/adb.exe'
    if (Test-Path -LiteralPath $adb) {
        $priorLegacy = $env:ADB_USB_LEGACY
        try {
            $env:ADB_USB_LEGACY = '1'
            $lines = @(& $adb devices 2>$null)
            $authorized = @($lines | Where-Object { $_ -match '\sdevice$' }).Count
            Add-Check 'Phone' ($authorized -eq 1) "Authorized devices: $authorized. Serial numbers are omitted."
            if ($authorized -eq 1) {
                $api = (& $adb shell getprop ro.build.version.sdk 2>$null | Out-String).Trim()
                Add-Check 'Android version' ($api -match '^\d+$' -and [int]$api -ge 26) "API $api"
                $google = @(& $adb shell pm list packages --user 0 com.google.android.gms 2>$null) -contains 'package:com.google.android.gms'
                Add-Check 'Google service component' $google 'Only package availability is checked; no accounts or records are read.'
            }
        } finally { $env:ADB_USB_LEGACY = $priorLegacy }
    } else { Add-Check 'ADB' $false 'Install Android platform-tools.' }
}
$checks | Format-Table -Wrap -AutoSize
if (@($checks | Where-Object { -not $_.Passed }).Count) { exit 1 }
