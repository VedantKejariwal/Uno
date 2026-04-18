package edu.bu.pas.uno.ui.frontend;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;

/**
 * Layout, typography, and color constants for the UNO spectator UI (PRD v3).
 */
public final class ThemeConfig {

    private ThemeConfig() {
    }

    // ── BOARD & REGIONS ──
    public static final int BOARD_W = 1280;
    public static final int BOARD_H = 800;
    public static final int TICKER_H = 24;
    public static final int BOTTOM_STRIP_H = 80;
    public static final int PLAY_AREA_H = BOARD_H - TICKER_H - BOTTOM_STRIP_H;

    /** Card height as a fraction of play-area height — adjust this single value to rescale cards. */
    public static final double CARD_SCALE = 0.132;
    public static final int CARD_H = (int) (PLAY_AREA_H * CARD_SCALE);
    /** Standard-ish UNO portrait aspect (taller than wide). */
    public static final int CARD_W = (int) (CARD_H * 0.67);

    /**
     * At {@code 1} card bitmaps match {@link #CARD_W}×{@link #CARD_H} for fast 1:1 blits. Use {@code 2}
     * to bake supersampled art (nicer when upscaled, more GPU/CPU per frame when drawn scaled down).
     */
    public static final int CARD_TEXTURE_SCALE = 1;

    public static final int PANEL_PREF_W = 1920;
    public static final int PANEL_PREF_H = 1080;

    public static final int MARGIN = 24;
    public static final int TOP_HAND_Y = 40;
    /** Bottom seat: inside play field, room for label above hand. */
    public static final int BOTTOM_HAND_Y = PLAY_AREA_H - CARD_H - 24 - 28;
    public static final int SIDE_HAND_X_LEFT = MARGIN;
    public static final int SIDE_HAND_X_RIGHT = BOARD_W - MARGIN - CARD_W;
    public static final int HORIZONTAL_HAND_AVAILABLE_WIDTH = 1008;
    public static final int VERTICAL_HAND_AVAILABLE_HEIGHT = PLAY_AREA_H - TOP_HAND_Y - CARD_H - 80;

    /** Center of the play field (hands and piles). */
    public static final int CENTER_X = BOARD_W / 2;
    public static final int CENTER_Y = PLAY_AREA_H / 2;

    /** Discard pile — left of center. */
    public static final int DISCARD_X = CENTER_X - CARD_W - 20;
    public static final int DISCARD_Y = CENTER_Y - CARD_H / 2;

    /** Draw pile — right of center. */
    public static final int DRAW_X = CENTER_X + 20;
    public static final int DRAW_Y = CENTER_Y - CARD_H / 2;

    /** Wild color hint — above the two piles. */
    public static final int COLOR_INDICATOR_X = CENTER_X;
    public static final int COLOR_INDICATOR_Y = CENTER_Y - CARD_H / 2 - 40;

    /** Penalty stack text sits under the discard face. */
    public static final int PENALTY_Y = DISCARD_Y + CARD_H + 12;

    public static final int HAND_OVERLAP_H_MAX = 42;
    public static final int HAND_OVERLAP_V_MAX = 72;

    // ── TYPOGRAPHY (PRD v3) ──
    public static final Font UI_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 16);
    public static final Font UI_FONT_BOLD = new Font(Font.SANS_SERIF, Font.BOLD, 18);
    public static final Font UI_FONT_LARGE = new Font(Font.SANS_SERIF, Font.BOLD, 28);
    public static final Font UI_FONT_SMALL = new Font(Font.SANS_SERIF, Font.PLAIN, 13);

    public static final float FONT_SMALL = 20.0f;
    public static final float FONT_MEDIUM = 26.0f;
    public static final float FONT_LABEL = 14.0f;
    public static final float FONT_LARGE = 40.0f;

    // ── TABLE (felt + frame) ──
    /** Uniform table cloth (no gradient — avoids banding / circular shading). */
    public static final Color FELT_PLAIN = new Color(14, 72, 38);
    public static final Color FELT_BORDER = new Color(160, 130, 40, 180);

    public static final Color STRIP_BG = new Color(8, 8, 8, 220);
    public static final Color STRIP_BORDER = new Color(160, 130, 40);
    public static final Color STRIP_DIVIDER = new Color(60, 60, 60);

    public static final Color TICKER_BG = new Color(0, 0, 0, 160);
    public static final Color TICKER_BORDER = new Color(160, 130, 40);
    public static final Color TICKER_TEXT = new Color(210, 210, 210);

    public static final Color LABEL_BG = new Color(0, 0, 0, 140);
    public static final Color LABEL_TEXT = Color.WHITE;

    public static final Color TURN_GLOW = new Color(0, 200, 255);
    public static final int TURN_GLOW_LAYERS = 5;
    public static final Color DANGER_GLOW = new Color(220, 40, 40);
    public static final int DANGER_GLOW_LAYERS = 4;

    public static final Color UNO_TEXT = new Color(220, 40, 40);

    // ── Card-face palette (for indicators; sprites carry actual art) ──
    public static final Color CARD_RED = new Color(227, 37, 36);
    public static final Color CARD_BLUE = new Color(18, 97, 197);
    public static final Color CARD_GREEN = new Color(62, 155, 63);
    public static final Color CARD_YELLOW = new Color(255, 213, 0);

    public static final Color SEAT_0 = new Color(80, 160, 255);
    public static final Color SEAT_1 = new Color(255, 140, 60);
    public static final Color SEAT_2 = new Color(100, 220, 100);
    public static final Color SEAT_3 = new Color(200, 100, 220);

    public static final Color[] SEAT_BAR_COLORS = {SEAT_0, SEAT_1, SEAT_2, SEAT_3};

    public static BasicStroke dashedRingStroke(float phase) {
        float[] dash = {6.0f, 6.0f};
        return new BasicStroke(2.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, dash, phase);
    }
}
