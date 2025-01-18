import { openDB } from 'idb';

const DB_NAME = 'wardrobeDB';
const STORE_NAME = 'items';

let dbPromise = null;

// Initialize the database
export async function initDB() {
  if (!dbPromise) {
    dbPromise = openDB(DB_NAME, 2, {
      upgrade(db, oldVersion) {
        console.log('Upgrading DB:', { oldVersion });

        // Create object store if it doesn't exist
        if (!db.objectStoreNames.contains(STORE_NAME)) {
          const store = db.createObjectStore(STORE_NAME, { keyPath: 'id', autoIncrement: true });
          store.createIndex('cost', 'cost', { unique: false });
          store.createIndex('wearCount', 'wearCount', { unique: false });
          store.createIndex('image', 'image', { unique: false });
        } else if (oldVersion < 2) {
          // If the store already exists, add indexes
          const store = db.transaction(STORE_NAME, 'versionchange').objectStore(STORE_NAME);
          if (!store.indexNames.contains('cost')) {
            store.createIndex('cost', 'cost', { unique: false });
          }
          if (!store.indexNames.contains('wearCount')) {
            store.createIndex('wearCount', 'wearCount', { unique: false });
          }
          if (!store.indexNames.contains('image')) {
            store.createIndex('image', 'image', { unique: false });
          }
        }
      },
      blocked() {
        console.error('Database opening blocked. Close other tabs or processes.');
      },
      blocking() {
        console.warn('Database upgrade blocking other connections. Reload the page.');
      },
    });
  }
  return dbPromise;
}

// Add a new item
export async function addItemToDB(item) {
  const db = await initDB();
  return db.add(STORE_NAME, item);
}

// Get all items
export async function getAllItemsFromDB() {
  const db = await initDB();
  return db.getAll(STORE_NAME);
}

// Update an item
export async function updateItemInDB(item) {
  const db = await initDB();
  return db.put(STORE_NAME, item);
}

// Delete all items (optional utility)
export async function deleteAllItemsFromDB() {
  const db = await initDB();
  return db.clear(STORE_NAME);
}
