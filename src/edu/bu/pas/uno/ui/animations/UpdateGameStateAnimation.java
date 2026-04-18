package edu.bu.pas.uno.ui.animations;

import edu.bu.pas.uno.Game;
import edu.bu.pas.uno.ui.UnoAnimation;
import edu.bu.pas.uno.ui.UnoPanel;
import java.awt.Graphics2D;

/** Refreshes the panel from the live game when applied (view built in {@link #onFinished}, not when queued). */
public class UpdateGameStateAnimation extends UnoAnimation {

    private final Game game;

    public UpdateGameStateAnimation(Game game) {
        super(0);
        this.game = game;
    }

    @Override
    public void render(Graphics2D g, UnoPanel panel) {
        // No overlay; {@link #onFinished} applies the new view before visible animations.
    }

    @Override
    protected void onFinished(UnoPanel panel) {
        panel.setGameView(this.game.getOmniscientView());
    }
}
