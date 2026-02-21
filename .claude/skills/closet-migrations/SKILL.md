---
name: closet-migrations
description: Write database migrations and seed data for the Closet app. Use when creating or modifying database schema, writing seed data, adding columns, or setting up initial data for any table.
---

# Closet — Migrations & Seed Data

## Migration Conventions

### File Naming
Sequential, zero-padded, stored in `db/migrations/`:
```
db/migrations/001_initial_schema.ts
db/migrations/002_add_icon_columns.ts
```

### Structure
```ts
import { SQLiteDatabase } from 'expo-sqlite';

export default {
  version: 2,
  async up(db: SQLiteDatabase) {
    await db.execAsync(`
      ALTER TABLE categories ADD COLUMN icon TEXT;
    `);
  },
};
```

### Rules
- Never skip version numbers
- Never edit an already-applied migration — create a new one
- Wrap multi-statement migrations in a transaction
- Track applied migrations in a `schema_migrations` table:
  ```sql
  CREATE TABLE IF NOT EXISTS schema_migrations (
    version INTEGER PRIMARY KEY,
    applied_at TEXT NOT NULL DEFAULT (datetime('now'))
  );
  ```

---

## Seed Data Conventions
- All inserts use `INSERT OR IGNORE` — seeds must be idempotent
- Never use `INSERT OR REPLACE` (breaks foreign key references via row ID change)
- Seeds run after all migrations are applied
- Values must match the canonical reference below exactly

### Seed Template
```ts
export async function seedSeasons(db: SQLiteDatabase) {
  const rows = [
    { name: 'Spring', icon: 'flower' },
    { name: 'Summer', icon: 'sun' },
    { name: 'Fall', icon: 'leaf' },
    { name: 'Winter', icon: 'snowflake' },
    { name: 'All Season', icon: 'thermometer' },
  ];
  for (const row of rows) {
    await db.runAsync(
      `INSERT OR IGNORE INTO seasons (name, icon) VALUES (?, ?)`,
      [row.name, row.icon]
    );
  }
}
```

---

## Canonical Seed Values

See [reference.md](reference.md) for the full seed tables.

### Quick reference — Categories
| Name | Icon | sort_order |
|------|------|------------|
| Tops | `t-shirt` | 1 |
| Bottoms | `pants` | 2 |
| Outerwear | `hoodie` | 3 |
| Dresses & Jumpsuits | `dress` | 4 |
| Footwear | `sneaker` | 5 |
| Accessories | `watch` | 6 |
| Bags | `handbag` | 7 |
| Activewear | `person-simple-running` | 8 |
| Underwear & Intimates | `sock` | 9 |
| Swimwear | `goggles` | 10 |

### Quick reference — Seasons
| Name | Icon |
|------|------|
| Spring | `flower` |
| Summer | `sun` |
| Fall | `leaf` |
| Winter | `snowflake` |
| All Season | `thermometer` |

### Quick reference — Occasions
| Name | Icon |
|------|------|
| Casual | `coffee` |
| Work/Business | `briefcase` |
| Formal | `crown-simple` |
| Athletic | `barbell` |
| Loungewear | `couch` |
| Date Night | `heart` |
| Vacation | `island` |
| Outdoor/Hiking | `mountains` |
| Special Occasion | `cheers` |

---

## Common Mistakes
- Do not store derived fields (wear count, cost per wear) in any table
- Do not use absolute image paths anywhere, even as examples
- Do not skip `INSERT OR IGNORE`
- Do not add columns without a migration
- Do not invent icon names — use only values from the Icon Selections reference