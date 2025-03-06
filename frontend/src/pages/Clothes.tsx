import { useState, useEffect } from "react";
import { getClothingItems, getCategories } from "../api/clothing";
import { Link } from "react-router-dom";

type Category = {
  id: string;
  name: string;
};

type ClothingItem = {
  id: string;
  name: string;
  size: string;
  price: number;
  category_id: string;
  image_url?: string;
};

export default function Clothes() {
  const [clothes, setClothes] = useState<ClothingItem[]>([]);
  const [categories, setCategories] = useState<Category[]>([]);
  const [selectedCategory, setSelectedCategory] = useState<string | undefined>(undefined);

  useEffect(() => {
    getCategories().then(setCategories);
    fetchClothing(); // Load all clothing initially
  }, []);

  const fetchClothing = async (categoryId?: string) => {
    console.log("Fetching clothing for category:", categoryId || "ALL");

    setSelectedCategory(categoryId);
    const items = await getClothingItems(categoryId);

    console.log(`Updated state with ${items.length} items.`);
    setClothes(items);
  };

  return (
    <div className="max-w-5xl mx-auto mt-6">
      <h2 className="text-3xl font-bold mb-6 text-center">My Clothes</h2>

      {/* Category Filter Buttons */}
      <div className="flex items-center justify-center py-4 flex-wrap">
        <button
          onClick={() => fetchClothing(undefined)}
          className={`${
            selectedCategory === undefined ? "text-blue-700 border-blue-600 bg-white" : "text-gray-900 border-white hover:border-gray-200"
          } border rounded-full text-base font-medium px-5 py-2.5 text-center me-3 mb-3 focus:ring-4 focus:outline-none focus:ring-blue-300`}
        >
          All categories
        </button>

        {categories.map((category) => (
          <button
            key={category.id}
            onClick={() => fetchClothing(category.id)}
            className={`${
              selectedCategory === category.id ? "text-blue-700 border-blue-600 bg-white" : "text-gray-900 border-white hover:border-gray-200"
            } border rounded-full text-base font-medium px-5 py-2.5 text-center me-3 mb-3 focus:ring-4 focus:outline-none focus:ring-gray-300`}
          >
            {category.name}
          </button>
        ))}
      </div>

      {/* Clothes Grid */}
      <div className="grid grid-cols-2 md:grid-cols-3 gap-4 mt-6">
        {clothes.length > 0 ? (
          clothes.map((item) => (
            <Link
              to={`/clothes/${item.id}`}
              key={item.id}
              className="relative border p-2 rounded-lg shadow-sm block hover:bg-gray-100"
            >
              <img
                src={item.image_url || "https://flowbite.s3.amazonaws.com/docs/gallery/square/image.jpg"}
                alt={item.name}
                className="h-auto max-w-full rounded-lg"
              />
              <div className="mt-2 text-center">
                <h3 className="font-bold">{item.name}</h3>
                <p className="text-sm">Size: {item.size}</p>
                <p className="text-sm font-semibold">Price: ${item.price.toFixed(2)}</p>
              </div>
            </Link>
          ))
        ) : (
          <p className="text-gray-500 text-center col-span-full mt-4">No items found</p>
        )}
      </div>
    </div>
  );
}
