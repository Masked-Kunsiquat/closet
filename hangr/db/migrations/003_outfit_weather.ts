import { SQLiteDatabase } from 'expo-sqlite';

export default {
  version: 3,

  async up(db: SQLiteDatabase): Promise<void> {
    await db.execAsync(`
      ALTER TABLE outfit_logs ADD COLUMN temperature_low  REAL;
      ALTER TABLE outfit_logs ADD COLUMN temperature_high REAL;
      ALTER TABLE outfit_logs ADD COLUMN weather_condition TEXT;
    `);
  },
};
