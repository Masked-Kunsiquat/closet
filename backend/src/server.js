import express from "express";
import cors from "cors";
import { PrismaClient } from "@prisma/client";
import clothingRoutes from "./routes/clothes.js";
import categoryRoutes from "./routes/categories.js"; // ✅ Import categories route

const app = express();
const prisma = new PrismaClient();

app.use(cors());
app.use(express.json());

// ✅ Register API routes
app.use("/clothing_items", clothingRoutes);
app.use("/categories", categoryRoutes); // ✅ Add categories endpoint

app.get("/", (req, res) => res.send("🔥 API is running"));

// Start the server
app.listen(3000, () => console.log("🚀 Server running on port 3000"));
