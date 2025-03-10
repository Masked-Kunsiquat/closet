import { useState } from "react";
import { ErrorItem, ErrorMode } from "../types";

const useErrorHandler = () => {
  const [errors, setErrors] = useState<ErrorItem[]>([]);

  const handleError = (message: string, mode: ErrorMode = "toast") => {
    const id = Date.now();
    setErrors((prev) => [...prev, { id, message, mode }]);

    if (mode === "toast") {
      setTimeout(() => removeError(id), 5000);
    }
  };

  const removeError = (id: number) => {
    setErrors((prev) => prev.filter((error) => error.id !== id));
  };

  const clearAllErrors = () => {
    setErrors([]);
  };

  return { errors, handleError, removeError, clearAllErrors };
};

export default useErrorHandler;
