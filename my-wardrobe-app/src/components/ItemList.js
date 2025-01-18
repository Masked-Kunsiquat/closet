import React, { useEffect, useState } from 'react';
import { getItems } from '../utils/idb';

const ItemList = () => {
  const [items, setItems] = useState([]);

  const fetchItems = async () => {
    const allItems = await getItems();
    setItems(allItems);
  };

  useEffect(() => {
    fetchItems();
  }, []);

  return (
    <div>
      <h2>Wardrobe Items</h2>
      <ul>
        {items.map((item) => (
          <li key={item.id}>
            <strong>{item.name}</strong> ({item.brand}) - {item.cost} USD
            <div style={{ backgroundColor: item.color, width: '20px', height: '20px' }}></div>
            <p>{item.notes}</p>
          </li>
        ))}
      </ul>
    </div>
  );
};

export default ItemList;
