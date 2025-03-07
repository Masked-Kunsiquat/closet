import { useState } from "react";
import { Modal } from "flowbite-react";
import { createClothingItem } from "../api/clothing";
import { Category, ClothingItem } from "../types";
import Alert from "../utils/Alert"; // ✅ Import Alert component

interface AddClothingModalProps {
  isOpen: boolean;
  categories: Category[];
  onClose: () => void;
  onAdd: () => void;
}

const AddClothingModal: React.FC<AddClothingModalProps> = ({ isOpen, categories, onClose, onAdd }) => {
  const [newItem, setNewItem] = useState<Partial<ClothingItem>>({
    name: "",
    size: "",
    price: 0,
    category_id: "",
    brand_id: "",
    image_url: "",
    purchase_date: "",
  });

  const [loading, setLoading] = useState(false);
  const [alert, setAlert] = useState<{ type: "error" | "success"; message: string } | null>(null); // ✅ Store alerts

  const handleChange = (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>) => {
    setNewItem({ ...newItem, [e.target.name]: e.target.value });
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setAlert(null); // ✅ Reset alerts

    if (!newItem.name || !newItem.price || !newItem.category_id) {
      setAlert({ type: "error", message: "Please fill in all required fields." });
      return;
    }

    setLoading(true);
    const created = await createClothingItem(newItem);
    setLoading(false);

    if (created) {
      console.log("✅ Item added successfully:", created);
      setNewItem({
        name: "",
        size: "",
        price: 0,
        category_id: "",
        brand_id: "",
        image_url: "",
        purchase_date: "",
      });

      setAlert({ type: "success", message: "Item added successfully!" }); // ✅ Show success alert
      setTimeout(() => {
        onAdd();
        onClose();
      }, 1500); // ✅ Close modal after delay
    } else {
      setAlert({ type: "error", message: "Failed to add item. Try again." });
    }
  };

  return (
    <Modal show={isOpen} onClose={onClose}>
      <Modal.Header>Add New Clothing Item</Modal.Header>
      <Modal.Body>
        {/* ✅ Show Alerts */}
        {alert && <Alert type={alert.type} message={alert.message} onClose={() => setAlert(null)} />}

        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="grid gap-4 sm:grid-cols-2">
            <div>
              <label htmlFor="name" className="block mb-2 text-sm font-medium text-gray-900 dark:text-white">
                Name *
              </label>
              <input
                type="text"
                name="name"
                id="name"
                placeholder="Item name"
                className="w-full p-2 border rounded-lg"
                value={newItem.name}
                onChange={handleChange}
                required
              />
            </div>
            <div>
              <label htmlFor="brand_id" className="block mb-2 text-sm font-medium text-gray-900 dark:text-white">
                Brand (optional)
              </label>
              <input
                type="text"
                name="brand_id"
                id="brand_id"
                placeholder="Brand name"
                className="w-full p-2 border rounded-lg"
                value={newItem.brand_id}
                onChange={handleChange}
              />
            </div>
            <div>
              <label htmlFor="purchase_date" className="block mb-2 text-sm font-medium text-gray-900 dark:text-white">
                Purchase Date (optional)
              </label>
              <input
                type="date"
                name="purchase_date"
                id="purchase_date"
                className="w-full p-2 border rounded-lg"
                value={newItem.purchase_date || ""}
                onChange={handleChange}
              />
            </div>
            <div>
              <label htmlFor="price" className="block mb-2 text-sm font-medium text-gray-900 dark:text-white">
                Price *
              </label>
              <input
                type="number"
                name="price"
                id="price"
                placeholder="Price"
                className="w-full p-2 border rounded-lg"
                value={newItem.price}
                onChange={handleChange}
                required
              />
            </div>
            <div>
              <label htmlFor="category_id" className="block mb-2 text-sm font-medium text-gray-900 dark:text-white">
                Category *
              </label>
              <select
                name="category_id"
                id="category_id"
                className="w-full p-2 border rounded-lg"
                value={newItem.category_id}
                onChange={handleChange}
                required
              >
                <option value="">Select category</option>
                {categories.map((cat) => (
                  <option key={cat.id} value={cat.id}>
                    {cat.name}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label htmlFor="size" className="block mb-2 text-sm font-medium text-gray-900 dark:text-white">
                Size (optional)
              </label>
              <input
                type="text"
                name="size"
                id="size"
                placeholder="Size"
                className="w-full p-2 border rounded-lg"
                value={newItem.size}
                onChange={handleChange}
              />
            </div>
            <div>
              <label htmlFor="image_url" className="block mb-2 text-sm font-medium text-gray-900 dark:text-white">
                Image URL (optional)
              </label>
              <input
                type="text"
                name="image_url"
                id="image_url"
                placeholder="Image URL"
                className="w-full p-2 border rounded-lg"
                value={newItem.image_url}
                onChange={handleChange}
              />
            </div>
          </div>
        </form>
      </Modal.Body>
      <Modal.Footer>
        <button
          onClick={handleSubmit}
          className="px-5 py-2.5 bg-primary-700 text-white rounded-lg hover:bg-primary-800 focus:ring-4 focus:ring-primary-300"
          disabled={loading}
        >
          {loading ? "Adding..." : "Add Item"}
        </button>
        <button onClick={onClose} className="px-4 py-2 bg-gray-200 rounded-lg hover:bg-gray-300">
          Cancel
        </button>
      </Modal.Footer>
    </Modal>
  );
};

export default AddClothingModal;
