# Logging & Observability Roadmap

> Dev-mode only. No telemetry, no cloud, no accounts — this is purely for local
> development visibility as the app grows.

---

## Current State

| Layer | Status |
|-------|--------|
| DB queries (50+) | Zero instrumentation |
| Hooks (7) | Errors caught + stored in state, never logged |
| Screens | 13 `console.error()` calls, inconsistent coverage |
| Error boundaries | None — unhandled throws crash the screen |
| Settings context | Silent fallback to defaults on load/save failure |
| Logging packages | None |

All existing `console.error()` calls use a `[ComponentName]` tag prefix —
keep that convention.

---

## Layers to Instrument (priority order)

### 1. DB Initialization — `db/index.ts`
**What's missing:** `getDatabase()` has a try/catch but logs nothing.

Add a `console.log` on successful init (with timing) and a `console.error`
on failure before rethrowing. This is the single most important signal —
if the DB never opens, everything else is moot.

```ts
// approximate shape
console.log('[db] initialized in', Date.now() - t0, 'ms');
console.error('[db] init failed', e);
```

### 2. Query wrapper — `db/queries.ts`
**What's missing:** 50+ exported query functions with zero logging.

Rather than adding `console.error` to every function, create a thin
`query()` helper in `db/index.ts` (or a new `db/logger.ts`) that wraps
`db.getAllAsync / getFirstAsync / runAsync` and:
- Logs the function name + key params on error
- Optionally logs slow queries (> 200 ms) in dev

```ts
// db/logger.ts (new file)
export async function runQuery<T>(
  label: string,
  fn: () => Promise<T>
): Promise<T> {
  if (__DEV__) {
    const t = Date.now();
    try {
      const result = await fn();
      const ms = Date.now() - t;
      if (ms > 200) console.warn(`[db/slow] ${label} took ${ms}ms`);
      return result;
    } catch (e) {
      console.error(`[db] ${label} failed`, e);
      throw e;
    }
  }
  return fn();
}
```

Callers in `queries.ts` wrap their body: `return runQuery('getAllClothingItems', () => db.getAllAsync(...))`.

### 3. Hook layer — `hooks/*.ts`
**What's missing:** All hooks catch errors and set `error` state but never log.

Each hook's catch block should add one line:

```ts
} catch (e) {
  if (__DEV__) console.error('[useClothingItems] load failed', e);
  setError(String(e));
}
```

Hooks affected: `useClothingItems`, `useClothingItem`, `useOutfits`,
`useLogsForDate`, `useLogsForOutfit`, `useCalendarMonth`, `useStats`.

### 4. Settings context — `context/SettingsContext.tsx`
**What's missing:** Two completely silent catch blocks.

- Load failure (line ~116): logs nothing, silently uses defaults
- Save failure (line ~137): logs nothing, silently rolls back UI

Both need a `console.error('[SettingsContext] ...', e)` in `__DEV__`.

### 5. Error boundary — new `components/ErrorBoundary.tsx`
**What's missing:** Any unhandled throw in a screen crashes the whole tree.

Add a class component `ErrorBoundary` that:
- Catches via `componentDidCatch` — logs in `__DEV__`
- Shows a user-facing fallback ("Something went wrong — restart the app")
- Wraps the root layout in `app/_layout.tsx`

This is the safety net, not the primary logging mechanism.

### 6. Item detail silent catch — `app/item/[id].tsx`
**What's missing:** The outfits/logs parallel fetch (line ~54) has an empty
catch with only a comment.

Add `if (__DEV__) console.error('[item/[id]] outfits+logs fetch failed', e)`.
Low priority since it's labeled non-critical, but worth knowing about in dev.

---

## What to skip

- **Structured log levels / log libraries** (pino, winston, etc.) — overkill
  for a single-user local app. `console.*` is sufficient.
- **Remote error reporting** (Sentry, Bugsnag) — contradicts the no-telemetry
  principle.
- **Query parameter logging** — avoid logging item names / notes / etc.
  in case the device is shared. Log function name + IDs only.
- **User-action logging** (button taps, navigation) — not needed yet.

---

## Delivery order

| # | Task | File(s) | Effort |
|---|------|---------|--------|
| 1 | DB init logging | `db/index.ts` | Tiny |
| 2 | `runQuery` helper + wrap all queries | `db/logger.ts` (new), `db/queries.ts` | Medium |
| 3 | Hook catch logging | `hooks/*.ts` (7 files) | Small per file |
| 4 | Settings context logging | `context/SettingsContext.tsx` | Tiny |
| 5 | Error boundary | `components/ErrorBoundary.tsx` (new), `app/_layout.tsx` | Small |
| 6 | Item detail silent catch | `app/item/[id].tsx` | Tiny |

Steps 1, 3, 4, 6 are one-liners each. Step 2 is the bulk of the work.
Step 5 is self-contained.
