import axios from "axios";
import { API_URL } from "./config";

/**
 * Handles API requests with automatic error handling.
 */
export const apiRequest = async <T>(
  url: string,
  method: "GET" | "POST" | "PUT" | "DELETE",
  data?: unknown
): Promise<T> => {
  try {
    const response = await axios({ method, url: `${API_URL}${url}`, data });
    return response.data;
  } catch (error) {
    console.error(`❌ API Error: ${url}`, error);

    // Check if the error is from Axios
    if (axios.isAxiosError(error)) {
      throw new Error(error.response?.data?.message || "An API error occurred.");
    } else {
      throw new Error("An unexpected error occurred.");
    }
  }
};
