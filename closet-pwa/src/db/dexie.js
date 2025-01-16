// src/db/dexie.js
import Dexie from 'dexie';

const db = new Dexie('ClosetDB');
db.version(1).stores({
    items: 'id, name, category, color, price, photo'
});

export default db;