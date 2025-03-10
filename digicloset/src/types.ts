// ✅ Defines allowed error display modes
export type ErrorMode = "alert" | "toast";

// ✅ Defines the structure of an error item
export interface ErrorItem {
  id: number;
  message: string;
  mode: ErrorMode;
}

// ✅ Defines props for the ErrorDisplay component
export interface ErrorDisplayProps {
  errors: ErrorItem[];
  onDismiss: (id: number) => void;
}

export interface Category {
    id: string;
    name: string;
  }

export interface SidebarProps {
  onCategorySelect: (categoryId: string | null) => void;
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

export interface ClothesProps {
    categoryId: string | null;
}

export interface ErrorBoundaryState {
  hasError: boolean;
  errorMessage: string;
}
