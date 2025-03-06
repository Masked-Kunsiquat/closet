CREATE INDEX idx_clothing_user ON clothing_items(user_id);
CREATE INDEX idx_clothing_category ON clothing_items(category_id);
CREATE INDEX idx_clothing_brand ON clothing_items(brand_id);

CREATE INDEX idx_outfits_user ON outfits(user_id);
CREATE INDEX idx_outfits_occasion ON outfits(occasion_id);
CREATE INDEX idx_outfit_items_outfit ON outfit_items(outfit_id);
CREATE INDEX idx_outfit_items_clothing ON outfit_items(clothing_item_id);

CREATE INDEX idx_wear_logs_user ON wear_logs(user_id);
CREATE INDEX idx_wear_logs_outfit ON wear_logs(outfit_id);