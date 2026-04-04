# Closet — Agent Instructions

## What This Project Is
**hangr** — a personal, local-first digital wardrobe and outfit tracking app. Native Android, Kotlin, Jetpack Compose, Room/SQLite on-device. No accounts, no cloud, no telemetry. Open-source passion project.

**Mantra: built well over built fast.**

## Available Skills
Read and apply these skills before working on relevant tasks. They are the source of truth for conventions — do not invent your own.

| Skill | When to Use |
|-------|-------------|
| `closet-engineer` | Any code, architecture, or technical decision |
| `closet-migrations` | Schema changes, Room migrations, seed data |
| `closet-ux` | Screens, Composables, layout, interactions |
| `closet-karen` | Reviewing, auditing, or sanity-checking anything |
| `closet-qa` | Edge cases, error handling, testing |

## Source of Truth Files
These files define canonical data — always consult them before generating seed data or migrations:

- `.claude/skills/closet-migrations/SKILL.md` — migration conventions, categories (with sort_order), seasons, occasions
- `.claude/skills/closet-migrations/reference.md` — full subcategory list, materials, patterns, all size systems and values
- `app-android/CLAUDE.md` — full project guidance, architecture, module structure, key files

## Absolute Rules
- Never store derived fields. Wear Count and Cost Per Wear are always computed at query time.
- Never store absolute image paths. Relative paths only, always.
- Never build deferred features (collage builder, outfit planning for future dates, packing lists, goals/missions).
- Enable `PRAGMA foreign_keys = ON` at DB open time.
- Use versioned Room migrations — never edit an applied migration, always add a new one.
- Every manual `migrate()` must `DROP INDEX IF EXISTS one_ootd_per_day` unconditionally at the top.
- Prefer `INSERT OR IGNORE` for seed data.
- All app code lives in `app-android/`. Run all commands from that directory.