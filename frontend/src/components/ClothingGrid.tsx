import React from "react";
import { Link } from "react-router-dom";
import { ClothingItem } from "../types";

interface ClothingGridProps {
  clothes: ClothingItem[];
}

const ClothingGrid: React.FC<ClothingGridProps> = ({ clothes }) => {
  return (
    <div className="grid grid-cols-2 md:grid-cols-3 gap-4 mt-6">
      {clothes.length > 0 ? (
        clothes.map((item) => (
          <Link
            to={`/clothes/${item.id}`}
            key={item.id}
            className="relative border p-2 rounded-lg shadow-sm block hover:bg-gray-100"
          >
            <img
              src={item.image_url || "https://flowbite.s3.amazonaws.com/docs/gallery/square/image.jpg"}
              alt={item.name}
              className="h-auto max-w-full rounded-lg"
            />
            <div className="mt-2 text-center">
              <h3 className="font-bold">{item.name}</h3>
              <p className="text-sm">Size: {item.size}</p>
              <p className="text-sm font-semibold">Price: ${item.price.toFixed(2)}</p>
            </div>
          </Link>
        ))
      ) : (
        <p className="text-gray-500 text-center col-span-full mt-4">No items found</p>
      )}
    </div>
  );
};

export default ClothingGrid;
