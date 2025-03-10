import { useState, useEffect } from "react";
import { BrowserRouter as Router, Routes, Route, Link } from "react-router-dom";
import { DarkThemeToggle } from "flowbite-react";
import Home from "./pages/Home.tsx";
import Outfits from "./pages/Outfits";
import Clothes from "./pages/Clothes";
import SidebarNav from "./components/Sidebar";
import ClothingDetail from "./pages/ClothingDetail.tsx";
import ErrorDisplay from "./components/ErrorDisplay";
import { ErrorItem } from "./types";

function App() {
  const [selectedCategory, setSelectedCategory] = useState<string | null>(null);
  const [globalErrors, setGlobalErrors] = useState<ErrorItem[]>([]);

  useEffect(() => {
    const handleGlobalError = (event: ErrorEvent) => {
      console.error("❌ Unhandled error:", event.error);

      const errorMessage =
        import.meta.env.MODE === "development"
          ? event.error?.message || "Something went wrong."
          : "Something went wrong. Please try again.";

      setGlobalErrors((prev) => [
        ...prev,
        { id: Date.now(), message: errorMessage, mode: "alert" },
      ]);
    };

    const handlePromiseRejection = (event: PromiseRejectionEvent) => {
      console.error("❌ Unhandled promise rejection:", event.reason);

      const errorMessage =
        import.meta.env.MODE === "development"
          ? event.reason?.message || "Something went wrong."
          : "Something went wrong. Please try again.";

      setGlobalErrors((prev) => [
        ...prev,
        { id: Date.now(), message: errorMessage, mode: "alert" },
      ]);
    };

    window.addEventListener("error", handleGlobalError);
    window.addEventListener("unhandledrejection", handlePromiseRejection);

    return () => {
      window.removeEventListener("error", handleGlobalError);
      window.removeEventListener("unhandledrejection", handlePromiseRejection);
    };
  }, []);

  const removeGlobalError = (id: number) => {
    setGlobalErrors((prev) => prev.filter((error) => error.id !== id));
  };

  return (
    <Router>
      <div className="flex min-h-screen">
        {/* ✅ Sidebar */}
        <SidebarNav onCategorySelect={setSelectedCategory} />

        {/* ✅ Main Content */}
        <main className="flex flex-col min-h-screen w-full px-4 py-6 dark:bg-gray-800">
          <nav className="flex gap-4 p-4 border-b border-gray-200 dark:border-gray-700 w-full">
            <Link to="/" className="text-lg font-semibold dark:text-white">Home</Link>
            <Link to="/outfits" className="text-lg font-semibold dark:text-white">Outfits</Link>
            <Link to="/clothes" className="text-lg font-semibold dark:text-white">Clothes</Link>
          </nav>

          {/* ✅ Show Global Errors */}
          <ErrorDisplay errors={globalErrors} onDismiss={removeGlobalError} />

          <Routes>
            <Route path="/" element={<Home />} />
            <Route path="/outfits" element={<Outfits />} />
            <Route path="/clothes" element={<Clothes categoryId={selectedCategory} />} />
            <Route path="/clothes/:id" element={<ClothingDetail />} />
          </Routes>

          <DarkThemeToggle />
        </main>
      </div>
    </Router>
  );
}

export default App;
