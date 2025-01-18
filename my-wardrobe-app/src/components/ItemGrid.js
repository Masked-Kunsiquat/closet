import React from 'react';

const ItemGrid = ({ items, openViewModal, incrementWearCount }) => (
  <div className="item-grid">
    {items.map((item) => (
      <div className="item-card" key={item.id}>
        <div
          className="item-image"
          onClick={() => openViewModal(item)}
        >
          {item.image ? (
            <img src={item.image} alt={item.name} />
          ) : (
            <div style={{ backgroundColor: item.color, height: '100px' }} />
          )}
        </div>
        <div className="item-details">
          <p className="item-brand">{item.brand}</p>
          <p className="item-date">{item.date}</p>
          {item.cost && <p className="item-cost">${item.cost}</p>}
          <p className="item-wears">Wears: {item.wearCount || 0}</p>
        </div>
        <button
          className="wear-button"
          onClick={() => incrementWearCount(item.id)}
        >
          + Wear
        </button>
      </div>
    ))}
  </div>
);

export default ItemGrid;
