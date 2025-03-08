import { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { createOutfit, addClothingToOutfit } from "../api/outfits";
import { getClothingItems } from "../api/clothing";
import { ClothingItem } from "../types";

const NewOutfit = () => {
  const [name, setName] = useState("");
  const [occasionId, setOccasionId] = useState("");
  const [clothingItems, setClothingItems] = useState<ClothingItem[]>([]);
  const [selectedItems, setSelectedItems] = useState<string[]>([]);
  const navigate = useNavigate();

  useEffect(() => {
    getClothingItems().then(setClothingItems);
  }, []);

  const handleSubmit = async () => {
    if (!name) return alert("Outfit name is required.");
    
    const outfit = await createOutfit(name, occasionId);
    if (outfit) {
      for (const itemId of selectedItems) {
        await addClothingToOutfit(outfit.id, itemId);
      }
      navigate("/outfits");
    } else {
      alert("Error creating outfit.");
    }
  };

  return (
    <div className="p-4">
      <h1 className="text-2xl font-bold">Create Outfit</h1>

      <label className="block mt-2">Outfit Name:</label>
      <input value={name} onChange={(e) => setName(e.target.value)} className="border p-2 w-full" />

      <label className="block mt-2">Occasion:</label>
      <select value={occasionId} onChange={(e) => setOccasionId(e.target.value)} className="border p-2 w-full">
        <option value="">None</option>
        <option value="1">Casual</option>
        <option value="2">Formal</option>
      </select>

      <h2 className="text-lg mt-4">Select Clothing Items:</h2>
      <ul>
        {clothingItems.map((item) => (
          <li key={item.id} className="p-2 border rounded mt-2 flex items-center">
            <input
              type="checkbox"
              checked={selectedItems.includes(item.id)}
              onChange={() =>
                setSelectedItems((prev) =>
                  prev.includes(item.id) ? prev.filter((id) => id !== item.id) : [...prev, item.id]
                )
              }
              className="mr-2"
            />
            {item.image_url && <img src={item.image_url} alt={item.name} className="w-12 h-12 mr-2" />}
            {item.name} - {item.category?.name}
          </li>
        ))}
      </ul>

      <button onClick={handleSubmit} className="mt-4 bg-blue-500 text-white px-4 py-2 rounded">
        ✅ Save Outfit
      </button>
    </div>
  );
};

export default NewOutfit;
