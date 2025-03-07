import { useFetchClothing } from "../hooks/useFetchClothing";
import Alert from "../utils/Alert";
import CategoryFilter from "../components/CategoryFilter";
import ClothingGrid from "../components/ClothingGrid";

export default function Clothes() {
  const { clothes, categories, selectedCategory, alert, setAlert, fetchClothing } = useFetchClothing();

  return (
    <div className="max-w-5xl mx-auto mt-6">
      <h2 className="text-3xl font-bold mb-6 text-center">My Clothes</h2>

      {/* ✅ Show Alert if Needed */}
      {alert && <Alert type={alert.type} message={alert.message} onClose={() => setAlert(null)} />}

      {/* ✅ Category Filter */}
      <CategoryFilter categories={categories} selectedCategory={selectedCategory} fetchClothing={fetchClothing} />

      {/* ✅ Clothing Grid */}
      <ClothingGrid clothes={clothes} />
    </div>
  );
}
