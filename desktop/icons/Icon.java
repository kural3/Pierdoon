import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import javax.imageio.ImageIO;

/**
 * Pier.doon's icon: the note-shaped P on a graphite Material "cookie", transparent around it.
 * Draws every PNG and the .ico from one place: run `java desktop/icons/Icon.java . preview.png` from the repo root.
 * The Android adaptive icon is the same mark in vectors (app/src/main/res/drawable/ic_launcher_*.xml).
 */
public class Icon {
    static final Color MARK = new Color(0xEE, 0xEA, 0xE3);
    static final Color TOP = new Color(0x3D, 0x3E, 0x43);
    static final Color BOTTOM = new Color(0x23, 0x24, 0x28);

    /** The mark in the Android foreground's 108-unit space (before its 1.15 scale). */
    static Area mark() {
        Area outer = new Area(new Rectangle2D.Double(52, 33, 5.5, 22));
        Area arc = new Area(new Ellipse2D.Double(46.5, 33, 22, 22));
        arc.intersect(new Area(new Rectangle2D.Double(57.5, 33, 11, 22)));
        outer.add(arc);
        Area hole = new Area(new Rectangle2D.Double(57, 38, 0.5, 12));
        Area holeArc = new Area(new Ellipse2D.Double(51.5, 38, 12, 12));
        holeArc.intersect(new Area(new Rectangle2D.Double(57.5, 38, 6, 12)));
        hole.add(holeArc);
        outer.subtract(hole);
        outer.add(new Area(new Rectangle2D.Double(52, 33, 5, 33.5)));
        outer.add(new Area(new Ellipse2D.Double(40, 58, 17, 17)));
        return outer;
    }

    static Shape cookie(double size) {
        Path2D p = new Path2D.Double();
        double c = size / 2, r = size * 0.46;
        for (int i = 0; i <= 1440; i++) {
            double t = i * Math.PI * 2 / 1440;
            double rr = r * (1 + 0.042 * Math.cos(10 * t));
            double x = c + rr * Math.sin(t), y = c - rr * Math.cos(t);
            if (i == 0) p.moveTo(x, y); else p.lineTo(x, y);
        }
        p.closePath();
        return p;
    }

    static BufferedImage render(int size) {
        int ss = size < 64 ? 8 : 4; // supersampled, then scaled down: crisp edges at 16 px too
        int big = size * ss;
        BufferedImage img = new BufferedImage(big, big, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setPaint(new GradientPaint(0, 0, TOP, 0, big, BOTTOM));
        g.fill(cookie(big));
        // A faint light rim: the graphite would sink into a dark taskbar without it.
        g.setStroke(new BasicStroke((float) Math.max(big * 0.012, ss * 0.8)));
        g.setPaint(new GradientPaint(0, 0, new Color(255, 255, 255, 70), 0, big, new Color(255, 255, 255, 14)));
        g.draw(cookie(big));
        // The mark a touch bigger than on Android: the cookie is the whole icon here.
        double k = big / 108.0 * (size <= 32 ? 1.62 : 1.45);
        AffineTransform t = new AffineTransform();
        t.translate(big / 2.0, big / 2.0);
        t.scale(k, k);
        t.translate(-54.25, -54);
        g.setColor(MARK);
        g.fill(t.createTransformedShape(mark()));
        g.dispose();
        return scale(img, size);
    }

    static BufferedImage scale(BufferedImage src, int size) {
        BufferedImage cur = src;
        while (cur.getWidth() > size) {
            int w = Math.max(size, cur.getWidth() / 2);
            BufferedImage next = new BufferedImage(w, w, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = next.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(cur, 0, 0, w, w, null);
            g.dispose();
            cur = next;
        }
        return cur;
    }

    static byte[] png(BufferedImage img) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    static void ico(Path file, int... sizes) throws IOException {
        byte[][] data = new byte[sizes.length][];
        for (int i = 0; i < sizes.length; i++) data[i] = png(render(sizes[i]));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DataOutputStream d = new DataOutputStream(out);
        d.writeShort(0); d.writeShort(Short.reverseBytes((short) 1)); d.writeShort(Short.reverseBytes((short) sizes.length));
        int offset = 6 + 16 * sizes.length;
        for (int i = 0; i < sizes.length; i++) {
            d.writeByte(sizes[i] >= 256 ? 0 : sizes[i]); d.writeByte(sizes[i] >= 256 ? 0 : sizes[i]);
            d.writeByte(0); d.writeByte(0);
            d.writeShort(Short.reverseBytes((short) 1)); d.writeShort(Short.reverseBytes((short) 32));
            d.writeInt(Integer.reverseBytes(data[i].length)); d.writeInt(Integer.reverseBytes(offset));
            offset += data[i].length;
        }
        for (byte[] b : data) d.write(b);
        Files.write(file, out.toByteArray());
    }

    public static void main(String[] a) throws IOException {
        Path root = Paths.get(a[0]);
        Path preview = Paths.get(a[1]);
        ImageIO.write(render(512), "png", root.resolve("desktop/icons/pierdoon.png").toFile());
        ImageIO.write(render(512), "png", root.resolve("ui/src/jvmMain/composeResources/drawable/pierdoon.png").toFile());
        ico(root.resolve("desktop/icons/pierdoon.ico"), 16, 20, 24, 32, 40, 48, 64, 128, 256);
        String[] dens = {"mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"};
        int[] px = {48, 72, 96, 144, 192};
        for (int i = 0; i < dens.length; i++) {
            Path dir = root.resolve("app/src/main/res/mipmap-" + dens[i]);
            ImageIO.write(render(px[i]), "png", dir.resolve("ic_launcher.png").toFile());
            ImageIO.write(render(px[i]), "png", dir.resolve("ic_launcher_round.png").toFile());
        }
        // Preview: on a dark and a light taskbar, at a few sizes.
        BufferedImage p = new BufferedImage(900, 360, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = p.createGraphics();
        g.setColor(new Color(0x20, 0x20, 0x20)); g.fillRect(0, 0, 900, 180);
        g.setColor(new Color(0xF3, 0xF3, 0xF3)); g.fillRect(0, 180, 900, 180);
        int[] show = {160, 64, 48, 32, 24, 16};
        for (int row = 0; row < 2; row++) {
            int x = 10;
            for (int s : show) { g.drawImage(render(s), x, row * 180 + 10 + (160 - s) / 2, null); x += s + 30; }
        }
        g.dispose();
        ImageIO.write(p, "png", preview.toFile());
    }
}
