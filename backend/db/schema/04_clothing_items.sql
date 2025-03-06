CREATE TABLE clothing_items (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    category_id UUID NOT NULL REFERENCES categories(id) ON DELETE CASCADE,
    brand_id UUID REFERENCES brands(id) ON DELETE SET NULL,
    name TEXT NOT NULL,
    size TEXT,
    purchase_date TIMESTAMP,
    price REAL,
    image_url TEXT, -- path to image file
    created_at TIMESTAMP DEFAULT now()
);