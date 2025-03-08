import axios from "axios";

export const API_URL = "http://localhost:3000"; // Your Express backend

export const getOutfits = async () => {
  const { data } = await axios.get(`${API_URL}/outfits`);
  return data;
};
