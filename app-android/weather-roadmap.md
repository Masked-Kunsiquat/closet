Audit Findings (updated 2026-03-24)

What Already Exists

outfit_logs table — columns in schema v1 (current):
- temperature_low REAL nullable
- temperature_high REAL nullable
- weather_condition TEXT nullable (Room type-converted from WeatherCondition enum)

WeatherCondition enum (core/data/.../model/Enums.kt):
Sunny | Partly Cloudy | Cloudy | Rainy | Snowy | Windy
Has fromString() factory. No fromWmoCode() factory yet.

These fields are on the log (what the user wore on a past date), not on
clothing items or lookup tables. They record observed conditions after the
fact, not predictive/filter attributes.

Seasons lookup (DatabaseSeeder.kt):
Spring | Summer | Fall | Winter | All Season
Generic calendar-based. No temperature ranges, no precipitation data attached.

Occasions lookup: 9 entries (Casual → Special Occasion). Tangentially
weather-aware (Outdoor/Hiking) but not weather-driven.

Settings screen: EXISTS — features/settings module, fully integrated.
  - SettingsScreen.kt + SettingsViewModel.kt (HiltViewModel)
  - Appearance section: dynamic color toggle (API 31+) + accent color picker
  - Accessed via gear icon on ClosetScreen top app bar
  - Route: SettingsRoute, registered in ClosetNavGraph.kt

DataStore: EXISTS — core/ui/preferences/PreferencesRepository.kt
  - DataStore file name: "closet_prefs"
  - Keys: accent (String), dynamic_color (Boolean)
  - Provided as @Singleton via Hilt in core/ui
  - Dependency already in libs.versions.toml (datastorePreferences = "1.1.4")
  - NOT yet added to core/data/build.gradle.kts

HTTP client: None — no OkHttp, Retrofit, or Ktor in libs.versions.toml.
Location permissions: None in manifest or any source file.
Navigation: 4 bottom-nav tabs (Closet / Outfits / Journal / Stats), 11 total
registered destinations. Settings route registered (gear icon entry point).
Database: schema v1 (pre-release; no shipped installs — migration chain can
  still be reset if needed).

  ---
Schema Granularity Assessment

What exists: temperature_low / temperature_high on outfit_logs
Maps to forecast data?: Yes — directly maps to a daily forecast range
────────────────────────────────────────
What exists: WeatherCondition (6 values)
Maps to forecast data?: Partially — covers precip (Rainy, Snowy), sky cover
    (Sunny, Partly Cloudy, Cloudy), wind (Windy) but collapses everything into
    one field. Missing: humidity, UV index, feels-like, heavy vs light rain,
    thunderstorm, fog
────────────────────────────────────────
What exists: Seasons (4 + All Season)
Maps to forecast data?: Very coarse — calendar quarters only, not
temperature-band driven
────────────────────────────────────────
What exists: Occasions
Maps to forecast data?: Not weather-related

Gap summary: The schema records user-observed weather on past logs, but
nothing on clothing items themselves links them to weather suitability.
There is no temperature-band, wind-chill, precipitation-type, or UV
metadata on clothing_items, categories, or any junction table. This is the
primary gap that would need filling for outfit recommendation filtering.

  ---
Implementation Roadmap

✅ = done  🔲 = not done

Phase 0 — Groundwork (no user-visible features)

✅ 0.1 — DataStore dependency
datastorePreferences = "1.1.4" in libs.versions.toml. Added to both
core/ui/build.gradle.kts and core/data/build.gradle.kts.

✅ 0.2 — WeatherPreferences data class + DataStore repository
Create core/data/.../repository/WeatherPreferencesRepository.kt.
Use a separate DataStore file ("weather_prefs") so weather concerns stay
in core/data and don't pollute core/ui's "closet_prefs" file.
Persists:
  weatherEnabled: Boolean = false
  weatherService: WeatherService = OPEN_METEO  // enum: OPEN_METEO | NWS | GOOGLE
  googleApiKey: String = ""
  temperatureUnit: TemperatureUnit = CELSIUS   // enum: CELSIUS | FAHRENHEIT
  cachedForecastJson: String = ""
  cachedForecastTimestamp: Long = 0L
  cachedLatitude: Double = 0.0
  cachedLongitude: Double = 0.0

Provide via Hilt @Singleton in DataModule.kt.

✅ 0.3 — HTTP client
Ktor 3.1.3 (ktor-client-okhttp + content-negotiation + serialization-kotlinx-json
+ client-logging) added to libs.versions.toml and core/data/build.gradle.kts.
HttpClient singleton provided in DataModule: OkHttp engine, ContentNegotiation
with ignoreUnknownKeys=true, Timber-backed logging (BODY in debug, NONE in
release). BuildConfig generation enabled in core/data for the DEBUG flag.
INTERNET permission added to AndroidManifest.xml (pulled forward from Phase 2.1
— it's a normal permission with no user-facing prompt, needed the moment any
HTTP call is made).

  ---
Phase 1 — Settings screen

✅ 1.1 — Settings destination
features/settings module exists. SettingsRoute registered in
ClosetNavGraph.kt. Gear icon wired on ClosetScreen. DONE.

✅ 1.2 — Settings screen skeleton
SettingsScreen.kt + SettingsViewModel.kt exist. Dynamic color toggle +
accent color picker implemented. DONE.

✅ 1.3 — Weather section in Settings
SettingsViewModel now injects WeatherPreferencesRepository alongside
PreferencesRepository. Exposes 4 new StateFlows (weatherEnabled,
weatherService, googleApiKey, temperatureUnit) + 5 new actions.
SettingsScreen adds a "Weather" section:
- Toggle: "Show weather forecast" (off by default)
- Service picker (SingleChoiceSegmentedButtonRow, visible when on):
  Open-Meteo / NWS / Google
- API key field (OutlinedTextField, visible only when Google selected)
- Temperature unit picker (°C / °F segmented button, visible when on)
- "Clear cached forecast" tappable ListItem
features/settings/build.gradle.kts gains :core:data dep.

  ---
Phase 2 — Location permission

✅ 2.1 — Manifest
ACCESS_COARSE_LOCATION added to AndroidManifest.xml.
(INTERNET already added in Phase 0.3.)

🔲 2.2 — Permission request flow
When the Settings toggle transitions from off → on:
1. Check if ACCESS_COARSE_LOCATION is already granted.
2. If not: use Compose rememberLauncherForActivityResult(RequestPermission)
   to surface the native modal.
3. On grant: save weatherEnabled = true in DataStore, trigger initial
   forecast fetch.
4. On deny: revert the toggle to off, show a non-blocking snackbar:
   "Location access is needed to fetch weather. You can enable it later in
   System Settings."
5. On permanent deny (shouldShowRationale = false after a denial): show a
   dialog explaining why + deep-link to system app settings via
   ACTION_APPLICATION_DETAILS_SETTINGS. Still revert the toggle.

🔲 2.3 — Location fetch
Use LocationManager (no Play Services dependency — the app has none).
Do a single passive location read (NETWORK_PROVIDER is sufficient for
city-level accuracy). Cache lat/lon in DataStore alongside the forecast.
Only re-request location when fetching a fresh forecast.

  ---
Phase 3 — Weather service abstraction

🔲 3.1 — WeatherService interface
interface WeatherServiceClient {
    suspend fun fetchDailyForecast(lat: Double, lon: Double): Result<DailyForecast>
}

data class DailyForecast(
    val date: LocalDate,
    val tempLow: Double,       // always stored in °C; convert for display
    val tempHigh: Double,
    val condition: WeatherCondition,
    val precipitationMm: Double,
    val windSpeedKmh: Double,
    val uvIndex: Int?,
    val humidity: Int?         // percent
)

Must implement WeatherCondition enum expansion (Gap 3 below) before this
step — the mapping from WMO codes depends on the expanded enum.

🔲 3.2 — Open-Meteo implementation (default, no key)
Endpoint: https://api.open-meteo.com/v1/forecast
Params: latitude, longitude,
  daily=temperature_2m_max,temperature_2m_min,precipitation_sum,
  windspeed_10m_max,weathercode,uv_index_max,
  forecast_days=7, timezone=auto
Map WMO weathercode integer → WeatherCondition via fromWmoCode() factory.
This is the most important mapping work in the whole feature.

🔲 3.3 — NWS implementation (US-only, no key)
Two-step: /points/{lat},{lon} → get forecastUrl → fetch that URL.
NWS returns 12-hour periods — merge pairs into daily low/high.
Detect US location: if /points returns 404, notify the user that NWS is
US-only and ask them to switch service (do not silently fall back — the
user chose NWS deliberately).

🔲 3.4 — Google Weather API implementation (key required)
Per Google's current Weather API docs. Validate key is non-empty before
any request. Surface an error state in Settings if the key returns 401.

🔲 3.5 — WeatherRepository
Wraps the active client. Implements the caching rule:
- Read cached forecast from DataStore (cachedForecastJson + timestamp).
- If age < FORECAST_CACHE_TTL_MS (3 hours): return cached data immediately.
- Otherwise: fetch fresh, serialize to JSON, store with new timestamp.
- On fetch failure: return cached data if present, otherwise
  Result.failure with a typed AppError.

FORECAST_CACHE_TTL_MS = 3 * 60 * 60 * 1000L  // named constant, not magic

Hilt: use @Named qualifier or a sealed binding to provide the correct
WeatherServiceClient at runtime based on
WeatherPreferencesRepository.weatherService.

  ---
Phase 4 — Surface forecast in the Journal

The Journal screen is the natural home for weather since it already shows
wear history by date.

🔲 4.1 — Today's forecast widget on Journal header
When weatherEnabled = true and forecast is cached for today: show a compact
weather chip (condition icon + temp range) at the top of JournalScreen
above the calendar. Tapping it opens a bottom sheet with the 7-day
forecast.

🔲 4.2 — Auto-populate weather on log creation
In JournalViewModel.logOutfitOnDate(): if weatherEnabled = true and a
cached forecast exists for today's date, pre-populate temperatureLow,
temperatureHigh, and weatherCondition on the new OutfitLogEntity. User
can still override manually.

🔲 4.3 — Weather on DayDetailSheet
DayDetailSheet.kt exists. Show the stored temperature_low/high and
weather_condition from the OutfitLog entity if present. This is a
read-only display — no fetch involved.

  ---
Phase 5 — Schema gaps (needed for outfit recommendation filtering)

These are independent of Phases 1–4 and can be done at any time, but are
required before weather data can drive outfit suggestions. All changes
go in Migration 2 (or a new migration if chain has moved).

🔲 Gap 1 — Temperature suitability on clothing items
Add temp_min_c REAL nullable and temp_max_c REAL nullable to
clothing_items. Lets a user express "this jacket is suitable for 0–10°C".
User-set, not auto-populated.

🔲 Gap 2 — Precipitation resistance on clothing items
Add water_resistant INTEGER NOT NULL DEFAULT 0 to clothing_items. Needed
to filter for rainy-day suitable items.

🔲 Gap 3 — WeatherCondition enum expansion  ← do this BEFORE Phase 3
Current 6 values don't cover: Thunderstorm, Foggy, Drizzle, Sleet,
HeavySnow, HeatWave. WMO weathercode mapping has ~30 codes. Expand the
enum and add fromWmoCode(code: Int): WeatherCondition factory. Room
stores this as a String (label), so adding new values is non-breaking.

🔲 Gap 4 — Seasons with temperature ranges (optional, low priority)
Add temp_low_c / temp_high_c to the seasons lookup table for automatic
season-tag suggestions from forecast temperature. Works fine without it
if temperature suitability is on items directly (Gap 1).

🔲 Gap 5 — precipitation_mm and wind_speed_kmh on outfit_logs
Add precipitation_mm REAL nullable and wind_speed_kmh REAL nullable to
outfit_logs for richer historical analysis ("what did I wear last time it
rained heavily?"). Include in the same migration as Gaps 1 and 2.

  ---
Phase ordering summary

┌───────┬─────────────────────────────────────────┬────────────────────────┐
│ Phase │               What ships                │    Prerequisite        │
├───────┼─────────────────────────────────────────┼────────────────────────┤
│ 0     │ WeatherPrefs DataStore repo + HTTP      │ —  (0.1 already done)  │
│       │ client wired up, no UI                  │                        │
├───────┼─────────────────────────────────────────┼────────────────────────┤
│ 1     │ Weather section in existing Settings    │ Phase 0                │
│       │ screen (1.1 + 1.2 already done)         │                        │
├───────┼─────────────────────────────────────────┼────────────────────────┤
│ 2     │ Location permission request flow        │ Phase 1                │
├───────┼─────────────────────────────────────────┼────────────────────────┤
│ 3     │ Weather service clients + caching       │ Phase 0, 2, Gap 3      │
├───────┼─────────────────────────────────────────┼────────────────────────┤
│ 4     │ Journal forecast widget + auto-populate │ Phase 3                │
│       │ on log                                  │                        │
├───────┼─────────────────────────────────────────┼────────────────────────┤
│ 5     │ Schema additions for future             │ Any time, independent  │
│       │ recommendation filtering                │                        │
└───────┴─────────────────────────────────────────┴────────────────────────┘

  ---
Key design decisions (resolved or still open)

✅ 1. Settings entry point — gear icon on Closet screen top app bar. DONE.

✅ 2. Location library — use LocationManager (no Play Services dependency;
   the app has none and adding Play Services is a meaningful SDK bloat
   decision). NETWORK_PROVIDER gives city-level accuracy — sufficient.

🔲 3. Temperature unit — canonical storage is °C (recommended). All DB
   columns (temperature_low, temperature_high, temp_min_c, temp_max_c)
   store Celsius. Convert to °F for display only, driven by
   temperatureUnit preference. CONFIRM before any data is written.

🔲 4. NWS out-of-US — notify the user and ask them to switch service
   (do not silently fall back). Rationale: user made a deliberate choice;
   silent fallback would be confusing if NWS and Open-Meteo disagree.
   CONFIRM this UX.

✅ 5. Cache TTL — 3 hours, stored as named constant FORECAST_CACHE_TTL_MS.
