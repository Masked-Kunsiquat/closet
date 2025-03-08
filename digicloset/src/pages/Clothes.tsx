import { useEffect, useState } from "react";
import { getClothingItems } from "../api/clothes"; // ✅ API function

const Clothes = () => {
  const [clothes, setClothes] = useState([]);

  useEffect(() => {
    getClothingItems().then(setClothes);
  }, []);

  return (
    <div className="p-4">
      <h1 className="text-2xl font-bold">Clothing Items</h1>
      <ul>
        {clothes.map((item: { id: string; name: string }) => (
          <li key={item.id} className="p-2 border rounded mt-2">
            {item.name}
          </li>
        ))}
      </ul>
    </div>
  );
};

export default Clothes;
