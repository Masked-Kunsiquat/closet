import express from "express";
import cors from "cors";
import { PrismaClient } from "@prisma/client";
import clothingRoutes from "./routes/clothes.js";

const app = express();
const prisma = new PrismaClient();

app.use(cors());
app.use(express.json());

// ✅ Register the clothing API routes
app.use("/clothing_items", clothingRoutes);

app.get("/", (req, res) => res.send("🔥 API is running"));

// Start the server
app.listen(3000, () => console.log("🚀 Server running on port 3000"));
