# OpenTracks HUD Pro

Fork de [OSMDashboard](https://codeberg.org/OpenTracksApp/OSMDashboard) (Apache-2.0) que actúa como
**dashboard de mapas para [OpenTracks](https://github.com/OpenTracksApp/OpenTracks)** con:

- 🗺️ **Mapas OSM e ICGC** (Catalunya) online **y offline** (MBTiles), motor **MapLibre GL Native**.
- 📊 **HUD "pro" configurable por widgets** superpuesto sobre el mapa mientras grabas.
- 🧭 **Seguimiento de tracks** precargados (GPX importado o sincronizado desde Endurain): ruta,
  distancia restante, desviación y rumbo.
- ☁️ **Subida a [Endurain](https://github.com/endurain-project/endurain)** con cola offline (WorkManager).
- 🎨 App de gestión propia en **Jetpack Compose / Material 3** para configurarlo todo visualmente.

## Arquitectura

Dos caras en el mismo APK (`applicationId = cat.hudpro.opentracks`):

| Cara | Punto de entrada | Descripción |
|------|------------------|-------------|
| **Visor** | `viewer.MapViewerActivity` | Lo lanza OpenTracks vía su Dashboard API (`Intent.OpenTracks-Dashboard`). Mapa MapLibre + HUD + seguimiento. |
| **Gestión** | `manager.ManagerActivity` | Launcher. Diseñador de HUD, capas, mapas offline, biblioteca de tracks, Endurain. |

```
data/opentracks/  Contrato con OpenTracks (Dashboard API) — portado de OSMDashboard, agnóstico del render.
                  DashboardReader expone StateFlows y observa cambios en vivo (ContentObserver + delta).
data/map/         Catálogo de fuentes (OSM/ICGC), MapStyleFactory (raster/vector/MBTiles), OfflineMapStore.
data/tracks/      Room: biblioteca de rutas a seguir. TrackRepository (import GPX, colecciones).
data/endurain/    Cliente Retrofit (X-API-Key), EndurainRepository, EndurainUploadWorker (cola).
data/gpx/         Lector/escritor GPX (DOM; funciona en Android y JVM).
data/prefs/       Preferencias del visor y de Endurain (SharedPreferences).
viewer/           MapLibreController (estilo + capas), hud/ (métricas + overlay Compose),
                  follow/ (FollowRouteEngine).
manager/          Navegación + pantallas Compose Material 3.
```

### Contrato con OpenTracks (se conserva)
OpenTracks (`de.dennisguse.opentracks`) pasa 3 URIs `[tracks, trackpoints, markers]` por Intent con
grants temporales (ClipData). Los trackpoints vienen en **microgrados enteros** (÷1e6), columnas
`_id, trackid, latitude, longitude, time, type, speed`. Se soportan protocolos **1, 2 y 3**.
> Nota: la proyección de trackpoints **no incluye altitud por punto**; altitud/pendiente/VAM
> instantáneos requieren un `ElevationProvider` (p.ej. el DEM del ICGC) — hay un hook preparado.
> El desnivel agregado (ganancia, min/max) sí llega en las estadísticas del track.

### Mapas ICGC
- Tiles WMTS EPSG:3857 estándar, sin API key, CC-BY © ICGC:
  `https://geoserveis.icgc.cat/servei/catalunya/mapa-base/wmts/{layer}/MON3857NW/{z}/{x}/{y}.png`
- Offline: importa los **MBTiles oficiales** desde <https://visors.icgc.cat/appdownloads/>.

### Endurain
- Subida: `POST /api/v1/activities/create/upload` (multipart, campo `file`) con cabecera
  `X-API-Key` (scope `activities:upload`). La subida se dispara automáticamente al parar la grabación.

## Compilar

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
./gradlew :app:assembleDebug        # APK en app/build/outputs/apk/debug/
./gradlew :app:testDebugUnitTest    # tests
```

Requisitos: JDK 17, Android SDK (compileSdk 35, build-tools 35). AGP 8.7 · Kotlin 2.0 · MapLibre 11.

## Verificación end-to-end (manual, en dispositivo)
1. Instala este APK y OpenTracks. En OpenTracks: Ajustes → API pública / Dashboard → selecciona
   «HUD Pro». Inicia una grabación y abre el dashboard: el visor pinta el track en vivo.
2. En la app de gestión: elige capa (OSM/ICGC), diseña el HUD, importa un GPX y márcalo «a seguir».
3. Configura Endurain (host + API key), pulsa «Guardar i provar connexió». Para la grabación → se
   encola la subida.

## Licencia
Apache-2.0 (ver `LICENSE`/`NOTICE`). Datos de mapa: © OpenStreetMap contributors (ODbL),
© Institut Cartogràfic i Geològic de Catalunya (ICGC, CC-BY).
