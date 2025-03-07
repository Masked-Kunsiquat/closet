import { useState, useEffect } from "react";
import { useParams, Link, useNavigate } from "react-router-dom";
import { getClothingItemById, getCategories, deleteClothingItem } from "../api/clothing";
import ClothingInfo from "../components/ClothingInfo";
import ClothingActions from "../components/ClothingActions";
import EditClothingModal from "../components/EditClothingModal";
import ConfirmationModal from "../components/ConfirmationModal";
import { ClothingItem, Category } from "../types";
import Alert from "../utils/Alert";

export default function ClothingDetail() {
  const { id } = useParams();
  const navigate = useNavigate();
  const [item, setItem] = useState<ClothingItem | null>(null);
  const [categories, setCategories] = useState<Category[]>([]);
  const [isEditing, setIsEditing] = useState(false);
  const [isConfirming, setIsConfirming] = useState(false);
  const [alert, setAlert] = useState<{ type: "error" | "warning" | "info" | "success" | "dark"; message: string } | null>(null);

  const fetchItem = async () => {
    if (!id) return;
    try {
      const data = await getClothingItemById(id);
      if (data) {
        setItem(data);
      } else {
        setAlert({ type: "error", message: "Item not found." });
      }
    } catch (error) {
      setAlert({ type: "error", message: "Failed to load item details. Please try again later." });
    }
  };

  const handleDelete = async () => {
    if (!id) return;
    setIsConfirming(true);
  };

  const confirmDelete = async () => {
    setIsConfirming(false);
    if (!id) return;

    const success = await deleteClothingItem(id);
    if (success) {
      setAlert({ type: "success", message: "Item deleted successfully!" });
      setTimeout(() => navigate("/clothes"), 1500);
    } else {
      setAlert({ type: "error", message: "Failed to delete item. Please try again later." });
    }
  };

  useEffect(() => {
    fetchItem();
    getCategories().then(setCategories).catch(() => {
      setAlert({ type: "warning", message: "Failed to load categories. Some features may not work." });
    });
  }, [id]);

  if (!item) return <p className="text-center text-gray-500 mt-6">Loading item details...</p>;

  return (
    <section className="bg-white dark:bg-gray-900">
      <div className="py-8 px-4 mx-auto max-w-2xl lg:py-16">
        {alert && <Alert type={alert.type} message={alert.message} onClose={() => setAlert(null)} />} 

        <ClothingInfo {...item} />
        <ClothingActions onEdit={() => setIsEditing(true)} onDelete={handleDelete} />

        <Link to="/clothes" className="block text-blue-500 mt-4">← Back to Clothes</Link>
      </div>

      {isEditing && (
        <EditClothingModal
          item={item}
          categories={categories}
          onClose={() => setIsEditing(false)}
          onUpdate={() => {
            fetchItem();
            setAlert({ type: "success", message: "Item updated successfully!" });
          }}
        />
      )}

      {/* ✅ Confirmation Modal for Deletion */}
      <ConfirmationModal
        isOpen={isConfirming}
        title="Delete Clothing Item"
        message="Are you sure you want to delete this item? This action cannot be undone."
        onConfirm={confirmDelete}
        onCancel={() => setIsConfirming(false)}
        confirmText="Delete"
        cancelText="Cancel"
      />
    </section>
  );
}
