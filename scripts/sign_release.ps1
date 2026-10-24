# SPDX-License-Identifier: Apache-2.0
param(
    [Parameter(Mandatory=$true)][string]$SigningDirectory,
    [string]$UnsignedApk = '',
    [string]$OutputApk = ''
)
$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Split-Path $PSScriptRoot -Parent))
$signingRoot = [IO.Path]::GetFullPath($SigningDirectory)
if ($signingRoot.StartsWith($projectRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Signing material must be stored outside the repository.'
}
if (-not $UnsignedApk) { $UnsignedApk = Join-Path $projectRoot 'app/build/outputs/apk/release/app-release-unsigned.apk' }
if (-not $OutputApk) { $OutputApk = Join-Path $projectRoot '.local/delivery/daily-3-minutes-0.4.0-beta.1.apk' }
$apkInput = [IO.Path]::GetFullPath($UnsignedApk)
$apkOutput = [IO.Path]::GetFullPath($OutputApk)
if ($apkInput -eq $apkOutput) { throw 'Input and output APK paths must differ.' }
if (-not $env:JAVA_HOME -or -not $env:ANDROID_HOME) { throw 'Set JAVA_HOME and ANDROID_HOME first.' }
$buildTools = Join-Path $env:ANDROID_HOME 'build-tools/35.0.0'
$java = Join-Path $env:JAVA_HOME 'bin/java.exe'
$signer = Join-Path $buildTools 'lib/apksigner.jar'
New-Item -ItemType Directory -Path (Split-Path $apkOutput -Parent) -Force | Out-Null
$aligned = $apkOutput + '.aligned'
& (Join-Path $buildTools 'zipalign.exe') -f 4 $apkInput $aligned
if ($LASTEXITCODE -ne 0) { throw 'APK alignment failed.' }
& $java -jar $signer sign --ks (Join-Path $signingRoot 'legacy.keystore') --ks-key-alias androiddebugkey --ks-pass pass:android --next-signer --ks (Join-Path $signingRoot 'release.p12') --ks-key-alias daily3minutes --ks-pass ('file:' + (Join-Path $signingRoot 'release-password.txt')) --lineage (Join-Path $signingRoot 'lineage.bin') --rotation-min-sdk-version 33 --out $apkOutput $aligned
if ($LASTEXITCODE -ne 0) { throw 'APK signing failed.' }
& $java -jar $signer verify --verbose --print-certs $apkOutput
if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed.' }
Remove-Item -LiteralPath $aligned
Get-FileHash -LiteralPath $apkOutput -Algorithm SHA256
