import React from "react";

interface CategoryFilterProps {
  categories: { id: string; name: string }[];
  selectedCategory?: string;
  fetchClothing: (categoryId?: string) => void;
}

const CategoryFilter: React.FC<CategoryFilterProps> = ({ categories, selectedCategory, fetchClothing }) => {
  return (
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
  );
};

export default CategoryFilter;
