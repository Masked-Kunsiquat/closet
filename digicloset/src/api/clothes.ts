import { apiRequest } from "./apiWrapper";

/**
 * Clothing item interface to ensure type safety
 */
export interface ClothingItem {
  id: string;
  name: string;
  categoryId: string | null;
  brand?: string;
  color?: string;
  size?: string;
  price?: number;
  purchaseDate?: string;
}

/**
 * Fetch all clothing items.
 */
export const getClothingItems = async (categoryId: string | null = null): Promise<ClothingItem[]> => {
  const endpoint = categoryId ? `/clothing_items?categoryId=${categoryId}` : `/clothing_items`;
  return apiRequest<ClothingItem[]>(endpoint, "GET");
};

/**
 * Fetch a single clothing item by ID.
 */
export const getClothingItemById = async (id: string): Promise<ClothingItem> => {
  return apiRequest<ClothingItem>(`/clothing_items/${id}`, "GET");
};

/**
 * Create a new clothing item.
 */
export const createClothingItem = async (newItem: Omit<ClothingItem, "id">): Promise<ClothingItem> => {
  return apiRequest<ClothingItem>(`/clothing_items`, "POST", newItem);
};

/**
 * Fetch all categories.
 */
export interface Category {
  id: string;
  name: string;
}

export const getCategories = async (): Promise<Category[]> => {
  return apiRequest<Category[]>(`/categories`, "GET");
};
