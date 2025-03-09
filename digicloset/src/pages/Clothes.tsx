import { useEffect, useState } from "react";
import { getClothingItems, getCategories } from "../api/clothes";
import { Card, Spinner } from "flowbite-react";
import { Link } from "react-router-dom";
import useErrorHandler from "../hooks/useErrorHandler";
import ErrorDisplay from "../components/ErrorDisplay";

// Import category-based placeholder images
import tshirtPlaceholder from "../assets/tshirt.jpg";
import shoesPlaceholder from "../assets/shoes.jpg";
import pantsPlaceholder from "../assets/pants.jpg";
import hatPlaceholder from "../assets/hat.jpg";

interface ClothingItem {
  id: string;
  name: string;
  imageUrl?: string;
  price?: number;
  size?: string;
  categoryId?: string | null;
}

interface Category {
  id: string;
  name: string;
}

interface ClothesProps {
  categoryId: string | null;
}

const Clothes = ({ categoryId }: ClothesProps) => {
  const [clothes, setClothes] = useState<ClothingItem[]>([]);
  const [categories, setCategories] = useState<Category[]>([]);
  const [loading, setLoading] = useState(true);
  const { error, errorMode, handleError, clearError } = useErrorHandler();

  useEffect(() => {
    setLoading(true);
    clearError(); // Reset errors on new load

    Promise.all([getClothingItems(categoryId), getCategories()])
      .then(([clothesData, categoriesData]) => {
        setClothes(clothesData);
        setCategories(categoriesData);
      })
      .catch((err) => {
        console.error("❌ Error fetching data:", err);
        handleError("Failed to load clothing data. Please try again.", "toast");
      })
      .finally(() => setLoading(false));
  }, [categoryId]);

  // Function to get category name by ID
  const getCategoryName = (categoryId: string | null) => {
    const category = categories.find((c) => c.id === categoryId);
    return category ? category.name.toLowerCase() : null;
  };

  // Function to assign a placeholder based on category name
  const getPlaceholderByCategory = (categoryName: string | null) => {
    switch (categoryName) {
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
    <div className="p-4 w-full">
      <h1 className="text-2xl font-bold mb-4 text-gray-900 dark:text-white">Clothing Items</h1>

      {/* ✅ Show Error if Exists */}
      {error && <ErrorDisplay message={error} mode={errorMode} onDismiss={clearError} />}

      {/* ✅ Loading State */}
      {loading && (
        <div className="flex justify-center items-center">
          <Spinner aria-label="Loading clothes..." size="lg" />
        </div>
      )}

      {/* ✅ Empty State */}
      {!loading && !error && clothes.length === 0 && (
        <p className="text-gray-700 dark:text-gray-400">No clothes found for this category.</p>
      )}

      {/* ✅ Render Clothing Items */}
      {!loading && !error && clothes.length > 0 && (
        <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-4">
          {clothes.map((item) => {
            const categoryName = getCategoryName(item.categoryId || null);
            return (
              <Card key={item.id} className="max-w-sm dark:bg-gray-800">
                <Link to={`/clothes/${item.id}`}>
                  <img
                    src={item.imageUrl || getPlaceholderByCategory(categoryName)}
                    alt={item.name}
                    className="w-full h-48 object-cover rounded-t-lg"
                  />
                </Link>
                <div className="p-4">
                  <h5 className="text-lg font-bold text-gray-900 dark:text-white">{item.name}</h5>
                  {typeof item.price === "number" && !isNaN(item.price) ? (
                    <p className="text-gray-800 dark:text-gray-300">💲 {item.price.toFixed(2)}</p>
                  ) : null}
                  {item.size && <p className="text-gray-700 dark:text-gray-400">Size: {item.size}</p>}
                </div>
              </Card>
            );
          })}
        </div>
      )}
    </div>
  );
};

export default Clothes;
