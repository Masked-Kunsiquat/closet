import express from "express";
import cors from "cors";
import dotenv from "dotenv";
import clothingRoutes from "./routes/clothes.js";
import categoryRoutes from "./routes/categories.js";
import prisma from "./prismaClient.js";

prisma.$connect()
  .then(() => console.log("✅ Database connected successfully"))
  .catch((error) => console.error("❌ Database connection failed:", error));

// Load environment variables
dotenv.config();

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
