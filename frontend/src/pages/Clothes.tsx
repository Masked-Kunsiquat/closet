import { useState, useEffect } from "react";
import { Tabs } from "flowbite-react";
import { getClothingItems, getCategories } from "../api/clothing";

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
    <div className="max-w-4xl mx-auto mt-6">
      <h2 className="text-3xl font-bold mb-4">My Clothes</h2>

      {/* Category Filter Tabs */}
      <Tabs aria-label="Clothing Categories">
        <Tabs.Item
          active={selectedCategory === undefined}
          title="All"
          onClick={() => {
            console.log("Clicked: All Categories");
            fetchClothing(undefined);
          }}
        >
          <p className="text-gray-500">Showing all clothing items</p>
        </Tabs.Item>

        {categories.map((category) => (
          <Tabs.Item
            key={category.id}
            title={category.name}
            active={selectedCategory === category.id} // FIX: Use ID for selection
            onClick={() => {
              console.log(`Clicked: ${category.name}`);
              fetchClothing(category.id); // FIX: Pass category ID instead of name
            }}
          >
            <p className="text-gray-500">Showing {category.name} items</p>
          </Tabs.Item>
        ))}
      </Tabs>

      {/* Clothes Grid */}
      <div className="grid grid-cols-2 md:grid-cols-3 gap-4 mt-6">
        {clothes.length > 0 ? (
          clothes.map((item) => (
            <div key={item.id} className="border p-4 rounded-lg shadow-sm">
              <h3 className="font-bold">{item.name}</h3>
              <p>Size: {item.size}</p>
              <p>Price: ${item.price.toFixed(2)}</p>
            </div>
          ))
        ) : (
          <p className="text-gray-500 text-center col-span-full mt-4">No items found</p>
        )}
      </div>
    </div>
  );
}
