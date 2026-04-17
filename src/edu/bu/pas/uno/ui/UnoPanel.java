package edu.bu.pas.uno.ui;

import edu.bu.pas.uno.Card;
import edu.bu.pas.uno.Game;
import edu.bu.pas.uno.Hand;
import edu.bu.pas.uno.enums.Color;
import edu.bu.pas.uno.enums.Value;
import edu.bu.pas.uno.ui.animations.UpdateGameStateAnimation;
import edu.bu.pas.uno.ui.frontend.AnimationUtils;
import edu.bu.pas.uno.ui.frontend.CardRenderer;
import edu.bu.pas.uno.ui.frontend.MetricsOverlay;
import edu.bu.pas.uno.ui.frontend.ThemeConfig;
import java.awt.BasicStroke;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import javax.swing.JPanel;

/**
 * Swing surface that paints the UNO table, reacts to {@link UnoAnimation} playback, and hosts HUD helpers.
 */
public class UnoPanel extends JPanel implements Runnable {

    private static final long FRAME_MILLIS = 16L;

    private final Registry<Sound> sounds;
    private final Font font;
    private final SoundManager soundManager;
    private final BufferedImage boardBuffer;
    private final boolean isColorblind;
    private final CardRenderer cardRenderer;
    private final MetricsOverlay metricsOverlay = new MetricsOverlay();
    private final String[] agentDisplayNames = new String[]{"P0", "P1", "P2", "P3"};

    private volatile Game.GameView gameView;
    private Thread renderThread;
    private volatile boolean isRunning;
    private long lastUpdateMillis;
    private final Queue<UnoAnimation> animationQueue;
    private volatile UnoAnimation currentAnimation;
    private CountDownLatch latch;
    private final Object latchLock = new Object();
    private final Point[] playerOffsets = new Point[4];
    private volatile int highlightedSeat = -1;
    private volatile Color activeColorDisplay;
    private volatile double colorIndicatorScale = 1.0;

    public UnoPanel(SoundManager soundManager, boolean colorblind) {
        this.soundManager = soundManager;
        this.animationQueue = new ConcurrentLinkedDeque<>();
        this.currentAnimation = null;
        this.latch = null;
        this.boardBuffer = new BufferedImage(ThemeConfig.BOARD_W, ThemeConfig.BOARD_H, BufferedImage.TYPE_INT_ARGB);
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
        this.cardRenderer = new CardRenderer(this.font, colorblind);
        this.setPreferredSize(new Dimension(ThemeConfig.PANEL_PREF_W, ThemeConfig.PANEL_PREF_H));
        this.setBackground(java.awt.Color.BLACK);
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

    public CardRenderer getCardRenderer() {
        return cardRenderer;
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
        if (view != null) {
            this.activeColorDisplay = view.getCurrentColor();
        }
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

    public Color getActiveColorDisplay() {
        return this.activeColorDisplay;
    }

    public void setActiveColorDisplay(Color c) {
        this.activeColorDisplay = c;
    }

    public double getColorIndicatorScale() {
        return this.colorIndicatorScale;
    }

    public void setColorIndicatorScale(double s) {
        this.colorIndicatorScale = s;
    }

    public void queueAnimation(UnoAnimation animation) {
        this.animationQueue.offer(animation);
    }

    public void join() {
        synchronized (this.latchLock) {
            if (this.animationQueue.isEmpty() && this.currentAnimation == null) {
                return;
            }
            this.latch = new CountDownLatch(1);
        }
        try {
            this.latch.await();
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
            int deltaMillis = (int) (now - this.lastUpdateMillis);
            this.lastUpdateMillis = now;
            this.update(deltaMillis);
            this.repaint();
            long elapsed = System.currentTimeMillis() - now;
            long sleepTime = FRAME_MILLIS - elapsed - 1L;
            if (sleepTime <= 0L) {
                continue;
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
        while (this.currentAnimation == null && !this.animationQueue.isEmpty()) {
            this.currentAnimation = this.animationQueue.poll();
            if (!(this.currentAnimation instanceof UpdateGameStateAnimation)) {
                continue;
            }
            this.currentAnimation.onFinished(this);
            this.currentAnimation = null;
        }
        if (this.currentAnimation == null && this.animationQueue.isEmpty()) {
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
        this.renderBoard();
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
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
        g2d.drawImage(this.boardBuffer, drawX, drawY, drawW, drawH, null);
    }

    // ── RENDER PASS ──

    private void renderBoard() {
        Graphics2D g = this.boardBuffer.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        this.drawBackground(g);
        Game.GameView view = this.gameView;
        if (view == null) {
            String msg = "Waiting for game...";
            this.drawText(g, msg, ThemeConfig.FONT_MEDIUM, java.awt.Color.WHITE, 0, ThemeConfig.BOARD_H / 2, ThemeConfig.BOARD_W, false);
            g.dispose();
            return;
        }
        metricsOverlay.update(view, view.getCurrentMoveIdx(), null);
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
        if (view.isOver()) {
            drawWinOverlay(g, view);
        }
        g.dispose();
    }

    private void drawBackground(Graphics2D g) {
        g.setColor(ThemeConfig.FELT_BASE);
        g.fillRect(0, 0, ThemeConfig.BOARD_W, ThemeConfig.BOARD_H);
        g.setColor(ThemeConfig.FELT_SPOTLIGHT);
        g.fill(new Ellipse2D.Double(ThemeConfig.BOARD_W * 0.15, ThemeConfig.BOARD_H * 0.12, ThemeConfig.BOARD_W * 0.7, ThemeConfig.BOARD_H * 0.76));
        g.setColor(ThemeConfig.TABLE_BORDER_GOLD);
        g.setStroke(new BasicStroke(ThemeConfig.TABLE_BORDER_STROKE));
        g.drawRect(2, 2, ThemeConfig.BOARD_W - 4, ThemeConfig.BOARD_H - 4);
        g.setStroke(new BasicStroke(1.0f));
    }

    private void drawCenterArea(Graphics2D g, Game.GameView view) {
        Card topCard = view.getDiscardPile().peek();
        int discardX = ThemeConfig.DISCARD_X;
        int discardY = ThemeConfig.DISCARD_Y;
        if (topCard != null) {
            this.drawCard(g, topCard, discardX, discardY);
        }
        if (!view.getUnresolvedCards().isEmpty()) {
            int total = view.getUnresolvedCards().total();
            String penaltyText = "DRAW " + total;
            this.drawText(g, penaltyText, ThemeConfig.FONT_SMALL, toAwtColor(Color.RED), discardX, discardY + 90, ThemeConfig.CARD_W, true);
        }
        int drawPileX = ThemeConfig.DRAW_PILE_X;
        int drawPileY = ThemeConfig.DRAW_PILE_Y;
        Card back = new Card(Color.UNKNOWN, Value.UNKNOWN);
        g.drawImage(cardRenderer.getCardImage(back), drawPileX, drawPileY, ThemeConfig.CARD_W, ThemeConfig.CARD_H, null);
        String countText = String.valueOf(view.getDrawPileSize());
        this.drawText(g, countText, ThemeConfig.FONT_SMALL, java.awt.Color.WHITE, drawPileX, drawPileY, ThemeConfig.CARD_W, false);
        this.drawColorIndicator(g, ThemeConfig.COLOR_INDICATOR_CX, ThemeConfig.COLOR_INDICATOR_CY);
    }

    private void drawText(Graphics2D g, String text, float fontSize, java.awt.Color color, int anchorX, int anchorY, int width, boolean bottomAlign) {
        g.setFont(this.font.deriveFont(fontSize));
        FontMetrics fm = g.getFontMetrics();
        int tx = anchorX + (width - fm.stringWidth(text)) / 2;
        int ty = bottomAlign ? anchorY + fm.getAscent() + 4 : anchorY - 4;
        g.setColor(java.awt.Color.BLACK);
        g.drawString(text, tx + 1, ty + 1);
        g.setColor(color);
        g.drawString(text, tx, ty);
    }

    /**
     * Table-center color token: large circle, readable label, animated dashed ring.
     */
    private void drawColorIndicator(Graphics2D g, int cx, int cy) {
        Color activeColor = this.activeColorDisplay;
        if (activeColor == null) {
            return;
        }
        java.awt.Color awtColor = this.toAwtColor(activeColor);
        int base = ThemeConfig.ACTIVE_COLOR_DIAMETER;
        int size = (int) (base * this.colorIndicatorScale);
        int ix = cx - size / 2;
        int iy = cy - size / 2;
        float phase = (System.currentTimeMillis() % 4000L) / 25.0f;
        g.setStroke(ThemeConfig.dashedRingStroke(phase));
        g.setColor(new java.awt.Color(255, 255, 255, 160));
        g.drawOval(ix - 4, iy - 4, size + 8, size + 8);
        g.setColor(awtColor);
        g.fillOval(ix, iy, size, size);
        g.setColor(java.awt.Color.BLACK);
        g.setFont(this.font.deriveFont(Font.BOLD, 10.0f));
        String name = activeColor.name();
        FontMetrics fm = g.getFontMetrics();
        int tx = ix + (size - fm.stringWidth(name)) / 2;
        int ty = iy + (size + fm.getAscent() - fm.getDescent()) / 2;
        g.drawString(name, tx, ty);
        g.setStroke(new BasicStroke(1.0f));
        g.setColor(java.awt.Color.WHITE);
        g.drawOval(ix, iy, size, size);
    }

    private void drawPlayerHand(Graphics2D g, Game.GameView view, Hand.HandView hand, int seatIdx, boolean isActiveTurn) {
        int numCards = hand.size();
        Point offset = this.playerOffsets[seatIdx];
        boolean isHorizontal = seatIdx == 0 || seatIdx == 2;
        if (isHorizontal) {
            int availableWidth = ThemeConfig.HORIZONTAL_HAND_AVAILABLE_WIDTH;
            int overlap = numCards <= 1 ? 0 : Math.min(28, (availableWidth - ThemeConfig.CARD_W) / Math.max(1, numCards - 1));
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
            String label = agentDisplayNames[seatIdx] + " [" + numCards + "]";
            if (seatIdx == 0) {
                drawNameBadge(g, label, offset.x, y + ThemeConfig.CARD_H + 6, ThemeConfig.BOARD_W, true);
            } else {
                drawNameBadge(g, label, offset.x, y - 8, ThemeConfig.BOARD_W, false);
            }
            if (numCards == 1) {
                drawUnoCallout(g, startX + totalWidth + 8, y + ThemeConfig.CARD_H / 2);
            }
        } else {
            int availableHeight = ThemeConfig.VERTICAL_HAND_AVAILABLE_HEIGHT;
            int overlap = numCards <= 1 ? 0 : Math.min(48, (availableHeight - ThemeConfig.CARD_H) / Math.max(1, numCards - 1));
            int totalHeight = numCards <= 1 ? ThemeConfig.CARD_H : ThemeConfig.CARD_H + overlap * (numCards - 1);
            int startY = (ThemeConfig.BOARD_H - totalHeight) / 2 + offset.y;
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
            String label = agentDisplayNames[seatIdx] + " [" + numCards + "]";
            int badgeAnchorX = seatIdx == 1 ? Math.max(4, x - 8) : Math.max(4, x - 172);
            drawNameBadge(g, label, badgeAnchorX, startY, 180, false);
            if (numCards == 1) {
                drawUnoCallout(g, x + ThemeConfig.CARD_W + 6, startY + totalHeight / 2);
            }
        }
    }

    private void drawTurnGlow(Graphics2D g, int rx, int ry, int rw, int rh) {
        double pulse = AnimationUtils.pulse01(System.currentTimeMillis(), 900.0);
        for (int layer = ThemeConfig.TURN_GLOW_LAYERS; layer >= 1; layer--) {
            int spread = layer * 4;
            int alpha = (int) (pulse * (120 - layer * 18));
            g.setColor(new java.awt.Color(ThemeConfig.TURN_GLOW.getRed(), ThemeConfig.TURN_GLOW.getGreen(), ThemeConfig.TURN_GLOW.getBlue(), Math.max(0, Math.min(255, alpha))));
            g.drawRoundRect(rx - spread, ry - spread, rw + spread * 2, rh + spread * 2, 12, 12);
        }
    }

    private void drawDangerGlow(Graphics2D g, int rx, int ry, int rw, int rh) {
        double pulse = AnimationUtils.pulse01(System.currentTimeMillis(), 700.0);
        for (int layer = ThemeConfig.DANGER_GLOW_LAYERS; layer >= 1; layer--) {
            int spread = layer * 3;
            int alpha = (int) (pulse * (100 - layer * 16));
            g.setColor(new java.awt.Color(ThemeConfig.DANGER_GLOW.getRed(), ThemeConfig.DANGER_GLOW.getGreen(), ThemeConfig.DANGER_GLOW.getBlue(), Math.max(0, Math.min(255, alpha))));
            g.drawRoundRect(rx - spread, ry - spread, rw + spread * 2, rh + spread * 2, 10, 10);
        }
    }

    private void drawNameBadge(Graphics2D g, String label, int anchorX, int anchorY, int width, boolean below) {
        g.setFont(this.font.deriveFont(ThemeConfig.FONT_SMALL));
        FontMetrics fm = g.getFontMetrics();
        int textW = fm.stringWidth(label);
        int padX = 10;
        int padY = 4;
        int boxW = textW + padX * 2;
        int boxH = fm.getHeight() + padY;
        int bx = anchorX + (width - boxW) / 2;
        int by = below ? anchorY : anchorY - boxH;
        g.setColor(new java.awt.Color(0, 0, 0, 160));
        g.fillRoundRect(bx, by, boxW, boxH, 10, 10);
        g.setColor(java.awt.Color.WHITE);
        g.drawString(label, bx + padX, by + fm.getAscent() + padY / 2);
    }

    private void drawUnoCallout(Graphics2D g, int x, int y) {
        g.setFont(this.font.deriveFont(Font.BOLD, ThemeConfig.FONT_LARGE));
        g.setColor(new java.awt.Color(255, 40, 40));
        g.drawString("UNO!", x, y);
    }

    private void drawCard(Graphics2D g, Card card, int x, int y) {
        BufferedImage img = cardRenderer.getCardImage(card);
        g.drawImage(img, x, y, ThemeConfig.CARD_W, ThemeConfig.CARD_H, null);
    }

    public Point getDiscardPosition() {
        return new Point(ThemeConfig.DISCARD_X, ThemeConfig.DISCARD_Y);
    }

    public Point getDrawPilePosition() {
        return new Point(ThemeConfig.DRAW_PILE_X, ThemeConfig.DRAW_PILE_Y);
    }

    public Point getSeatNewCardPosition(int seatIdx, int cardIndex, int newHandSize) {
        Point offset = this.playerOffsets[seatIdx];
        int x = switch (seatIdx) {
            case 1 -> ThemeConfig.SIDE_HAND_X_LEFT + offset.x;
            case 3 -> ThemeConfig.SIDE_HAND_X_RIGHT + offset.x;
            case 0, 2 -> {
                int availableWidth = ThemeConfig.HORIZONTAL_HAND_AVAILABLE_WIDTH;
                int overlap = newHandSize <= 1 ? 0 : Math.min(28, (availableWidth - ThemeConfig.CARD_W) / Math.max(1, newHandSize - 1));
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
                int overlap = newHandSize <= 1 ? 0 : Math.min(48, (availableHeight - ThemeConfig.CARD_H) / Math.max(1, newHandSize - 1));
                int totalHeight = newHandSize <= 1 ? ThemeConfig.CARD_H : ThemeConfig.CARD_H + overlap * (newHandSize - 1);
                int startY = (ThemeConfig.BOARD_H - totalHeight) / 2 + offset.y;
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

    private void drawWinOverlay(Graphics2D g, Game.GameView view) {
        g.setColor(new java.awt.Color(0, 0, 0, 180));
        g.fillRect(0, 0, ThemeConfig.BOARD_W, ThemeConfig.BOARD_H);
        int winner = winnerLogicalIdx(view);
        String title;
        if (winner >= 0) {
            title = agentDisplayNames[winner] + " wins";
        } else {
            title = "Game over";
        }
        int cx = ThemeConfig.BOARD_W / 2;
        int cy = ThemeConfig.BOARD_H / 2 - 40;
        g.setFont(this.font.deriveFont(Font.BOLD, ThemeConfig.FONT_LARGE));
        FontMetrics fm = g.getFontMetrics();
        g.setColor(java.awt.Color.WHITE);
        int tx = cx - fm.stringWidth(title) / 2;
        g.drawString(title, tx, cy);
        g.setFont(this.font.deriveFont(ThemeConfig.FONT_MEDIUM));
        fm = g.getFontMetrics();
        String stats = "Moves: " + view.getCurrentMoveIdx();
        g.drawString(stats, cx - fm.stringWidth(stats) / 2, cy + 48);
        int y = cy + 88;
        for (int i = 0; i < view.getNumPlayers(); i++) {
            String line = agentDisplayNames[i] + ": " + view.getHandView(i).size() + " cards";
            g.drawString(line, cx - fm.stringWidth(line) / 2, y);
            y += fm.getHeight();
        }
        Random rnd = new Random(Math.max(0, winner));
        for (int k = 0; k < 80; k++) {
            g.setColor(new java.awt.Color(rnd.nextInt(256), rnd.nextInt(256), rnd.nextInt(256), 200));
            g.fillOval(rnd.nextInt(ThemeConfig.BOARD_W), rnd.nextInt(ThemeConfig.BOARD_H), 6, 6);
        }
    }

    private int winnerLogicalIdx(Game.GameView view) {
        for (int i = 0; i < view.getNumPlayers(); i++) {
            if (view.getHandView(i).size() == 0) {
                return i;
            }
        }
        return -1;
    }
}
