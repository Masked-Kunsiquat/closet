import axios from "axios";
import { API_URL } from "./config";
import { ClothingItem, Category } from "../types";

/**
 * Fetch clothing items, optionally filtered by category ID.
 */
export const getClothingItems = async (categoryId?: string): Promise<ClothingItem[]> => {
  try {
    const url = categoryId 
      ? `${API_URL}/clothing_items?category_id=eq.${categoryId}` 
      : `${API_URL}/clothing_items`;

    const { data } = await axios.get<ClothingItem[]>(url);
    return data;
  } catch (error) {
    console.error("❌ Error fetching clothing items:", error);
    return [];
  }
};

/**
 * Fetch all clothing categories.
 */
export const getCategories = async (): Promise<Category[]> => {
  try {
    const { data } = await axios.get<Category[]>(`${API_URL}/categories`);
    return data;
  } catch (error) {
    console.error("❌ Error fetching categories:", error);
    return [];
  }
};

/**
 * Fetch a single clothing item by ID.
 */
export const getClothingItemById = async (id: string): Promise<ClothingItem | null> => {
  try {
    const { data } = await axios.get<ClothingItem[]>(
      `${API_URL}/clothing_items?id=eq.${id}&select=id,name,size,price,category_id,brand_id,image_url,purchase_date,category:categories(name)`
    );

    if (data.length > 0) {
      const item = data[0];
      return { ...item, category_name: item.category?.name };
    }

    return null;
  } catch (error) {
    console.error("❌ Error fetching clothing item:", error);
    return null;
  }
};

/**
 * Update an existing clothing item.
 */
export const updateClothingItem = async (id: string, updatedData: Partial<ClothingItem>): Promise<ClothingItem | null> => {
  try {
    const allowedData: Partial<ClothingItem> = {
      name: updatedData.name,
      price: updatedData.price,
      size: updatedData.size,
      category_id: updatedData.category_id,
      brand_id: updatedData.brand_id,
      image_url: updatedData.image_url,
      purchase_date: updatedData.purchase_date,
    };

    const { data } = await axios.patch(
      `${API_URL}/clothing_items?id=eq.${id}`,
      allowedData,
      { headers: { "Content-Type": "application/json" } }
    );

    return data;
  } catch (error: any) {
    console.error("❌ Error updating clothing item:", error.response?.data || error.message);
    return null;
  }
};

/**
 * Delete a clothing item by ID.
 */
export const deleteClothingItem = async (id: string): Promise<boolean> => {
  try {
    await axios.delete(`${API_URL}/clothing_items?id=eq.${id}`);
    return true; // ✅ Return `true` if deletion is successful
  } catch (error: any) {
    console.error("❌ Error deleting clothing item:", error.response?.data || error.message);
    return false; // ❌ Return `false` if deletion fails
  }
};
