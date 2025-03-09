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
  price?: number;
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

  useEffect(() => {
    if (id) {
      getClothingItemById(id).then(setClothingItem);
    }
    getCategories().then(setCategories);
  }, [id]);

  // Function to get category name by ID
  const getCategoryName = (categoryId: string | null) => {
    const category = categories.find((c) => c.id === categoryId);
    return category ? category.name : "Unknown";
  };

  // Function to assign a placeholder based on category name
  const getPlaceholderByCategory = (categoryName: string | null) => {
    switch (categoryName?.toLowerCase()) {
      case "tops":
        return tshirtPlaceholder;
      case "bottoms":
        return pantsPlaceholder;
      case "shoes":
        return shoesPlaceholder;
      case "accessories":
        return hatPlaceholder;
      default:
        return tshirtPlaceholder; // Default fallback
    }
  };

  if (!clothingItem) return <p className="text-white text-center">Loading...</p>;

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
          {clothingItem.price && <p className="text-gray-800 dark:text-gray-300">${clothingItem.price.toFixed(2)}</p>}
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
