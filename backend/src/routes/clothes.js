import express from "express";
import prisma from "../prismaClient.js"; // Use shared Prisma instance
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
