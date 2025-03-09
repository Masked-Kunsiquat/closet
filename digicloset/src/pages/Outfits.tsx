import { useEffect, useState } from "react";
import { getOutfits, Outfit } from "../api";

const Outfits = () => {
  const [outfits, setOutfits] = useState<Outfit[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const fetchOutfits = async () => {
      try {
        const data = await getOutfits();
        setOutfits(data);
      } catch (err) {
        setError("Failed to load outfits. Please try again later.");
      } finally {
        setLoading(false);
      }
    };

    fetchOutfits();
  }, []);

  return (
    <div className="p-4">
      <h1 className="text-2xl font-bold">Outfits</h1>

      {loading && <p>Loading outfits...</p>}
      {error && <p className="text-red-500">{error}</p>}

      {!loading && !error && outfits.length === 0 && (
        <p>No outfits available.</p>
      )}

      <ul>
        {outfits.map((outfit) => (
          <li key={outfit.id} className="p-2 border rounded mt-2">
            {outfit.name}
          </li>
        ))}
      </ul>
    </div>
  );
};

export default Outfits;
