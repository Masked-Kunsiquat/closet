import express from "express";
import prisma from "../prismaClient.js"; // Use the shared Prisma instance

const router = express.Router();

/**
 * ✅ Get all categories
 */
router.get("/", async (req, res) => {
  try {
    const categories = await prisma.category.findMany();
    res.json(categories);
  } catch (error) {
    console.error("❌ Error fetching categories:", error);
    res.status(500).json({ error: "Failed to fetch categories" });
  }
});

export default router;
