package com.cleantv.home;

import android.view.KeyEvent;

/** D-pad rules over the tiles: the dock row (indices 0..dock-1), then grid rows of {@code cols}. */
final class Nav {
    /** Returned when Up leaves the tiles for the top bar. */
    static final int TOP_BAR = -1;

    private Nav() {}

    static int next(int tile, int dock, int count, int cols, int key) {
        boolean inDock = tile < dock;
        int row = inDock ? -1 : (tile - dock) / cols;
        int col = inDock ? tile : (tile - dock) % cols;
        switch (key) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                return col > 0 ? tile - 1 : tile;
            case KeyEvent.KEYCODE_DPAD_RIGHT: {
                boolean sameRow = inDock ? tile + 1 < dock : col + 1 < cols;
                return sameRow && tile + 1 < count ? tile + 1 : tile;
            }
            case KeyEvent.KEYCODE_DPAD_UP:
                if (inDock || (row == 0 && dock == 0)) return TOP_BAR;
                return row == 0 ? Math.min(col, dock - 1) : tile - cols;
            case KeyEvent.KEYCODE_DPAD_DOWN: {
                int firstOfNextRow = inDock ? dock : dock + (row + 1) * cols;
                return firstOfNextRow < count ? Math.min(firstOfNextRow + col, count - 1) : tile;
            }
            default:
                return tile;
        }
    }
}
