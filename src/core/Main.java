package core;

import tileengine.TERenderer;
import tileengine.TETile;
import edu.princeton.cs.algs4.StdDraw;
import tileengine.Tileset;
import java.awt.*;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class Main {
    private static final int WIDTH = 80;
    private static final int HEIGHT = 30;
    private static final String SAVE_FILE = "save.txt";

    private static final String SAVE_FILE_PREFIX = "save_slot_";
    private static final int MAX_SLOTS = 3;

    private static boolean losEnabled = false;
    private static int totalCoins = 0;
    private static int coinsCollected = 0;
    private static List<Point> collectedPositions = new ArrayList<>();
    private static int currentSlot = -1;  // -1 means no slot loaded yet

    public static void main(String[] args) {
        showMainMenu();
    }

    private static void showMainMenu() {
        StdDraw.setCanvasSize(WIDTH * 8 * 2, HEIGHT * 8 * 2);
        StdDraw.setXscale(0, WIDTH);
        StdDraw.setYscale(0, HEIGHT);
        StdDraw.clear(StdDraw.BLACK);
        StdDraw.enableDoubleBuffering();
        TERenderer ter = new TERenderer();
        ter.initialize(WIDTH, HEIGHT);

        while (true) {
            StdDraw.clear(StdDraw.BLACK);

            StdDraw.setPenColor(StdDraw.WHITE);
            StdDraw.text((double) WIDTH / 2, (double) (HEIGHT * 2) / 3, "CS61B: BYOW");

            StdDraw.text((double) WIDTH / 2, (double) HEIGHT / 2, "(N) New Game");
            StdDraw.text((double) WIDTH / 2, (double) HEIGHT / 2 - 2, "(L) Load Game");
            StdDraw.text((double) WIDTH / 2, (double) HEIGHT / 2 - 4, "(P) Load from Slot");
            StdDraw.text((double) WIDTH / 2, (double) HEIGHT / 2 - 6, "(Q) Quit");

            StdDraw.show();

            if (StdDraw.hasNextKeyTyped()) {
                char c = Character.toLowerCase(StdDraw.nextKeyTyped());
                if (c == 'n') {
                    long seed = getSeedFromUser();
                    startNewGame(seed);
                    break;
                } else if (c == 'l') {
                    loadGame();
                    return;
                } else if(c == 'p') {
                    loadFromSlotMenu();
                    return;
                }else if (c == 'q') {
                    System.exit(0);
                }
            }
        }
    }

    private static long getSeedFromUser() {
        StringBuilder seedInput = new StringBuilder();

        while (true) {
            StdDraw.clear(StdDraw.BLACK);
            StdDraw.text((double) WIDTH / 2, (double) (HEIGHT * 2) / 3, "Enter Seed, Then Press S to Start: ");
            StdDraw.text((double) WIDTH / 2, (double) HEIGHT / 2, seedInput.toString());
            StdDraw.show();

            if (StdDraw.hasNextKeyTyped()) {
                char c = StdDraw.nextKeyTyped();
                if (Character.isDigit(c)) {
                    seedInput.append(c);
                } else if (c == 's' || c == 'S') {
                    if (seedInput.length() > 0) {
                        return Long.parseLong(seedInput.toString());
                    }
                }
            }
        }
    }

    private static int slotInputMenu() {
        while (true) {
            StdDraw.clear(StdDraw.BLACK);
            StdDraw.setPenColor(StdDraw.WHITE);
            StdDraw.text(WIDTH / 2, HEIGHT / 2, "Select Slot (1-" + MAX_SLOTS + "):");
            StdDraw.show();
            if (StdDraw.hasNextKeyTyped()) {
                char c = StdDraw.nextKeyTyped();
                if (c >= '1' && c <= '0' + MAX_SLOTS) {
                    return c - '0';
                }
            }
        }
    }

    private static GameState loadStateFromSlot(int slot) {
        currentSlot = slot;
        try (ObjectInputStream ois = new ObjectInputStream(
                new FileInputStream(SAVE_FILE_PREFIX + slot + ".dat"))) {
            GameState state = (GameState) ois.readObject();
            System.err.println("[Slot " + slot + "] Loaded seed: " + state.getSeed()
                    + "avatar:" + state.getAvatarPosition() + "coins:" + state.getCollectedCoins().size());
            return state;
        } catch (Exception e) {
            throw new RuntimeException("Cannot load slot " + slot, e);
        }
    }

    private static void loadFromSlotMenu() {
        while (true) {
            StdDraw.clear(StdDraw.BLACK);
            StdDraw.setPenColor(StdDraw.WHITE);
            StdDraw.text(WIDTH / 2, HEIGHT / 2 + 2, "Load From Slot");
            StdDraw.text(WIDTH / 2, HEIGHT / 2, "Press 1, 2, or 3 to load a save slot");
            StdDraw.text(WIDTH / 2, HEIGHT / 2 - 2, "Press B to go back");
            StdDraw.show();

            if (StdDraw.hasNextKeyTyped()) {
                char c = StdDraw.nextKeyTyped();
                if (c >= '1' && c <= '0' + MAX_SLOTS) {
                    int slot = c - '0';
                    try {
                        GameState state = loadStateFromSlot(slot);
                        startLoadedGame(state);
                        return;
                    } catch (Exception e) {
                        System.out.println("Failed to load slot " + slot);
                    }
                } else if (c == 'b' || c == 'B') {
                    return;
                }
            }
        }
    }

    private static void startNewGame(long seed) {
        System.out.println("Using seed: " + seed);
        World worldGen = new World(WIDTH, HEIGHT, seed);
        TETile[][] world = worldGen.generate();
        currentSlot = -1;
        totalCoins = worldGen.getCoinCount();
        coinsCollected = 0;
        collectedPositions = new ArrayList<>();

        TERenderer ter = new TERenderer();
        ter.initialize(WIDTH, HEIGHT);
        ter.renderFrame(world);

        Point avatarPos = findStartingPosition(world);
        playGame(worldGen, world, seed, avatarPos);
    }

    private static void startLoadedGame(GameState state) {
        long seed = state.getSeed();
        World worldGen = new World(WIDTH, HEIGHT, seed);
        TETile[][] world = worldGen.generate();

        collectedPositions = new ArrayList<>(state.getCollectedCoins());
        coinsCollected = collectedPositions.size();
        totalCoins = worldGen.getCoinCount();
        for (Point p : collectedPositions) {
            world[p.x][p.y] = Tileset.FLOOR;
        }

        Point avatarPos = state.getAvatarPosition();

        playGame(worldGen, world, seed, avatarPos);
    }

    private static void playGame(World worldGen, TETile[][] world, long seed, Point avatarPos) {
        TERenderer ter = new TERenderer();
        ter.initialize(WIDTH, HEIGHT);

        while (true) {
            StdDraw.clear(StdDraw.BLACK);

            if (losEnabled) {
                worldGen.updateLineOfSight(avatarPos.x, avatarPos.y);
                boolean[][] visible = worldGen.getVisibility();

                for (int x = 0; x < WIDTH; x++) {
                    for (int y = 0; y < HEIGHT; y++) {
                        if (visible[x][y]) {
                            world[x][y].draw(x, y);
                        } else {
                            Tileset.NOTHING.draw(x, y);
                        }
                    }
                }
            } else {
                for (int x = 0; x < WIDTH; x++) {
                    for (int y = 0; y < HEIGHT; y++) {
                        world[x][y].draw(x, y);
                    }
                }
            }

            StdDraw.setPenColor(StdDraw.RED);
            StdDraw.filledCircle(avatarPos.x + 0.6, avatarPos.y + 0.5, 0.4);

            StdDraw.setPenColor(StdDraw.WHITE);
            StdDraw.textLeft(1, HEIGHT - 1, "Coins: " + coinsCollected + "/" + totalCoins + " LOS:" + (losEnabled ? "ON" : "OFF"));

            StdDraw.show();

            if (StdDraw.hasNextKeyTyped()) {
                char c = Character.toLowerCase(StdDraw.nextKeyTyped());

                if (c == ':') {
                    if (handleColonCommand(world, seed, avatarPos)) {
                        return;
                    }
                } else if (c >= '1' && c <= '0' + MAX_SLOTS) {
                    saveToSlot(seed, avatarPos, c - '0');
                    return;
                } else if (c == 'o') {
                    losEnabled = !losEnabled;
                } else {
                    int newX = avatarPos.x;
                    int newY = avatarPos.y;
                    switch (c) {
                        case 'w': newY++;
                        break;
                        case 'a': newX--;
                        break;
                        case 's': newY--;
                        break;
                        case 'd': newX++;
                        break;
                    }

                    if (newX >= 0 && newX < WIDTH && newY >= 0 && newY < HEIGHT && world[newX][newY] != Tileset.WALL) {
                        if (world[newX][newY] == Tileset.COIN) {
                            coinsCollected++;
                            collectedPositions.add(new Point(newX, newY));
                            world[newX][newY] = Tileset.FLOOR;
                        }
                        world[avatarPos.x][avatarPos.y] = Tileset.FLOOR;
                        avatarPos = new Point(newX, newY);

                        if (coinsCollected >= totalCoins) {
                            showVictoryScreen();
                            return;
                        }
                    }
                }
            }
            StdDraw.pause(30);
        }
    }

    private static void showVictoryScreen() {
        StdDraw.clear(StdDraw.BLACK);
        StdDraw.setPenColor(StdDraw.WHITE);
        StdDraw.text(WIDTH/2, HEIGHT/2 + 2, "VICTORY!");
        StdDraw.text(WIDTH/2, HEIGHT/2, "You collected all " + totalCoins + " coins!");
        StdDraw.text(WIDTH/2, HEIGHT/2 - 2, "Press any key to exit");
        StdDraw.show();

        while (!StdDraw.hasNextKeyTyped()) {
            StdDraw.pause(100);
        }
        StdDraw.nextKeyTyped();
        System.exit(0);
    }

    private static void saveToSlot(long seed, Point avatar, int slot) {
        String filename = SAVE_FILE_PREFIX + slot + ".dat";
        try (ObjectOutputStream oos = new ObjectOutputStream(
                new FileOutputStream(filename))) {
            oos.writeObject(new GameState(seed, avatar, collectedPositions));
            System.out.println("[Slot " + slot + "] Saved seed: " + seed + " avatar=" + avatar + " coins= " + collectedPositions.size());
        } catch (IOException ignored) {}
    }

    private static boolean withinLOS(int x, int y, Point a, int r) {
        int dx = x - a.x;
        int dy = y - a.y;
        return dx*dx + dy*dy <= r*r;
    }

    private static Point findStartingPosition(TETile[][] world) {
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                if (world[x][y] == Tileset.FLOOR) {
                    return new Point(x, y);
                }
            }
        }
        throw new RuntimeException("No floor tile found for avatar");
    }

    private static boolean handleColonCommand(TETile[][] world, long seed, Point avatarPos) {
        while (true) {
            if (StdDraw.hasNextKeyTyped()) {
                char c = Character.toLowerCase(StdDraw.nextKeyTyped());

                if (c == 'q') {
                    saveGame(world, seed, avatarPos);
                    System.exit(0);
                } else if (c >= '1' && c <= '0' + MAX_SLOTS) {
                    int slot = c - '0';
                    saveToSlot(seed, avatarPos, slot);
                    currentSlot = slot;
                    System.out.println("Manually saved to slot " + slot);
                    System.exit(0);
                } else {
                    System.out.println("Invalid input after ':'. Save cancelled.");
                    return false;
                }
            }
        }
    }

    private static int findFirstAvailableSlot() {
        for (int i = 1; i <= MAX_SLOTS; i++) {
            File f = new File(SAVE_FILE_PREFIX + i + ".dat");
            if (!f.exists()) {
                return i;
            }
        }
        return -1;
    }

    private static void saveGame(TETile[][] world, long seed, Point avatarPos) {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(SAVE_FILE))) {
            GameState state = new GameState(seed, avatarPos, collectedPositions);
            oos.writeObject(state);
            System.out.println("Game saved successfully");
        } catch (IOException e) {
            System.err.println("Failed to save game: " + e.getMessage());
        }
        System.exit(0);
    }

    private static void loadGame() {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(SAVE_FILE))) {
            GameState state = (GameState) ois.readObject();
            startLoadedGame(state);
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Failed to load game: " + e.getMessage());
            showMainMenu();
        }
    }

    private static void drawHUD(TETile[][] world) {
        int mouseX = (int) StdDraw.mouseX();
        int mouseY = (int) StdDraw.mouseY();

        if (mouseX >= 0 && mouseX < WIDTH && mouseY >= 0 && mouseY < HEIGHT) {
            String tileDescription = world[mouseX][mouseY].description();

            StdDraw.setPenColor(StdDraw.BLACK);
            StdDraw.filledRectangle(WIDTH / 2, HEIGHT - 1, WIDTH / 2, 1);
            StdDraw.setPenColor(StdDraw.WHITE);

            StdDraw.textLeft(1, HEIGHT - 1, tileDescription);
            StdDraw.show();
        }
    }
}
