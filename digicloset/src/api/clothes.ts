import axios from "axios";
import { API_URL } from "./config";

/**
 * Clothing item interface to ensure type safety
 */
export interface ClothingItem {
  id: string; // Optional for new items (not yet created)
  name: string;
  categoryId: string | null;
  brand?: string;
  color?: string;
  size?: string;
  price?: number;
  purchaseDate?: string; // ISO format string
}

/**
 * Fetch all clothing items.
 */
export const getClothingItems = async (categoryId: string | null = null): Promise<ClothingItem[]> => {
  try {
    const url = categoryId ? `${API_URL}/clothing_items?categoryId=${categoryId}` : `${API_URL}/clothing_items`;
    const { data } = await axios.get<ClothingItem[]>(url);
    return data;
  } catch (error) {
    console.error("❌ Error fetching clothing items:", error);
    return [];
  }
};

/**
 * Fetch a single clothing item by ID.
 */
export const getClothingItemById = async (id: string): Promise<ClothingItem | null> => {
  try {
    const { data } = await axios.get<ClothingItem>(`${API_URL}/clothing_items/${id}`);
    return data;
  } catch (error) {
    console.error("❌ Error fetching clothing item:", error);
    return null;
  }
};

/**
 * Create a new clothing item.
 */
export const createClothingItem = async (newItem: Omit<ClothingItem, "id">): Promise<ClothingItem | null> => {
  try {
    const { data } = await axios.post<ClothingItem>(`${API_URL}/clothing_items`, newItem, {
      headers: { "Content-Type": "application/json" },
    });

    return data;
  } catch (error) {
    console.error("❌ Error creating clothing item:", error);
    return null;
  }
};

/**
 * Fetch all categories.
 */
export interface Category {
  id: string;
  name: string;
}

export const getCategories = async (): Promise<Category[]> => {
  try {
    const { data } = await axios.get<Category[]>(`${API_URL}/categories`);
    return data;
  } catch (error) {
    console.error("❌ Error fetching categories:", error);
    return [];
  }
};
