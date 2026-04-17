package edu.bu.pas.uno.ui;

import edu.bu.pas.uno.Card;
import edu.bu.pas.uno.Game;
import edu.bu.pas.uno.enums.Color;
import edu.bu.pas.uno.enums.Value;
import edu.bu.pas.uno.ui.animations.CardFlightAnimation;
import edu.bu.pas.uno.ui.animations.ColorChangeAnimation;
import edu.bu.pas.uno.ui.animations.DelayAnimation;
import edu.bu.pas.uno.ui.animations.DrawPenaltyAnimation;
import edu.bu.pas.uno.ui.animations.HighlightAnimation;
import edu.bu.pas.uno.ui.animations.ReverseAnimation;
import edu.bu.pas.uno.ui.animations.SkipAnimation;
import edu.bu.pas.uno.ui.animations.SoundAnimation;
import edu.bu.pas.uno.ui.animations.UpdateGameStateAnimation;
import java.awt.Point;

/**
 * Bridges engine callbacks into short UI animations (sounds, motion, highlights).
 */
public class UnoAnimationListener implements UnoListener {

    private final UnoPanel panel;

    public UnoAnimationListener(UnoPanel panel) {
        this.panel = panel;
    }

    @Override
    public void onGameStarted(Game game) {
        this.panel.setGameView(game.getOmniscientView());
        this.panel.setAgentDisplayNamesFromGame(game);
        Sound shuffleSound = this.panel.getSounds().getByName("shuffle");
        if (shuffleSound != null) {
            this.panel.queueAnimation(new SoundAnimation(shuffleSound.getAudioStream(), true));
        }
    }

    @Override
    public void onTurnStarted(Game game, int playerIdx) {
        this.panel.queueAnimation(new UpdateGameStateAnimation(game));
        int seat = game.getPlayerOrder().getLogicalIdx(playerIdx);
        this.panel.queueAnimation(new HighlightAnimation(seat, 400));
        this.panel.queueAnimation(new DelayAnimation(200));
    }

    @Override
    public void onCardPlayed(Game game, int playerIdx, Card card, int cardIndex) {
        Sound playSound = this.panel.getSounds().getByName("playcard");
        if (playSound != null) {
            this.panel.queueAnimation(new SoundAnimation(playSound.getAudioStream(), false));
        }
        int logicalIdx = game.getPlayerOrder().getLogicalIdx(playerIdx);
        int handSize = game.getHand(logicalIdx).size() + 1;
        int seat = game.getPlayerOrder().getLogicalIdx(playerIdx);
        Point src = this.panel.getSeatNewCardPosition(seat, cardIndex, handSize);
        Point dst = this.panel.getDiscardPosition();
        this.panel.queueAnimation(new CardFlightAnimation(card, src.x, src.y, dst.x, dst.y, 800));
        this.panel.queueAnimation(new UpdateGameStateAnimation(game));
        Value v = card.value();
        if (v == Value.SKIP) {
            this.panel.queueAnimation(new SkipAnimation(1000));
        } else if (v == Value.REVERSE) {
            this.panel.queueAnimation(new ReverseAnimation(1000));
        } else if (v == Value.DRAW_TWO || v == Value.WILD_DRAW_FOUR) {
            this.panel.queueAnimation(new DrawPenaltyAnimation(v, 1000));
        }
    }

    @Override
    public void onCardsDrawn(Game game, int playerIdx, Card[] cards) {
        int logicalIdx = game.getPlayerOrder().getLogicalIdx(playerIdx);
        for (int i = 0; i < cards.length; ++i) {
            Card card = cards[i];
            Sound drawSound = this.panel.getSounds().getByName("draw");
            if (drawSound != null) {
                this.panel.queueAnimation(new SoundAnimation(drawSound.getAudioStream(), false));
            }
            int seat = game.getPlayerOrder().getLogicalIdx(playerIdx);
            Point src = this.panel.getDrawPilePosition();
            int sizeBeforeDraws = game.getHand(logicalIdx).size() - cards.length;
            int handSizeWhenAdded = sizeBeforeDraws + i + 1;
            Point dst = this.panel.getSeatNewCardPosition(seat, handSizeWhenAdded - 1, handSizeWhenAdded);
            this.panel.queueAnimation(new CardFlightAnimation(card, src.x, src.y, dst.x, dst.y, 800));
        }
        this.panel.queueAnimation(new UpdateGameStateAnimation(game));
    }

    @Override
    public void onColorChosen(Game game, Color chosenColor) {
        Sound chooseSound = this.panel.getSounds().getByName("passturn");
        if (chooseSound != null) {
            this.panel.queueAnimation(new SoundAnimation(chooseSound.getAudioStream(), false));
        }
        this.panel.queueAnimation(new ColorChangeAnimation(chosenColor, 500));
    }

    @Override
    public void onDirectionReversed(Game game) {
        Sound changeSound = this.panel.getSounds().getByName("stagechange");
        if (changeSound != null) {
            this.panel.queueAnimation(new SoundAnimation(changeSound.getAudioStream(), false));
        }
        this.panel.queueAnimation(new UpdateGameStateAnimation(game));
    }

    @Override
    public void onPlayerSkipped(Game game, int skippedPlayerIdx) {
        Sound passSound = this.panel.getSounds().getByName("error");
        if (passSound != null) {
            this.panel.queueAnimation(new SoundAnimation(passSound.getAudioStream(), false));
        }
    }

    @Override
    public void onGameOver(Game game, int winnerIdx) {
        this.panel.queueAnimation(new UpdateGameStateAnimation(game));
        Sound cuckooSound = this.panel.getSounds().getByName("cuckoo");
        if (cuckooSound != null) {
            this.panel.queueAnimation(new SoundAnimation(cuckooSound.getAudioStream(), true));
        }
    }
}
