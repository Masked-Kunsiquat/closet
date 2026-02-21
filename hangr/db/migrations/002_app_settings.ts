import { SQLiteDatabase } from 'expo-sqlite';

// ---------------------------------------------------------------------------
// Migration 002 — App settings table
//
// Key/value store for user preferences. All values stored as TEXT.
// Defaults are inserted here so existing installs get them on upgrade.
// ---------------------------------------------------------------------------

export default {
  version: 2,

  async up(db: SQLiteDatabase): Promise<void> {
    await db.execAsync(`
      CREATE TABLE IF NOT EXISTS app_settings (
        key   TEXT PRIMARY KEY NOT NULL,
        value TEXT NOT NULL
      );

      -- Defaults inserted at migration time for both new installs and upgrades.
      -- This migration is the sole source of these defaults.
      INSERT OR IGNORE INTO app_settings (key, value) VALUES
        ('accent_key',          'amber'),
        ('currency_symbol',     '$'),
        ('week_start_day',      '0'),
        ('temperature_unit',    'F'),
        ('show_archived_items', '0');
    `);
  },
};
