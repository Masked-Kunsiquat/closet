import axios from "axios";
import { API_URL } from "./config";
import { Outfit, OutfitDetail } from "../types";

/**
 * Fetch all outfits for the current user.
 */
export const getOutfits = async (): Promise<Outfit[]> => {
  try {
    const { data } = await axios.get<Outfit[]>(
      `${API_URL}/outfits?select=id,name,created_at,occasion:occasions(name)`
    );
    return data;
  } catch (error) {
    console.error("❌ Error fetching outfits:", error);
    return [];
  }
};

/**
 * Fetch a specific outfit with its clothing items.
 */
export const getOutfitById = async (id: string): Promise<OutfitDetail | null> => {
    try {
      const { data } = await axios.get<OutfitDetail[]>(
        `${API_URL}/outfits?id=eq.${id}&select=id,name,created_at,occasion:occasions(name),clothing:outfit_items(clothing_item_id,clothing:clothing_items(name,category:categories(name),brand:brands(name),image_url))`
      );
  
      return data.length > 0 ? data[0] : null;
    } catch (error) {
      console.error("❌ Error fetching outfit:", error);
      return null;
    }
  };

/**
 * Create a new outfit.
 */
export const createOutfit = async (name: string, occasion_id?: string): Promise<Outfit | null> => {
    try {
      const newOutfit = {
        user_id: "1325b351-92b4-42ab-a90b-3c4e6fb0cad8", // Temp user
        name,
        occasion_id: occasion_id || null,
      };
  
      const response = await axios.post(`${API_URL}/outfits`, newOutfit, {
        headers: { "Content-Type": "application/json" },
      });
  
      return response.status === 201 ? response.data : null;
    } catch (error) {
      console.error("❌ Error creating outfit:", error);
      return null;
    }
  };

/**
 * Delete an outfit by ID.
 */
export const deleteOutfit = async (id: string): Promise<boolean> => {
    try {
      await axios.delete(`${API_URL}/outfits?id=eq.${id}`);
      return true;
    } catch (error) {
      console.error("❌ Error deleting outfit:", error);
      return false;
    }
  };

/**
 * Add a clothing item to an outfit.
 */
export const addClothingToOutfit = async (outfitId: string, clothingItemId: string): Promise<boolean> => {
    try {
      const response = await axios.post(`${API_URL}/outfit_items`, {
        outfit_id: outfitId,
        clothing_item_id: clothingItemId,
      });
  
      return response.status === 201;
    } catch (error) {
      console.error("❌ Error adding clothing to outfit:", error);
      return false;
    }
  };

/**
 * Remove a clothing item from an outfit.
 */
export const removeClothingFromOutfit = async (outfitId: string, clothingItemId: string): Promise<boolean> => {
    try {
      await axios.delete(`${API_URL}/outfit_items?outfit_id=eq.${outfitId}&clothing_item_id=eq.${clothingItemId}`);
      return true;
    } catch (error) {
      console.error("❌ Error removing clothing from outfit:", error);
      return false;
    }
  };
