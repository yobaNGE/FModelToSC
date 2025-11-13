# FModelToSC

Convert FModel‑exported Squad gameplay layers (.json) into SquadCalc‑compatible JSON and serve them locally via a tiny mock backend.

- Converter: Java 21 CLI (`com.pipemasters.Main`) that writes to `output/`.
- Mock API: Node.js Express server in `mock-api/` that mimics the SquadCalc backend (https://github.com/sh4rkman/SquadCalc) and serves static images under `/api/img`.

---

## Prerequisites

- FModel (to export a layer’s properties to JSON)
- Java 21 (JDK) and Maven (to build/run the converter)
- Node.js 18+ and npm (to run the mock API)
- Optional: IntelliJ IDEA (or any Java IDE) if you prefer running `Main` directly

Notes for FModel:
- In FModel, select the game "Squad".
- Settings → General → enable "Local Mapping File" and point it to the provided `SQUADGAME904b.usmap` file in this repository.
- Drop the mod folder to `\Squad\SquadGame\Plugins\Mods\`.

---

## Quick start

1) Export a layer from FModel
- Load your mod in FModel.
- Go to `maps/gameplay_layers`.
- Right‑click the layer → "Save properties (.json)".
- Example exported file: `SD_Fallujah_Invasion_v3.json`.

Images for reference:
<p align="center"><strong>Load mod</strong></p>
<p align="center">
  <img width="770" height="909" alt="image" src="https://github.com/user-attachments/assets/e6a30c26-45b2-44a4-8fde-b2f73a57d59c" />
</p>

<p align="center"><strong>Navigate to layers</strong></p>
<p align="center">
  <img width="639" height="368" alt="image" src="https://github.com/user-attachments/assets/d53695a8-70f5-4d74-a5eb-79476f7e24e0" />
</p>

<p align="center"><strong>Save properties (.json)</strong></p>
<p align="center">
  <img width="413" height="395" alt="image" src="https://github.com/user-attachments/assets/0c15d465-b236-446c-8ba0-69496aac468d" />
</p>


2) Convert the exported JSON (Java CLI)
- From the repo root, build and run the converter. The output goes to `output/` with the same filename.

Windows (cmd.exe):

```cmd
:: Build and copy runtime dependencies
mvn package

:: Run the converter
mvn exec:java "-Dexec.args=SD_Fallujah_Invasion_v3.json"
```

- In IDEs, run `com.pipemasters.Main` with a single Program Argument: the full path to your exported `.json`.
- Note: this project does not build a fat JAR by default.

3) Serve layers via the mock SquadCalc backend (mock-api)
- The mock API serves data from `mock-api/data/` and static assets from `mock-api/public/img` under `/api/img`.

```cmd
cd mock-api
npm install
npm start
```

- Default base URL: http://localhost:4000/api
- Change port and disable factions in SquadCalc project .env file:

```cmd
API_URL=http://localhost:4000/api
DISABLE_FACTIONS=true
```

- Dev mode with auto‑reload of the server code:

```cmd
npm run dev
```

Tip: Data files in `mock-api/data/` are read per request; you can edit them without restarting the server.

---

## Using the mock API with your converted file

1. Convert your layer (step 2). You’ll get `output/YourLayer.json`.
2. Copy it to `mock-api/data/get/layer/`:
   - `mock-api/data/get/layer/YourLayer.json`
3. Add the layer name to `mock-api/data/get/layers/<MapName>.json` so it appears in `/api/get/layers?map=<MapName>`.
   - Example: for Fallujah, edit `mock-api/data/get/layers/Fallujah.json` and follow the existing format.

If you see 404s like `No mock data for map '...'` or `No mock data for layer '...'`, ensure filenames are exact and entries exist in the map list file.

---

## API surface (mock server)

- GET `/api` → Lists available top‑level resources in `mock-api/data/`.
- GET `/api/get/layers?map=<MapName>` → Returns layer list for that map.
- GET `/api/get/layer?name=<LayerName>` → Returns the converted layer JSON.
- GET `/api/img/...` → Serves static images from `mock-api/public/img`.
- GET `/api/:resource` → Returns `mock-api/data/:resource.json` if present.
- GET `/api/:resource/:id` → Returns `mock-api/data/:resource/:id.json` if present.

---

## Repository structure (short)

- `src/main/java` — Java sources, entry point: `com.pipemasters.Main`
- `output/` — converted layer JSON files
- `mock-api/` — mock backend for SquadCalc
  - `server.mjs` — Express server
  - `data/get/layers/*.json` — lists of layers per map
  - `data/get/layer/*.json` — individual layer payloads
  - `public/img/...` — static assets served under `/api/img`
- `SQUADGAME904b.usmap` — mapping file for FModel "Local Mapping File"

---

## Notes

- Java version: this project targets Java 21. If `mvn -v` shows an older JDK, update `JAVA_HOME`.
- The converter preserves the input filename, writing to `output/<same-name>.json`.
- CORS is enabled; requests are logged with morgan.
- I was goind to implement units and teamConfigs but that's involves parsing more Unreal data structures, so maybe later. Way, way later.

---

## Examples of output

<img width="1908" height="950" alt="image" src="https://github.com/user-attachments/assets/0aa142f7-cc41-418f-bf28-3e3bc0d534ec" />

<img width="1902" height="831" alt="image" src="https://github.com/user-attachments/assets/9a71837f-d399-4509-88d9-dbe46ae14c11" />

<img width="1917" height="950" alt="image" src="https://github.com/user-attachments/assets/3f282c83-52db-447c-9c26-2cb6e5e471a8" />


---

## Attribution

- Mock API is intentionally designed to mock the SquadCalc backend: https://github.com/sh4rkman/SquadCalc
- I understand why sharkman doesn't really want add modded layers. First of all too much work to add new factions. And this layer parsing thing probably have some bugs.
