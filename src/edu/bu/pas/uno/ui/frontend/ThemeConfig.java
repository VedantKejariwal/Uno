package edu.bu.pas.uno.ui.frontend;

import java.awt.BasicStroke;
import java.awt.Color;

/**
 * Central place for UNO spectator UI colors, sizes, and fonts.
 * <p>
 * Keeping values here means future visual tweaks do not require hunting through {@code UnoPanel}
 * and related classes.
 */
public final class ThemeConfig {

    private ThemeConfig() {
    }

    // ── BOARD & SCALE ──
    /** Internal raster width for the game board (logical pixels before window scaling). */
    public static final int BOARD_W = 1280;
    /** Internal raster height for the game board. */
    public static final int BOARD_H = 720;
    /** Integer scale applied when painting the buffer into the Swing window. */
    public static final int SCALE = 4;
    /** Width of one card face in board pixels. */
    public static final int CARD_W = 36;
    /** Height of one card face in board pixels. */
    public static final int CARD_H = 60;

    /** Preferred Swing panel size (window client area hint). */
    public static final int PANEL_PREF_W = 1920;
    public static final int PANEL_PREF_H = 1080;

    // ── LAYOUT (1280×720 coordinates) ──
    public static final int MARGIN = 32;
    public static final int TOP_HAND_Y = 60;
    public static final int BOTTOM_HAND_Y = 546;
    public static final int SIDE_HAND_X_LEFT = 32;
    /** Left edge of the right vertical hand column. */
    public static final int SIDE_HAND_X_RIGHT = BOARD_W - MARGIN - CARD_W;
    public static final int HORIZONTAL_HAND_AVAILABLE_WIDTH = 1008;
    public static final int VERTICAL_HAND_AVAILABLE_HEIGHT = 396;

    public static final int DISCARD_X = 560;
    public static final int DISCARD_Y = 303;
    public static final int DRAW_PILE_X = 648;
    public static final int DRAW_PILE_Y = 303;

    /** Anchor used for the active color indicator (center of felt spotlight). */
    public static final int COLOR_INDICATOR_CX = DISCARD_X - 64;
    public static final int COLOR_INDICATOR_CY = DISCARD_Y + 30;

    /** Diameter of the large active-color circle (PRD). */
    public static final int ACTIVE_COLOR_DIAMETER = 40;

    // ── TYPOGRAPHY ──
    public static final float FONT_SMALL = 22.0f;
    public static final float FONT_MEDIUM = 28.0f;
    public static final float FONT_LABEL = 18.0f;
    /** Used for win banner and other large announcements. */
    public static final float FONT_LARGE = 36.0f;

    // ── TABLE (felt) ──
    public static final Color FELT_BASE = new Color(20, 80, 40);
    public static final Color FELT_SPOTLIGHT = new Color(30, 100, 55);
    public static final Color TABLE_BORDER_GOLD = new Color(180, 150, 50);
    public static final float TABLE_BORDER_STROKE = 3.0f;

    // ── TURN HIGHLIGHT (cyan glow) ──
    public static final Color TURN_GLOW = new Color(0, 220, 255);
    public static final int TURN_GLOW_LAYERS = 5;

    // ── DANGER (low card count) ──
    public static final Color DANGER_GLOW = new Color(255, 50, 50);
    public static final int DANGER_GLOW_LAYERS = 4;

    // ── VIBRANT CARD COLORS (non-colorblind toAwt mapping) ──
    public static final Color CARD_RED = new Color(220, 30, 30);
    public static final Color CARD_BLUE = new Color(20, 80, 200);
    public static final Color CARD_GREEN = new Color(20, 160, 50);
    public static final Color CARD_YELLOW = new Color(230, 200, 0);

    // ── PROGRAMMATIC CARD FACES (CardRenderer) ──
    public static final Color RENDER_FACE_RED = new Color(220, 50, 50);
    public static final Color RENDER_FACE_BLUE = new Color(50, 100, 200);
    public static final Color RENDER_FACE_GREEN = new Color(50, 160, 70);
    public static final Color RENDER_FACE_YELLOW = new Color(220, 190, 30);
    public static final Color RENDER_WILD_BG = new Color(35, 35, 55);

    // ── METRICS PANEL ──
    public static final int METRICS_PANEL_X = 1020;
    public static final int METRICS_PANEL_W = BOARD_W - METRICS_PANEL_X - 12;
    public static final int TICKER_X = 12;
    public static final int TICKER_W = 200;

    public static BasicStroke dashedRingStroke(float phase) {
        float[] dash = {6.0f, 6.0f};
        return new BasicStroke(2.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, dash, phase);
    }
}
