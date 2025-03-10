import express from "express";
import prisma from "../prismaClient.js";
import { AppError } from "../middleware/errorHandler.js"; // ✅ Import AppError

const router = express.Router();

/**
 * ✅ Get all categories
 */
router.get("/", async (req, res, next) => {
  try {
    const categories = await prisma.category.findMany();
    res.json(categories);
  } catch (error) {
    next(new AppError("Failed to fetch categories", 500)); // ✅ Pass error to global handler
  }
});

export default router;
