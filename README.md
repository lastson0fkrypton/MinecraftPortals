# Minecraft Portals (Fabric)

A starter Fabric mod that adds a **Portal Gun**.

## Features

- `portal_gun` item
- Left click on a block: places a **blue** portal (1 wide × 2 high)
- Right click: places an **orange** portal (1 wide × 2 high)
- Re-shooting the same color replaces your previous portal of that color

## Version target

This project is currently configured for **Minecraft 1.21.11** with Fabric Loader **0.18.4**.

If you specifically need another patch line (for example `1.21.11` if/when available), update these values in `gradle.properties`:

- `minecraft_version`
- `yarn_mappings`
- `fabric_version`

## Run in dev

1. Install **JDK 21**.
2. From project root, use the wrapper commands:
   - `./gradlew start` (macOS/Linux)
   - `gradlew.bat start` (Windows)

`start` is an alias for Fabric's `runClient` task.

### Local Gradle bootstrap script (Windows)

If you want Gradle downloaded automatically and kept local to this repo:

- `powershell -ExecutionPolicy Bypass -File .\scripts\gradle-local.ps1 start`

This script will:

- use local cache folder `.gradle-local`
- auto-download Gradle into `.tools` only if needed
- generate wrapper if missing
- run the task you pass (`start`, `buildMod`, `copyModJar`, etc.)

## Build mod jar

- `./gradlew buildMod` (macOS/Linux)
- `gradlew.bat buildMod` (Windows)

`buildMod` is an alias for `build`.

The built, remapped mod jar is created in:

- `build/libs`

## Copy jar to MultiMC mods folder

You can build and copy in one command:

- `gradlew.bat copyModJar -PmodsDir="C:\\Path\\To\\MultiMC\\instances\\MyInstance\\.minecraft\\mods"`

Or set a default once in `gradle.properties`:

- `minecraft_mods_dir=run/mods`

Then run simply:

- `gradlew.bat copyModJar`

Or on macOS/Linux:

- `./gradlew copyModJar -PmodsDir="/path/to/instance/.minecraft/mods"`

Using the local bootstrap script on Windows:

- `powershell -ExecutionPolicy Bypass -File .\scripts\gradle-local.ps1 copyModJar -ModsDir "C:\\Path\\To\\MultiMC\\instances\\MyInstance\\.minecraft\\mods"`

## Item ID

- `minecraftportals:portal_gun`

Give yourself the item in-game with:

- `/give @p minecraftportals:portal_gun`
