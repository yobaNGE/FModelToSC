# Unique Weapon Exporter
# Extracts unique weapons from vehicles.json by weaponName+rawProjectileName
# Usage: .\export_unique_weapons.ps1 -InputFile "output\vehicles.json" -OutputFile "unique_weapons.json"

param(
    [Parameter(Mandatory=$true)]
    [string]$InputFile,

    [Parameter(Mandatory=$false)]
    [string]$OutputFile = "unique_weapons.json"
)

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectRoot = Resolve-Path (Join-Path $scriptDir "..")

function Resolve-InputPath {
    param([string]$PathValue)

    if (Test-Path -LiteralPath $PathValue) {
        return (Resolve-Path -LiteralPath $PathValue).Path
    }
    if ([System.IO.Path]::IsPathRooted($PathValue)) {
        return $null
    }
    $candidate = Join-Path $projectRoot $PathValue
    if (Test-Path -LiteralPath $candidate) {
        return (Resolve-Path -LiteralPath $candidate).Path
    }
    return $null
}

function Resolve-OutputPath {
    param([string]$PathValue)

    if ([System.IO.Path]::IsPathRooted($PathValue)) {
        return $PathValue
    }
    $dir = [System.IO.Path]::GetDirectoryName($PathValue)
    if ([string]::IsNullOrWhiteSpace($dir)) {
        return $PathValue
    }
    return (Join-Path $projectRoot $PathValue)
}

Write-Host "Unique Weapon Exporter" -ForegroundColor Cyan
Write-Host "======================" -ForegroundColor Cyan
Write-Host ""

$resolvedInput = Resolve-InputPath -PathValue $InputFile
if (-not $resolvedInput) {
    Write-Host "Error: Input file '$InputFile' not found!" -ForegroundColor Red
    exit 1
}
$InputFile = $resolvedInput

$OutputFile = Resolve-OutputPath -PathValue $OutputFile
$outputDir = Split-Path -Parent $OutputFile
if (-not [string]::IsNullOrWhiteSpace($outputDir)) {
    New-Item -ItemType Directory -Force -Path $outputDir | Out-Null
}

Write-Host "Reading JSON file: $InputFile" -ForegroundColor Green

try {
    $jsonContent = Get-Content -Path $InputFile -Raw -Encoding UTF8
    $data = $jsonContent | ConvertFrom-Json
} catch {
    Write-Host "Error: Failed to parse JSON file: $_" -ForegroundColor Red
    exit 1
}

$unique = New-Object 'System.Collections.Generic.Dictionary[string, object]' ([System.StringComparer]::Ordinal)

foreach ($vehicle in $data) {
    if (-not $vehicle.weapons) {
        continue
    }
    foreach ($weapon in $vehicle.weapons) {
        $name = [string]$weapon.weaponName
        $rawName = [string]$weapon.rawWeaponName
        $proj = [string]$weapon.rawProjectileName
        $key = "$name|$rawName|$proj"
        if (-not $unique.ContainsKey($key)) {
            $unique[$key] = [pscustomobject]@{
                weaponName = $name
                rawWeaponName = $rawName
                rawProjectileName = $proj
            }
        }
    }
}

$results = $unique.Values | Sort-Object weaponName, rawProjectileName

Write-Host "Writing results to: $OutputFile" -ForegroundColor Green
try {
    $results | ConvertTo-Json -Depth 4 | Set-Content -Path $OutputFile -Encoding UTF8
    Write-Host "  Successfully wrote $($results.Count) unique weapons" -ForegroundColor Green
} catch {
    Write-Host "  Error writing file: $_" -ForegroundColor Red
    exit 1
}
