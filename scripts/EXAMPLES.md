# Vehicle Extractor - Usage Examples

## Quick Start Examples

### Example 1: Extract ALL vehicles
```powershell
powershell.exe -ExecutionPolicy Bypass -File extract_vehicles.ps1 -InputFile "output\units.json" -OutputFile "all_vehicles.txt"
```

### Example 2: Extract only Main Battle Tanks (MBT)
```powershell
powershell.exe -ExecutionPolicy Bypass -File extract_vehicles.ps1 -InputFile "output\units.json" -OutputFile "mbts.txt" -VehTypes "MBT"
```

### Example 3: Extract all helicopters (Attack + Utility)
```powershell
powershell.exe -ExecutionPolicy Bypass -File extract_vehicles.ps1 -InputFile "output\units.json" -OutputFile "helicopters.txt" -VehTypes "AH,UH"
```

### Example 4: Extract vehicles with ATGM capability
```powershell
powershell.exe -ExecutionPolicy Bypass -File extract_vehicles.ps1 -InputFile "output\units.json" -OutputFile "atgm_vehicles.txt" -VehTags "ATGM"
```

### Example 5: Extract heavy armor (MBT, IFV, APC with Heavy tag)
```powershell
powershell.exe -ExecutionPolicy Bypass -File extract_vehicles.ps1 -InputFile "output\units.json" -OutputFile "heavy_armor.txt" -VehTypes "MBT,IFV,APC" -VehTags "Class_Heavy"
```

### Example 6: Extract all logistics vehicles
```powershell
powershell.exe -ExecutionPolicy Bypass -File extract_vehicles.ps1 -InputFile "output\units.json" -OutputFile "logistics.txt" -VehTypes "LOGI"
```

### Example 7: Extract light vehicles
```powershell
powershell.exe -ExecutionPolicy Bypass -File extract_vehicles.ps1 -InputFile "output\units.json" -OutputFile "light_vehicles.txt" -VehTags "Class_Light"
```

### Example 8: Extract amphibious vehicles (Watercraft tag)
```powershell
powershell.exe -ExecutionPolicy Bypass -File extract_vehicles.ps1 -InputFile "output\units.json" -OutputFile "watercraft.txt" -VehTags "Watercraft"
```

### Example 9: Extract transport vehicles
```powershell
powershell.exe -ExecutionPolicy Bypass -File extract_vehicles.ps1 -InputFile "output\units.json" -OutputFile "transports.txt" -VehTypes "TRAN"
```

### Example 10: Extract artillery (Self-Propelled Artillery)
```powershell
powershell.exe -ExecutionPolicy Bypass -File extract_vehicles.ps1 -InputFile "output\units.json" -OutputFile "artillery.txt" -VehTypes "SPA"
```

---

## Available Vehicle Types (vehType)

| Type | Description |
|------|-------------|
| **RSV** | Reconnaissance Strike Vehicle |
| **IFV** | Infantry Fighting Vehicle |
| **APC** | Armored Personnel Carrier |
| **MBT** | Main Battle Tank |
| **LOGI** | Logistics Vehicle |
| **LTV** | Light Transport Vehicle |
| **UH** | Utility Helicopter |
| **AH** | Attack Helicopter |
| **ULTV** | Ultra-Light Transport Vehicle |
| **SPAA** | Self-Propelled Anti-Aircraft |
| **MRAP** | Mine-Resistant Ambush Protected |
| **TRAN** | Transport |
| **SPA** | Self-Propelled Artillery |
| **TD** | Tank Destroyer |
| **MGS** | Mobile Gun System |
| **MSV** | Mission Support Vehicle |

---

## Available Vehicle Tags (vehTags)

| Tag | Description |
|-----|-------------|
| **Class_Light** | Light weight class vehicles |
| **Class_Medium** | Medium weight class vehicles |
| **Class_Heavy** | Heavy weight class vehicles |
| **AGL** | Automatic Grenade Launcher |
| **ATGM** | Anti-Tank Guided Missile |
| **Low Caliber** | Low caliber weapons |
| **RWS** | Remote Weapon Station |
| **Watercraft** | Amphibious/water vehicles |

---

## Tips

- You can combine multiple vehicle types: `-VehTypes "MBT,IFV,APC"`
- You can combine multiple tags: `-VehTags "Class_Heavy,ATGM"`
- Filters work together (AND logic): vehicles must match BOTH type AND tag filters
- Leave filters empty to extract all vehicles
- The script automatically removes duplicates based on `type:rawType` combination

