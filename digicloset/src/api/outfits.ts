import { apiRequest } from "./apiWrapper";

export interface Outfit {
  id: string;
  name: string;
  description?: string;
  createdAt: string;
  updatedAt: string;
}

/**
 * Fetch all outfits.
 */
export const getOutfits = async (): Promise<Outfit[]> => {
  return apiRequest<Outfit[]>(`/outfits`, "GET");
};
