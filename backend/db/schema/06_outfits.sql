CREATE TABLE outfits (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    occasion_id UUID REFERENCES occasions(id) ON DELETE SET NULL,
    created_at TIMESTAMP DEFAULT now()
);