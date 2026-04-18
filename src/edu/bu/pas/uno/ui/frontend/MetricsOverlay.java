package edu.bu.pas.uno.ui.frontend;

import edu.bu.pas.uno.Game;
import edu.bu.pas.uno.enums.Color;
import edu.bu.pas.uno.enums.Value;
import edu.bu.pas.uno.history.DrewManyCardsHistoryItem;
import edu.bu.pas.uno.history.HistoryItem;
import edu.bu.pas.uno.history.PlayedCardHistoryItem;
import java.awt.BasicStroke;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.List;

/**
 * Horizontal moves ticker (above the bottom strip) plus the metrics HUD strip (PRD v3).
 */
public class MetricsOverlay {

    private static final int MAX_MOVES = 12;
    /** Long enough for labels like {@code ExpectedOutcomeAgent: +2}. */
    private static final int MAX_SEGMENT_CHARS = 24;
    private static final String ARROW = "  →  ";

    private String tickerLine = "";
    private int lastTickerMove = Integer.MIN_VALUE;
    private int lastHistorySize = -1;

    /**
     * Refreshes ticker text from {@link Game.GameView#getHistory()}.
     *
     * @param view         current omniscient or spectator view
     * @param moveNumber   engine move index (kept for API compatibility)
     * @param lastMoveHint unused
     * @param agentNames   display names (simple class names) for on-screen abbreviations
     */
    public void update(Game.GameView view, int moveNumber, String lastMoveHint, String[] agentNames) {
        if (view == null) {
            return;
        }
        List<HistoryItem> hist = view.getHistory();
        int hSize = hist == null ? 0 : hist.size();
        if (moveNumber == this.lastTickerMove && hSize == this.lastHistorySize) {
            return;
        }
        this.lastTickerMove = moveNumber;
        this.lastHistorySize = hSize;
        rebuildHorizontalTicker(view, agentNames);
    }

    /**
     * Draws the 24px moves band and the bottom HUD strip.
     */
    public void render(Graphics2D g, Game.GameView view, String[] agentNames, int boardW, int boardH) {
        if (view == null) {
            return;
        }
        Object aa = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        drawMovesTickerBar(g, boardW);
        drawBottomStrip(g, view, agentNames, boardW, boardH);

        if (aa != null) {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, aa);
        }
    }

    private void drawMovesTickerBar(Graphics2D g, int boardW) {
        int y = ThemeConfig.PLAY_AREA_H;
        int h = ThemeConfig.TICKER_H;
        g.setColor(ThemeConfig.TICKER_BG);
        g.fillRect(0, y, boardW, h);
        g.setColor(ThemeConfig.TICKER_BORDER);
        g.setStroke(new BasicStroke(1.0f));
        g.drawLine(0, y, boardW, y);
        g.drawLine(0, y + h - 1, boardW, y + h - 1);

        g.setFont(ThemeConfig.UI_FONT.deriveFont(ThemeConfig.FONT_LABEL));
        g.setColor(ThemeConfig.TICKER_TEXT);
        FontMetrics fm = g.getFontMetrics();
        int textY = y + (h - fm.getHeight()) / 2 + fm.getAscent();
        String line = tickerLine == null ? "" : tickerLine;
        g.drawString(line, 8, textY);
    }

    private void drawBottomStrip(Graphics2D g, Game.GameView view, String[] agentNames, int boardW, int boardH) {
        int stripY = boardH - ThemeConfig.BOTTOM_STRIP_H;
        int stripH = ThemeConfig.BOTTOM_STRIP_H;
        g.setColor(ThemeConfig.STRIP_BG);
        g.fillRect(0, stripY, boardW, stripH);
        g.setColor(ThemeConfig.STRIP_BORDER);
        g.setStroke(new BasicStroke(1.5f));
        g.drawLine(0, stripY, boardW, stripY);

        g.setFont(ThemeConfig.UI_FONT);
        FontMetrics fm = g.getFontMetrics();
        int midY = stripY + stripH / 2 + fm.getAscent() / 2 - 4;

        float x0 = boardW * 0.02f;
        float x1 = boardW * 0.14f;
        float xBars = boardW * 0.22f;

        g.setColor(java.awt.Color.WHITE);
        g.drawString("Move " + view.getCurrentMoveIdx(), (int) x0, midY);
        verticalDivider(g, (int) x1 - 8, stripY, stripH);

        g.drawString("Deck " + view.getDrawPileSize(), (int) x1, midY);
        verticalDivider(g, (int) xBars - 8, stripY, stripH);

        drawStrengthBarsRow(g, view, agentNames, (int) xBars, stripY, boardW - (int) xBars - 16, stripH);

        drawThinking(g, agentNames, view.getPlayerOrder().getCurrentLogicalPlayerIdx(), boardW, midY);
    }

    private void verticalDivider(Graphics2D g, int x, int stripY, int stripH) {
        g.setColor(ThemeConfig.STRIP_DIVIDER);
        g.setStroke(new BasicStroke(1f));
        g.drawLine(x, stripY + 8, x, stripY + stripH - 8);
    }

    private void drawStrengthBarsRow(Graphics2D g, Game.GameView view, String[] agentNames, int startX, int stripY, int totalWidth, int stripH) {
        int n = view.getNumPlayers();
        if (n <= 0) {
            return;
        }
        double[] weights = new double[n];
        double sum = 0.0;
        for (int i = 0; i < n; i++) {
            int sz = view.getHandView(i).size();
            double wv = 1.0 / (0.5 + sz);
            weights[i] = wv;
            sum += wv;
        }
        int slotW = Math.max(48, totalWidth / n - 10);
        int nameY = stripY + 10;
        int barY = stripY + 26;
        int pctY = stripY + 44;
        g.setFont(ThemeConfig.UI_FONT_SMALL);
        FontMetrics fm = g.getFontMetrics();
        for (int i = 0; i < n; i++) {
            int sx = startX + i * (slotW + 10);
            double share = sum <= 0 ? 0 : weights[i] / sum;
            int pct = (int) Math.round(share * 100.0);
            java.awt.Color seatCol = ThemeConfig.SEAT_BAR_COLORS[i % ThemeConfig.SEAT_BAR_COLORS.length];
            String nm = abbrev(UiLabeling.shortAgentName(shortAgent(agentNames, i)), 18);
            g.setColor(java.awt.Color.WHITE);
            g.drawString(nm, sx, nameY + fm.getAscent());
            if (view.getHandView(i).size() <= 2) {
                drawSkullMini(g, sx + fm.stringWidth(nm) + 4, nameY + 2);
            }
            g.setColor(new java.awt.Color(40, 40, 40, 220));
            g.fillRoundRect(sx, barY, slotW, 8, 4, 4);
            g.setColor(seatCol);
            g.fillRoundRect(sx, barY, Math.max(2, (int) (slotW * share)), 8, 4, 4);
            g.setColor(new java.awt.Color(200, 200, 200));
            g.drawString(pct + "%", sx, pctY + fm.getAscent());
        }
    }

    private void drawSkullMini(Graphics2D g, int x, int y) {
        g.setColor(new java.awt.Color(240, 240, 240));
        g.fillOval(x, y, 12, 12);
        g.setColor(java.awt.Color.BLACK);
        g.fillOval(x + 3, y + 3, 2, 2);
        g.fillOval(x + 7, y + 3, 2, 2);
    }

    private void drawThinking(Graphics2D g, String[] agentNames, int currentLogical, int boardW, int textY) {
        if (!"UCTAgent".equals(UiLabeling.shortAgentName(shortAgent(agentNames, currentLogical)))) {
            return;
        }
        int dots = (int) ((System.currentTimeMillis() / 500) % 4);
        StringBuilder b = new StringBuilder("Thinking");
        for (int d = 0; d < dots; d++) {
            b.append('.');
        }
        g.setColor(java.awt.Color.YELLOW);
        g.setFont(ThemeConfig.UI_FONT_BOLD.deriveFont(ThemeConfig.FONT_LABEL));
        g.drawString(b.toString(), boardW - 140, textY);
    }

    private String abbrev(String s, int max) {
        if (s.length() <= max) {
            return s;
        }
        return s.substring(0, max - 1) + "…";
    }

    private String shortAgent(String[] agentNames, int logicalIdx) {
        if (agentNames == null || logicalIdx < 0 || logicalIdx >= agentNames.length) {
            return "P" + logicalIdx;
        }
        String n = agentNames[logicalIdx];
        return n == null ? "P" + logicalIdx : n;
    }

    private void rebuildHorizontalTicker(Game.GameView view, String[] agentNames) {
        List<HistoryItem> hist = view.getHistory();
        if (hist == null || hist.isEmpty()) {
            tickerLine = "";
            return;
        }
        int from = Math.max(0, hist.size() - MAX_MOVES);
        List<String> segments = new ArrayList<>(MAX_MOVES);
        for (int i = from; i < hist.size(); i++) {
            HistoryItem item = hist.get(i);
            int logical = view.getPlayerOrder().getLogicalIdx(item.getPlayerIdx());
            String seg = trimTo(formatSegment(logical, item, agentNames), MAX_SEGMENT_CHARS);
            segments.add(seg);
        }
        tickerLine = String.join(ARROW, segments);
    }

    private String formatSegment(int logical, HistoryItem item, String[] agentNames) {
        String who = UiLabeling.shortAgentName(shortAgent(agentNames, logical));
        return switch (item.getType()) {
            case PLAYED_CARD -> {
                PlayedCardHistoryItem p = (PlayedCardHistoryItem) item;
                String c = colorAbbrev(p.getCard().color());
                String v = valueAbbrev(p.getCard().value());
                yield who + ": " + c + v;
            }
            case DREW_SINGLE_CARD -> who + ": drew";
            case DREW_MANY_CARDS -> {
                DrewManyCardsHistoryItem d = (DrewManyCardsHistoryItem) item;
                yield who + ": +" + d.getTotal();
            }
            case DREW_SINGLE_CARD_AND_PLAYED -> who + ": d+p";
            case TIMEOUT_TURN_SKIPPED -> who + ": time";
        };
    }

    private String colorAbbrev(Color c) {
        if (c == null) {
            return "?";
        }
        return switch (c) {
            case RED -> "R";
            case BLUE -> "B";
            case GREEN -> "G";
            case YELLOW -> "Y";
            default -> "";
        };
    }

    private String valueAbbrev(Value v) {
        return switch (v) {
            case ZERO, ONE, TWO, THREE, FOUR, FIVE, SIX, SEVEN, EIGHT, NINE -> String.valueOf(v.ordinal());
            case SKIP -> "Sk";
            case REVERSE -> "Rv";
            case DRAW_TWO -> "+2";
            case WILD -> "WILD";
            case WILD_DRAW_FOUR -> "+4";
            default -> "?";
        };
    }

    private String trimTo(String s, int maxChars) {
        if (s.length() <= maxChars) {
            return s;
        }
        return s.substring(0, maxChars - 1) + "…";
    }
}
