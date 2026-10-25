package com.cleantv.home;

import static android.view.KeyEvent.KEYCODE_DPAD_DOWN;
import static android.view.KeyEvent.KEYCODE_DPAD_LEFT;
import static android.view.KeyEvent.KEYCODE_DPAD_RIGHT;
import static android.view.KeyEvent.KEYCODE_DPAD_UP;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class NavTest {
    // 5 dock tiles (0-4), then 12 grid tiles in rows of 5: 5-9, 10-14, 15-16
    private static int go(int tile, int key) {
        return Nav.next(tile, 5, 17, 5, key);
    }

    @Test public void upFromDockGoesToTopBar() {
        assertEquals(Nav.TOP_BAR, go(3, KEYCODE_DPAD_UP));
    }

    @Test public void downMovesExactlyOneRow() {
        assertEquals(7, go(2, KEYCODE_DPAD_DOWN));
        assertEquals(12, go(7, KEYCODE_DPAD_DOWN));
        assertEquals(16, go(14, KEYCODE_DPAD_DOWN)); // short last row: its last tile
        assertEquals(16, go(16, KEYCODE_DPAD_DOWN)); // bottom: stays
    }

    @Test public void upMovesExactlyOneRow() {
        assertEquals(7, go(12, KEYCODE_DPAD_UP));
        assertEquals(2, go(7, KEYCODE_DPAD_UP));
    }

    @Test public void leftAndRightStayInTheRow() {
        assertEquals(5, go(5, KEYCODE_DPAD_LEFT));
        assertEquals(9, go(9, KEYCODE_DPAD_RIGHT));
        assertEquals(4, go(4, KEYCODE_DPAD_RIGHT));
        assertEquals(16, go(16, KEYCODE_DPAD_RIGHT));
        assertEquals(11, go(10, KEYCODE_DPAD_RIGHT));
    }

    @Test public void shortDock() {
        // 2 dock tiles, grid of 4 in rows of 5: Up from grid col 4 lands on the last dock tile
        assertEquals(1, Nav.next(6, 2, 6, 5, KEYCODE_DPAD_UP));
        assertEquals(Nav.TOP_BAR, Nav.next(0, 0, 3, 5, KEYCODE_DPAD_UP));
    }
}
