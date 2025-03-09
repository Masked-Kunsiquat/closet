import { useEffect, useState } from "react";
import { useParams, Link } from "react-router-dom";
import { getClothingItemById, getCategories } from "../api/clothes";
import { Card, Button } from "flowbite-react";

// Import category-based placeholder images
import tshirtPlaceholder from "../assets/tshirt.jpg"; // Tops
import shoesPlaceholder from "../assets/shoes.jpg"; // Shoes
import pantsPlaceholder from "../assets/pants.jpg"; // Bottoms
import hatPlaceholder from "../assets/hat.jpg"; // Accessories

interface ClothingItem {
  id: string;
  name: string;
  imageUrl?: string;
  price?: number | null;
  size?: string;
  categoryId?: string | null;
  purchaseDate?: string;
}

interface Category {
  id: string;
  name: string;
}

const ClothingDetail = () => {
  const { id } = useParams<{ id: string }>();
  const [clothingItem, setClothingItem] = useState<ClothingItem | null>(null);
  const [categories, setCategories] = useState<Category[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const fetchData = async () => {
      try {
        if (!id) {
          throw new Error("Invalid clothing item ID.");
        }

        const [itemData, categoryData] = await Promise.allSettled([
          getClothingItemById(id),
          getCategories(),
        ]);

        if (itemData.status === "fulfilled" && itemData.value) {
          setClothingItem(itemData.value);
        } else {
          throw new Error("Clothing item not found.");
        }

        if (categoryData.status === "fulfilled") {
          setCategories(categoryData.value);
        } else {
          console.warn("⚠️ Warning: Failed to load categories.");
        }
      } catch (err) {
        setError((err as Error).message || "Failed to load data.");
      } finally {
        setLoading(false);
      }
    };

    fetchData();
  }, [id]);

  // Function to get category name by ID
  const getCategoryName = (categoryId: string | null) => {
    const category = categories.find((c) => c.id === categoryId);
    return category ? category.name : "Unknown";
  };

  // Function to assign a placeholder based on category name
  const getPlaceholderByCategory = (categoryName: string | null) => {
    if (!categoryName) return tshirtPlaceholder; // Default fallback

    switch (categoryName.toLowerCase()) {
      case "tops":
        return tshirtPlaceholder;
      case "bottoms":
        return pantsPlaceholder;
      case "shoes":
        return shoesPlaceholder;
      case "accessories":
        return hatPlaceholder;
      default:
        return tshirtPlaceholder;
    }
  };

  if (loading) {
    return <p className="text-white text-center">Loading...</p>;
  }

  if (error) {
    return (
      <div className="text-center text-red-500 p-4">
        <p>{error}</p>
        <Link to="/clothes">
          <Button color="blue" className="mt-4">Back to Clothes</Button>
        </Link>
      </div>
    );
  }

  if (!clothingItem) {
    return (
      <div className="text-center text-gray-400 p-4">
        <p>No clothing item found.</p>
        <Link to="/clothes">
          <Button color="blue" className="mt-4">Back to Clothes</Button>
        </Link>
      </div>
    );
  }

  const categoryName = getCategoryName(clothingItem.categoryId || null);

  return (
    <div className="p-4 flex justify-center">
      <Card className="max-w-md w-full dark:bg-gray-800">
        <img
          src={clothingItem.imageUrl || getPlaceholderByCategory(categoryName)}
          alt={clothingItem.name}
          className="w-full h-64 object-cover rounded-t-lg"
        />
        <div className="p-4">
          <h1 className="text-2xl font-bold text-gray-900 dark:text-white">{clothingItem.name}</h1>
          {typeof clothingItem.price === "number" && !isNaN(clothingItem.price) && (
            <p className="text-gray-800 dark:text-gray-300">${clothingItem.price.toFixed(2)}</p>
          )}
          {clothingItem.size && <p className="text-gray-700 dark:text-gray-300">Size: {clothingItem.size}</p>}
          <p className="text-gray-700 dark:text-gray-400">Category: {categoryName}</p>
          {clothingItem.purchaseDate && (
            <p className="text-gray-700 dark:text-gray-400">Purchased on: {clothingItem.purchaseDate}</p>
          )}
        </div>
        <div className="p-4">
          <Link to="/clothes">
            <Button color="blue">Back to Clothes</Button>
          </Link>
        </div>
      </Card>
    </div>
  );
};

export default ClothingDetail;
