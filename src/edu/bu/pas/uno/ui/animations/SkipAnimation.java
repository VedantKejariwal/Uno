package edu.bu.pas.uno.ui.animations;

import edu.bu.pas.uno.ui.UnoAnimation;
import edu.bu.pas.uno.ui.UnoPanel;
import edu.bu.pas.uno.ui.frontend.AnimationUtils;
import edu.bu.pas.uno.ui.frontend.ThemeConfig;
import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;

/**
 * Short “skip turn” burst: a red no-entry mark that fades out so viewers notice the rule effect.
 */
public class SkipAnimation extends UnoAnimation {

    private static final int MAX_ALPHA = 220;

    public SkipAnimation(int durationMillis) {
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
        int r = 48;
        java.awt.Color red = new java.awt.Color(255, 40, 40, alpha);
        g.setColor(red);
        g.setStroke(new BasicStroke(6.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));
        g.draw(new Line2D.Double(cx - r * 0.65, cy - r * 0.65, cx + r * 0.65, cy + r * 0.65));
    }
}
