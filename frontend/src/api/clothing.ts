import axios from "axios";
import { API_URL } from "./config";

/**
 * Fetch clothing items, optionally filtered by category name.
 */
export const getClothingItems = async (categoryName?: string) => {
  try {
    let url = `${API_URL}/clothing_items`;

    if (categoryName) {
      console.log(`Looking up category ID for: ${categoryName}`);

      // Fetch category ID from name
      const categoryRes = await axios.get(`${API_URL}/categories?name=eq.${categoryName}`);
      const category = categoryRes.data[0];

      if (!category) {
        console.warn(`Category '${categoryName}' not found! Returning all items.`);
      } else {
        console.log(`Found category ID: ${category.id}`);
        url = `${API_URL}/clothing_items?category_id=eq.${category.id}`;
      }
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
