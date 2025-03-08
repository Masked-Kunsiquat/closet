import express from "express";
import { PrismaClient } from "@prisma/client";

const router = express.Router();
const prisma = new PrismaClient();

/**
 * ✅ Get all clothing items (optionally filter by category or brand)
 */
router.get("/", async (req, res) => {
  try {
    const { categoryId, brandId } = req.query;

    const clothes = await prisma.clothingItem.findMany({
      where: {
        categoryId: categoryId ? categoryId : undefined,
        brandId: brandId ? brandId : undefined,
      },
      include: {
        category: true, // Fetch category details
        brand: true, // Fetch brand details
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
 * ✅ Create a new clothing item
 */
router.post("/", async (req, res) => {
  try {
    console.log("📥 Received data:", req.body); // ✅ Log incoming request body

    const { name, size, categoryId, brandId, price, purchaseDate, imageUrl } = req.body;

    const newItem = await prisma.clothingItem.create({
      data: {
        name,
        size,
        categoryId: categoryId ? categoryId : null, // ✅ Ensure Prisma handles null properly
        brandId: brandId ? brandId : null, // ✅ Ensure Prisma handles null properly
        price,
        purchaseDate: purchaseDate ? new Date(purchaseDate) : null,
        imageUrl,
      },
    });

    console.log("✅ Created item:", newItem); // ✅ Log successful creation
    res.status(201).json(newItem);
  } catch (error) {
    console.error("❌ Error creating clothing item:", error); // ✅ Log full error details
    res.status(500).json({ error: error.message || "Failed to create clothing item" });
  }
});

export default router;
