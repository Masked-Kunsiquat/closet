import axios from "axios";
import { API_URL } from "./config";

/**
 * Fetch clothing items, optionally filtered by category name.
 */
export const getClothingItems = async (categoryId?: string) => {
  try {
    let url = `${API_URL}/clothing_items`;

    // Directly filter by `category_id` instead of searching for it
    if (categoryId) {
      url = `${API_URL}/clothing_items?category_id=eq.${categoryId}`;
    }

    console.log(`Fetching clothing items from: ${url}`);
    const response = await axios.get(url);
    console.log(`Fetched ${response.data.length} items`);
    return response.data;
  } catch (error) {
    console.error("Error fetching clothing items:", error);
    return [];
  }
};


/**
 * Fetch all clothing categories.
 */
export const getCategories = async () => {
  try {
    const response = await axios.get(`${API_URL}/categories`);
    return response.data;
  } catch (error) {
    console.error("Error fetching categories:", error);
    return [];
  }
};

export const getClothingItemById = async (id: string) => {
  try {
    const response = await axios.get(`${API_URL}/clothing_items?id=eq.${id}`);
    return response.data.length ? response.data[0] : null;
  } catch (error) {
    console.error("Error fetching clothing item:", error);
    return null;
  }
};
