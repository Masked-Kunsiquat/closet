import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { getOutfits } from "../api/outfits";
import { Outfit } from "../types";

const Outfits = () => {
  const [outfits, setOutfits] = useState<Outfit[]>([]);

  useEffect(() => {
    getOutfits().then(setOutfits);
  }, []);

  return (
    <div className="p-4">
      <h1 className="text-2xl font-bold">Outfits</h1>
      <Link to="/outfits/new" className="bg-blue-500 text-white p-2 rounded">
        ➕ Create Outfit
      </Link>
      <ul className="mt-4">
        {outfits.map((outfit) => (
          <li key={outfit.id} className="p-2 border rounded mt-2">
            <Link to={`/outfits/${outfit.id}`} className="text-lg font-semibold">
              {outfit.name}
            </Link>
            {outfit.occasion && <p className="text-gray-600">🎉 {outfit.occasion}</p>}
          </li>
        ))}
      </ul>
    </div>
  );
};

export default Outfits;
