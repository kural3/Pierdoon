import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * README banners: the hero and one frame per feature, screenshots in a window on graphite.
 * Run from the repo root on Windows (it uses Segoe UI): `java docs/Banners.java <folder with post_home.png, post_queue.png,
 * post_log.png> docs desktop/icons/pierdoon.png`. The screenshots come from `./gradlew shots` at 1440x900.
 */
public class Banners {
    static Font bold, regular;
    static final Color INK = new Color(0xF2, 0xEF, 0xEA), MUTED = new Color(0xA9, 0xA6, 0xA0);

    public static void main(String[] a) throws Exception {
        Path shots = Paths.get(a[0]), out = Paths.get(a[1]), icon = Paths.get(a[2]);
        bold = Font.createFont(Font.TRUETYPE_FONT, new File("C:/Windows/Fonts/segoeuib.ttf"));
        regular = Font.createFont(Font.TRUETYPE_FONT, new File("C:/Windows/Fonts/segoeui.ttf"));
        BufferedImage home = ImageIO.read(shots.resolve("post_home.png").toFile());
        hero(ImageIO.read(icon.toFile()), home, out.resolve("readme-hero.png"));
        feature(home, "A fresh, expressive look",
            "Material 3 Expressive on the phone and the PC. On a wide window the screens spread into two columns.",
            false, out.resolve("readme-design.png"));
        feature(ImageIO.read(shots.resolve("post_queue.png").toFile()), "Lyrics and queue on frosted glass",
            "Synced lyrics and the queue open over the screen, so nothing moves aside. Menus and sheets are frosted too.",
            true, out.resolve("readme-glass.png"));
        feature(ImageIO.read(shots.resolve("post_log.png").toFile()), "Downloads that explain themselves",
            "Every download is logged with the full error. When YouTube asks if you\u2019re a bot, sign in once and it stops asking.",
            false, out.resolve("readme-downloads.png"));
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

    /** Graphite, a soft glow in the screenshot's own colour, and a faint cookie in a corner, like the app's cards. */
    static void background(Graphics2D g, int w, int h, Color glow, double gx, double gy, boolean cookieLeft) {
        Shape card = new RoundRectangle2D.Double(0, 0, w, h, 56, 56);
        g.setClip(card);
        g.setPaint(new GradientPaint(0, 0, new Color(0x2B, 0x2C, 0x30), 0, h, new Color(0x16, 0x17, 0x1A)));
        g.fill(card);
        float r = (float) (w * 0.55);
        g.setPaint(new RadialGradientPaint(new Point2D.Double(gx, gy), r, new float[]{0f, 1f},
            new Color[]{new Color(glow.getRed(), glow.getGreen(), glow.getBlue(), 70), new Color(glow.getRed(), glow.getGreen(), glow.getBlue(), 0)}));
        g.fill(card);
        g.setColor(new Color(255, 255, 255, 9));
        double cs = h * 0.9;
        AffineTransform t = AffineTransform.getTranslateInstance(cookieLeft ? -cs * 0.35 : w - cs * 0.65, h - cs * 0.6);
        t.rotate(Math.toRadians(18), cs / 2, cs / 2);
        g.fill(t.createTransformedShape(cookie(cs, 9, 0.06)));
    }

    static Shape cookie(double size, int lobes, double depth) {
        Path2D p = new Path2D.Double();
        double c = size / 2, r = size * 0.46;
        for (int i = 0; i <= 1440; i++) {
            double t = i * Math.PI * 2 / 1440, rr = r * (1 + depth * Math.cos(lobes * t));
            double x = c + rr * Math.sin(t), y = c - rr * Math.cos(t);
            if (i == 0) p.moveTo(x, y); else p.lineTo(x, y);
        }
        p.closePath();
        return p;
    }

    /** The screenshot as a window: rounded, a hairline edge, a soft shadow under it. */
    static void window(Graphics2D g, BufferedImage shot, double x, double y, double w) {
        double h = w * shot.getHeight() / shot.getWidth(), arc = w * 0.03;
        for (int i = 28; i > 0; i--) {
            g.setColor(new Color(0, 0, 0, (int) (5 + (28 - i) * 0.35)));
            g.fill(new RoundRectangle2D.Double(x - i * 0.6, y - i * 0.2 + 16, w + i * 1.2, h + i * 1.2, arc + i, arc + i));
        }
        Shape rr = new RoundRectangle2D.Double(x, y, w, h, arc, arc);
        Shape old = g.getClip();
        Area clip = new Area(old);
        clip.intersect(new Area(rr));
        g.setClip(clip);
        g.drawImage(shot, (int) x, (int) y, (int) w, (int) h, null);
        g.setClip(old);
        g.setStroke(new BasicStroke(1.5f));
        g.setColor(new Color(255, 255, 255, 34));
        g.draw(rr);
    }

    static Color average(BufferedImage img) {
        long r = 0, gr = 0, b = 0, n = 0;
        for (int y = 0; y < img.getHeight(); y += 8) for (int x = 0; x < img.getWidth(); x += 8) {
            int c = img.getRGB(x, y);
            int cr = c >> 16 & 255, cg = c >> 8 & 255, cb = c & 255;
            int max = Math.max(cr, Math.max(cg, cb)), min = Math.min(cr, Math.min(cg, cb));
            if (max - min < 40) continue; // only the colourful parts: covers, accents
            r += cr; gr += cg; b += cb; n++;
        }
        return n == 0 ? new Color(0x9A, 0x96, 0x8E) : new Color((int) (r / n), (int) (gr / n), (int) (b / n));
    }

    static int text(Graphics2D g, String s, Font f, Color c, int x, int y, int width, float lineGap) {
        g.setFont(f);
        g.setColor(c);
        FontMetrics m = g.getFontMetrics();
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : s.split(" ")) {
            String tryLine = line.length() == 0 ? word : line + " " + word;
            if (m.stringWidth(tryLine) > width && line.length() > 0) {
                lines.add(line.toString());
                line = new StringBuilder(word);
            } else line = new StringBuilder(tryLine);
        }
        lines.add(line.toString());
        int lh = (int) (m.getHeight() * lineGap);
        for (String l : lines) {
            y += lh;
            g.drawString(l, x, y - m.getDescent());
        }
        return y;
    }

    static void hero(BufferedImage icon, BufferedImage shot, Path file) throws IOException {
        int w = 1600, h = 680;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = canvas(img);
        background(g, w, h, average(shot), w * 0.8, h * 0.4, true);
        // The app, big and running off the right and bottom edges.
        window(g, shot, 760, 120, 1000);
        g.drawImage(icon, 92, 72, 140, 140, null);
        int y = text(g, "Pier.doon", bold.deriveFont(104f), INK, 88, 222, 700, 0.95f);
        y = text(g, "An offline music player", regular.deriveFont(32f), MUTED, 94, y + 4, 700, 1.15f);
        y = text(g, "for Android and Windows", regular.deriveFont(32f), MUTED, 94, y, 700, 1.15f);
        String[] chips = {"Material 3 Expressive", "Synced lyrics", "Radio", "Downloads", "Cassette mode"};
        g.setFont(regular.deriveFont(22f));
        FontMetrics m = g.getFontMetrics();
        int cx = 96, cy = y + 34;
        for (String c : chips) {
            int cw = m.stringWidth(c) + 36;
            if (cx + cw > 700) { cx = 96; cy += 54; }
            g.setColor(new Color(255, 255, 255, 18));
            g.fill(new RoundRectangle2D.Double(cx, cy, cw, 42, 42, 42));
            g.setColor(new Color(255, 255, 255, 40));
            g.draw(new RoundRectangle2D.Double(cx, cy, cw, 42, 42, 42));
            g.setColor(INK);
            g.drawString(c, cx + 18, cy + 29);
            cx += cw + 12;
        }
        g.dispose();
        ImageIO.write(img, "png", file.toFile());
    }

    static void feature(BufferedImage shot, String title, String body, boolean textRight, Path file) throws IOException {
        int w = 1600, h = 760;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = canvas(img);
        double sw = 1000, sx = textRight ? 80 : w - sw - 80;
        background(g, w, h, average(shot), sx + sw / 2, h / 2.0, !textRight);
        window(g, shot, sx, (h - sw * shot.getHeight() / shot.getWidth()) / 2, sw);
        int tx = textRight ? (int) (sx + sw + 64) : 80, tw = w - (int) sw - 80 - 64 - 80;
        int y = text(g, title, bold.deriveFont(54f), INK, tx, 230, tw, 0.88f);
        text(g, body, regular.deriveFont(27f), MUTED, tx, y + 20, tw, 1.12f);
        g.dispose();
        ImageIO.write(img, "png", file.toFile());
    }
}
