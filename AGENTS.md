# Closet — Agent Instructions

## What This Project Is
A personal, local-first digital wardrobe and outfit tracking app. React Native / Expo. SQLite on-device. No accounts, no ads, no telemetry, no cloud dependency. Open-source passion project.

**Mantra: built well over built fast.**

## Available Skills
Read and apply these skills before working on relevant tasks. They are the source of truth for conventions — do not invent your own.

| Skill | When to Use |
|-------|-------------|
| `closet-engineer` | Any code, architecture, or technical decision |
| `closet-migrations` | Schema changes, migrations, seed data |
| `closet-ux` | Screens, components, layout, interactions |
| `closet-karen` | Reviewing, auditing, or sanity-checking anything |
| `closet-qa` | Edge cases, error handling, testing |

## Source of Truth Files
These files live in the project and define the canonical data — always consult them before generating seed data, icons, or categories:

- `.claude/skills/closet-migrations/SKILL.md` — categories (with sort_order), subcategory quick-ref, seasons, occasions
- `.claude/skills/closet-migrations/reference.md` — full subcategory list, materials, patterns, all size systems and values
- `.claude/skills/closet-engineer/SKILL.md` — approved Phosphor icon names per entity (Icon System section)

## Absolute Rules
- Never store derived fields. Wear Count and Cost Per Wear are always computed.
- Never use absolute image paths. Relative paths only, always.
- Never build deferred features (collage builder, outfit planning, packing lists, weather API, goals, cloud backup, color dropper).
- Enable `PRAGMA foreign_keys = ON` at DB open time.
- Use versioned migrations — never raw ad-hoc schema changes.
- Prefer `INSERT OR IGNORE` for seed data.