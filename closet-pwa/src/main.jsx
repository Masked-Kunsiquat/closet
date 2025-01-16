import React, { useState, useEffect } from 'react';
import { createItem } from './utils/schema';
import { addItem, getItems, deleteItem } from './utils/db';
import ReactDOM from 'react-dom/client';

function App() {
    const [items, setItems] = useState([]); // Manage list of items
    const [formData, setFormData] = useState({ name: '', category: '', color: '' }); // Manage form input

    // Fetch items from IndexedDB on initial load
    useEffect(() => {
        const fetchItems = async () => {
            const storedItems = await getItems();
            setItems(storedItems);
        };
        fetchItems();
    }, []);

    // Handle input change in the form
    const handleChange = (e) => {
        const { name, value } = e.target;
        setFormData({ ...formData, [name]: value });
    };

    // Handle form submission to add new items
    const handleSubmit = async (e) => {
        e.preventDefault();
        const newItem = createItem(formData.name, formData.category, formData.color);
        await addItem(newItem); // Add item to IndexedDB
        setItems((prev) => [...prev, newItem]); // Update local state
        setFormData({ name: '', category: '', color: '' }); // Reset form
    };

    // Handle item deletion
    const handleDelete = async (id) => {
        await deleteItem(id); // Remove item from IndexedDB
        setItems((prev) => prev.filter((item) => item.id !== id)); // Update local state
    };

    return (
        <div>
            <h1>Closet PWA</h1>

            {/* Form to Add New Items */}
            <form onSubmit={handleSubmit}>
                <input
                    type="text"
                    name="name"
                    value={formData.name}
                    onChange={handleChange}
                    placeholder="Item Name"
                    required
                />
                <input
                    type="text"
                    name="category"
                    value={formData.category}
                    onChange={handleChange}
                    placeholder="Category"
                    required
                />
                <input
                    type="text"
                    name="color"
                    value={formData.color}
                    onChange={handleChange}
                    placeholder="Color"
                    required
                />
                <button type="submit">Add Item</button>
            </form>

            {/* List of Items */}
            <ul>
                {items.map((item) => (
                    <li key={item.id}>
                        {item.name} - {item.category} - {item.color}
                        <button onClick={() => handleDelete(item.id)}>Delete</button>
                    </li>
                ))}
            </ul>
        </div>
    );
}

const root = ReactDOM.createRoot(document.getElementById('root'));
root.render(<App />);

// Register Service Worker
if ('serviceWorker' in navigator) {
    navigator.serviceWorker
        .register('/service-worker.js')
        .then((registration) => {
            console.log('Service Worker registered with scope:', registration.scope);
        })
        .catch((error) => {
            console.error('Service Worker registration failed:', error);
        });
}
