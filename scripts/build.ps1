param([switch]$DeviceTests, [switch]$AlarmProbe, [switch]$Synthetic, [string]$SigningDirectory = '')
if ($AlarmProbe) { $DeviceTests = $true }
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
# JDK 17's Windows test-worker argument files can misread non-ASCII classpaths.
# Reuse this machine's verified junction when it points to this checkout.
$workspaceAlias = 'D:/Android/daily3minutes-workspace'
if (Test-Path -LiteralPath $workspaceAlias) {
    $aliasItem = Get-Item -LiteralPath $workspaceAlias
    if ($aliasItem.LinkType -eq 'Junction' -and [IO.Path]::GetFullPath($aliasItem.Target) -eq [IO.Path]::GetFullPath($projectRoot)) {
        $projectRoot = $workspaceAlias
    }
}
Set-Location -LiteralPath $projectRoot
if (-not $env:JAVA_HOME) {
    $candidate = Get-ChildItem -LiteralPath 'D:/Android/daily3minutes-jdk' -Directory -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($candidate) { $env:JAVA_HOME = $candidate.FullName }
}
if (-not $env:JAVA_HOME -or -not (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin/java.exe'))) { throw 'Set JAVA_HOME to JDK 17.' }
$env:PATH = (Join-Path $env:JAVA_HOME 'bin') + ';' + $env:PATH
if (-not $env:GRADLE_USER_HOME -and (Test-Path -LiteralPath 'D:/Android/daily3minutes-gradle-home')) { $env:GRADLE_USER_HOME = 'D:/Android/daily3minutes-gradle-home' }
if (-not $env:ANDROID_HOME -and (Test-Path -LiteralPath 'D:/Android/daily3minutes-sdk')) { $env:ANDROID_HOME = 'D:/Android/daily3minutes-sdk' }
# Platform-tools 37 Windows USB backend compatibility on this development machine.
if ($IsWindows -or $env:OS -eq 'Windows_NT') { $env:ADB_USB_LEGACY = '1' }
$tasks = @(':app:assembleDebug', ':app:testDebugUnitTest', ':app:lintDebug')
if ($DeviceTests) { $tasks += ':app:assembleDebugAndroidTest' }
$extraArguments = @()
if ($Synthetic) { $extraArguments += '-PSYNTHETIC_APPLICATION=true' }
$packageName = if ($Synthetic) { 'io.github.daily3minutes.verification' } else { 'io.github.daily3minutes' }
if (Test-Path -LiteralPath '.local/local-maven.init.gradle') { $extraArguments += @('--init-script', '.local/local-maven.init.gradle') }
& ./gradlew.bat @tasks @extraArguments --stacktrace --console=plain
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
if ($DeviceTests) {
    # Install in place and run instrumentation directly. Some Gradle device runners
    # uninstall their target afterward, which is unsuitable for a personal phone.
    $adb = Join-Path $env:ANDROID_HOME 'platform-tools/adb.exe'
    $appApk = './app/build/outputs/apk/debug/app-debug.apk'
    $testApk = './app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'
    if ($SigningDirectory) {
        # Both packages must have compatible signing after the production key rotation.
        $signedApp = Join-Path $projectRoot '.local/delivery/development-rotated.apk'
        $signedTest = Join-Path $projectRoot '.local/delivery/instrumentation-rotated.apk'
        & ./scripts/sign_release.ps1 -SigningDirectory $SigningDirectory -UnsignedApk $appApk -OutputApk $signedApp
        & ./scripts/sign_release.ps1 -SigningDirectory $SigningDirectory -UnsignedApk $testApk -OutputApk $signedTest
        $appApk = $signedApp
        $testApk = $signedTest
    }
    & $adb install -r $appApk
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    & $adb install -r -t $testApk
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    & $adb shell appwidget grantbind --package $packageName --user 0
    try {
        & $adb shell input keyevent KEYCODE_WAKEUP
        $result = & $adb shell am instrument -w -r ($packageName + '.test/io.github.daily3minutes.SyntheticTestRunner') 2>&1
        $result | Write-Output
        if ($LASTEXITCODE -ne 0 -or ($result -join "`n") -notmatch 'OK \(\d+ tests?\)') { throw 'Device instrumentation failed; inspect the output above.' }
    } finally {
        & $adb shell appwidget revokebind --package $packageName --user 0
        if (($result -join "`n") -match 'daily3_initial_notification_permission=denied') {
            & $adb shell pm revoke --user 0 $packageName android.permission.POST_NOTIFICATIONS
        }
        if (($result -join "`n") -match 'daily3_initial_microphone_permission=denied') {
            & $adb shell pm revoke --user 0 $packageName android.permission.RECORD_AUDIO
        }
    }
    if ($AlarmProbe) {
        $probeResult = @()
        try {
            $probeResult = & $adb shell am instrument -w -r -e alarmProbe true ($packageName + '.test/io.github.daily3minutes.SyntheticTestRunner') 2>&1
            $probeResult | Write-Output
            if ($LASTEXITCODE -ne 0 -or ($probeResult -join "`n") -notmatch 'alarm_probe=PASS:') {
                throw 'The bounded alarm observation did not confirm delivery. Inspect the result; inexact alarms may be delayed by system policy.'
            }
        } finally {
            if (($probeResult -join "`n") -match 'daily3_initial_notification_permission=denied') {
                & $adb shell pm revoke --user 0 $packageName android.permission.POST_NOTIFICATIONS
            }
        }
    }
}
Get-FileHash -LiteralPath './app/build/outputs/apk/debug/app-debug.apk' -Algorithm SHA256
