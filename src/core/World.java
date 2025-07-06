package core;

import java.awt.*;
import java.util.*;
import java.util.List;

import tileengine.TETile;
import tileengine.Tileset;
import utils.RandomUtils;

public class World {
    private final int width, height;
    private final long seed;
    private final Random random;
    private final TETile[][] world;
    private final List<Room> rooms;
    private final List<Hallway> hallways;

    private static final int MIN_ROOM_W = 6;
    private static final int MAX_ROOM_W = 14;
    private static final int MIN_ROOM_H = 6;
    private static final int MAX_ROOM_H = 12;
    private static final int TARGET_ROOMS = 8;
    private static final int MAX_ATTEMPTS = 1000;

    private static final double MIN_FILL_RATIO = 0.7;
    private static final int MAX_EXTRA_ROOMS = 100;

    private static final int COIN_COUNT = 10;
    private int placedCoins = 0;
    private static final int LOS_RADIUS = 8;
    private boolean[][] visible;

    public World(int width, int height, long seed) {
        this.width = width;
        this.height = height;
        this.seed = seed;
        this.random = new Random(seed);
        this.world = new TETile[width][height];
        this.rooms = new ArrayList<>();
        this.hallways = new ArrayList<>();
        visible = new boolean[width][height];
        initializeWorld();
    }

    private void initializeWorld() {
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                world[x][y] = Tileset.NOTHING;
            }
        }
    }

    public TETile[][] generate() {
        placeRooms();
        connectRoomsWithMST();
        int extra = 0;
        while (calculateFillRatio() < MIN_FILL_RATIO && extra < MAX_EXTRA_ROOMS) {
            boolean added = addAdditionalRoom();
            if (!added) {
                break;  // no space for more rooms
            }
            extra++;
        }
        drawWalls();
        placeCoins();
        return world;
    }

    private void placeCoins() {
        List<Point> floors = new ArrayList<>();
        for (Room r : rooms) {
            floors.addAll(r.floorTiles());
        }
        for (Hallway h : hallways) {
            floors.addAll(h.getPath());
        }
        Collections.shuffle(floors, random);
        int count = Math.min(COIN_COUNT, floors.size());
        for (int i = 0; i < count; i++) {
            Point p = floors.get(i);
            world[p.getX()][p.getY()] = Tileset.COIN;
            placedCoins++;
        }
    }

    public int getCoinCount() {
        return placedCoins;
    }

    public void updateLineOfSight(int playerX, int playerY) {
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                visible[x][y] = false;
            }
        }

        for (int dx = -LOS_RADIUS; dx <= LOS_RADIUS; dx++) {
            for (int dy = -LOS_RADIUS; dy <= LOS_RADIUS; dy++) {
                int tx = playerX + dx;
                int ty = playerY + dy;
                if (inBounds(tx, ty) && distance(playerX, playerY, tx, ty) <= LOS_RADIUS) {
                    if (hasWallBetween(playerX, playerY, tx, ty)) {
                        continue; // wall blocking view
                    }
                    visible[tx][ty] = true;
                }
            }
        }
    }

    private boolean hasWallBetween(int x0, int y0, int x1, int y1) {
        int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        for (int i = 1; i <= steps; i++) {
            int xi = x0 + i * (x1 - x0) / steps;
            int yi = y0 + i * (y1 - y0) / steps;
            if (inBounds(xi, yi) && isWall(xi, yi)) {
                return true;
            }
        }
        return false;
    }

    private boolean isWall(int x, int y) {
        return world[x][y] == Tileset.WALL;
    }

    private boolean inBounds(int x, int y) {
        return x >= 0 && x < width && y >= 0 && y < height;
    }

    private double distance(int x0, int y0, int x1, int y1) {
        return Math.sqrt((x1 - x0) * (x1 - x0) + (y1 - y0) * (y1 - y0));
    }

    public boolean[][] getVisibility() {
        return visible;
    }

    private void placeRooms() {
        int attempts = 0;
        while (rooms.size() < TARGET_ROOMS && attempts < MAX_ATTEMPTS) {
            // use the helper to generate room candidates
            Room r = createRandomRoom();
            if (r != null) {
                rooms.add(r);
                r.carve(world);
            }
            attempts++;
        }
    }

    private Room createRandomRoom() {
        int rw = RandomUtils.uniform(random, MIN_ROOM_W, MAX_ROOM_W + 1);
        int rh = RandomUtils.uniform(random, MIN_ROOM_H, MAX_ROOM_H + 1);
        int rx = RandomUtils.uniform(random, 1, width - rw - 1);
        int ry = RandomUtils.uniform(random, 1, height - rh - 1);
        Room candidate = new Room(rx, ry, rw, rh);
        for (Room existing : rooms) {
            if (candidate.overlaps(existing)) {
                return null;
            }
        }
        return candidate;
    }

    // Connect all rooms using a Minimum Spanning Tree (Prim's algorithm)
    private void connectRoomsWithMST() {
        if (rooms.isEmpty()) {
            return;
        }
        Set<Room> connected = new HashSet<>();
        Set<Room> remaining = new HashSet<>(rooms);
        // start with first room
        Room start = rooms.get(0);
        connected.add(start);
        remaining.remove(start);

        while (!remaining.isEmpty()) {
            Room bestA = null, bestB = null;
            int bestDist = Integer.MAX_VALUE;
            // Find closest pair (a in connected, b in remaining)
            for (Room a : connected) {
                for (Room b : remaining) {
                    int dist = Math.abs(a.centerX() - b.centerX()) + Math.abs(a.centerY() - b.centerY());
                    if (dist < bestDist) {
                        bestDist = dist;
                        bestA = a;
                        bestB = b;
                    }
                }
            }
            connectPair(bestA, bestB);
            connected.add(bestB);
            remaining.remove(bestB);
        }
    }

    private void connectPair(Room a, Room b) {
        Point p1 = a.randomPoint(random);
        Point p2 = b.randomPoint(random);
        Hallway h = Hallway.buildLShaped(p1, p2, random.nextBoolean());
        hallways.add(h);
        h.carve(world);
    }

    private double calculateFillRatio() {
        int floorCount = 0;
        for (Room r : rooms) {
            floorCount += r.floorTiles().size();
        }
        for (Hallway h : hallways) {
            floorCount += h.getPath().size();
        }
        return (double) floorCount / (width * height);
    }

    // attempts to place and connect one extra room.
    private boolean addAdditionalRoom() {
        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            Room r = createRandomRoom();
            if (r != null) {
                rooms.add(r);
                r.carve(world);
                Room nearest = findNearestRoom(r);
                connectPair(nearest, r);
                return true;
            }
        }
        return false;
    }

    // finds the closest room to target for connecting.
    private Room findNearestRoom(Room target) {
        Room nearest = null;
        int bestDist = Integer.MAX_VALUE;
        for (Room r : rooms) {
            if (r == target) {
                continue;
            }
            int dist = Math.abs(r.centerX() - target.centerX())
                    + Math.abs(r.centerY() - target.centerY());
            if (dist < bestDist) {
                bestDist = dist;
                nearest = r;
            }
        }
        return nearest;
    }

    private void drawWalls() {
        Set<Point> floors = new HashSet<>();
        for (Room r : rooms) {
            floors.addAll(r.floorTiles());
        }
        for (Hallway h : hallways) {
            floors.addAll(h.getPath());
        }
        for (Point p : floors) {
            for (Point n : p.neighbors()) {
                if (inBounds(n) && world[n.getX()][n.getY()] == Tileset.NOTHING) {
                    world[n.getX()][n.getY()] = Tileset.WALL;
                }
            }
        }
    }

    private boolean inBounds(Point p) {
        return p.getX() >= 0 && p.getX() < width && p.getY() >= 0 && p.getY() < height;
    }

    // --- Nested helper classes ---

    private static class Room {
        private final int x;
        private final int y;
        private final int width;
        private final int height;

        Room(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
        boolean overlaps(Room o) {
            return (x - 1 < o.x + o.width + 1 && x + width + 1 > o.x - 1
                    &&
                    y - 1 < o.y + o.height + 1 && y + height + 1 > o.y - 1);
        }
        List<Point> floorTiles() {
            List<Point> list = new ArrayList<>();
            for (int i = x + 1; i < x + width - 1; i++) {
                for (int j = y + 1; j < y + height - 1; j++) {
                    list.add(new Point(i, j));
                }
            }
            return list;
        }
        Point randomPoint(Random rand) {
            int px = rand.nextInt(width - 2) + x + 1;
            int py = rand.nextInt(height - 2) + y + 1;
            return new Point(px, py);
        }
        void carve(TETile[][] world) {
            for (Point p : floorTiles()) {
                world[p.getX()][p.getY()] = Tileset.FLOOR;
            }
        }

        int centerX() {
            return x + width / 2;
        }

        int centerY() {
            return y + height / 2;
        }

    }

    private static class Hallway {
        private final List<Point> path;
        private Hallway(List<Point> path) {
            this.path = path;
        }

        static Hallway buildLShaped(Point p1, Point p2, boolean horizontalFirst) {
            List<Point> pts = new ArrayList<>();
            pts.add(p1);
            if (horizontalFirst) {
                int dx = Integer.signum(p2.getX() - p1.getX());
                int x = p1.getX();
                while (x != p2.getX()) {
                    x += dx;
                    pts.add(new Point(x, p1.getY()));
                }
                int dy = Integer.signum(p2.getY() - p1.getY());
                int y = p1.getY();
                while (y != p2.getY()) {
                    y += dy;
                    pts.add(new Point(p2.getX(), y));
                }
            } else {
                int dy = Integer.signum(p2.getY() - p1.getY());
                int y = p1.getY();
                while (y != p2.getY()) {
                    y += dy;
                    pts.add(new Point(p1.getX(), y));
                }
                int dx = Integer.signum(p2.getX() - p1.getX());
                int x = p1.getX();
                while (x != p2.getX()) {
                    x += dx;
                    pts.add(new Point(x, p2.getY()));
                }
            }
            return new Hallway(pts);
        }
        void carve(TETile[][] world) {
            for (Point p : path) {
                world[p.getX()][p.getY()] = Tileset.FLOOR;
            }
        }
        List<Point> getPath() {
            return path;
        }

    }

    private static class Point {
        private final int x;
        private final int y;
        Point(int x, int y) {
            this.x = x;
            this.y = y;
        }

        int getX() {
            return x;
        }

        int getY() {
            return y;
        }

        List<Point> neighbors() {
            return Arrays.asList(
                    new Point(x + 1, y), new Point(x - 1, y),
                    new Point(x, y + 1), new Point(x, y - 1)
            );
        }
        @Override public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Point)) {
                return false;
            }
            Point p = (Point) o;
            return p.x == x && p.y == y;
        }
        @Override public int hashCode() {
            return Objects.hash(x, y);
        }
    }
}


