export type ClothingItem = {
    id: string;
    name: string;
    size: string;
    price: number;
    category_id?: string;
    category_name?: string;
    brand_name?: string;
    purchase_date?: string;
    image_url?: string;
    brand_id?: string;
    category?: { name: string };
    description?: string;
    user_id: string;
  };

export type Category = {
    id: string;
    name: string;
  };

  export type UserAuthResponse = {
    success: boolean;
    message: string;
};

export type UserSessionResponse = string | null; // Returns user ID or null if not authenticated
