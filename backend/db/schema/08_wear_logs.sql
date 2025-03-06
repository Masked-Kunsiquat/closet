CREATE TABLE wear_logs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    outfit_id UUID NOT NULL REFERENCES outfits(id) ON DELETE CASCADE,
    worn_date TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT now()
);