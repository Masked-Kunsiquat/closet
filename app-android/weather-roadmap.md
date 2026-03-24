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

✅ 2.2 — Permission request flow
Toggle interception lives in SettingsScreen (not ViewModel). On → off:
writes directly. On → on: checks ContextCompat.checkSelfPermission first;
if already granted, writes directly. If not granted, launches
rememberLauncherForActivityResult(RequestPermission).
Launcher result:
  Granted → viewModel.setWeatherEnabled(true)
  Denied + shouldShowRationale=true → Snackbar (non-blocking)
  Denied + shouldShowRationale=false → PermissionRationaleDialog with
    "Open Settings" deeplink to ACTION_APPLICATION_DETAILS_SETTINGS
SettingsContent gains SnackbarHostState param wired to Scaffold.
"Trigger initial forecast fetch" deferred to Phase 3 (no fetch client yet).

✅ 2.3 — Location fetch
LocationProvider (@Singleton) wraps LocationManager. Tries NETWORK_PROVIDER
last-known, then GPS last-known, then a one-shot active update with 10s
timeout if both caches are empty. Falls back to cached lat/lon in DataStore
if no device fix is available.

  ---
Phase 3 — Weather service abstraction

✅ 3.1 — WeatherService interface + domain models
WeatherServiceClient interface: fetchDailyForecast(lat, lon): Result<List<DailyForecast>>
DailyForecast domain model (all temps °C).
CachedForecastEntry @Serializable DTO (primitives only) + toDomain/toCached extensions.

✅ 3.2 — Open-Meteo implementation (default, no key)
GET /v1/forecast with 7-day daily params. WMO weathercode → WeatherCondition
via fromWmoCode(). humidity=null (daily endpoint only).

✅ 3.3 — NWS implementation (US-only, no key)
Two-step /points + forecast URL. Merges 12h periods to daily via date grouping.
shortForecast text → WeatherCondition keyword map. F→C conversion. Returns
Result.failure on 404 (non-US) — user must switch service manually.
precipitationMm=0.0, uvIndex=null (not in basic forecast endpoint).

✅ 3.4 — Google Weather API implementation (key required)
GoogleWeatherClient has separate fetchDailyForecast(lat, lon, apiKey) — does
not implement WeatherServiceClient (different signature). DTOs match the
actual Google Weather API v1 daily forecast schema:
- displayDate.year/month/day for date parsing
- daytimeForecast.wind.speed.value + unit (mph→km/h conversion)
- daytimeForecast.uvIndex and relativeHumidity
- Temperature.unit field with F→C conversion; no unitsSystem request param

✅ 3.5 — WeatherRepository + caching
FORECAST_CACHE_TTL_MS = 3h named constant. Cache-first: if fresh, parse and
return cached JSON. On fresh fetch: get location (LocationProvider → cached
lat/lon fallback), select client, fetch, serialize to CachedForecastEntry list,
saveCache(). On fetch failure: return stale cache if present.
DataModule: Json extracted to @Provides @Singleton; HttpClient now takes json
param so both HTTP and cache serialization share the same Json config.

  ---
Phase 4 — Surface forecast in the Journal

The Journal screen is the natural home for weather since it already shows
wear history by date.

✅ 4.1 — Today's forecast widget on Journal header
TodayForecastChip shown above the month nav header when todayForecast != null.
Chip label: "{condition} · {low} / {high}" with condition icon. Taps open
ForecastSheet (7-day ModalBottomSheet). WeatherConditionExt.kt holds the
shared icon() extension (all 11 conditions) and toDisplayTemp() helper.
JournalViewModel injects WeatherRepository + WeatherPreferencesRepository;
forecastDays uses flatMapLatest on weatherEnabled.

✅ 4.2 — Auto-populate weather on log creation
LogRepository.wearOutfitOnDate() gains three optional nullable params
(temperatureLow, temperatureHigh, weatherCondition) passed into the new
OutfitLogEntity on insert. Idempotent path (existing row) returns early
and does not overwrite existing weather data.
JournalViewModel.logOutfitOnDate() looks up forecastDays for the selected
date and passes values through. No-op when weather is disabled or the date
has no forecast entry. User can still override via the log-edit sheet.

✅ 4.3 — Weather on DayDetailSheet
LogCard upgraded: condition icon (12dp) + label + temp range on a dedicated
weather row; notes on a separate line below. Both are read-only from stored
OutfitLogWithMeta fields. WeatherCondition.fromString() parses the DB string;
toDisplayTemp() honours temperatureUnit preference passed from JournalContent.

  ---
Phase 5 — Schema gaps (needed for outfit recommendation filtering)

These are independent of Phases 1–4 and can be done at any time, but are
required before weather data can drive outfit suggestions. All changes
go in Migration 2 (or a new migration if chain has moved).

Design decisions settled (2026-03-24):

Gap 1 & 2 — DEFERRED. Do not add user-input temp_min_c / temp_max_c or
water_resistant to clothing_items. Instead, suitability will be DERIVED
from log history at query time (never stored as a column — consistent with
the project's no-derived-data rule). The engine concept:
  - For each clothing item, collect all outfit_logs where it appears with a
    recorded temperature range.
  - Comfortable range = statistical distribution (e.g. 10th–90th percentile)
    of logged temps. Rain suitability = % of logs recorded in wet conditions.
  - The layering problem (t-shirt OK at 0°C if worn with a heavy jacket) is
    addressed at the OUTFIT level, not the item level. A warmth_layer enum
    will be added to categories (None / Base / Mid / Outer) so the engine
    can score outfit combinations rather than individual items.
  - MLKit is not the right tool here — personal wardrobes top out at a few
    hundred log entries, where simple SQL statistics outperform ML. No
    training data, no model, no on-device inference needed.
  - This work belongs in a dedicated Recommendation feature roadmap.
    See recommendation-roadmap.md when ready.

✅ Gap 3 — WeatherCondition enum expansion — DONE in Phase 0.2.
Added Thunderstorm, Foggy, Drizzle, Sleet, HeavySnow and fromWmoCode().

🔲 Gap 4 — Seasons with temperature ranges
Add temp_low_c REAL nullable and temp_high_c REAL nullable to the seasons
lookup table. Enables temperature-band-driven season matching alongside
calendar quarters (e.g. "Winter" = below 5°C in user's locale, not just
Dec–Feb). Feeds the future recommendation engine: if the forecast hits the
Winter temp band on a warm January day, Winter-tagged items are still
surfaced. Update DatabaseSeeder with sensible defaults per season.
Add warmth_layer TEXT NOT NULL DEFAULT 'None' to the categories table
(values: None / Base / Mid / Outer) at the same time — needed for the
layering logic described above.

🔲 Gap 5 — precipitation_mm and wind_speed_kmh on outfit_logs
Add precipitation_mm REAL nullable and wind_speed_kmh REAL nullable to
outfit_logs. Pure data-collection infrastructure — auto-populated from the
cached forecast on log creation (same hook as temperature, Phase 4.2).
This is what the recommendation engine feeds on for rain/wind suitability
inference. No user-facing change. Do in the same migration as Gap 4.

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

✅ 3. Temperature unit — canonical storage is °C. All DB columns store
   Celsius. toDisplayTemp() converts to °F for display only, driven by
   temperatureUnit preference. CONFIRMED — implemented throughout.

✅ 4. NWS out-of-US — returns Result.failure with a descriptive message;
   WeatherRepository surfaces it to the caller. No silent fallback to
   Open-Meteo. CONFIRMED — implemented in NwsClient.

✅ 5. Cache TTL — 3 hours, stored as named constant FORECAST_CACHE_TTL_MS.
