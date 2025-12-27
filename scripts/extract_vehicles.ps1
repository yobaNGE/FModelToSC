# Vehicle Extractor Script
# Extracts vehicles from JSON files based on vehType and vehTags filters
# Usage: .\extract_vehicles.ps1 -InputFile "path\to\file.json" -OutputFile "vehicles.txt" [-VehTypes "MBT,IFV"] [-VehTags "Class_Heavy,ATGM"]

param(
    [Parameter(Mandatory=$true)]
    [string]$InputFile,

    [Parameter(Mandatory=$false)]
    [string]$OutputFile = "vehicles_output.txt",

    [Parameter(Mandatory=$false)]
    [string]$VehTypes = "",  # Comma-separated list (e.g., "MBT,IFV,APC")

    [Parameter(Mandatory=$false)]
    [string]$VehTags = ""    # Comma-separated list (e.g., "Class_Heavy,ATGM")
)

# Available vehicle types from VehicleSettingsLoader.java:
# RSV, IFV, APC, MBT, LOGI, LTV, UH, AH, ULTV, SPAA, MRAP, TRAN, SPA, TD, MGS, MSV

# Available vehicle tags from VehicleSettingsLoader.java:
# Class_Light, Class_Medium, Class_Heavy, AGL, ATGM, Low Caliber, RWS, Watercraft

Write-Host "Vehicle Extractor Script" -ForegroundColor Cyan
Write-Host "=========================" -ForegroundColor Cyan
Write-Host ""

# Check if input file exists
if (-not (Test-Path $InputFile)) {
    Write-Host "Error: Input file '$InputFile' not found!" -ForegroundColor Red
    exit 1
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

# HashSet to track unique vehicles (by type:rawType)
$script:uniqueVehicles = @{}
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
                if (-not $script:uniqueVehicles.ContainsKey($key)) {
                    $script:uniqueVehicles[$key] = $true
                    $script:vehicleList += $key
                    $count++
                    Write-Host "  [+] $key" -ForegroundColor Gray
                }
            }
        }
    }
    return $count
}

# Process team1Units
$team1Count = 0
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

# Process team2Units
$team2Count = 0
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

# Sort vehicles alphabetically
$script:vehicleList = $script:vehicleList | Sort-Object

# Write to output file
Write-Host "`nWriting results to: $OutputFile" -ForegroundColor Green
try {
    $script:vehicleList | Out-File -FilePath $OutputFile -Encoding UTF8
    Write-Host "  Successfully wrote $($script:vehicleList.Count) vehicles" -ForegroundColor Green
} catch {
    Write-Host "  Error writing file: $_" -ForegroundColor Red
}

# Summary
Write-Host ""
Write-Host "=========================" -ForegroundColor Cyan
Write-Host "Summary:" -ForegroundColor Cyan
Write-Host "  Total unique vehicles found: $($script:vehicleList.Count)" -ForegroundColor White
Write-Host "  From team1Units: $team1Count" -ForegroundColor White
Write-Host "  From team2Units: $team2Count" -ForegroundColor White
Write-Host "  Output saved to: $OutputFile" -ForegroundColor Green
Write-Host "=========================" -ForegroundColor Cyan

