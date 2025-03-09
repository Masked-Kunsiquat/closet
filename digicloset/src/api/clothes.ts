import axios from "axios";
import { API_URL } from "./config";

/**
 * Fetch all clothing items.
 */
export const getClothingItems = async (categoryId: string | null = null) => {
  try {
    const url = categoryId ? `${API_URL}/clothing_items?categoryId=${categoryId}` : `${API_URL}/clothing_items`;
    const { data } = await axios.get(url);
    return data;
  } catch (error) {
    console.error("❌ Error fetching clothing items:", error);
    return [];
  }
};

/**
 * Fetch a single clothing item by ID.
 */
export const getClothingItemById = async (id: string) => {
  try {
    const { data } = await axios.get(`${API_URL}/clothing_items/${id}`);
    return data;
  } catch (error) {
    console.error("❌ Error fetching clothing item:", error);
    return null;
  }
};

/**
 * Create a new clothing item.
 */
export const createClothingItem = async (newItem: any) => {
  try {
    const { data } = await axios.post(`${API_URL}/clothing_items`, newItem, {
      headers: { "Content-Type": "application/json" },
    });

    return data;
  } catch (error) {
    console.error("❌ Error creating clothing item:", error);
    return null;
  }
};

/**
 * ✅ Fetch all categories
 */
export const getCategories = async () => {
  try {
    const { data } = await axios.get(`${API_URL}/categories`);
    return data;
  } catch (error) {
    console.error("❌ Error fetching categories:", error);
    return [];
  }
};