import { BrowserRouter as Router, Routes, Route, Link } from "react-router-dom";
import { DarkThemeToggle } from "flowbite-react";
import Home from "./pages/Home";
import Clothes from "./pages/Clothes";
import ClothingDetail from "./pages/ClothingDetail";

function App() {
  return (
    <Router>
      <main className="flex flex-col min-h-screen items-center justify-center gap-4 dark:bg-gray-800">
        <nav className="flex gap-4 p-4 border-b border-gray-200 dark:border-gray-700">
          <Link to="/" className="text-lg font-semibold dark:text-white">
            Home
          </Link>
        </nav>
        
        <Routes>
          <Route path="/" element={<Home />} />
          <Route path="/clothes" element={<Clothes />} />
          <Route path="/clothes/:id" element={<ClothingDetail />} />
        </Routes>

        <DarkThemeToggle />
      </main>
    </Router>
  );
}

export default App;
