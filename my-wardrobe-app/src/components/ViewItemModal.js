import React from 'react';

const ViewItemModal = ({ item, closeModal, openEditModal, incrementWearCount }) => {
  if (!item) return null;

  const costPerWear =
    item.cost && item.wearCount > 0
      ? (item.cost / item.wearCount).toFixed(2)
      : 'N/A';

  return (
    <div className="modal">
      <div className="modal-content">
        <h3>Item Details</h3>
        {item.image && <img src={item.image} alt={item.name} />}
        <p><strong>Name:</strong> {item.name}</p>
        <p><strong>Brand:</strong> {item.brand}</p>
        <p><strong>Date:</strong> {item.date}</p>
        <p><strong>Category:</strong> {item.category}</p>
        <p><strong>Cost:</strong> ${item.cost}</p>
        <p><strong>Wear Count:</strong> {item.wearCount}</p>
        <p><strong>Cost Per Wear:</strong> ${costPerWear}</p>
        <button onClick={() => incrementWearCount(item.id)}>Wear</button>
        <button onClick={() => openEditModal(item)}>Edit</button>
        <button onClick={closeModal}>Close</button>
      </div>
    </div>
  );
};

export default ViewItemModal;
