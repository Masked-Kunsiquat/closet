import { Sidebar } from "flowbite-react";
import { useEffect, useState } from "react";
import { getCategories } from "../api/clothes";
import useErrorHandler from "../hooks/useErrorHandler";
import ErrorDisplay from "../components/ErrorDisplay";

interface SidebarProps {
  onCategorySelect: (categoryId: string | null) => void;
}

interface Category {
  id: string;
  name: string;
}

const SidebarNav = ({ onCategorySelect }: SidebarProps) => {
  const [categories, setCategories] = useState<Category[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const { error, errorMode, handleError, clearError } = useErrorHandler();

  useEffect(() => {
    const fetchCategories = async () => {
      try {
        const data = await getCategories();
        setCategories(data);
      } catch (err) {
        handleError("Failed to load categories. Please try again.", "toast");
      } finally {
        setLoading(false);
      }
    };

    fetchCategories();
  }, []);

  return (
    <Sidebar
      aria-label="Sidebar Navigation"
      className="w-64 min-h-screen dark:bg-gray-900 bg-white"
    >
      <Sidebar.Items className="p-4 space-y-2">
        <Sidebar.ItemGroup>
          <Sidebar.Item
            onClick={() => onCategorySelect(null)}
            className="cursor-pointer hover:bg-gray-700 transition"
          >
            All Clothes
          </Sidebar.Item>

          {loading && <p className="text-gray-500">Loading categories...</p>}

          {/* ✅ Show Error if Exists */}
          {error && <ErrorDisplay message={error} mode={errorMode} onDismiss={clearError} />}

          {!loading && !error && categories.length === 0 && (
            <p className="text-gray-500">No categories available.</p>
          )}

          {!loading &&
            !error &&
            categories.map((category) => (
              <Sidebar.Item
                key={category.id}
                onClick={() => onCategorySelect(category.id)}
                className="cursor-pointer hover:bg-gray-700 transition"
              >
                {category.name}
              </Sidebar.Item>
            ))}
        </Sidebar.ItemGroup>
      </Sidebar.Items>
    </Sidebar>
  );
};

export default SidebarNav;
