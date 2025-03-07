import { Navigate, RouteProps } from "react-router-dom";
import { useAuth } from "./AuthContext"; // Import the AuthContext

// ProtectedRoute component
const ProtectedRoute: React.FC<RouteProps> = ({ element }) => {
  const { isAuthenticated } = useAuth(); // Check if the user is authenticated

  if (!isAuthenticated) {
    // Redirect to login if not authenticated
    return <Navigate to="/login" />;
  }

  // Render the element if authenticated
  return <>{element}</>;
};

export default ProtectedRoute;
