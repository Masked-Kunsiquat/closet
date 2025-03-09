import axios from "axios";

export const API_URL = "http://localhost:3000"; // Your Express backend

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
