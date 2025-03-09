import express from "express";
import prisma from "../prismaClient.js"; // ✅ Use shared Prisma instance
import { z } from "zod";

const router = express.Router();

/**
 * 🛠️ Zod Schema for Request Body Validation
 */
const clothingItemSchema = z.object({
  name: z.string().min(1, "Name is required"),
  size: z.string().min(1, "Size is required"),
  categoryId: z.string().optional(), // Allow null but ensure it's a string if provided
  brandId: z.string().optional(),
  price: z.number().positive("Price must be a positive number"),
  purchaseDate: z.string().optional().nullable(), // Allow null values
  imageUrl: z.string().url("Invalid URL format").optional().nullable(),
});

/**
 * ✅ Get all clothing items (optionally filter by category or brand)
 */
router.get("/", async (req, res) => {
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
    console.error("❌ Error fetching clothing items:", error);
    res.status(500).json({ error: "Failed to fetch clothing items" });
  }
});

/**
 * ✅ Get a single clothing item by ID
 */
router.get("/:id", async (req, res) => {
  try {
    const { id } = req.params;

    const item = await prisma.clothingItem.findUnique({
      where: { id },
      include: {
        category: true,
        brand: true,
      },
    });

    if (!item) return res.status(404).json({ error: "Item not found" });

    res.json(item);
  } catch (error) {
    console.error("❌ Error fetching clothing item:", error);
    res.status(500).json({ error: "Failed to fetch clothing item" });
  }
});

/**
 * ✅ Create a new clothing item with validation
 */
router.post("/", async (req, res) => {
  try {
    console.log("📥 Received data:", req.body);

    // Validate request body
    const validatedData = clothingItemSchema.safeParse(req.body);
    if (!validatedData.success) {
      return res.status(400).json({
        error: "Invalid request data",
        details: validatedData.error.errors,
      });
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
    console.error("❌ Error creating clothing item:", error);
    res.status(500).json({ error: "Failed to create clothing item" });
  }
});

export default router;
