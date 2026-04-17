package edu.bu.pas.uno.ui.frontend;

import edu.bu.pas.uno.Game;
import edu.bu.pas.uno.enums.Color;
import edu.bu.pas.uno.history.DrewManyCardsHistoryItem;
import edu.bu.pas.uno.history.HistoryItem;
import edu.bu.pas.uno.history.PlayedCardHistoryItem;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Extra HUD painted on top of the felt: quick stats for spectators and recorders.
 * <p>
 * Each metric is intentionally simple (derived only from the public {@link Game.GameView}) so it
 * stays honest: these are heuristics, not true equilibrium win chances.
 */
public class MetricsOverlay {

    private int lastMoveNumber = -1;
    private final List<String> tickerLines = new ArrayList<>(6);

    /**
     * Refreshes cached text from the latest authoritative view.
     *
     * @param view         read-only snapshot of the table
     * @param moveNumber   {@link Game.GameView#getCurrentMoveIdx()} for the on-screen counter
     * @param lastMoveHint optional short string from outside (unused today; reserved for hooks)
     */
    public void update(Game.GameView view, int moveNumber, String lastMoveHint) {
        if (view == null) {
            return;
        }
        if (moveNumber != lastMoveNumber) {
            lastMoveNumber = moveNumber;
        }
        rebuildTicker(view);
    }

    /**
     * Paints metrics in board pixel coordinates (same space as {@code UnoPanel}'s buffer).
     *
     * @param g      board graphics context
     * @param boardW pass {@link ThemeConfig#BOARD_W}
     * @param boardH pass {@link ThemeConfig#BOARD_H}
     */
    public void render(Graphics2D g, Game.GameView view, String[] agentNames, int boardW, int boardH) {
        if (view == null) {
            return;
        }
        Object oldHints = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // ── Top center: absolute move counter (engine step index) ──
        String moveTitle = "Move #" + view.getCurrentMoveIdx();
        g.setFont(g.getFont().deriveFont(ThemeConfig.FONT_MEDIUM));
        FontMetrics fm = g.getFontMetrics();
        int mx = (boardW - fm.stringWidth(moveTitle)) / 2;
        g.setColor(new java.awt.Color(0, 0, 0, 140));
        g.fillRoundRect(mx - 8, 6, fm.stringWidth(moveTitle) + 16, fm.getHeight() + 8, 10, 10);
        g.setColor(java.awt.Color.WHITE);
        g.drawString(moveTitle, mx, 8 + fm.getAscent());

        // ── Left ticker: last six history events in newest-first order ──
        drawTicker(g);

        // ── Right stack: pseudo win odds, deck size, thinking, color chip, skulls ──
        int px = ThemeConfig.METRICS_PANEL_X;
        int py = 48;
        int panelInnerW = ThemeConfig.METRICS_PANEL_W - 8;

        g.setColor(new java.awt.Color(0, 0, 0, 120));
        g.fillRoundRect(px - 6, py - 10, ThemeConfig.METRICS_PANEL_W, boardH - py - 24, 12, 12);

        g.setColor(java.awt.Color.WHITE);
        g.setFont(g.getFont().deriveFont(ThemeConfig.FONT_LABEL));
        fm = g.getFontMetrics();
        g.drawString("Deck: " + view.getDrawPileSize(), px, py + fm.getAscent());
        py += fm.getHeight() + 8;

        // Heuristic bars: smaller hands are stronger in UNO, so weight inverse size.
        double[] weights = new double[view.getNumPlayers()];
        double sum = 0.0;
        for (int i = 0; i < view.getNumPlayers(); i++) {
            int sz = view.getHandView(i).size();
            double w = 1.0 / (0.5 + sz);
            weights[i] = w;
            sum += w;
        }

        g.drawString("Strength (heuristic)", px, py + fm.getAscent());
        py += fm.getHeight() + 4;

        for (int i = 0; i < view.getNumPlayers(); i++) {
            double share = sum <= 0.0 ? 0.0 : weights[i] / sum;
            String label = shortAgent(agentNames, i) + " ";
            g.drawString(label, px, py + fm.getAscent());
            int barY = py + fm.getHeight() + 2;
            int barH = 10;
            g.setColor(new java.awt.Color(40, 40, 40));
            g.fillRect(px, barY, panelInnerW, barH);
            g.setColor(new java.awt.Color(80, 200, 120));
            g.fillRect(px, barY, (int) (panelInnerW * share), barH);
            py = barY + barH + 10;
            g.setColor(java.awt.Color.WHITE);
        }

        py += 6;
        int currentLogical = view.getPlayerOrder().getCurrentLogicalPlayerIdx();
        boolean thinking = shortAgent(agentNames, currentLogical).contains("UCTAgent");
        if (thinking) {
            g.setColor(java.awt.Color.YELLOW);
            int dots = (int) ((System.currentTimeMillis() / 500) % 4);
            StringBuilder b = new StringBuilder("Thinking");
            for (int d = 0; d < dots; d++) {
                b.append('.');
            }
            g.drawString(b.toString(), px, py + fm.getAscent());
            py += fm.getHeight() + 8;
        }

        drawActiveColorBadge(g, view.getCurrentColor(), px, py);
        py += ThemeConfig.ACTIVE_COLOR_DIAMETER + 36;

        for (int i = 0; i < view.getNumPlayers(); i++) {
            int sz = view.getHandView(i).size();
            if (sz <= 2) {
                drawSkull(g, px, py);
                g.setColor(java.awt.Color.WHITE);
                g.drawString(shortAgent(agentNames, i), px + 22, py + 12);
                py += 28;
            }
        }

        if (oldHints != null) {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldHints);
        } else {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        }
    }

    private void drawActiveColorBadge(Graphics2D g, Color uno, int px, int py) {
        if (uno == null) {
            return;
        }
        java.awt.Color fill = awt(uno);
        int d = ThemeConfig.ACTIVE_COLOR_DIAMETER;
        double phase = (System.currentTimeMillis() % 2000) / 40.0f;
        g.setStroke(ThemeConfig.dashedRingStroke((float) phase));
        g.setColor(java.awt.Color.WHITE);
        g.drawOval(px - 2, py - 2, d + 4, d + 4);
        g.setColor(fill);
        g.fill(new Ellipse2D.Double(px, py, d, d));
        g.setColor(java.awt.Color.BLACK);
        g.setFont(g.getFont().deriveFont(Font.BOLD, 11.0f));
        String name = uno.name();
        FontMetrics fm = g.getFontMetrics();
        int tx = px + (d - fm.stringWidth(name)) / 2;
        int ty = py + (d + fm.getAscent() - fm.getDescent()) / 2;
        g.drawString(name, tx, ty);
    }

    private java.awt.Color awt(Color c) {
        return switch (c) {
            case RED -> ThemeConfig.CARD_RED;
            case BLUE -> ThemeConfig.CARD_BLUE;
            case GREEN -> ThemeConfig.CARD_GREEN;
            case YELLOW -> ThemeConfig.CARD_YELLOW;
            default -> java.awt.Color.LIGHT_GRAY;
        };
    }

    /**
     * Tiny skull marker: warns that this player is one good hit away from winning.
     */
    private void drawSkull(Graphics2D g, int x, int y) {
        g.setColor(new java.awt.Color(240, 240, 240));
        g.fillOval(x, y, 16, 16);
        g.setColor(java.awt.Color.BLACK);
        g.fillOval(x + 4, y + 5, 3, 3);
        g.fillOval(x + 9, y + 5, 3, 3);
        g.drawLine(x + 5, y + 12, x + 11, y + 12);
    }

    private String shortAgent(String[] agentNames, int logicalIdx) {
        if (agentNames == null || logicalIdx < 0 || logicalIdx >= agentNames.length) {
            return "P" + logicalIdx;
        }
        String n = agentNames[logicalIdx];
        return n == null ? "P" + logicalIdx : n;
    }

    private void rebuildTicker(Game.GameView view) {
        tickerLines.clear();
        List<HistoryItem> hist = view.getHistory();
        if (hist == null || hist.isEmpty()) {
            return;
        }
        int n = Math.min(6, hist.size());
        for (int i = 0; i < n; i++) {
            HistoryItem item = hist.get(i);
            int logical = view.getPlayerOrder().getLogicalIdx(item.getPlayerIdx());
            tickerLines.add(formatHistoryItem(logical, item));
        }
    }

    private String formatHistoryItem(int logical, HistoryItem item) {
        return switch (item.getType()) {
            case PLAYED_CARD -> {
                PlayedCardHistoryItem p = (PlayedCardHistoryItem) item;
                yield "L" + logical + " play " + p.getCard().color() + " " + p.getCard().value();
            }
            case DREW_SINGLE_CARD -> "L" + logical + " drew 1";
            case DREW_MANY_CARDS -> {
                DrewManyCardsHistoryItem d = (DrewManyCardsHistoryItem) item;
                yield "L" + logical + " drew " + d.getTotal();
            }
            case DREW_SINGLE_CARD_AND_PLAYED -> "L" + logical + " draw+play";
            case TIMEOUT_TURN_SKIPPED -> "L" + logical + " timeout";
        };
    }

    private void drawTicker(Graphics2D g) {
        int x = ThemeConfig.TICKER_X;
        int y = 48;
        g.setColor(new java.awt.Color(0, 0, 0, 110));
        g.fillRoundRect(x - 4, y - 8, ThemeConfig.TICKER_W, Math.max(80, 20 + tickerLines.size() * 18), 10, 10);
        g.setColor(java.awt.Color.WHITE);
        g.setFont(g.getFont().deriveFont(ThemeConfig.FONT_LABEL));
        FontMetrics fm = g.getFontMetrics();
        g.drawString("Recent", x, y + fm.getAscent());
        y += fm.getHeight();
        List<String> lines = new ArrayList<>(tickerLines);
        // Show oldest at bottom for reading top-down as a feed
        Collections.reverse(lines);
        for (String line : lines) {
            g.drawString(trimTo(line, 28), x, y + fm.getAscent());
            y += fm.getHeight();
        }
    }

    private String trimTo(String s, int maxChars) {
        if (s.length() <= maxChars) {
            return s;
        }
        return s.substring(0, maxChars - 1) + "…";
    }
}
