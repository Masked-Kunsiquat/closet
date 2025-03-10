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

    if (axios.isAxiosError(error)) {
      // ✅ Extracts structured backend error message
      const backendError = error.response?.data?.error || "An unknown error occurred.";
      throw new Error(backendError);
    } else {
      throw new Error("An unexpected error occurred.");
    }
  }
};
