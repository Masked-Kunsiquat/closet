CREATE TABLE wear_logs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    outfit_id UUID NOT NULL REFERENCES outfits(id) ON DELETE CASCADE,
    worn_date TIMESTAMPZ NOT NULL,
    created_at TIMESTAMPZ DEFAULT now()
);