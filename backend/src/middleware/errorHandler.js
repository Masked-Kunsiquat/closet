const errorHandler = (err, req, res, next) => {
    console.error("🔥 Backend Error:", err);
  
    // If error has a status code, use it. Otherwise, default to 500.
    const statusCode = err.statusCode || 500;
    const message = err.message || "Internal Server Error";
  
    res.status(statusCode).json({ error: message });
  };
  
  // Custom Error Class for structured errors
  class AppError extends Error {
    constructor(message, statusCode) {
      super(message);
      this.statusCode = statusCode;
    }
  }
  
  export { errorHandler, AppError };
  