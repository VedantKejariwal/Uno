package edu.bu.pas.uno.ui;

import edu.bu.pas.uno.Card;
import edu.bu.pas.uno.Game;
import edu.bu.pas.uno.Hand;
import edu.bu.pas.uno.enums.Color;
import edu.bu.pas.uno.enums.Value;
import edu.bu.pas.uno.ui.animations.UpdateGameStateAnimation;
import edu.bu.pas.uno.ui.frontend.AnimationUtils;
import edu.bu.pas.uno.ui.frontend.ImageCardLoader;
import edu.bu.pas.uno.ui.frontend.MetricsOverlay;
import edu.bu.pas.uno.ui.frontend.ThemeConfig;
import edu.bu.pas.uno.ui.frontend.UiLabeling;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import javax.swing.JPanel;

/**
 * Swing surface that paints the UNO table, reacts to {@link UnoAnimation} playback, and hosts HUD helpers.
 */
public class UnoPanel extends JPanel implements Runnable {

    /**
     * Target ~50fps so the sim thread is not contending with a 60Hz repaint budget on every move.
     */
    private static final long FRAME_MILLIS = 20L;

    private final Registry<Sound> sounds;
    private final Font font;
    private final SoundManager soundManager;
    private final BufferedImage[] boardBuffers;
    /** Swapped when a full off-screen render is complete: {@code 1 -} always writes a non-visible buffer. */
    private volatile int displayBufferIndex;
    /** Ensures EDT reads the buffer index only after the write buffer is fully painted (avoids rare torn frames). */
    private final Object frameSwapLock = new Object();
    private final boolean isColorblind;
    private final MetricsOverlay metricsOverlay = new MetricsOverlay();
    private final String[] agentDisplayNames = new String[]{"P0", "P1", "P2", "P3"};

    private volatile Game.GameView gameView;
    private Thread renderThread;
    private volatile boolean isRunning;
    private long lastUpdateMillis;
    private final Queue<UnoAnimation> animationQueue;
    /** Serializes {@link #queueAnimation} with {@link #update} polls (iterator.remove was racing polls). */
    private final Object animationQueueLock = new Object();
    private volatile UnoAnimation currentAnimation;
    private CountDownLatch latch;
    private final Object latchLock = new Object();
    private final Point[] playerOffsets = new Point[4];
    private volatile int highlightedSeat = -1;

    public UnoPanel(SoundManager soundManager, boolean colorblind) {
        this.soundManager = soundManager;
        this.animationQueue = new ConcurrentLinkedDeque<>();
        this.currentAnimation = null;
        this.latch = null;
        this.boardBuffers = new BufferedImage[2];
        for (int b = 0; b < 2; b++) {
            this.boardBuffers[b] = new BufferedImage(ThemeConfig.BOARD_W, ThemeConfig.BOARD_H, BufferedImage.TYPE_INT_ARGB);
        }
        this.displayBufferIndex = 0;
        this.isColorblind = colorblind;
        for (int i = 0; i < 4; ++i) {
            this.playerOffsets[i] = new Point(0, 0);
        }
        String prefix = colorblind ? "/colorblind" : "";
        Function<Path, String> extractFileName = path -> {
            String fileName = path.getFileName().toString();
            return fileName.contains(".") ? fileName.substring(0, fileName.lastIndexOf('.')) : fileName;
        };
        try {
            this.font = Font.createFont(Font.TRUETYPE_FONT, this.getClass().getResourceAsStream(prefix + "/RBYGSC.ttf"));
            URI soundsFolder = this.getClass().getResource(prefix + "/sounds").toURI();
            this.sounds = Registry.from(soundsFolder, Sound::fromWav, extractFileName);
        } catch (FontFormatException | IOException | URISyntaxException e) {
            throw new RuntimeException("Failed to load UI assets", e);
        }
        ImageCardLoader.loadAll();
        this.setPreferredSize(new Dimension(ThemeConfig.PANEL_PREF_W, ThemeConfig.PANEL_PREF_H));
        this.setBackground(java.awt.Color.BLACK);
        this.setOpaque(true);
        this.setDoubleBuffered(true);
    }

    /**
     * Copies simple class names from live agents once at startup for on-screen labels.
     *
     * @param game authoritative game object (not a snapshot)
     */
    public void setAgentDisplayNamesFromGame(Game game) {
        for (int logical = 0; logical < game.getNumPlayers(); logical++) {
            agentDisplayNames[logical] = game.getAgent(logical).getClass().getSimpleName();
        }
    }

    public SoundManager getSoundManager() {
        return this.soundManager;
    }

    public Registry<Sound> getSounds() {
        return this.sounds;
    }

    public Font getGameFont() {
        return this.font;
    }

    public Game.GameView getGameView() {
        return this.gameView;
    }

    public void setGameView(Game.GameView view) {
        this.gameView = view;
    }

    public Point getPlayerOffset(int seatIdx) {
        return this.playerOffsets[Math.min(seatIdx, this.playerOffsets.length - 1)];
    }

    public int getHighlightedSeat() {
        return this.highlightedSeat;
    }

    public void setHighlightedSeat(int seat) {
        this.highlightedSeat = seat;
    }

    /**
     * Enqueues an animation. Pending {@link UpdateGameStateAnimation}s are dropped when a new one is
     * queued so the engine thread is not held behind redundant state snaps (major latency win).
     */
    public void queueAnimation(UnoAnimation animation) {
        synchronized (this.animationQueueLock) {
            if (animation instanceof UpdateGameStateAnimation) {
                this.animationQueue.removeIf(e -> e instanceof UpdateGameStateAnimation);
            }
            this.animationQueue.offer(animation);
        }
    }

    public void join() {
        CountDownLatch waitOn;
        synchronized (this.animationQueueLock) {
            synchronized (this.latchLock) {
                if (this.animationQueue.isEmpty() && this.currentAnimation == null) {
                    return;
                }
                waitOn = new CountDownLatch(1);
                this.latch = waitOn;
            }
        }
        try {
            waitOn.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public synchronized void start() {
        if (this.isRunning) {
            return;
        }
        this.lastUpdateMillis = System.currentTimeMillis();
        this.renderThread = new Thread((Runnable) this, "UNO Render Thread");
        this.isRunning = true;
        this.renderThread.start();
    }

    @Override
    public void run() {
        while (this.isRunning) {
            long now = System.currentTimeMillis();
            int rawDelta = (int) (now - this.lastUpdateMillis);
            this.lastUpdateMillis = now;
            // Avoid one giant timestep after a stall (debugger, GC pause) skipping animation frames.
            int deltaMillis = Math.min(Math.max(rawDelta, 0), 48);
            this.update(deltaMillis);
            int write = 1 - this.displayBufferIndex;
            Graphics2D rg = this.boardBuffers[write].createGraphics();
            try {
                this.renderBoard(rg);
            } finally {
                rg.dispose();
            }
            synchronized (this.frameSwapLock) {
                this.displayBufferIndex = write;
            }
            // repaint() is thread-safe and coalesces internally; invokeLater every frame flooded the EDT queue.
            this.repaint();
            long elapsed = System.currentTimeMillis() - now;
            long sleepTime = FRAME_MILLIS - elapsed - 1L;
            // Always sleep at least 1ms so a slow frame does not busy-spin the CPU.
            if (sleepTime < 1L) {
                sleepTime = 1L;
            }
            try {
                TimeUnit.MILLISECONDS.sleep(sleepTime);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void update(int deltaMillis) {
        if (this.currentAnimation != null) {
            this.currentAnimation.update(deltaMillis, this);
            if (this.currentAnimation.isFinished()) {
                this.currentAnimation = null;
            }
        }
        while (this.currentAnimation == null) {
            UnoAnimation next;
            synchronized (this.animationQueueLock) {
                if (this.animationQueue.isEmpty()) {
                    break;
                }
                next = this.animationQueue.poll();
            }
            if (next == null) {
                break;
            }
            if (next instanceof UpdateGameStateAnimation) {
                next.onFinished(this);
                continue;
            }
            this.currentAnimation = next;
            break;
        }
        boolean queueEmpty;
        synchronized (this.animationQueueLock) {
            queueEmpty = this.animationQueue.isEmpty();
        }
        if (this.currentAnimation == null && queueEmpty) {
            synchronized (this.latchLock) {
                if (this.latch != null) {
                    this.latch.countDown();
                    this.latch = null;
                }
            }
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        int panelW = this.getWidth();
        int panelH = this.getHeight();
        double scaleX = (double) panelW / (double) ThemeConfig.BOARD_W;
        double scaleY = (double) panelH / (double) ThemeConfig.BOARD_H;
        double scale = Math.min(scaleX, scaleY);
        int drawW = (int) ((double) ThemeConfig.BOARD_W * scale);
        int drawH = (int) ((double) ThemeConfig.BOARD_H * scale);
        int drawX = (panelW - drawW) / 2;
        int drawY = (panelH - drawH) / 2;
        g2d.setColor(java.awt.Color.BLACK);
        g2d.fillRect(0, 0, panelW, panelH);
        BufferedImage frame;
        synchronized (this.frameSwapLock) {
            frame = this.boardBuffers[this.displayBufferIndex];
        }
        g2d.drawImage(frame, drawX, drawY, drawW, drawH, null);
    }

    // ── RENDER PASS ──

    private void renderBoard(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        // LCD subpixel AA flickers when UI moves; grayscale AA is steadier for HUD / ticker text.
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
        this.drawBackground(g);
        Game.GameView view = this.gameView;
        if (view == null) {
            String msg = "Waiting for game...";
            this.drawUiCenteredLine(g, msg, ThemeConfig.UI_FONT.deriveFont(ThemeConfig.FONT_MEDIUM), java.awt.Color.WHITE);
            return;
        }
        metricsOverlay.update(view, view.getCurrentMoveIdx(), null, agentDisplayNames);
        int numPlayers = view.getNumPlayers();
        this.drawCenterArea(g, view);
        for (int logicalIdx = 0; logicalIdx < numPlayers; ++logicalIdx) {
            Hand.HandView hand = view.getHandView(logicalIdx);
            boolean isActiveTurn = view.getPlayerOrder().getCurrentLogicalPlayerIdx() == logicalIdx;
            this.drawPlayerHand(g, view, hand, logicalIdx, isActiveTurn);
        }
        metricsOverlay.render(g, view, agentDisplayNames, ThemeConfig.BOARD_W, ThemeConfig.BOARD_H);
        if (this.currentAnimation != null) {
            this.currentAnimation.render(g, this);
        }
    }

    private void drawBackground(Graphics2D g) {
        int ph = ThemeConfig.PLAY_AREA_H;
        g.setColor(ThemeConfig.FELT_PLAIN);
        g.fillRect(0, 0, ThemeConfig.BOARD_W, ph);
        g.setColor(ThemeConfig.FELT_BORDER);
        g.setStroke(new BasicStroke(2.0f));
        g.drawRect(8, 8, ThemeConfig.BOARD_W - 16, ph - 16);
        g.setStroke(new BasicStroke(1.0f));
    }

    private void drawUiCenteredLine(Graphics2D g, String text, java.awt.Font font, java.awt.Color color) {
        g.setFont(font);
        FontMetrics fm = g.getFontMetrics();
        int tx = (ThemeConfig.BOARD_W - fm.stringWidth(text)) / 2;
        int ty = ThemeConfig.BOARD_H / 2;
        g.setColor(color);
        g.drawString(text, tx, ty);
    }

    private void drawCenterArea(Graphics2D g, Game.GameView view) {
        Card topCard = view.getDiscardPile().peek();
        int discardX = ThemeConfig.DISCARD_X;
        int discardY = ThemeConfig.DISCARD_Y;
        int drawPileX = ThemeConfig.DRAW_X;
        int drawPileY = ThemeConfig.DRAW_Y;
        if (topCard != null) {
            g.setColor(new java.awt.Color(0, 0, 0, 80));
            g.fillRoundRect(discardX + 3, discardY + 3, ThemeConfig.CARD_W, ThemeConfig.CARD_H, 8, 8);
            this.drawCard(g, topCard, discardX, discardY);
        }
        if (!view.getUnresolvedCards().isEmpty()) {
            int total = view.getUnresolvedCards().total();
            String penaltyText = "+" + total;
            g.setFont(ThemeConfig.UI_FONT_LARGE);
            FontMetrics pfm = g.getFontMetrics();
            g.setColor(ThemeConfig.UNO_TEXT);
            int px = discardX + ThemeConfig.CARD_W / 2 - pfm.stringWidth(penaltyText) / 2;
            g.drawString(penaltyText, px, ThemeConfig.PENALTY_Y + pfm.getAscent());
        }
        Card back = new Card(Color.UNKNOWN, Value.UNKNOWN);
        BufferedImage backImg = ImageCardLoader.getCard(getCardSpriteKey(back));
        for (int stack = 3; stack >= 0; stack--) {
            g.drawImage(backImg, drawPileX - stack, drawPileY - stack, ThemeConfig.CARD_W, ThemeConfig.CARD_H, null);
        }
        String countText = "Deck: " + view.getDrawPileSize();
        g.setFont(ThemeConfig.UI_FONT);
        FontMetrics cfm = g.getFontMetrics();
        int cx = drawPileX + ThemeConfig.CARD_W / 2 - cfm.stringWidth(countText) / 2;
        int countY = drawPileY + ThemeConfig.CARD_H + 18;
        g.setColor(java.awt.Color.WHITE);
        g.drawString(countText, cx, countY);
    }

    private void drawPlayerHand(Graphics2D g, Game.GameView view, Hand.HandView hand, int seatIdx, boolean isActiveTurn) {
        int numCards = hand.size();
        Point offset = this.playerOffsets[seatIdx];
        boolean isHorizontal = seatIdx == 0 || seatIdx == 2;
        if (isHorizontal) {
            int availableWidth = ThemeConfig.HORIZONTAL_HAND_AVAILABLE_WIDTH;
            int overlap = numCards <= 1 ? 0 : Math.min(ThemeConfig.HAND_OVERLAP_H_MAX, (availableWidth - ThemeConfig.CARD_W) / Math.max(1, numCards - 1));
            int totalWidth = numCards <= 1 ? ThemeConfig.CARD_W : ThemeConfig.CARD_W + overlap * (numCards - 1);
            int startX = (ThemeConfig.BOARD_W - totalWidth) / 2 + offset.x;
            int y = seatIdx == 0 ? ThemeConfig.BOTTOM_HAND_Y + offset.y : ThemeConfig.TOP_HAND_Y + offset.y;
            if (isActiveTurn) {
                drawTurnGlow(g, startX - 6, y - 6, totalWidth + 12, ThemeConfig.CARD_H + 12);
            }
            if (numCards <= 2) {
                drawDangerGlow(g, startX - 8, y - 8, totalWidth + 16, ThemeConfig.CARD_H + 16);
            }
            for (int i = 0; i < numCards; ++i) {
                int cx = startX + i * overlap;
                this.drawCard(g, hand.getCard(i), cx, y);
            }
            String shortNm = UiLabeling.shortAgentName(agentDisplayNames[seatIdx]);
            String label = UiLabeling.handLabel(shortNm, numCards);
            drawNameBadge(g, label, offset.x, y - 8, ThemeConfig.BOARD_W);
            if (numCards == 1) {
                drawUnoCallout(g, startX + totalWidth + 8, y + ThemeConfig.CARD_H / 2);
            }
        } else {
            int availableHeight = ThemeConfig.VERTICAL_HAND_AVAILABLE_HEIGHT;
            int overlap = numCards <= 1 ? 0 : Math.min(ThemeConfig.HAND_OVERLAP_V_MAX, (availableHeight - ThemeConfig.CARD_H) / Math.max(1, numCards - 1));
            int totalHeight = numCards <= 1 ? ThemeConfig.CARD_H : ThemeConfig.CARD_H + overlap * (numCards - 1);
            int startY = (ThemeConfig.PLAY_AREA_H - totalHeight) / 2 + offset.y;
            int x = seatIdx == 1 ? ThemeConfig.SIDE_HAND_X_LEFT + offset.x : ThemeConfig.SIDE_HAND_X_RIGHT + offset.x;
            if (isActiveTurn) {
                drawTurnGlow(g, x - 6, startY - 6, ThemeConfig.CARD_W + 12, totalHeight + 12);
            }
            if (numCards <= 2) {
                drawDangerGlow(g, x - 8, startY - 8, ThemeConfig.CARD_W + 16, totalHeight + 16);
            }
            for (int i = 0; i < numCards; ++i) {
                int cy = startY + i * overlap;
                this.drawCard(g, hand.getCard(i), x, cy);
            }
            String shortNm = UiLabeling.shortAgentName(agentDisplayNames[seatIdx]);
            String label = UiLabeling.handLabel(shortNm, numCards);
            int badgeAnchorX = seatIdx == 1 ? Math.max(4, x - 8) : Math.max(4, x - 200);
            drawNameBadge(g, label, badgeAnchorX, startY, 200);
            if (numCards == 1) {
                drawUnoCallout(g, x + ThemeConfig.CARD_W + 6, startY + totalHeight / 2);
            }
        }
    }

    private void drawTurnGlow(Graphics2D g, int rx, int ry, int rw, int rh) {
        double pulse = AnimationUtils.pulse01(System.currentTimeMillis(), 900.0);
        float base = (float) (pulse * 0.45 + 0.15);
        for (int layer = ThemeConfig.TURN_GLOW_LAYERS; layer >= 1; layer--) {
            int spread = layer * 4;
            float a = base * (1.0f - layer * 0.12f);
            a = Math.min(1f, Math.max(0.05f, a));
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, a));
            g.setColor(ThemeConfig.TURN_GLOW);
            g.drawRoundRect(rx - spread, ry - spread, rw + spread * 2, rh + spread * 2, 12, 12);
        }
        g.setComposite(AlphaComposite.SrcOver);
    }

    private void drawDangerGlow(Graphics2D g, int rx, int ry, int rw, int rh) {
        double pulse = AnimationUtils.pulse01(System.currentTimeMillis(), 700.0);
        float base = (float) (pulse * 0.38 + 0.12);
        for (int layer = ThemeConfig.DANGER_GLOW_LAYERS; layer >= 1; layer--) {
            int spread = layer * 3;
            float a = base * (1.0f - layer * 0.14f);
            a = Math.min(1f, Math.max(0.05f, a));
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, a));
            g.setColor(ThemeConfig.DANGER_GLOW);
            g.drawRoundRect(rx - spread, ry - spread, rw + spread * 2, rh + spread * 2, 10, 10);
        }
        g.setComposite(AlphaComposite.SrcOver);
    }

    private void drawNameBadge(Graphics2D g, String label, int anchorX, int anchorY, int width) {
        g.setFont(ThemeConfig.UI_FONT_BOLD);
        FontMetrics fm = g.getFontMetrics();
        int labelW = fm.stringWidth(label) + 16;
        int labelH = fm.getHeight() + 6;
        int labelX = anchorX + (width - labelW) / 2;
        int labelY = anchorY;
        g.setColor(ThemeConfig.LABEL_BG);
        g.fillRoundRect(labelX - 8, labelY - fm.getAscent() - 3, labelW, labelH, 10, 10);
        g.setColor(ThemeConfig.LABEL_TEXT);
        g.drawString(label, labelX, labelY);
    }

    private void drawUnoCallout(Graphics2D g, int x, int y) {
        g.setFont(ThemeConfig.UI_FONT_BOLD.deriveFont(ThemeConfig.FONT_LARGE));
        g.setColor(ThemeConfig.UNO_TEXT);
        g.drawString("UNO!", x, y);
    }

    private void drawCard(Graphics2D g, Card card, int x, int y) {
        String key = getCardSpriteKey(card);
        BufferedImage img = ImageCardLoader.getCard(key);
        g.drawImage(img, x, y, ThemeConfig.CARD_W, ThemeConfig.CARD_H, null);
    }

    public Point getDiscardPosition() {
        return new Point(ThemeConfig.DISCARD_X, ThemeConfig.DISCARD_Y);
    }

    public Point getDrawPilePosition() {
        return new Point(ThemeConfig.DRAW_X, ThemeConfig.DRAW_Y);
    }

    public Point getSeatNewCardPosition(int seatIdx, int cardIndex, int newHandSize) {
        Point offset = this.playerOffsets[seatIdx];
        int x = switch (seatIdx) {
            case 1 -> ThemeConfig.SIDE_HAND_X_LEFT + offset.x;
            case 3 -> ThemeConfig.SIDE_HAND_X_RIGHT + offset.x;
            case 0, 2 -> {
                int availableWidth = ThemeConfig.HORIZONTAL_HAND_AVAILABLE_WIDTH;
                int overlap = newHandSize <= 1 ? 0 : Math.min(ThemeConfig.HAND_OVERLAP_H_MAX, (availableWidth - ThemeConfig.CARD_W) / Math.max(1, newHandSize - 1));
                int totalWidth = newHandSize <= 1 ? ThemeConfig.CARD_W : ThemeConfig.CARD_W + overlap * (newHandSize - 1);
                int startX = (ThemeConfig.BOARD_W - totalWidth) / 2 + offset.x;
                yield startX + cardIndex * overlap;
            }
            default -> throw new IllegalArgumentException("bad seat");
        };
        int y = switch (seatIdx) {
            case 0 -> ThemeConfig.BOTTOM_HAND_Y + offset.y;
            case 2 -> ThemeConfig.TOP_HAND_Y + offset.y;
            case 1, 3 -> {
                int availableHeight = ThemeConfig.VERTICAL_HAND_AVAILABLE_HEIGHT;
                int overlap = newHandSize <= 1 ? 0 : Math.min(ThemeConfig.HAND_OVERLAP_V_MAX, (availableHeight - ThemeConfig.CARD_H) / Math.max(1, newHandSize - 1));
                int totalHeight = newHandSize <= 1 ? ThemeConfig.CARD_H : ThemeConfig.CARD_H + overlap * (newHandSize - 1);
                int startY = (ThemeConfig.PLAY_AREA_H - totalHeight) / 2 + offset.y;
                yield startY + cardIndex * overlap;
            }
            default -> throw new IllegalArgumentException("bad seat");
        };
        return new Point(x, y);
    }

    public String getCardSpriteKey(Card card) {
        if (card.color() == Color.UNKNOWN || card.value() == Value.UNKNOWN) {
            return "back";
        }
        if (card.value() == Value.WILD_DRAW_FOUR) {
            return "wild_draw4";
        }
        if (card.value() == Value.WILD) {
            return "wild";
        }
        return card.color().name().toLowerCase() + "_" + valueToSpriteString(card.value());
    }

    private String valueToSpriteString(Value v) {
        return switch (v) {
            case ZERO -> "0";
            case ONE -> "1";
            case TWO -> "2";
            case THREE -> "3";
            case FOUR -> "4";
            case FIVE -> "5";
            case SIX -> "6";
            case SEVEN -> "7";
            case EIGHT -> "8";
            case NINE -> "9";
            case SKIP -> "skip";
            case REVERSE -> "reverse";
            case DRAW_TWO -> "draw2";
            case WILD, WILD_DRAW_FOUR -> "wild";
            default -> "back";
        };
    }

    public java.awt.Color toAwtColor(Color c) {
        if (this.isColorblind) {
            return switch (c) {
                case RED -> new java.awt.Color(15886634);
                case BLUE -> new java.awt.Color(2958318);
                case GREEN -> new java.awt.Color(45736);
                case YELLOW -> new java.awt.Color(16312348);
                default -> java.awt.Color.GRAY;
            };
        }
        return switch (c) {
            case RED -> ThemeConfig.CARD_RED;
            case BLUE -> ThemeConfig.CARD_BLUE;
            case GREEN -> ThemeConfig.CARD_GREEN;
            case YELLOW -> ThemeConfig.CARD_YELLOW;
            default -> java.awt.Color.GRAY;
        };
    }
}
