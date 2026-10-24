param([switch]$Device, [string]$Python = 'python')
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
Push-Location -LiteralPath $root
try {
    $pythonCommand = Get-Command $Python -ErrorAction Stop
    if ($pythonCommand.Source -match 'WindowsApps') { throw 'Pass -Python with the path to an installed Python interpreter.' }
    & $Python ./scripts/check_sensitive.py --self-test
    if ($LASTEXITCODE -ne 0) { throw 'Source publication check failed.' }
    # The separate applicationId is mandatory even when a personal phone is connected.
    & ./scripts/build.ps1 -Synthetic -DeviceTests:$Device
    if ($LASTEXITCODE -ne 0) { throw 'Build or synthetic regression failed.' }
    Write-Output 'Review generated reports and synthetic screenshots using docs/SELF_REVIEW.md before publishing.'
} finally { Pop-Location }
