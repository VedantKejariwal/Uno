package edu.bu.pas.uno.ui.frontend;

import edu.bu.pas.uno.Card;
import edu.bu.pas.uno.enums.Color;
import edu.bu.pas.uno.enums.Value;
import java.awt.BasicStroke;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

/**
 * Builds crisp UNO card faces as {@link BufferedImage}s instead of scaling tiny GIF art.
 * <p>
 * Images are cached by {@link Card} identity plus colorblind mode so the UI only pays the draw
 * cost once per distinct card type.
 */
public class CardRenderer {

    private final Font baseFont;
    private final boolean colorblindMode;
    private final Map<String, BufferedImage> cache = new HashMap<>();

    /**
     * @param gameFont loaded RBYGSC.ttf (already created by {@code UnoPanel})
     * @param colorblindMode when true, use higher-contrast face colors for readability
     */
    public CardRenderer(Font gameFont, boolean colorblindMode) {
        this.baseFont = gameFont;
        this.colorblindMode = colorblindMode;
    }

    /**
     * @param card logical card; unknown backs render as deck back art
     * @return cached RGBA image sized {@link ThemeConfig#CARD_W}×{@link ThemeConfig#CARD_H}
     */
    public BufferedImage getCardImage(Card card) {
        String key = cacheKey(card);
        BufferedImage hit = cache.get(key);
        if (hit != null) {
            return hit;
        }
        BufferedImage img = renderNew(card);
        cache.put(key, img);
        return img;
    }

    private String cacheKey(Card card) {
        return card.color() + "_" + card.value() + "_cb" + colorblindMode;
    }

    private BufferedImage renderNew(Card card) {
        int w = ThemeConfig.CARD_W;
        int h = ThemeConfig.CARD_H;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            if (card.color() == Color.UNKNOWN || card.value() == Value.UNKNOWN) {
                // Step: unknown cards (opponent backs or hidden deck entries) become a neutral back.
                drawBackFace(g, w, h);
                return img;
            }
            if (card.value() == Value.WILD || card.value() == Value.WILD_DRAW_FOUR) {
                // Step: wild cards use a dark field plus a four-color quadrant hint.
                drawWildFace(g, w, h, card.value() == Value.WILD_DRAW_FOUR);
                return img;
            }

            // Step: pick solid fill from UNO color; colorblind mode nudges hues for separation.
            java.awt.Color fill = faceColor(card.color());
            int arc = 8;
            // Step: outer rounded rectangle = card body.
            g.setColor(fill);
            g.fill(new RoundRectangle2D.Double(0, 0, w, h, arc, arc));
            // Step: thin white inset frame so the card reads clearly on green felt.
            g.setColor(java.awt.Color.WHITE);
            g.setStroke(new BasicStroke(2.0f));
            g.draw(new RoundRectangle2D.Double(1.5, 1.5, w - 3, h - 3, arc - 2, arc - 2));

            String center = centerLabel(card.value());
            String mini = miniLabel(card.value());

            // Step: corner pips (top-left and upside-down bottom-right like real UNO).
            g.setColor(java.awt.Color.WHITE);
            g.setFont(baseFont.deriveFont(9.0f));
            g.drawString(mini, 4, 12);
            drawUpsideDownMini(g, mini, w, h);

            // Step: dominant glyph centered for at-a-glance reading during play.
            g.setFont(baseFont.deriveFont(Font.BOLD, 16.0f));
            FontMetrics fm = g.getFontMetrics();
            int cx = (w - fm.stringWidth(center)) / 2;
            int cy = (h + fm.getAscent() - fm.getDescent()) / 2;
            g.drawString(center, cx, cy);
        } finally {
            g.dispose();
        }
        return img;
    }

    private void drawBackFace(Graphics2D g, int w, int h) {
        // Step: deck back is a deep navy slab with a simple emblem so it is not mistaken for a color card.
        g.setColor(new java.awt.Color(20, 40, 90));
        g.fill(new RoundRectangle2D.Double(0, 0, w, h, 8, 8));
        g.setColor(new java.awt.Color(255, 200, 80));
        g.setStroke(new BasicStroke(2));
        g.drawOval(w / 2 - 8, h / 2 - 8, 16, 16);
    }

    private void drawWildFace(Graphics2D g, int w, int h, boolean drawFour) {
        // Step: wild background anchors the rainbow corners.
        g.setColor(ThemeConfig.RENDER_WILD_BG);
        g.fill(new RoundRectangle2D.Double(0, 0, w, h, 8, 8));
        int hw = w / 2;
        int hh = h / 2;
        // Step: four quadrants tinted R/B/G/Y to hint "choose any color".
        g.setColor(ThemeConfig.RENDER_FACE_RED);
        g.fillRect(0, 0, hw, hh);
        g.setColor(ThemeConfig.RENDER_FACE_BLUE);
        g.fillRect(hw, 0, w - hw, hh);
        g.setColor(ThemeConfig.RENDER_FACE_GREEN);
        g.fillRect(0, hh, hw, h - hh);
        g.setColor(ThemeConfig.RENDER_FACE_YELLOW);
        g.fillRect(hw, hh, w - hw, h - hh);
        g.setColor(java.awt.Color.WHITE);
        g.setStroke(new BasicStroke(2));
        g.draw(new RoundRectangle2D.Double(1, 1, w - 2, h - 2, 8, 8));
        String tag = drawFour ? "+4" : "W";
        g.setFont(baseFont.deriveFont(Font.BOLD, 14.0f));
        FontMetrics fm = g.getFontMetrics();
        int cx = (w - fm.stringWidth(tag)) / 2;
        int cy = (h + fm.getAscent() - fm.getDescent()) / 2;
        g.drawString(tag, cx, cy);
    }

    private void drawUpsideDownMini(Graphics2D g, String mini, int w, int h) {
        // Step: rotate graphics for bottom-right corner pip to mimic physical card layout.
        var old = g.getTransform();
        g.rotate(Math.PI, w / 2.0, h / 2.0);
        g.drawString(mini, 4, 12);
        g.setTransform(old);
    }

    private java.awt.Color faceColor(Color c) {
        if (colorblindMode) {
            return switch (c) {
                case RED -> new java.awt.Color(200, 60, 60);
                case BLUE -> new java.awt.Color(60, 110, 220);
                case GREEN -> new java.awt.Color(50, 170, 90);
                case YELLOW -> new java.awt.Color(230, 210, 60);
                default -> java.awt.Color.DARK_GRAY;
            };
        }
        return switch (c) {
            case RED -> ThemeConfig.RENDER_FACE_RED;
            case BLUE -> ThemeConfig.RENDER_FACE_BLUE;
            case GREEN -> ThemeConfig.RENDER_FACE_GREEN;
            case YELLOW -> ThemeConfig.RENDER_FACE_YELLOW;
            default -> java.awt.Color.DARK_GRAY;
        };
    }

    private String centerLabel(Value v) {
        return switch (v) {
            case ZERO, ONE, TWO, THREE, FOUR, FIVE, SIX, SEVEN, EIGHT, NINE -> String.valueOf(v.ordinal());
            case SKIP -> "X";
            case REVERSE -> "R";
            case DRAW_TWO -> "+2";
            default -> "?";
        };
    }

    private String miniLabel(Value v) {
        return centerLabel(v);
    }
}
