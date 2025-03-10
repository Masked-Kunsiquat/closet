// ✅ Error Handling Types
export type ErrorMode = "alert" | "toast";

export interface ErrorItem {
  id: number;
  message: string;
  mode: ErrorMode;
}

export interface ErrorDisplayProps {
  errors: ErrorItem[];
  onDismiss: (id: number) => void;
}

export interface ErrorBoundaryState {
  hasError: boolean;
  errorMessage: string;
}

// ✅ UI Component Props
export interface SidebarProps {
  onCategorySelect: (categoryId: string | null) => void;
}

export interface ClothesProps {
  categoryId: string | null;
}

// ✅ Data Models
export interface Category {
  id: string;
  name: string;
}

export interface ClothingItem {
  id: string;
  name: string;
  imageUrl?: string;
  price?: number;
  size?: string;
  categoryId: string | null;
  purchaseDate?: string | null;
}
