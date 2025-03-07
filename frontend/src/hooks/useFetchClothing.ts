import { useState, useEffect } from "react";
import { getClothingItems, getCategories } from "../api/clothing";
import { ClothingItem, Category } from "../types";
import { useSearchParams, useNavigate } from "react-router-dom";

export function useFetchClothing() {
    const [clothes, setClothes] = useState<ClothingItem[]>([]);
    const [categories, setCategories] = useState<Category[]>([]);
    const [selectedCategory, setSelectedCategory] = useState<string | undefined>(undefined);
    const [alert, setAlert] = useState<{ type: "error" | "warning" | "info" | "success" | "dark"; message: string } | null>(null);
    
    const [searchParams] = useSearchParams();
    const navigate = useNavigate();
  
    useEffect(() => {
      getCategories()
        .then(setCategories)
        .catch(() => {
          console.error("⚠️ Error loading categories");
          setAlert({ type: "warning", message: "Failed to load categories. Some features may not work." });
        });
  
      const categoryId = searchParams.get("category");
      fetchClothing(categoryId || undefined);
    }, [searchParams]);
  
    const fetchClothing = async (categoryId?: string) => {
      setSelectedCategory(categoryId);
      navigate(categoryId ? `/clothes?category=${categoryId}` : "/clothes");
  
      try {
        const items = await getClothingItems(categoryId);
        setClothes(items);
        
        setAlert(items.length === 0 ? { type: "info", message: "No items found in this category." } : null);
      } catch (error) {
        console.error("❌ Error fetching clothing items:", error);
        setAlert({ type: "error", message: "Failed to load clothing items. Please try again later." });
      }
    };
  
    return { clothes, categories, selectedCategory, alert, setAlert, fetchClothing };
  }
  
