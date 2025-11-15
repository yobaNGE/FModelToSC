# FModelToSC

Convert FModel‑exported Squad mod data into JSON payloads that SquadCalc can read. The project now ships with two tooling entry points:

- **Layer exporter (`com.pipemasters.Main`)** – takes a gameplay layer export (the `layerdata.json` that references the layer, objectives, map assets, capture points, etc.), resolves every linked asset that FModel exported, and writes a SquadCalc‑compatible payload.
- **Units exporter (`com.pipemasters.units.UnitsMain`)** – scans exported faction setup data and builds `units.json` with teams, vehicles, and commander abilities for every faction used by your mod.

The repository also contains a mock SquadCalc backend (`mock-api/`) so you can preview your converted layers locally.

---

## Prerequisites

| Tool | Purpose |
| --- | --- |
| [FModel](https://fmodel.app/) + the mapping file `SQUADGAME10.usmap` from this repository | Export gameplay layers, map data, faction setups, and any missing assets. |
| [FModel fork](https://github.com/yobaNGE/FModel/releases/tag/1.0.0) | Provides the **Missing Asset Extractor** used to dump extra assets listed in `missing-assets.txt`. |
| Java 21 (JDK) + Maven | Build and run both exporters. |
| Node.js 18+ and npm | Run the mock API server. |
| Optional: IntelliJ IDEA or any Java IDE | For running the exporters without Maven commands. |

---

## 0. Configure FModel

1. Launch FModel and choose the **Squad** game profile.
   <img width="647" height="392" alt="image" src="https://github.com/user-attachments/assets/067e5136-8fb4-4661-9f87-2571bf6e68f5" />

2. Go to **Settings → General** and enable **Local Mapping File**. Point it at [`SQUADGAME10.usmap`](./SQUADGAME10.usmap) (bundled with this repo).

<img width="1158" height="649" alt="image" src="https://github.com/user-attachments/assets/ae10f426-5e7e-481f-813e-6417c64964ab" />

3. Drop your mod folder into `\Squad\SquadGame\Plugins\Mods\` so FModel can load it.

<img width="652" height="132" alt="image" src="https://github.com/user-attachments/assets/36d940f5-fdce-436c-8cad-a26855fc19ca" />

4. Check that Fmodel sees your mod, then press load.

<img width="452" height="789" alt="image" src="https://github.com/user-attachments/assets/f20e61c5-4cbf-4e8d-994b-e3f2a8927f5e" />

4. If you plan to use the Missing Asset Extractor, install the fork alongside your normal FModel install. I recommend you to, cause thats about ~100 assets to extract.

<img width="1157" height="773" alt="image" src="https://github.com/user-attachments/assets/385367ff-2565-4445-afdf-bf9d49d62f7a" />

All exports referenced below assume you right‑click assets or folders in FModel and choose **Save properties (.json)**.

---

## 1. Export faction setup data (for UnitsMain)

Run this step once per mod, then rerun only when you add factions or vehicles.

1. In FModel open your mod → `Content/Settings/FactionSetup`.

<img width="576" height="1157" alt="image" src="https://github.com/user-attachments/assets/542b1fa3-8ece-483d-a461-1ceadddc1d9c" />

2. Export the entire `FactionSetup` folder to JSON (every subfolder per faction should now contain the exported `.json` files). Keep the directory structure exactly as FModel produced it.

3. Put path to Factionsetup as an argument, e.g. `C:\Program Files\Fmodel\Output\Exports\SquadGame\Plugins\Mods\Steel_Division\Content\Settings\Factionsetup`.

> **Tip:** Skip `Template` factions when exporting if they exist; the parser ignores them. Remove factions that are deprecated/WIP.

---

## 2. Build `output/units.json` with UnitsMain

1. From the repo root run a Maven build (first run downloads dependencies):
   ```bash
   mvn package
   ```
2. Execute the units exporter, pointing it at the exported `FactionSetup` directory:
   ```bash
   mvn exec:java \
     -Dexec.mainClass=com.pipemasters.units.UnitsMain \
     -Dexec.args="C:\Program Files\Fmodel\Output\Exports\SquadGame\Plugins\Mods\Steel_Division\Content\Settings\Factionsetup"
   ```
   
3. The tool writes `output/units.json` and logs how many factions were parsed for Team 1 and Team 2.
4. If anything is missing, the run also creates/updates **`missing-assets.txt`** in the project root. Every line is an asset that needs to be exported (commander ability settings, vehicle data tables, delay presets, etc.).
5. Open the yobaNGE FModel fork → **Tools → Missing Asset Extractor**, paste the contents of `missing-assets.txt`, press **Extract**. I just extract stuff in default Fmodel directory. Parser expect extracted assets to have same hierarchy as in Fmodel.
6. Rerun step 2 until `missing-assets.txt` is no longer populated. When it stays empty the unit data is complete.

> `UnitsMain` only needs to run again when you add new factions, modify vehicles, or see new missing assets. The layer exporter reads `output/units.json` automatically; you can also pass a custom path as the third argument to `Main` (see below).

---

## 3. Export gameplay data for a layer

For every layer you want to convert:

1. In FModel navigate to `Maps/<MapName>/Gameplay_Layer_Data/Layer/` and export **`layerdata`** (the asset is usually named same way as layer or closely resembles it). This JSON references the actual gameplay layer asset.
<img width="340" height="289" alt="image" src="https://github.com/user-attachments/assets/e719b52f-e00b-4241-8001-4eddcb6a38a2" />

<img width="465" height="372" alt="image" src="https://github.com/user-attachments/assets/e7a75a70-1b47-4358-bbd7-74dd6ce5904d" />

2. Ensure the referenced assets exist in your export directory. The layer exporter follows those references automatically. If a file is missing it will be written to console during conversion.

Example path that becomes the first CLI argument:
```
"C:\Program Files\Fmodel\Output\Exports\SquadGame\Plugins\Mods\Steel_Division\Content\Maps\Yehorivka\Gameplay_Data\Layer\SD_Yehorivka_Invasion_v5.json"
```

---

## 4. Convert the layer with `com.pipemasters.Main`

With `output/units.json` in place you can now create SquadCalc‑compatible layer payloads:

```bash
mvn exec:java \
  -Dexec.mainClass=com.pipemasters.Main \
  -Dexec.args="C:\Program Files\Fmodel\Output\Exports\SquadGame\Plugins\Mods\Steel_Division\Content\Maps\Yehorivka\Gameplay_Data\Layer\SD_Yehorivka_Invasion_v5.json"
```

What the arguments mean:

1. **Required:** path to the exported gameplay data JSON (the `layerdata.json` discussed above).
2. **Optional:** explicit path to the actual gameplay layer JSON if the resolver cannot find it automatically.
3. **Optional:** path to a `units.json` file. If omitted, the tool uses `output/units.json`.

Successful runs print the detected layer version, write the converted payload to `output/<LayerName>_vX.json`, and log the absolute path. `missing-layers.txt` is updated with any assets that could not be resolved—export them via FModel, copy into your exports folder, and rerun the command.

> The exporter preserves whatever folder structure you have under your export root. You can point it directly at an export from FModel or at a copy living elsewhere on disk.

---

## 5. Preview layers with the mock SquadCalc API

The mock server lives in `mock-api/` and now proxies missing data to the live SquadCalc service when possible.

```bash
cd mock-api
npm install
npm start
```

- Base URL: <http://localhost:4000/api>
- Static images are proxied from `https://squadcalc.app/api/img/...`, so you no longer need to download image assets manually.
- `/api/get/layer?name=SD_*` is served from local JSON files in `mock-api/data/get/layer/`.
- `/api/get/layer` for other names is proxied to `https://squadcalc.app/api/get/layer`.

To view a converted layer in SquadCalc:

1. Copy `output/YourLayer_vX.json` to `mock-api/data/get/layer/YourLayer_vX.json`.
2. Add the layer name to `mock-api/data/get/layers/<MapName>.json` so it appears in `/api/get/layers?map=<MapName>`.
3. Point your SquadCalc `.env` at the mock server:
   ```bash
   API_URL=http://localhost:4000/api
   ```

Use `npm run dev` for auto‑reload while tweaking the mock server code.

---

## Repository layout

- `src/main/java/com/pipemasters/Main.java` – layer exporter entry point.
- `src/main/java/com/pipemasters/units/UnitsMain.java` – units exporter entry point.
- `output/` – generated `units.json` and converted layer files.
- `mock-api/` – local backend with Express, JSON fixtures, and proxy behaviour.
- `SQUADGAME10.usmap` – mapping file for FModel’s Local Mapping setting.

---

## Troubleshooting

- **`Gameplay data file does not exist`** – double‑check the first CLI argument or drag‑and‑drop the JSON onto your terminal to get the absolute path.
- **`Unable to resolve gameplay layer file` / `missing-layers.txt` keeps filling up** – export every asset listed in the file via FModel, copy it into your export directory, and rerun the layer exporter.
- **`missing-assets.txt` lists more files after running UnitsMain** – repeat the Missing Asset Extractor workflow until the file stays empty.
- **Layer references factions that are not in `units.json`** – rerun UnitsMain after updating your mod, then reconvert the layer.
- **Mock API 404s for your map** – ensure you copied the converted layer JSON to `mock-api/data/get/layer/` and added the layer name to the corresponding map list file under `mock-api/data/get/layers/`.

---
## Current limitations
- Does not support ATGM detection for vehicles. (It sort of does, but due to Attack helis and other stuff that was meant to have ATGMs, but doesn't actually have them, the detection is unreliable.)
- TicketValue is not calculated for vehicles. Due to how game handles tickets, this is non-trivial and may require manual adjustment.
- isAmphibious flag is not detected for vehicles. May add it later.
- Layers that are not Invasion will likely have issues. Or straight-up won't parse.
