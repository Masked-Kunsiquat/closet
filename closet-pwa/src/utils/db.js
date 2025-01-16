import { openDB } from 'idb';

const DB_NAME = 'closet-pwa';
const ITEM_STORE = 'items';
const CATEGORY_STORE = 'categories';

export const initDB = async () => {
    const db = await openDB(DB_NAME, 1, {
        upgrade(db) {
            // Create the item store if it doesn't exist
            if (!db.objectStoreNames.contains(ITEM_STORE)) {
                db.createObjectStore(ITEM_STORE, { keyPath: 'id' });
            }

            // Create the category store if it doesn't exist
            if (!db.objectStoreNames.contains(CATEGORY_STORE)) {
                const categoryStore = db.createObjectStore(CATEGORY_STORE, { keyPath: 'id' });
                // Add default categories
                categoryStore.transaction.oncomplete = () => {
                    const transaction = db.transaction(CATEGORY_STORE, 'readwrite');
                    const defaultCategories = [
                        { id: '1', name: 'Tops' },
                        { id: '2', name: 'Bottoms' },
                        { id: '3', name: 'Shoes' },
                        { id: '4', name: 'Accessories' },
                    ];
                    defaultCategories.forEach((category) => transaction.store.add(category));
                };
            }
        },
    });
    return db;
};

// Item methods
export const addItem = async (item) => {
    const db = await initDB();
    await db.add(ITEM_STORE, item);
};

export const getItems = async () => {
    const db = await initDB();
    return await db.getAll(ITEM_STORE);
};

export const deleteItem = async (id) => {
    const db = await initDB();
    await db.delete(ITEM_STORE, id);
};

// Category methods
export const getCategories = async () => {
    const db = await initDB();
    return await db.getAll(CATEGORY_STORE);
};
