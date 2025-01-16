# Storage
We'll use `IndexedDB` for localb storage. Here's the schema:
```json
{
    "id": "unique_id",
    "name": "T-shirt",
    "category": "blue",
    "price": 20.00,
    "photo": "base64_string"
}
```

# Tech Stack
- Fronted: React with Vite (for fast builds) or Create React App
- Styling: Tailwind CSS for minimalist design
- Offiline Storage: IndexedDB via Dexie.js
- PWA Tools:
    - Service Workers: offline caching
    - `manifest.json`: installability