import axios from "axios";

// ✅ Use environment variable for API URL
export const API_URL = import.meta.env.VITE_API_URL || "http://localhost:3000"; 

// Define TypeScript interface for outfit data
export interface Outfit {
  id: string;
  name: string;
  description?: string;
  createdAt: string;
  updatedAt: string;
}

export const getOutfits = async (): Promise<Outfit[]> => {
  try {
    const { data } = await axios.get<Outfit[]>(`${API_URL}/outfits`);
    return data;
  } catch (error) {
    console.error("Error fetching outfits:", error);
    throw new Error("Failed to fetch outfits. Please try again later.");
  }
};
