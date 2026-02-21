// ---------------------------------------------------------------------------
// Database row types
// These mirror the schema exactly. No derived fields.
// ---------------------------------------------------------------------------

export type ClothingItem = {
  id: number;
  name: string;
  brand: string | null;
  category_id: number | null;
  subcategory_id: number | null;
  size_value_id: number | null;
  waist: number | null;
  inseam: number | null;
  purchase_price: number | null;
  purchase_date: string | null;
  purchase_location: string | null;
  image_path: string | null;
  notes: string | null;
  status: 'Active' | 'Sold' | 'Donated' | 'Lost';
  wash_status: 'Clean' | 'Dirty';
  is_favorite: 0 | 1;
  created_at: string;
  updated_at: string;
};

export type ClothingItemWithMeta = ClothingItem & {
  category_name: string | null;
  subcategory_name: string | null;
  /** Computed at query time — COUNT of outfit_logs containing this item. */
  wear_count: number;
};

export type Category = {
  id: number;
  name: string;
  icon: string | null;
  sort_order: number;
};

export type Subcategory = {
  id: number;
  category_id: number;
  name: string;
  sort_order: number;
};

export type Season = {
  id: number;
  name: string;
  icon: string | null;
};

export type Occasion = {
  id: number;
  name: string;
  icon: string | null;
};

export type Color = {
  id: number;
  name: string;
  hex: string | null;
};

export type Material = {
  id: number;
  name: string;
};

export type Pattern = {
  id: number;
  name: string;
};

export type SizeSystem = {
  id: number;
  name: string;
};

export type SizeValue = {
  id: number;
  size_system_id: number;
  value: string;
  sort_order: number;
};

// ---------------------------------------------------------------------------
// Outfits
// ---------------------------------------------------------------------------

export type Outfit = {
  id: number;
  name: string | null;
  notes: string | null;
  created_at: string;
  updated_at: string;
};

/** Outfit row joined with item count and first item image path (for preview). */
export type OutfitWithMeta = Outfit & {
  item_count: number;
  /** image_path of the first item in the outfit, used as a cover thumbnail. */
  cover_image: string | null;
};

/** Outfit + the full clothing items that belong to it. */
export type OutfitWithItems = Outfit & {
  items: ClothingItemWithMeta[];
};

// ---------------------------------------------------------------------------
// Outfit logs
// ---------------------------------------------------------------------------

export type OutfitLog = {
  id: number;
  outfit_id: number | null;
  date: string; // YYYY-MM-DD
  is_ootd: 0 | 1;
  notes: string | null;
  created_at: string;
};

/** Log row joined with outfit name (nullable). */
export type OutfitLogWithMeta = OutfitLog & {
  outfit_name: string | null;
  item_count: number;
  cover_image: string | null;
};

/** Per-date summary used by the calendar view. */
export type CalendarDay = {
  date: string; // YYYY-MM-DD
  log_count: number;
  has_ootd: 0 | 1;
};

// ---------------------------------------------------------------------------
// Stats
// ---------------------------------------------------------------------------

export type StatsOverview = {
  total_items: number;
  worn_items: number;
  never_worn_items: number;
  /** Sum of purchase_price for active items; null when no prices have been entered. */
  total_value: number | null;
};

/** A clothing item with its computed wear count, used in most/least/never-worn lists. */
export type StatItem = {
  id: number;
  name: string;
  image_path: string | null;
  wear_count: number;
};

/** A single row in a breakdown chart (category, brand, material, occasion, season). */
export type BreakdownRow = {
  label: string;
  count: number;
};

/** A single row in the color breakdown chart — includes the hex value for the bar color. */
export type ColorBreakdownRow = {
  label: string;
  hex: string | null;
  count: number;
};
