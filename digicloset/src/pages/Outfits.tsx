import { useEffect, useState } from "react";
import { getOutfits, Outfit } from "../api/outfits";
import useErrorHandler from "../hooks/useErrorHandler";
import ErrorDisplay from "../components/ErrorDisplay";

const Outfits = () => {
  const [outfits, setOutfits] = useState<Outfit[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const { error, errorMode, handleError, clearError } = useErrorHandler();

  useEffect(() => {
    const fetchOutfits = async () => {
      try {
        const data = await getOutfits();
        setOutfits(data);
      } catch (err) {
        handleError("Failed to load outfits. Please try again later.", "toast");
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

      {error && <ErrorDisplay message={error} mode={errorMode} onDismiss={clearError} />}

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
