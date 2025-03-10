import { useEffect, useState } from "react";
import { useParams, Link } from "react-router-dom";
import { getClothingItemById, getCategories } from "../api/clothes";
import { Card, Button } from "flowbite-react";
import useErrorHandler from "../hooks/useErrorHandler";
import ErrorDisplay from "../components/ErrorDisplay";
import { ClothingItem, Category } from "../types";

// Import category-based placeholder images
import tshirtPlaceholder from "../assets/tshirt.jpg";
import shoesPlaceholder from "../assets/shoes.jpg";
import pantsPlaceholder from "../assets/pants.jpg";
import hatPlaceholder from "../assets/hat.jpg";


const ClothingDetail = () => {
  const { id } = useParams<{ id: string }>();
  const [clothingItem, setClothingItem] = useState<ClothingItem | null>(null);
  const [categories, setCategories] = useState<Category[]>([]);
  const [loading, setLoading] = useState<boolean>(true);

  // ✅ Updated Error Handler to match new structure
  const { errors, handleError, removeError } = useErrorHandler();

  useEffect(() => {
    const fetchData = async () => {
      try {
        if (!id) {
          handleError("Invalid clothing item ID.", "alert");
          return;
        }

        const [itemData, categoryData] = await Promise.allSettled([
          getClothingItemById(id),
          getCategories(),
        ]);

        if (itemData.status === "fulfilled" && itemData.value) {
          setClothingItem(itemData.value);
        } else {
          handleError("Clothing item not found.", "alert");
          return;
        }

        if (categoryData.status === "fulfilled") {
          setCategories(categoryData.value);
        } else {
          console.warn("⚠️ Warning: Failed to load categories.");
        }
      } catch (err) {
        console.error("❌ Error fetching clothing item:", err);

        if (err instanceof Error) {
          handleError(err.message, "toast"); // ✅ Displays actual backend error
        } else {
          handleError("An unknown error occurred.", "toast"); // ✅ Fallback for unexpected errors
        }
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
    if (!categoryName) return tshirtPlaceholder;

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

  return (
    <div className="p-4 flex justify-center">
      {/* ✅ Show Errors using the updated error system */}
      <ErrorDisplay errors={errors} onDismiss={removeError} />

      {/* ✅ Loading State */}
      {loading && <p className="text-white text-center">Loading...</p>}

      {!loading && clothingItem ? (
        <Card className="max-w-md w-full dark:bg-gray-800">
          <img
            src={clothingItem.imageUrl || getPlaceholderByCategory(getCategoryName(clothingItem.categoryId))}
            alt={clothingItem.name}
            className="w-full h-64 object-cover rounded-t-lg"
          />
          <div className="p-4">
            <h1 className="text-2xl font-bold text-gray-900 dark:text-white">{clothingItem.name}</h1>
            {typeof clothingItem.price === "number" && !isNaN(clothingItem.price) && (
              <p className="text-gray-800 dark:text-gray-300">${clothingItem.price.toFixed(2)}</p>
            )}
            {clothingItem.size && <p className="text-gray-700 dark:text-gray-300">Size: {clothingItem.size}</p>}
            <p className="text-gray-700 dark:text-gray-400">Category: {getCategoryName(clothingItem.categoryId)}</p>
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
      ) : (
        !loading && (
          <div className="text-center text-gray-400 p-4">
            <p>No clothing item found.</p>
            <Link to="/clothes">
              <Button color="blue" className="mt-4">Back to Clothes</Button>
            </Link>
          </div>
        )
      )}
    </div>
  );
};

export default ClothingDetail;
