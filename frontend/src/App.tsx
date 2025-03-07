import { BrowserRouter as Router, Routes, Route, Link } from "react-router-dom";
import { DarkThemeToggle } from "flowbite-react";
import Home from "./pages/Home";
import Clothes from "./pages/Clothes";
import ClothingDetail from "./pages/ClothingDetail";
import LoginPage from "./pages/Login";
import SignupPage from "./pages/Signup";
import { AuthProvider, useAuth } from "./AuthContext"; // Import the AuthContext
import ProtectedRoute from "./ProtectedRoute"; // Import ProtectedRoute for protecting routes

function App() {
  return (
    <Router>
      <AuthProvider> {/* Wrap AuthProvider inside Router */}
        <MainContent />
      </AuthProvider>
    </Router>
  );
}

const MainContent = () => {
  const { isAuthenticated, logout } = useAuth(); // Use the AuthContext

  return (
    <main className="flex flex-col min-h-screen items-center justify-center gap-4 dark:bg-gray-800">
      <nav className="flex gap-4 p-4 border-b border-gray-200 dark:border-gray-700">
        <Link to="/" className="text-lg font-semibold dark:text-white">
          Home
        </Link>
        <Link to="/clothes" className="text-lg font-semibold dark:text-white">
          Clothes
        </Link>

        {/* Conditionally render Login/Logout */}
        {isAuthenticated ? (
          <button
            onClick={logout}
            className="text-lg font-semibold dark:text-white"
          >
            Logout
          </button>
        ) : (
          <>
            <Link to="/login" className="text-lg font-semibold dark:text-white">
              Login
            </Link>
            <Link to="/signup" className="text-lg font-semibold dark:text-white">
              Sign Up
            </Link>
          </>
        )}
      </nav>

      <Routes>
        {/* Public Route */}
        <Route path="/" element={<Home />} />

        {/* Protected Route */}
        <Route
          path="/clothes"
          element={<ProtectedRoute element={<Clothes />} />}
        />

        {/* Protected Route for detail page */}
        <Route
          path="/clothes/:id"
          element={<ProtectedRoute element={<ClothingDetail />} />}
        />

        {/* Public Routes */}
        <Route path="/login" element={<LoginPage />} />
        <Route path="/signup" element={<SignupPage />} />
      </Routes>

      <DarkThemeToggle />
    </main>
  );
};

export default App;
