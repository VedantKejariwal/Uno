package edu.bu.pas.uno.ui.animations;

import edu.bu.pas.uno.Card;
import edu.bu.pas.uno.ui.UnoAnimation;
import edu.bu.pas.uno.ui.UnoPanel;
import edu.bu.pas.uno.ui.frontend.ImageCardLoader;
import edu.bu.pas.uno.ui.frontend.ThemeConfig;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

/**
 * Animates a single card from a seat or the deck toward the discard pile using rendered art.
 */
public class CardFlightAnimation extends UnoAnimation {

    private final Card card;
    private final int srcX;
    private final int srcY;
    private final int dstX;
    private final int dstY;

    public CardFlightAnimation(Card card, int srcX, int srcY, int dstX, int dstY, int durationMillis) {
        super(durationMillis);
        this.card = card;
        this.srcX = srcX;
        this.srcY = srcY;
        this.dstX = dstX;
        this.dstY = dstY;
    }

    @Override
    public void render(Graphics2D g, UnoPanel panel) {
        double t = getProgress();
        double eased = 1.0 - (1.0 - t) * (1.0 - t);
        int x = (int) (srcX + (dstX - srcX) * eased);
        int y = (int) (srcY + (dstY - srcY) * eased);
        BufferedImage img = ImageCardLoader.getCard(panel.getCardSpriteKey(card));
        g.drawImage(img, x, y, ThemeConfig.CARD_W, ThemeConfig.CARD_H, null);
    }
}
