import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * README pictures: the hero and one frame per feature, each screenshot as a window on graphite, lit by its own colours.
 * Run from the repo root: `java docs/Banners.java <folder with home.png, lyrics.png, delicious.png> docs desktop/icons/pierdoon.png`.
 * The screenshots come from `./gradlew :desktop:shots` at 1440x900 on the made-up library of DemoData.java.
 * home: play "Continue listening", `muted off`, 3 s. lyrics: then the player bar's expand button, about 14 s more
 * (a line half lit). delicious: on a fresh copy, play, then the sparkle button in the player bar, about 10 s.
 */
public class Banners {
    static Font font;
    static final Color INK = new Color(0xF4, 0xF1, 0xEC), MUTED = new Color(0xA3, 0xA0, 0x9A);

    public static void main(String[] a) throws Exception {
        Path shots = Paths.get(a[0]), out = Paths.get(a[1]), icon = Paths.get(a[2]);
        font = Font.createFont(Font.TRUETYPE_FONT, new File("ui/src/commonMain/composeResources/font/google_sans.ttf"));
        hero(ImageIO.read(icon.toFile()), ImageIO.read(shots.resolve("home.png").toFile()), out.resolve("readme-cover.png"));
        feature(ImageIO.read(shots.resolve("lyrics.png").toFile()), "Lyrics that light up as they’re sung",
            "Synced lyrics found for you, or written and timed by hand.", out.resolve("readme-lyrics.png"));
        feature(ImageIO.read(shots.resolve("delicious.png").toFile()), "Delicious mode",
            "On the computer: just the words, over the cover, moving with the music.", out.resolve("readme-delicious.png"));
    }

    static Graphics2D canvas(BufferedImage img) {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        return g;
    }

    /** A rounded graphite card with two soft glows in the screenshot's colours. */
    static void background(Graphics2D g, int w, int h, Color[] glow) {
        Shape card = new RoundRectangle2D.Double(0, 0, w, h, 48, 48);
        g.setClip(card);
        g.setPaint(new GradientPaint(0, 0, new Color(0x1E, 0x1F, 0x23), 0, h, new Color(0x111214)));
        g.fill(card);
        glow(g, card, glow[0], w * 0.22, h * 0.95, w * 0.55, 90);
        glow(g, card, glow[1], w * 0.82, h * 0.7, w * 0.5, 70);
    }

    static void glow(Graphics2D g, Shape area, Color c, double x, double y, double r, int alpha) {
        g.setPaint(new RadialGradientPaint(new Point2D.Double(x, y), (float) r, new float[]{0f, 1f},
            new Color[]{new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha), new Color(c.getRed(), c.getGreen(), c.getBlue(), 0)}));
        g.fill(area);
    }

    /** The screenshot as a window: rounded, a hairline edge, a soft shadow under it. */
    static void window(Graphics2D g, BufferedImage shot, double x, double y, double w) {
        double h = w * shot.getHeight() / shot.getWidth(), arc = 36;
        for (int i = 36; i > 0; i--) {
            g.setColor(new Color(0, 0, 0, (int) (3 + (36 - i) * 0.3)));
            g.fill(new RoundRectangle2D.Double(x - i * 0.5, y - i * 0.1 + 20, w + i, h + i, arc + i, arc + i));
        }
        Shape rr = new RoundRectangle2D.Double(x, y, w, h, arc, arc);
        Shape old = g.getClip();
        Area clip = new Area(old);
        clip.intersect(new Area(rr));
        g.setClip(clip);
        g.drawImage(shot, (int) x, (int) y, (int) w, (int) h, null);
        g.setClip(old);
        g.setStroke(new BasicStroke(1.5f));
        g.setColor(new Color(255, 255, 255, 30));
        g.draw(rr);
    }

    /** The two most common vivid colours of the picture, for the glows. */
    static Color[] palette(BufferedImage img) {
        Map<Integer, int[]> bins = new HashMap<>();
        for (int y = 0; y < img.getHeight(); y += 4) for (int x = 0; x < img.getWidth(); x += 4) {
            int c = img.getRGB(x, y), r = c >> 16 & 255, gr = c >> 8 & 255, b = c & 255;
            float[] hsb = Color.RGBtoHSB(r, gr, b, null);
            if (hsb[1] < 0.35f || hsb[2] < 0.3f) continue;
            int[] s = bins.computeIfAbsent((int) (hsb[0] * 12) % 12, k -> new int[4]);
            s[0] += r; s[1] += gr; s[2] += b; s[3]++;
        }
        List<int[]> sorted = new ArrayList<>(bins.values());
        sorted.sort((p, q) -> q[3] - p[3]);
        Color fallback = new Color(0xC8553D);
        Color first = sorted.isEmpty() ? fallback : avg(sorted.get(0));
        Color second = sorted.size() < 2 || sorted.get(1)[3] < sorted.get(0)[3] / 8 ? first : avg(sorted.get(1));
        return new Color[]{first, second};
    }

    static Color avg(int[] s) { return new Color(s[0] / s[3], s[1] / s[3], s[2] / s[3]); }

    static void centered(Graphics2D g, String s, float size, Color c, int cx, int baseline) {
        g.setFont(font.deriveFont(size));
        g.setColor(c);
        g.drawString(s, cx - g.getFontMetrics().stringWidth(s) / 2f, baseline);
    }

    static void hero(BufferedImage icon, BufferedImage shot, Path file) throws IOException {
        int w = 1600, h = 900;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = canvas(img);
        background(g, w, h, palette(shot));
        // The name, small and centred, the app rising from the bottom edge under it.
        g.setFont(font.deriveFont(68f));
        String name = "Pier.doon";
        int nameW = g.getFontMetrics().stringWidth(name), iconS = 76, gap = 20;
        int left = (w - (iconS + gap + nameW)) / 2;
        g.drawImage(icon, left, 64, iconS, iconS, null);
        g.setColor(INK);
        g.drawString(name, left + iconS + gap, 126);
        centered(g, "An offline music player for Android and Windows", 28f, MUTED, w / 2, 190);
        window(g, shot, 160, 250, w - 320);
        g.dispose();
        ImageIO.write(img, "png", file.toFile());
    }

    static void feature(BufferedImage shot, String title, String body, Path file) throws IOException {
        int w = 1600, h = 980;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = canvas(img);
        background(g, w, h, palette(shot));
        centered(g, title, 48f, INK, w / 2, 104);
        centered(g, body, 26f, MUTED, w / 2, 150);
        window(g, shot, 240, 206, w - 480);
        g.dispose();
        ImageIO.write(img, "png", file.toFile());
    }
}
