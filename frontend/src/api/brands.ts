import axios from "axios";
import { API_URL } from "./config";

export const getBrands = async () => {
  try {
    const response = await axios.get(`${API_URL}/brands`);
    return response.data;
  } catch (error) {
    console.error("Error fetching brands:", error);
    return [];
  }
};
