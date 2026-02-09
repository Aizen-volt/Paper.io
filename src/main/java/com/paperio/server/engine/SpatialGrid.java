package com.paperio.server.engine;

import com.paperio.server.model.Player;
import java.util.ArrayList;
import java.util.List;

public class SpatialGrid {
    private final int cellSize;
    private final int cols;
    private final int rows;
    private final List<Player>[][] grid;

    @SuppressWarnings("unchecked")
    public SpatialGrid(int mapWidth, int mapHeight, int cellSize) {
        this.cellSize = cellSize;
        this.cols = (int) Math.ceil((double) mapWidth / cellSize);
        this.rows = (int) Math.ceil((double) mapHeight / cellSize);
        this.grid = new ArrayList[cols][rows];

        for (int x = 0; x < cols; x++) {
            for (int y = 0; y < rows; y++) {
                grid[x][y] = new ArrayList<>();
            }
        }
    }

    public void clear() {
        for (int x = 0; x < cols; x++) {
            for (int y = 0; y < rows; y++) {
                grid[x][y].clear();
            }
        }
    }

    public void insert(Player p) {
        if (!p.isAlive()) return;
        int col = (int) (p.getX() / cellSize);
        int row = (int) (p.getY() / cellSize);

        col = Math.clamp(col, 0, cols - 1);
        row = Math.clamp(row, 0, rows - 1);

        grid[col][row].add(p);
    }

    public List<Player> getPotentialColliders(Player p) {
        int col = (int) (p.getX() / cellSize);
        int row = (int) (p.getY() / cellSize);
        List<Player> nearby = new ArrayList<>();

        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                int c = col + i;
                int r = row + j;
                if (c >= 0 && c < cols && r >= 0 && r < rows) {
                    nearby.addAll(grid[c][r]);
                }
            }
        }
        return nearby;
    }
}