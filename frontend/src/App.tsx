import { BrowserRouter as Router, Routes, Route, Link } from "react-router-dom";
import { DarkThemeToggle } from "flowbite-react";
import Home from "./pages/Home";
import Clothes from "./pages/Clothes";
import ClothingDetail from "./pages/ClothingDetail";
import LoginPage from "./pages/Login";
import SignupPage from "./pages/Signup";
import Outfits from "./pages/Outfits"; // ✅ Import Outfits List Page
import OutfitDetail from "./pages/OutfitDetail"; // ✅ Import Outfit Detail Page
import NewOutfit from "./pages/NewOutfit"; // ✅ Import Create Outfit Page

function App() {
  return (
    <Router>
      <MainContent />
    </Router>
  );
}

const MainContent = () => {
  return (
    <main className="flex flex-col min-h-screen items-center justify-center gap-4 dark:bg-gray-800">
      <nav className="flex gap-4 p-4 border-b border-gray-200 dark:border-gray-700">
        <Link to="/" className="text-lg font-semibold dark:text-white">
          Home
        </Link>
        <Link to="/clothes" className="text-lg font-semibold dark:text-white">
          Clothes
        </Link>
        <Link to="/outfits" className="text-lg font-semibold dark:text-white">
          Outfits
        </Link> {/* ✅ Add Outfits link to navbar */}
      </nav>

      <Routes>
        {/* Public Routes (All Unprotected) */}
        <Route path="/" element={<Home />} />
        <Route path="/clothes" element={<Clothes />} />
        <Route path="/clothes/:id" element={<ClothingDetail />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/signup" element={<SignupPage />} />

        {/* ✅ New Outfits Routes */}
        <Route path="/outfits" element={<Outfits />} />
        <Route path="/outfits/new" element={<NewOutfit />} />
        <Route path="/outfits/:id" element={<OutfitDetail />} />
      </Routes>

      <DarkThemeToggle />
    </main>
  );
};

export default App;
