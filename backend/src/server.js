import express from "express";
import cors from "cors";
import dotenv from "dotenv";

// ✅ Load environment variables first
dotenv.config();

import prisma from "./prismaClient.js";
import clothingRoutes from "./routes/clothes.js";
import categoryRoutes from "./routes/categories.js";

// ✅ Connect to database only after environment variables are loaded
prisma.$connect()
  .then(() => console.log("✅ Database connected successfully"))
  .catch((error) => console.error("❌ Database connection failed:", error));

const app = express();
const PORT = process.env.PORT || 3000;

app.use(cors());
app.use(express.json());

// ✅ Register API routes
app.use("/clothing_items", clothingRoutes);
app.use("/categories", categoryRoutes);

app.get("/", (req, res) => res.send("🔥 API is running"));

// Start the server
app.listen(PORT, () => console.log(`🚀 Server running on port ${PORT}`));
