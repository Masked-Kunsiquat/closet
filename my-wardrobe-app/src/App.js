import React, { useState, useEffect } from 'react';
import CategoryFilter from './components/CategoryFilter';
import ItemGrid from './components/ItemGrid';
import AddItemModal from './components/AddItemModal';
import ViewItemModal from './components/ViewItemModal';
import EditItemModal from './components/EditItemModal';
import {
  addItemToDB,
  getAllItemsFromDB,
  updateItemInDB,
} from './utils/idb';
import './App.css';

function App() {
  const [items, setItems] = useState([]);
  const [showAddModal, setShowAddModal] = useState(false);
  const [selectedCategory, setSelectedCategory] = useState('All');
  const [selectedItem, setSelectedItem] = useState(null);
  const [isEditModalOpen, setIsEditModalOpen] = useState(false);

  const predefinedCategories = ['Shirts', 'Pants', 'Shoes', 'Accessories'];

  // Fetch items from IndexedDB when the app loads
  useEffect(() => {
    const fetchItems = async () => {
      try {
        const savedItems = await getAllItemsFromDB();
        setItems(savedItems);
      } catch (error) {
        console.error('Error fetching items:', error);
      }
    };
    fetchItems();
  }, []);

  // Add an item
  const addItem = async (newItem) => {
    try {
      await addItemToDB(newItem);
      setItems((prev) => [...prev, newItem]);
    } catch (error) {
      console.error('Error adding item:', error);
    }
  };

  // Increment wear count
  const incrementWearCount = async (id) => {
    const updatedItems = items.map((item) => {
      if (item.id === id) {
        const updatedItem = { ...item, wearCount: (item.wearCount || 0) + 1 };
        updateItemInDB(updatedItem); // Save to IndexedDB
        return updatedItem;
      }
      return item;
    });
    setItems(updatedItems);
  };

  // Update an item
  const updateItem = async (updatedItem) => {
    try {
      await updateItemInDB(updatedItem);
      setItems((prev) =>
        prev.map((item) => (item.id === updatedItem.id ? updatedItem : item))
      );
    } catch (error) {
      console.error('Error updating item:', error);
    }
  };

  // Filter items by category
  const filteredItems = items.filter(
    (item) => selectedCategory === 'All' || item.category === selectedCategory
  );

  // Modal handlers
  const openViewModal = (item) => setSelectedItem(item);
  const closeViewModal = () => setSelectedItem(null);

  const openEditModal = (item) => {
    setSelectedItem(item);
    setIsEditModalOpen(true);
  };
  const closeEditModal = () => setIsEditModalOpen(false);

  return (
    <div className="App">
      <CategoryFilter
        selectedCategory={selectedCategory}
        setSelectedCategory={setSelectedCategory}
      />
      <ItemGrid
        items={filteredItems}
        openViewModal={openViewModal}
        incrementWearCount={incrementWearCount} // Pass the function here
      />
      <button className="fab" onClick={() => setShowAddModal(true)}>+</button>

      {/* Add Item Modal */}
      {showAddModal && (
        <AddItemModal
          closeModal={() => setShowAddModal(false)}
          addItem={addItem}
          predefinedCategories={predefinedCategories}
        />
      )}

      {/* View Item Modal */}
      {selectedItem && (
        <ViewItemModal
          item={selectedItem}
          closeModal={closeViewModal}
          openEditModal={openEditModal}
          incrementWearCount={incrementWearCount}
        />
      )}

      {/* Edit Item Modal */}
      {isEditModalOpen && (
        <EditItemModal
          item={selectedItem}
          closeModal={closeEditModal}
          saveItem={updateItem}
          predefinedCategories={predefinedCategories}
        />
      )}
    </div>
  );
}

export default App;
