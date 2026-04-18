package edu.bu.pas.uno.ui.animations;

import edu.bu.pas.uno.enums.Value;
import edu.bu.pas.uno.ui.UnoAnimation;
import edu.bu.pas.uno.ui.UnoPanel;
import edu.bu.pas.uno.ui.frontend.AnimationUtils;
import edu.bu.pas.uno.ui.frontend.ThemeConfig;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;

/**
 * Big red “+2 / +4” text that drifts upward while fading, matching draw-stack penalties.
 */
public class DrawPenaltyAnimation extends UnoAnimation {

    private static final int MAX_ALPHA = 240;
    private final String label;

    public DrawPenaltyAnimation(Value value, int durationMillis) {
        super(durationMillis);
        this.label = value == Value.WILD_DRAW_FOUR ? "+4" : "+2";
    }

    @Override
    public void render(Graphics2D g, UnoPanel panel) {
        double p = getProgress();
        int alpha = AnimationUtils.alphaForFade(p, MAX_ALPHA);
        if (alpha <= 2) {
            return;
        }
        int rise = AnimationUtils.bounceUpPixels(p, 42);
        int cx = ThemeConfig.BOARD_W / 2;
        int cy = ThemeConfig.PLAY_AREA_H / 2 - rise;
        g.setFont(panel.getGameFont().deriveFont(Font.BOLD, 48.0f));
        FontMetrics fm = g.getFontMetrics();
        int tx = cx - fm.stringWidth(label) / 2;
        java.awt.Color red = new java.awt.Color(255, 30, 30, alpha);
        g.setColor(new java.awt.Color(0, 0, 0, Math.min(alpha, 120)));
        g.drawString(label, tx + 2, cy + fm.getAscent() + 2);
        g.setColor(red);
        g.drawString(label, tx, cy + fm.getAscent());
    }
}
