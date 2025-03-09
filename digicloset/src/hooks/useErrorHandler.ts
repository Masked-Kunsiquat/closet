import { useState } from "react";

type ErrorMode = "alert" | "toast";

const useErrorHandler = () => {
  const [error, setError] = useState<string | null>(null);
  const [errorMode, setErrorMode] = useState<ErrorMode>("toast");

  const handleError = (message: string, mode: ErrorMode = "toast") => {
    setError(message);
    setErrorMode(mode);

    if (mode === "toast") {
      setTimeout(() => setError(null), 5000); // Auto-dismiss toast after 5s
    }
  };

  const clearError = () => {
    setError(null);
  };

  return { error, errorMode, handleError, clearError };
};

export default useErrorHandler;
