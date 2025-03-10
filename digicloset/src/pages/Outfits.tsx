import { useEffect, useState } from "react";
import { getOutfits, Outfit } from "../api/outfits";
import useErrorHandler from "../hooks/useErrorHandler";
import ErrorDisplay from "../components/ErrorDisplay";

const Outfits = () => {
  const [outfits, setOutfits] = useState<Outfit[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const { errors, handleError, removeError } = useErrorHandler();

  useEffect(() => {
    const fetchOutfits = async () => {
      try {
        const data = await getOutfits();
        setOutfits(data);
      } catch (err) {
        if (err instanceof Error) {
          handleError(err.message, "toast"); // ✅ Type-safe error handling
        } else {
          handleError("An unknown error occurred.", "toast"); // ✅ Fallback for unexpected errors
        }
      } finally {
        setLoading(false);
      }
    };

    fetchOutfits();
  }, []);

  return (
    <div className="p-4">
      <h1 className="text-2xl font-bold">Outfits</h1>

      {/* ✅ Display multiple errors correctly */}
      <ErrorDisplay errors={errors} onDismiss={removeError} />

      {loading && <p>Loading outfits...</p>}

      {!loading && outfits.length === 0 && (
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
