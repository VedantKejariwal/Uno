package edu.bu.pas.uno.ui.animations;

import edu.bu.pas.uno.ui.UnoAnimation;
import edu.bu.pas.uno.ui.UnoPanel;
import edu.bu.pas.uno.ui.frontend.AnimationUtils;
import edu.bu.pas.uno.ui.frontend.ThemeConfig;
import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.geom.Arc2D;

/**
 * Highlights a direction change with two yellow curved arrows that fade away.
 */
public class ReverseAnimation extends UnoAnimation {

    private static final int MAX_ALPHA = 220;

    public ReverseAnimation(int durationMillis) {
        super(durationMillis);
    }

    @Override
    public void render(Graphics2D g, UnoPanel panel) {
        double p = getProgress();
        int alpha = AnimationUtils.alphaForFade(p, MAX_ALPHA);
        if (alpha <= 2) {
            return;
        }
        int cx = ThemeConfig.BOARD_W / 2;
        int cy = ThemeConfig.PLAY_AREA_H / 2;
        java.awt.Color yellow = new java.awt.Color(255, 230, 60, alpha);
        g.setColor(yellow);
        g.setStroke(new BasicStroke(5.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        int r = 70;
        g.draw(new Arc2D.Double(cx - r, cy - r, r * 2, r * 2, 40, 110, Arc2D.OPEN));
        g.draw(new Arc2D.Double(cx - r, cy - r, r * 2, r * 2, 220, 110, Arc2D.OPEN));
    }
}
