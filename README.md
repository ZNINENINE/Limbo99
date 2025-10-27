# Limbo99

Plugin Minecraft (Paper/Spigot 1.21.x) - Limbo99

Features:
- Teleporta jogadores mortos para um local configurável e impede interação por tempo configurável.
- ActionBar com contagem regressiva (hh mm ss).
- BossBar com contagem regressiva.
- Scoreboard tag "[PRESO]" (config ON/OFF).
- Efeitos sonoros e partículas sobrenaturais.
- Câmera espectadora para presos (config ON/OFF).
- Comando principal `/limbo` com subcomandos: setloc | free <player> | reload | view <nick> | exitcam

## Build (GitHub Actions)
This project contains GitHub Actions workflows:
- `.github/workflows/build.yml` - builds on push to `main` and uploads artifact.
- `.github/workflows/release-build.yml` - builds and creates a GitHub Release when you push a tag like `v1.0.0`.

If you prefer local build:
1. Run `gradle wrapper` (if wrapper not present)
2. Run `./gradlew shadowJar`
3. Jar will be in `build/libs/Limbo99-1.0.0.jar`

## Installing
Put the jar in your server `plugins/` folder (Paper/Spigot 1.21.x) and start the server.

## Config
See `src/main/resources/config.yml` for options.