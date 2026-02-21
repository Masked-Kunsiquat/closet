import { SQLiteDatabase } from 'expo-sqlite';

// ---------------------------------------------------------------------------
// Migration 001 — Initial schema
//
// Creates all tables for the MVP. No derived fields are stored.
// Wear count and cost per wear are always computed at query time.
// ---------------------------------------------------------------------------

export default {
  version: 1,

  async up(db: SQLiteDatabase): Promise<void> {
    await db.execAsync(`

      -- -----------------------------------------------------------------------
      -- Lookup / reference tables
      -- -----------------------------------------------------------------------

      CREATE TABLE categories (
        id         INTEGER PRIMARY KEY AUTOINCREMENT,
        name       TEXT    NOT NULL UNIQUE,
        icon       TEXT,                        -- Phosphor icon name, nullable
        sort_order INTEGER NOT NULL DEFAULT 0
      );

      CREATE TABLE subcategories (
        id          INTEGER PRIMARY KEY AUTOINCREMENT,
        category_id INTEGER NOT NULL REFERENCES categories(id) ON DELETE CASCADE,
        name        TEXT    NOT NULL,
        sort_order  INTEGER NOT NULL DEFAULT 0,
        UNIQUE (category_id, name)
      );

      CREATE TABLE seasons (
        id   INTEGER PRIMARY KEY AUTOINCREMENT,
        name TEXT    NOT NULL UNIQUE,
        icon TEXT                               -- Phosphor icon name, nullable
      );

      CREATE TABLE occasions (
        id   INTEGER PRIMARY KEY AUTOINCREMENT,
        name TEXT    NOT NULL UNIQUE,
        icon TEXT                               -- Phosphor icon name, nullable
      );

      CREATE TABLE colors (
        id   INTEGER PRIMARY KEY AUTOINCREMENT,
        name TEXT    NOT NULL UNIQUE,
        hex  TEXT                               -- optional display hint, e.g. '#1A1A1A'
      );

      CREATE TABLE materials (
        id   INTEGER PRIMARY KEY AUTOINCREMENT,
        name TEXT    NOT NULL UNIQUE
      );

      CREATE TABLE patterns (
        id   INTEGER PRIMARY KEY AUTOINCREMENT,
        name TEXT    NOT NULL UNIQUE
      );

      CREATE TABLE size_systems (
        id   INTEGER PRIMARY KEY AUTOINCREMENT,
        name TEXT    NOT NULL UNIQUE            -- e.g. 'Letter', 'US Women's Numeric'
      );

      CREATE TABLE size_values (
        id             INTEGER PRIMARY KEY AUTOINCREMENT,
        size_system_id INTEGER NOT NULL REFERENCES size_systems(id) ON DELETE CASCADE,
        value          TEXT    NOT NULL,        -- e.g. 'M', '32', '10.5'
        sort_order     INTEGER NOT NULL DEFAULT 0,
        UNIQUE (size_system_id, value)
      );

      -- -----------------------------------------------------------------------
      -- Core clothing item
      -- -----------------------------------------------------------------------

      CREATE TABLE clothing_items (
        id               INTEGER PRIMARY KEY AUTOINCREMENT,
        name             TEXT    NOT NULL,
        brand            TEXT,
        category_id      INTEGER REFERENCES categories(id)    ON DELETE SET NULL,
        subcategory_id   INTEGER REFERENCES subcategories(id) ON DELETE SET NULL,
        size_value_id    INTEGER REFERENCES size_values(id)   ON DELETE SET NULL,
        -- Waist/inseam stored as REAL to support half sizes (e.g. 32.5)
        waist            REAL,
        inseam           REAL,
        purchase_price   REAL,
        purchase_date    TEXT,                  -- ISO 8601 date string YYYY-MM-DD
        purchase_location TEXT,
        image_path       TEXT,                  -- relative path only, never absolute
        notes            TEXT,
        status           TEXT    NOT NULL DEFAULT 'Active'
                                 CHECK (status IN ('Active','Sold','Donated','Lost')),
        wash_status      TEXT    NOT NULL DEFAULT 'Clean'
                                 CHECK (wash_status IN ('Clean','Dirty')),
        is_favorite      INTEGER NOT NULL DEFAULT 0 CHECK (is_favorite IN (0,1)),
        created_at       TEXT    NOT NULL DEFAULT (datetime('now')),
        updated_at       TEXT    NOT NULL DEFAULT (datetime('now'))
      );

      -- -----------------------------------------------------------------------
      -- Junction tables for clothing_items → many-to-many relations
      -- -----------------------------------------------------------------------

      CREATE TABLE clothing_item_colors (
        clothing_item_id INTEGER NOT NULL REFERENCES clothing_items(id) ON DELETE CASCADE,
        color_id         INTEGER NOT NULL REFERENCES colors(id)         ON DELETE CASCADE,
        PRIMARY KEY (clothing_item_id, color_id)
      );

      CREATE TABLE clothing_item_materials (
        clothing_item_id INTEGER NOT NULL REFERENCES clothing_items(id) ON DELETE CASCADE,
        material_id      INTEGER NOT NULL REFERENCES materials(id)      ON DELETE CASCADE,
        PRIMARY KEY (clothing_item_id, material_id)
      );

      CREATE TABLE clothing_item_seasons (
        clothing_item_id INTEGER NOT NULL REFERENCES clothing_items(id) ON DELETE CASCADE,
        season_id        INTEGER NOT NULL REFERENCES seasons(id)        ON DELETE CASCADE,
        PRIMARY KEY (clothing_item_id, season_id)
      );

      CREATE TABLE clothing_item_occasions (
        clothing_item_id INTEGER NOT NULL REFERENCES clothing_items(id) ON DELETE CASCADE,
        occasion_id      INTEGER NOT NULL REFERENCES occasions(id)      ON DELETE CASCADE,
        PRIMARY KEY (clothing_item_id, occasion_id)
      );

      CREATE TABLE clothing_item_patterns (
        clothing_item_id INTEGER NOT NULL REFERENCES clothing_items(id) ON DELETE CASCADE,
        pattern_id       INTEGER NOT NULL REFERENCES patterns(id)       ON DELETE CASCADE,
        PRIMARY KEY (clothing_item_id, pattern_id)
      );

      -- -----------------------------------------------------------------------
      -- Outfits
      -- -----------------------------------------------------------------------

      CREATE TABLE outfits (
        id         INTEGER PRIMARY KEY AUTOINCREMENT,
        name       TEXT,                        -- optional user-assigned name
        notes      TEXT,
        created_at TEXT    NOT NULL DEFAULT (datetime('now')),
        updated_at TEXT    NOT NULL DEFAULT (datetime('now'))
      );

      CREATE TABLE outfit_items (
        outfit_id        INTEGER NOT NULL REFERENCES outfits(id)       ON DELETE CASCADE,
        clothing_item_id INTEGER NOT NULL REFERENCES clothing_items(id) ON DELETE CASCADE,
        PRIMARY KEY (outfit_id, clothing_item_id)
      );

      -- -----------------------------------------------------------------------
      -- Outfit logs (the journal / wear history)
      -- -----------------------------------------------------------------------

      CREATE TABLE outfit_logs (
        id         INTEGER PRIMARY KEY AUTOINCREMENT,
        outfit_id  INTEGER REFERENCES outfits(id) ON DELETE SET NULL,
        date       TEXT    NOT NULL,             -- ISO 8601 date string YYYY-MM-DD
        is_ootd    INTEGER NOT NULL DEFAULT 0 CHECK (is_ootd IN (0,1)),
        notes      TEXT,
        created_at TEXT    NOT NULL DEFAULT (datetime('now'))
      );

      -- Enforce: only one OOTD per calendar day.
      -- A partial unique index means the constraint only applies where is_ootd = 1.
      CREATE UNIQUE INDEX one_ootd_per_day ON outfit_logs(date) WHERE is_ootd = 1;

      -- -----------------------------------------------------------------------
      -- Indexes for common query patterns
      -- -----------------------------------------------------------------------

      CREATE INDEX idx_clothing_items_category    ON clothing_items(category_id);
      CREATE INDEX idx_clothing_items_status      ON clothing_items(status);
      CREATE INDEX idx_clothing_items_is_favorite ON clothing_items(is_favorite);
      CREATE INDEX idx_outfit_logs_date           ON outfit_logs(date);
      CREATE INDEX idx_subcategories_category     ON subcategories(category_id);
      CREATE INDEX idx_size_values_system         ON size_values(size_system_id);

    `);
  },
};
