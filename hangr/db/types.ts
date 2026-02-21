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
