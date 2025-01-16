import { openDB } from 'idb';

const DB_NAME = 'closet-pwa';
const STORE_NAME = 'items';

export const initDB = async () => {
    const db = await openDB(DB_NAME, 1, {
        upgrade(db) {
            if (!db.objectStoreNames.contains(STORE_NAME)) {
                db.createObjectStore(STORE_NAME, { keyPath: 'id' });
            }
        },
    });
    return db;
};

export const addItem = async (item) => {
    const db = await initDB();
    await db.add(STORE_NAME, item);
};

export const getItems = async () => {
    const db = await initDB();
    return await db.getAll(STORE_NAME);
};

export const deleteItem = async (id) => {
    const db = await initDB();
    await db.delete(STORE_NAME, id);
};
