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

/**
 * Create a new clothing item.
 */
export const createClothingItem = async (newItem: Partial<ClothingItem>): Promise<ClothingItem | null> => {
  try {
    const formattedItem: Omit<ClothingItem, "id"> = {
      user_id: "1325b351-92b4-42ab-a90b-3c4e6fb0cad8", // ✅ Temporary user ID
      category_id: newItem.category_id || undefined, // ✅ Convert null to undefined
      brand_id: newItem.brand_id || undefined, // ✅ Convert null to undefined
      purchase_date: newItem.purchase_date || undefined, // ✅ Convert null to undefined
      price: newItem.price ? parseFloat(newItem.price.toString()) : 0,
      name: newItem.name || "",
      size: newItem.size || "",
      image_url: newItem.image_url || "",
//      description: newItem.description || "",
    };

    const response = await axios.post(`${API_URL}/clothing_items`, formattedItem, {
      headers: { "Content-Type": "application/json" },
    });

    console.log("✅ API Response:", response.status, response.data);

    if (response.status === 201 || response.status === 200) {
      if (!response.data || Object.keys(response.data).length === 0) {
        console.warn("⚠️ API did not return data, assuming success.");
        return { ...formattedItem, id: "temp-id" }; // ✅ Assume success and return a temporary ID
      }
      return response.data;
    } else {
      console.error("❌ Unexpected response status:", response.status, response.data);
      return null;
    }
  } catch (error: any) {
    console.error("❌ Error creating clothing item:", error.response?.data || error.message);
    return null;
  }
};
