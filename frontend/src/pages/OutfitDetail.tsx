import { useParams } from "react-router-dom";
import { useEffect, useState } from "react";
import { getOutfitById, removeClothingFromOutfit, deleteOutfit } from "../api/outfits";
import type { OutfitDetail } from "../types"; // ✅ Type-only import

const OutfitDetail = () => {
  const { id } = useParams();
  const [outfit, setOutfit] = useState<OutfitDetail | null>(null);

  useEffect(() => {
    if (id) getOutfitById(id).then(setOutfit);
  }, [id]);

  const handleRemoveItem = async (clothingItemId: string) => {
    if (id) {
      const success = await removeClothingFromOutfit(id, clothingItemId);
      if (success) setOutfit((prev) =>
        prev ? { ...prev, clothing: prev.clothing.filter(item => item.clothing_item_id !== clothingItemId) } : null
      );
    }
  };

  const handleDeleteOutfit = async () => {
    if (id && confirm("Are you sure you want to delete this outfit?")) {
      await deleteOutfit(id);
      window.location.href = "/outfits";
    }
  };

  if (!outfit) return <p>Loading...</p>;

  return (
    <div className="p-4">
      <h1 className="text-2xl font-bold">{outfit.name}</h1>
      {outfit.occasion && <p className="text-gray-600">🎉 {outfit.occasion}</p>}

      <h2 className="text-lg mt-4">Clothing Items:</h2>
      <ul>
        {outfit.clothing.map(({ clothing_item_id, clothing }) => (
          <li key={clothing_item_id} className="p-2 border rounded mt-2 flex items-center">
            {clothing.image_url && <img src={clothing.image_url} alt={clothing.name} className="w-12 h-12 mr-2" />}
            <span>{clothing.name} - {clothing.category?.name}</span>
            <button onClick={() => handleRemoveItem(clothing_item_id)} className="ml-auto bg-red-500 text-white px-2 py-1 rounded">
              ❌ Remove
            </button>
          </li>
        ))}
      </ul>

      <button onClick={handleDeleteOutfit} className="mt-4 bg-red-500 text-white px-4 py-2 rounded">
        🗑️ Delete Outfit
      </button>
    </div>
  );
};

export default OutfitDetail;
