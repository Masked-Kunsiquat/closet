import { BrowserRouter as Router, Routes, Route, Link } from "react-router-dom";
import { DarkThemeToggle } from "flowbite-react";
import Home from "./pages/Home.tsx";
import Outfits from "./pages/Outfits";
import Clothes from "./pages/Clothes"; // ✅ Import Clothes page

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
        <Link to="/outfits" className="text-lg font-semibold dark:text-white">
          Outfits
        </Link>
        <Link to="/clothes" className="text-lg font-semibold dark:text-white">
          Clothes
        </Link>
      </nav>

      <Routes>
        <Route path="/" element={<Home />} />
        <Route path="/outfits" element={<Outfits />} />
        <Route path="/clothes" element={<Clothes />} /> {/* ✅ Add this */}
      </Routes>

      <DarkThemeToggle />
    </main>
  );
};

export default App;
