import React, { useState, useEffect } from 'react';
import db from '../db/dexie';

const ItemList = () => {
    const [items, setItems] = useState([]);

    useEffect(() => {
        const fetchItems = async () => {
            const allItems = await db.items.toArray();
            setItems(allItems);
        };
        fetchItems();
    }, []);

return (
    <div>
        {items.map((item) => (
            <div key={item.id}>
            <h3>{item.name}</h3>
            <p>Category: {item.category}</p>
            <p>Color: {item.color}</p>
            <p>Price: ${item.price}</p>
            {item.photo && <img src={item.photo} alt={item.name} width={100} />}
            </div>
        ))}
    </div>
    );
};

export default ItemList;