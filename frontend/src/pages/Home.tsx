import { Card } from "flowbite-react";
import { Link } from "react-router-dom";

const sections = [
  { name: "Outfits", path: "/outfits", description: "Plan and manage outfits." },
  { name: "Clothes", path: "/clothes", description: "View and organize your wardrobe." },
  { name: "Calendar/Log", path: "/calendar", description: "Track past outfit logs." },
  { name: "Settings", path: "/settings", description: "Manage your preferences." },
];

const Home = () => {
  return (
    <div className="min-h-screen flex flex-col items-center justify-center bg-gray-100 dark:bg-gray-900 p-6">
      <h1 className="text-3xl font-bold dark:text-white mb-6">My Closet</h1>
      <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-2 lg:grid-cols-4 gap-6">
        {sections.map((section) => (
          <Link to={section.path} key={section.name}>
            <Card className="max-w-xs cursor-pointer hover:shadow-lg transition">
              <h2 className="text-xl font-semibold">{section.name}</h2>
              <p className="text-gray-600 dark:text-gray-300">{section.description}</p>
            </Card>
          </Link>
        ))}
      </div>
    </div>
  );
};

export default Home;
