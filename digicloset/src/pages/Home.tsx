import { Link } from "react-router-dom";

const Home = () => {
  return (
    <div className="flex flex-col items-center justify-center min-h-screen text-center">
      <h1 className="text-3xl font-bold mb-4">Welcome to Digital Closet 👕👗</h1>
      <p className="text-lg text-gray-600 mb-6">
        Organize your outfits and clothing effortlessly.
      </p>
      <div className="flex gap-4">
        <Link to="/outfits" className="px-4 py-2 bg-blue-600 text-white rounded-lg shadow hover:bg-blue-700">
          View Outfits
        </Link>
        <Link to="/clothes" className="px-4 py-2 bg-gray-600 text-white rounded-lg shadow hover:bg-gray-700">
          Manage Clothes
        </Link>
      </div>
    </div>
  );
};

export default Home;
