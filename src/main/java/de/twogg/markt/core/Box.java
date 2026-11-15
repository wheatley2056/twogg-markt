package de.twogg.markt.core;

import java.util.Locale;

/**
 * Quader in einer Dimension, Grenzen jeweils einschließlich. Wird beim Anlegen normalisiert (min ≤ max).
 * Bewusst ohne Minecraft-Klassen, damit sich der Kern ohne Spiel testen lässt.
 */
public final class Box {
    public String dim = "minecraft:overworld";
    public int minX, minY, minZ, maxX, maxY, maxZ;

    public Box() {}

    public static Box of(String dim, int x1, int y1, int z1, int x2, int y2, int z2) {
        Box b = new Box();
        b.dim = dim;
        b.minX = Math.min(x1, x2);
        b.minY = Math.min(y1, y2);
        b.minZ = Math.min(z1, z2);
        b.maxX = Math.max(x1, x2);
        b.maxY = Math.max(y1, y2);
        b.maxZ = Math.max(z1, z2);
        return b;
    }

    public boolean contains(String dim, int x, int y, int z) {
        return this.dim.equals(dim) && x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    /** Nur X/Z (für „steht im Stand“, egal wie hoch). */
    public boolean containsColumn(String dim, int x, int z) {
        return this.dim.equals(dim) && x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    public boolean intersects(Box o) {
        return dim.equals(o.dim) && minX <= o.maxX && maxX >= o.minX && minY <= o.maxY && maxY >= o.minY
                && minZ <= o.maxZ && maxZ >= o.minZ;
    }

    public int sizeX() { return maxX - minX + 1; }
    public int sizeY() { return maxY - minY + 1; }
    public int sizeZ() { return maxZ - minZ + 1; }

    public long volume() {
        return (long) sizeX() * sizeY() * sizeZ();
    }

    /** Grundfläche in Blöcken. */
    public int area() {
        return sizeX() * sizeZ();
    }

    public double centerX() { return (minX + maxX + 1) / 2.0; }
    public double centerZ() { return (minZ + maxZ + 1) / 2.0; }

    public String size() {
        return sizeX() + " × " + sizeZ() + " (" + sizeY() + " hoch)";
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "%d %d %d → %d %d %d", minX, minY, minZ, maxX, maxY, maxZ);
    }
}
