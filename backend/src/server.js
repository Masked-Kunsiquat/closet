import express from "express";
import { PrismaClient } from "@prisma/client";
import dotenv from "dotenv";
import cors from "cors";

dotenv.config();
const prisma = new PrismaClient();
const app = express();
app.use(cors());
app.use(express.json());

app.get("/", (req, res) => res.send("🔥 API is running"));

app.listen(3000, () => console.log("Server running on port 3000"));
