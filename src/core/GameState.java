package core;

import java.awt.Point;
import java.io.Serializable;
import java.util.List;

public class GameState implements Serializable {
    private static final long serialVersionUID = 1L;

    private final long seed;
    private final Point avatarPosition;
    private final List<Point> collectedCoins;

    public GameState(long seed, Point avatarPosition, List<Point> collectedCoins) {
        this.seed = seed;
        this.avatarPosition = avatarPosition;
        this.collectedCoins = collectedCoins;
    }

    public long getSeed() {
        return seed;
    }

    public Point getAvatarPosition() {
        return avatarPosition;
    }

    public List<Point> getCollectedCoins() {
        return collectedCoins;
    }
}
