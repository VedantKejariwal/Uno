package edu.bu.pas.uno.ui.frontend;

/**
 * Spectator-facing agent labels derived from {@link Class#getSimpleName()} values.
 */
public final class UiLabeling {

    private UiLabeling() {
    }

    /**
     * Display name for HUD labels, tickers, and the bottom strip.
     *
     * @param simpleName {@link Class#getSimpleName()} of the agent
     */
    public static String shortAgentName(String simpleName) {
        if (simpleName == null || simpleName.isEmpty()) {
            return "?";
        }
        return switch (simpleName) {
            case "UCTAgent" -> "UCTAgent";
            case "RandomAgent" -> "Random AI";
            case "ExpectedOutcomeAgent" -> "ExpectedOutcomeAgent";
            default -> simpleName;
        };
    }

    /**
     * Player hand caption: e.g. {@code UCTAgent · 7 cards}.
     */
    public static String handLabel(String displayName, int cards) {
        return displayName + "  ·  " + cards + (cards == 1 ? " card" : " cards");
    }
}
