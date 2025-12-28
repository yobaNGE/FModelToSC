# Vehicle Extractor Script
# Extracts vehicles from vehicles.json based on vehType and vehTags filters
# Usage: .\extract_vehicles.ps1 -InputFile "output\vehiclesSD.json" -OutputFile "vehicles_filtered.json" [-VehTypes "MBT,IFV"] [-VehTags "Class_Heavy,ATGM"]

param(
    [Parameter(Mandatory=$true)]
    [string]$InputFile,

    [Parameter(Mandatory=$false)]
    [string]$OutputFile = "vehicles_filtered.json",

    [Parameter(Mandatory=$false)]
    [string]$VehTypes = "",  # Comma-separated list (e.g., "MBT,IFV,APC")

    [Parameter(Mandatory=$false)]
    [string]$VehTags = ""    # Comma-separated list (e.g., "Class_Heavy,ATGM")
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

# Available vehicle types from VehicleSettingsLoader.java:
# RSV, IFV, APC, MBT, LOGI, LTV, UH, AH, ULTV, SPAA, MRAP, TRAN, SPA, TD, MGS, MSV

# Available vehicle tags from VehicleSettingsLoader.java:
# Class_Light, Class_Medium, Class_Heavy, AGL, ATGM, Low Caliber, RWS, Watercraft

Write-Host "Vehicle Extractor Script" -ForegroundColor Cyan
Write-Host "=========================" -ForegroundColor Cyan
Write-Host ""

# Check if input file exists
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

# Parse filter lists
$vehTypeList = @()
$vehTagList = @()

if ($VehTypes -ne "") {
    $vehTypeList = $VehTypes -split ',' | ForEach-Object { $_.Trim() }
    Write-Host "Filtering by vehicle types: $($vehTypeList -join ', ')" -ForegroundColor Yellow
}

if ($VehTags -ne "") {
    $vehTagList = $VehTags -split ',' | ForEach-Object { $_.Trim() }
    Write-Host "Filtering by vehicle tags: $($vehTagList -join ', ')" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "Reading JSON file: $InputFile" -ForegroundColor Green

# Read and parse JSON
try {
    $jsonContent = Get-Content -Path $InputFile -Raw -Encoding UTF8
    $data = $jsonContent | ConvertFrom-Json
} catch {
    Write-Host "Error: Failed to parse JSON file: $_" -ForegroundColor Red
    exit 1
}

# HashSet to track unique vehicles (by type:rawType key, but store full vehicle objects)
$script:uniqueVehicleKeys = @{}
$script:vehicleList = @()

# Function to check if vehicle matches filters
function Test-VehicleMatch {
    param($vehicle)

    # Check vehicle type filter
    if ($vehTypeList.Count -gt 0) {
        if (-not ($vehTypeList -contains $vehicle.vehType)) {
            return $false
        }
    }

    # Check vehicle tags filter
    if ($vehTagList.Count -gt 0) {
        $hasMatchingTag = $false
        foreach ($tag in $vehicle.vehTags) {
            if ($vehTagList -contains $tag) {
                $hasMatchingTag = $true
                break
            }
        }
        if (-not $hasMatchingTag) {
            return $false
        }
    }

    return $true
}

# Function to process vehicle array
function Process-Vehicles {
    param($vehicles, $teamName)

    $count = 0
    foreach ($vehicle in $vehicles) {
        if ($vehicle.type -and $vehicle.rawType) {
            # Check if vehicle matches filters
            if (Test-VehicleMatch -vehicle $vehicle) {
                # Create unique key
                $key = "$($vehicle.type):$($vehicle.rawType)"

                # Add only if not duplicate
                if (-not $script:uniqueVehicleKeys.ContainsKey($key)) {
                    $script:uniqueVehicleKeys[$key] = $true
                    $script:vehicleList += $vehicle
                    $count++
                    Write-Host "  [+] $key" -ForegroundColor Gray
                }
            }
        }
    }
    return $count
}

$team1Count = 0
$team2Count = 0

if ($data -is [System.Array]) {
    Write-Host "`nProcessing vehicles list..." -ForegroundColor Cyan
    Process-Vehicles -vehicles $data -teamName "Vehicles" | Out-Null
} elseif ($data.team1Units -or $data.team2Units) {
    # Backward-compatible path for units.json
    $team1StartCount = $script:vehicleList.Count
    if ($data.team1Units) {
        Write-Host "`nProcessing team1Units..." -ForegroundColor Cyan
        foreach ($unit in $data.team1Units) {
            if ($unit.vehicles) {
                Process-Vehicles -vehicles $unit.vehicles -teamName "Team1" | Out-Null
            }
        }
        $team1Count = $script:vehicleList.Count - $team1StartCount
    }

    $team2StartCount = $script:vehicleList.Count
    if ($data.team2Units) {
        Write-Host "`nProcessing team2Units..." -ForegroundColor Cyan
        foreach ($unit in $data.team2Units) {
            if ($unit.vehicles) {
                Process-Vehicles -vehicles $unit.vehicles -teamName "Team2" | Out-Null
            }
        }
        $team2Count = $script:vehicleList.Count - $team2StartCount
    }
} else {
    Write-Host "Error: Unsupported JSON format. Expected vehicles.json array." -ForegroundColor Red
    exit 1
}

# Sort vehicles alphabetically by type
$script:vehicleList = $script:vehicleList | Sort-Object -Property type

# Write to output file as JSON
Write-Host "`nWriting results to: $OutputFile" -ForegroundColor Green
try {
    $jsonOutput = $script:vehicleList | ConvertTo-Json -Depth 10
    $jsonOutput | Out-File -FilePath $OutputFile -Encoding UTF8
    Write-Host "  Successfully wrote $($script:vehicleList.Count) vehicles" -ForegroundColor Green
} catch {
    Write-Host "  Error writing file: $_" -ForegroundColor Red
}

# Summary
Write-Host ""
Write-Host "=========================" -ForegroundColor Cyan
Write-Host "Summary:" -ForegroundColor Cyan
Write-Host "  Total unique vehicles found: $($script:vehicleList.Count)" -ForegroundColor White
if ($team1Count -gt 0 -or $team2Count -gt 0) {
    Write-Host "  From team1Units: $team1Count" -ForegroundColor White
    Write-Host "  From team2Units: $team2Count" -ForegroundColor White
}
Write-Host "  Output saved to: $OutputFile" -ForegroundColor Green
Write-Host "=========================" -ForegroundColor Cyan

