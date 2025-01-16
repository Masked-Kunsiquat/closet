// src/components/AddItemForm.jsx
import React, { use, useState } from 'react';
import db from '../db/dexie';

const AddItemForm = () => {
    const [name, setName] = useState('');
    const [category, setCategory] = useState('');
    const [color, setColor] = useState('');
    const [price, setPrice] = useState('');
    const [photo, setPhoto] = useState('');

    const handleSubmit = async (e) => {
        e.preventDefault();
        const id = Date.now(); // Unique ID
        await db.items.add({id, name, category, color, price, photo});
        alert('Item added successfully!');
    };

    return(
        <form onSubmit={handleSubmit}>
            <input placeholder='Name' value={name} onChange={(e) => setName(e.target.value)} />
            <input placeholder='Category' value={category} onChange={(e) => setCategory(e.target.value)} />
            <input placeholder='Color' value={color} onChange={(e) => setColor(e.target.value)} />
            <input placeholder='Price' value={price} onChange={(e) => setPrice(e.target.value)} />
            <input type='file' accept='image/*' onChange={(e) => {
                const file = e.target.files[0];
                const reader = new FileReader();
                reader.onload = () => setPhoto(reader.result);
                reader.readAsDataURL(file);
            }} />
            <button type='submit'>Add Item</button>
        </form>
    );
};

export default AddItemForm;