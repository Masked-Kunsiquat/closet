import { useEffect, useState } from "react";
import { getOutfits } from "../api";

const Outfits = () => {
  const [outfits, setOutfits] = useState([]);

  useEffect(() => {
    getOutfits().then(setOutfits);
  }, []);

  return (
    <div className="p-4">
      <h1 className="text-2xl font-bold">Outfits</h1>
      <ul>
        {outfits.map((outfit: { id: string; name: string }) => (
          <li key={outfit.id} className="p-2 border rounded mt-2">
            {outfit.name}
          </li>
        ))}
      </ul>
    </div>
  );
};

export default Outfits;
