import { useState } from "react";
import { useFetchClothing } from "../hooks/useFetchClothing";
import Alert from "../utils/Alert";
import CategoryFilter from "../components/CategoryFilter";
import ClothingGrid from "../components/ClothingGrid";
import AddClothingModal from "../components/AddClothingModal";

export default function Clothes() {
  const { clothes, categories, selectedCategory, alert, setAlert, fetchClothing } = useFetchClothing();
  const [isAdding, setIsAdding] = useState(false); // Modal state

  return (
    <div className="max-w-5xl mx-auto mt-6">
      <h2 className="text-3xl font-bold mb-6 text-center">My Clothes</h2>

      {/* ✅ Show Alert if Needed */}
      {alert && <Alert type={alert.type} message={alert.message} onClose={() => setAlert(null)} />}

      {/* ✅ Add Clothing Button */}
      <div className="flex justify-end mb-4">
        <button
          onClick={() => setIsAdding(true)}
          className="px-4 py-2 bg-green-600 text-white rounded-lg hover:bg-green-700"
        >
          + Add Clothing
        </button>
      </div>

      {/* ✅ Category Filter */}
      <CategoryFilter categories={categories} selectedCategory={selectedCategory} fetchClothing={fetchClothing} />

      {/* ✅ Clothing Grid */}
      <ClothingGrid clothes={clothes} />

      {/* ✅ Add Clothing Modal */}
      <AddClothingModal
        isOpen={isAdding}
        categories={categories}
        onClose={() => setIsAdding(false)}
        onAdd={fetchClothing} // Refresh clothing list after adding
      />
    </div>
  );
}
