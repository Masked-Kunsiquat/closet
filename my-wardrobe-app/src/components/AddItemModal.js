import React, { useState } from 'react';

const AddItemModal = ({ closeModal, addItem, predefinedCategories }) => {
  const [formData, setFormData] = useState({
    name: '',
    brand: '',
    date: '',
    color: '#000000',
    category: predefinedCategories[0] || 'Shirts',
    cost: '',
    image: null,
  });

  const handleChange = (e) => {
    const { name, value } = e.target;
    setFormData({ ...formData, [name]: value });
  };

  const handleImageUpload = (e) => {
    const file = e.target.files[0];
    const reader = new FileReader();

    reader.onload = () => {
      setFormData({ ...formData, image: reader.result }); // Save image as Base64
    };

    if (file) {
      reader.readAsDataURL(file);
    }
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    addItem({ ...formData, id: Date.now(), wearCount: 0 }); // Add default wearCount
    closeModal();
  };

  return (
    <div className="modal">
      <div className="modal-content">
        <h3>Add Item</h3>
        <form onSubmit={handleSubmit}>
          <input
            type="text"
            name="name"
            placeholder="Name"
            value={formData.name}
            onChange={handleChange}
            required
          />
          <input
            type="text"
            name="brand"
            placeholder="Brand"
            value={formData.brand}
            onChange={handleChange}
          />
          <input
            type="date"
            name="date"
            value={formData.date}
            onChange={handleChange}
          />
          <input
            type="number"
            name="cost"
            placeholder="Cost"
            value={formData.cost}
            onChange={handleChange}
          />
          <input
            type="color"
            name="color"
            value={formData.color}
            onChange={handleChange}
          />
          <select
            name="category"
            value={formData.category}
            onChange={handleChange}
            required
          >
            <option value="" disabled>Select a category</option>
            {predefinedCategories.map((category) => (
              <option key={category} value={category}>
                {category}
              </option>
            ))}
          </select>
          <input type="file" accept="image/*" onChange={handleImageUpload} />
          <button type="submit">Add</button>
          <button type="button" onClick={closeModal}>
            Cancel
          </button>
        </form>
      </div>
    </div>
  );
};

export default AddItemModal;
