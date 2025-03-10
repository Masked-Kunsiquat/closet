import express from "express";
import prisma from "../prismaClient.js"; // ✅ Use shared Prisma instance
import { z } from "zod";
import { AppError } from "../middleware/errorHandler.js"; // ✅ Import error handler

const router = express.Router();

/**
 * 🛠️ Zod Schema for Request Body Validation
 */
const clothingItemSchema = z.object({
  name: z.string().min(1, "Name is required"),
  size: z.string().min(1, "Size is required"),
  categoryId: z.string().optional(),
  brandId: z.string().optional(),
  price: z.number().positive("Price must be a positive number"),
  purchaseDate: z.string().optional().nullable(),
  imageUrl: z.string().url("Invalid URL format").optional().nullable(),
});

/**
 * ✅ Get all clothing items (optionally filter by category or brand)
 */
router.get("/", async (req, res, next) => {
  try {
    const { categoryId, brandId } = req.query;

    console.log(`🔍 Fetching items with filters: categoryId=${categoryId}, brandId=${brandId}`);

    const clothes = await prisma.clothingItem.findMany({
      where: {
        categoryId: categoryId ? String(categoryId) : undefined,
        brandId: brandId ? String(brandId) : undefined,
      },
      include: {
        category: true,
        brand: true,
      },
    });

    res.json(clothes);
  } catch (error) {
    next(new AppError("Failed to fetch clothing items", 500)); // ✅ Pass to error handler
  }
});

/**
 * ✅ Get a single clothing item by ID
 */
router.get("/:id", async (req, res, next) => {
  try {
    const { id } = req.params;

    const item = await prisma.clothingItem.findUnique({
      where: { id },
      include: {
        category: true,
        brand: true,
      },
    });

    if (!item) {
      return next(new AppError("Clothing item not found", 404)); // ✅ Return structured error
    }

    res.json(item);
  } catch (error) {
    next(new AppError("Failed to fetch clothing item", 500)); // ✅ Centralized error handling
  }
});

/**
 * ✅ Create a new clothing item with validation
 */
router.post("/", async (req, res, next) => {
  try {
    console.log("📥 Received data:", req.body);

    // Validate request body
    const validatedData = clothingItemSchema.safeParse(req.body);
    if (!validatedData.success) {
      return next(new AppError("Invalid request data", 400)); // ✅ Standardized validation error
    }

    const { name, size, categoryId, brandId, price, purchaseDate, imageUrl } = validatedData.data;

    // Create new clothing item
    const newItem = await prisma.clothingItem.create({
      data: {
        name,
        size,
        categoryId: categoryId ?? null,
        brandId: brandId ?? null,
        price,
        purchaseDate: purchaseDate ? new Date(purchaseDate) : null,
        imageUrl,
      },
    });

    console.log("✅ Created item:", newItem);
    res.status(201).json(newItem);
  } catch (error) {
    next(new AppError("Failed to create clothing item", 500)); // ✅ Consistent error handling
  }
});

export default router;
