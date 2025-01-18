import React, { useState } from 'react';
import { addItem } from '../utils/idb';

const AddItemForm = ({ refreshItems }) => {
  const [formData, setFormData] = useState({
    name: '',
    brand: '',
    date: '',
    cost: '',
    color: '#000000',
    notes: '',
  });

  const handleChange = (e) => {
    setFormData({ ...formData, [e.target.name]: e.target.value });
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    await addItem(formData);
    refreshItems();
    setFormData({
      name: '',
      brand: '',
      date: '',
      cost: '',
      color: '#000000',
      notes: '',
    });
  };

  return (
    <form onSubmit={handleSubmit}>
      <input type="text" name="name" placeholder="Item Name" value={formData.name} onChange={handleChange} required />
      <input type="text" name="brand" placeholder="Brand" value={formData.brand} onChange={handleChange} />
      <input type="date" name="date" value={formData.date} onChange={handleChange} />
      <input type="number" name="cost" placeholder="Cost" value={formData.cost} onChange={handleChange} />
      <input type="color" name="color" value={formData.color} onChange={handleChange} />
      <textarea name="notes" placeholder="Notes" value={formData.notes} onChange={handleChange}></textarea>
      <button type="submit">Add Item</button>
    </form>
  );
};

export default AddItemForm;
