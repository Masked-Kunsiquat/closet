export const createItem = (name, category, color, tags = []) => {
    return {
        id: crypto.randomUUID(), // Generates a unique ID
        name,
        category,
        color,
        tags,
    };
};
