package edu.bu.pas.uno.ui.frontend;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;

/**
 * Loads card PNGs from the first directory on the search path that contains the file, then classpath
 * {@code /images/}). Search order:
 * <ol>
 *   <li>{@code -Duno.images.dir=/absolute/path} (folder containing {@code red0.png}, etc.)</li>
 *   <li>{@code UNO_IMAGES_DIR} environment variable (same semantics)</li>
 *   <li>{@code ${user.dir}/images} (PRD default when the JVM is started from the repo root)</li>
 * </ol>
 * scales each face to the current {@link ThemeConfig} card size once at startup, and caches
 * the results for cheap painting during the render loop.
 */
public final class ImageCardLoader {

    private static final Map<String, BufferedImage> CACHE = new HashMap<>();

    private ImageCardLoader() {
    }

    /**
     * Clears the cache and loads every known sprite key, producing placeholders when files are absent.
     */
    public static void loadAll() {
        CACHE.clear();
        String[] colors = {"red", "blue", "green", "yellow"};
        for (String c : colors) {
            for (int n = 0; n <= 9; n++) {
                putScaled(c + "_" + n, c + n + ".png");
            }
            putScaled(c + "_skip", c + "10.png");
            putScaled(c + "_reverse", c + "11.png");
            putScaled(c + "_draw2", c + "12.png");
        }
        putScaled("wild", "wild13.png");
        putScaled("wild_draw4", "wild14.png");
        putScaled("back", "back.png");
        putScaled("empty", "empty.png");
    }

    /**
     * Returns the pre-scaled front or back image for a sprite key; falls back to {@code back}, then a gray tile.
     *
     * @param key same format as {@code UnoPanel#getCardSpriteKey}
     */
    public static BufferedImage getCard(String key) {
        BufferedImage img = CACHE.get(key);
        if (img != null) {
            return img;
        }
        BufferedImage back = CACHE.get("back");
        if (back != null) {
            return back;
        }
        int tw = textureW();
        int th = textureH();
        BufferedImage ph = solidPlaceholder(tw, th, "back");
        CACHE.put("back", ph);
        return ph;
    }

    private static void putScaled(String spriteKey, String fileName) {
        BufferedImage raw = readImage(fileName);
        if (raw == null) {
            CACHE.put(spriteKey, solidPlaceholder(textureW(), textureH(), spriteKey));
            return;
        }
        CACHE.put(spriteKey, scaleToCard(toArgb(raw)));
    }

    private static int textureW() {
        return ThemeConfig.CARD_W * ThemeConfig.CARD_TEXTURE_SCALE;
    }

    private static int textureH() {
        return ThemeConfig.CARD_H * ThemeConfig.CARD_TEXTURE_SCALE;
    }

    /** Normalize type so scaling always compositing through ARGB. */
    private static BufferedImage toArgb(BufferedImage src) {
        if (src.getType() == BufferedImage.TYPE_INT_ARGB) {
            return src;
        }
        BufferedImage copy = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D cg = copy.createGraphics();
        cg.drawImage(src, 0, 0, null);
        cg.dispose();
        return copy;
    }

    private static List<Path> imageDirCandidates() {
        List<Path> roots = new ArrayList<>();
        String prop = System.getProperty("uno.images.dir");
        if (prop != null && !prop.isBlank()) {
            roots.add(Paths.get(prop.trim()));
        }
        String env = System.getenv("UNO_IMAGES_DIR");
        if (env != null && !env.isBlank()) {
            roots.add(Paths.get(env.trim()));
        }
        roots.add(Paths.get(System.getProperty("user.dir"), "images"));
        return roots;
    }

    private static BufferedImage readImage(String fileName) {
        for (Path dir : imageDirCandidates()) {
            try {
                Path p = dir.resolve(fileName);
                if (Files.isRegularFile(p)) {
                    return ImageIO.read(p.toFile());
                }
            } catch (IOException ignored) {
                // try next root
            }
        }
        try (InputStream in = ImageCardLoader.class.getResourceAsStream("/images/" + fileName)) {
            if (in != null) {
                return ImageIO.read(in);
            }
        } catch (IOException ignored) {
            // missing asset
        }
        return null;
    }

    /**
     * Downscales sources to {@link ThemeConfig#CARD_TEXTURE_SCALE}× the logical card size.
     * Multi-step shrink preserves detail; final textures are drawn into {@code CARD_W}×{@code CARD_H}
     * each frame with bicubic hints for a crisp result on screen.
     */
    private static BufferedImage scaleToCard(BufferedImage src) {
        int w = textureW();
        int h = textureH();
        BufferedImage cur = src;
        int cw = cur.getWidth();
        int ch = cur.getHeight();
        while (cw > w * 2 && ch > h * 2) {
            int nw = Math.max(w, cw / 2);
            int nh = Math.max(h, ch / 2);
            cur = scaleToSize(cur, nw, nh);
            cw = nw;
            ch = nh;
        }
        if (cw != w || ch != h) {
            cur = scaleToSize(cur, w, h);
        }
        return cur;
    }

    private static BufferedImage scaleToSize(BufferedImage src, int w, int h) {
        BufferedImage scaled = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D sg = scaled.createGraphics();
        sg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        sg.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        sg.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
        sg.drawImage(src, 0, 0, w, h, null);
        sg.dispose();
        return scaled;
    }

    private static BufferedImage solidPlaceholder(int w, int h, String key) {
        BufferedImage im = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = im.createGraphics();
        g.setColor(java.awt.Color.DARK_GRAY);
        g.fillRect(0, 0, w, h);
        g.setColor(java.awt.Color.LIGHT_GRAY);
        g.drawRect(0, 0, w - 1, h - 1);
        g.dispose();
        return im;
    }
}
