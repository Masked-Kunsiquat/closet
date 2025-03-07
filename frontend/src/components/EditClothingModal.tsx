import { useState } from "react";
import { updateClothingItem } from "../api/clothing";
import { ClothingItem } from "../types";
import Alert from "../utils/Alert";

type Props = {
  item: ClothingItem;
  categories: { id: string; name: string }[];
  onClose: () => void;
  onUpdate: (updatedItem: ClothingItem) => void;
};

export default function EditClothingModal({ item, categories, onClose, onUpdate }: Props) {
  const [formData, setFormData] = useState<ClothingItem>(item);
  const [loading, setLoading] = useState(false);
  const [alert, setAlert] = useState<{ type: "success" | "error"; message: string } | null>(null);

  const handleChange = (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>) => {
    setFormData({ ...formData, [e.target.name]: e.target.value });
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
  
    try {
      const { category_name, brand_name, ...allowedData } = formData;
  
      console.log("📤 Submitting updated item:", allowedData);
  
      await updateClothingItem(formData.id, allowedData);
      
      setAlert({ type: "success", message: "Item updated successfully!" }); // ✅ Show success alert
      onUpdate(allowedData);
      setTimeout(onClose, 2000); // ✅ Close modal after 2 sec
    } catch (error) {
      console.error("❌ Update failed:", error);
      setAlert({ type: "error", message: "Update failed. Try again." }); // ✅ Show error alert
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="fixed inset-0 flex items-center justify-center bg-black bg-opacity-50 z-50">
      <div className="bg-white dark:bg-gray-800 rounded-lg shadow-lg p-6 w-full max-w-2xl">
        {alert && <Alert type={alert.type} message={alert.message} onClose={() => setAlert(null)} />}
        
        <div className="flex justify-between items-center pb-4 border-b border-gray-300 dark:border-gray-600">
          <h3 className="text-lg font-semibold text-gray-900 dark:text-white">Edit Clothing Item</h3>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-900 dark:hover:text-white">✖</button>
        </div>

        <form onSubmit={handleSubmit} className="mt-4">
          <div className="grid gap-4 sm:grid-cols-2">
            <div>
              <label className="block text-sm font-medium text-gray-900 dark:text-white">Name</label>
              <input
                type="text"
                name="name"
                value={formData.name}
                onChange={handleChange}
                className="w-full p-2 border rounded dark:bg-gray-700 dark:text-white"
              />
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-900 dark:text-white">Brand</label>
              <input
                type="text"
                name="brand_name"
                value={formData.brand_name || ""}
                onChange={handleChange}
                className="w-full p-2 border rounded dark:bg-gray-700 dark:text-white"
              />
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-900 dark:text-white">Price</label>
              <input
                type="number"
                name="price"
                value={formData.price}
                onChange={handleChange}
                className="w-full p-2 border rounded dark:bg-gray-700 dark:text-white"
              />
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-900 dark:text-white">Category</label>
              <select
                name="category_id"
                value={formData.category_id}
                onChange={handleChange}
                className="w-full p-2 border rounded dark:bg-gray-700 dark:text-white"
              >
                {categories.map((cat) => (
                  <option key={cat.id} value={cat.id}>
                    {cat.name}
                  </option>
                ))}
              </select>
            </div>
          </div>

          <div className="flex items-center mt-4 space-x-4">
            <button
              type="submit"
              disabled={loading}
              className="text-white bg-primary-700 hover:bg-primary-800 focus:ring-4 focus:outline-none focus:ring-primary-300 font-medium rounded-lg text-sm px-5 py-2.5"
            >
              {loading ? "Updating..." : "Update Item"}
            </button>
            <button
              type="button"
              onClick={onClose}
              className="text-red-600 hover:text-white border border-red-600 hover:bg-red-600 font-medium rounded-lg text-sm px-5 py-2.5"
            >
              Cancel
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
