-- Enable UUID support
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Run schema creation scripts
\i schema/01_users.sql
\i schema/02_categories.sql
\i schema/03_brands.sql
\i schema/04_clothing_items.sql
\i schema/05_occasions.sql
\i schema/06_outfits.sql
\i schema/07_outfit_items.sql
\i schema/08_wear_logs.sql
\i schema/09_indexes.sql
\i schema/10_user_sessions.sql

-- Seed data
\i seeds/seed_categories.sql
\i seeds/seed_brands.sql
\i seeds/seed_occasions.sql