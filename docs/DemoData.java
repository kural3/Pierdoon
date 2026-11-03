import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * A made-up library for screenshots: fictional artists and albums, abstract covers, silent
 * tracks of real lengths, a week of plays, favourites, playlists and a download log.
 * Usage: java docs/DemoData.java <ffmpeg.exe> <demo dir> <settings.properties to start from>, then
 * `./gradlew shots -PshotsHome=<demo dir> ...` and `java docs/Banners.java` for the README pictures.
 * ffmpeg.exe is the one JavaCV unpacks under ~/.javacpp/cache.
 */
public class DemoData {
    record Album(String artist, String title, Color a, Color b, Color c, int motif, String[] tracks) {}

    static final List<Album> ALBUMS = List.of(
        new Album("Nova Harbor", "Low Tide Lights", new Color(0x1B3B5A), new Color(0xF2A65A), new Color(0xF6E7CB), 0,
            new String[]{"Paper Moons", "Harbor Lights", "Slow Signal"}),
        new Album("Mira Vale", "Glass Garden", new Color(0x2E1F47), new Color(0xD98FB5), new Color(0x9AD1D4), 1,
            new String[]{"Glass Garden", "Velvet Static", "Afterglow"}),
        new Album("The Quiet Arcade", "Neon Postcards", new Color(0x0F1A2B), new Color(0x35E0C2), new Color(0xFF5D8F), 2,
            new String[]{"Midnight Arcade", "Postcard from Nowhere", "Pixel Rain"}),
        new Album("Juno Park", "Soft Machines", new Color(0xE9E3D5), new Color(0xE2553F), new Color(0x2C2C34), 3,
            new String[]{"Soft Machines", "Coffee at 3 AM", "Satellite Heart"}),
        new Album("Kite & Compass", "Northbound", new Color(0x24443A), new Color(0xE7C76A), new Color(0xA7C4A0), 4,
            new String[]{"Northbound", "Wildflower Road", "Open Window"}),
        new Album("Ostrava Sun", "Summer Archive", new Color(0xF4B860), new Color(0xC8553D), new Color(0x2B2D42), 5,
            new String[]{"Summer Archive", "Tape Delay", "Golden Hour"}),
        new Album("Lumen Drift", "Weightless", new Color(0x090C1A), new Color(0x6C7BFF), new Color(0xC9D1FF), 0,
            new String[]{"Weightless", "Blue Hours", "Orbit"}),
        new Album("Fennel", "Small Rooms", new Color(0xB9D3C2), new Color(0x3E5641), new Color(0xF0EAD2), 3,
            new String[]{"Small Rooms", "Sunday Bus", "Elevator Song"})
    );

    public static void main(String[] a) throws Exception {
        String ffmpeg = a[0];
        Path demo = Paths.get(a[1]), music = demo.resolve("music"), files = demo.resolve("files");
        Files.createDirectories(music);
        Files.createDirectories(files);
        Files.createDirectories(demo.resolve("covers"));
        Random rnd = new Random(7);
        List<Long> ids = new ArrayList<>();
        List<Integer> lengths = new ArrayList<>();
        for (Album al : ALBUMS) {
            Path cover = demo.resolve("covers").resolve(al.title().replace(' ', '_') + ".jpg");
            ImageIO.write(cover(al, 640), "jpg", cover.toFile());
            for (int i = 0; i < al.tracks().length; i++) {
                String title = al.tracks()[i];
                String artist = al.artist();
                if (title.equals("Satellite Heart")) artist = "Juno Park feat. Mira Vale";
                File out = music.resolve(al.artist().replace("&", "and") + " - " + title + ".m4a").toFile();
                int seconds = 150 + rnd.nextInt(110);
                if (!out.exists()) {
                    Process p = new ProcessBuilder(ffmpeg, "-y", "-loglevel", "error",
                        "-f", "lavfi", "-i", "anullsrc=r=22050:cl=mono", "-i", cover.toString(),
                        "-t", String.valueOf(seconds), "-map", "0:a", "-map", "1:v",
                        "-c:a", "aac", "-b:a", "16k", "-c:v", "copy", "-disposition:v:0", "attached_pic",
                        "-metadata", "title=" + title, "-metadata", "artist=" + artist,
                        "-metadata", "album=" + al.title(), "-metadata", "album_artist=" + al.artist(),
                        "-metadata", "track=" + (i + 1), "-metadata", "date=2026",
                        out.toString()).inheritIO().start();
                    if (p.waitFor() != 0) throw new IOException("ffmpeg failed on " + out);
                }
                ids.add(idFor(out));
                lengths.add(seconds);
            }
        }
        // A week of listening, most of it to a few favourites.
        long now = System.currentTimeMillis();
        StringBuilder plays = new StringBuilder();
        List<Long> order = new ArrayList<>();
        int[] weight = new int[ids.size()];
        for (int i = 0; i < weight.length; i++) weight[i] = 1 + rnd.nextInt(3) + (i / 3 == 0 || i / 3 == 1 || i / 3 == 3 ? 4 : 0);
        int total = Arrays.stream(weight).sum();
        long t = now - 7L * 24 * 3600 * 1000;
        for (int n = 0; n < 160; n++) {
            int pick = rnd.nextInt(total), k = 0;
            while (pick >= weight[k]) pick -= weight[k++];
            t += (long) ((7.0 * 24 * 3600 * 1000 / 170) * (0.5 + rnd.nextDouble()));
            if (t > now - 60_000) break;
            plays.append(ids.get(k)).append(' ').append(t).append(' ').append(lengths.get(k) * 1000L).append('\n');
            order.add(0, ids.get(k));
        }
        Files.writeString(files.resolve("plays.log"), plays.toString());
        LinkedHashSet<Long> recent = new LinkedHashSet<>(order);
        List<Long> fav = List.of(ids.get(0), ids.get(4), ids.get(7), ids.get(10), ids.get(13), ids.get(19));
        String lib = "{\"version\":1,\"favorites\":" + fav + ",\"recent\":" + new ArrayList<>(recent).subList(0, Math.min(20, recent.size()))
            + ",\"playlists\":["
            + playlist(1, "Late night", ids, 6, 1, 19, 20, 8, 22, 3)
            + "," + playlist(2, "Focus", ids, 18, 19, 20, 14, 2, 23)
            + "," + playlist(3, "Road trip", ids, 12, 13, 15, 16, 17, 9, 11) + "]}";
        Files.writeString(files.resolve("library.json"), lib.replace(" ", ""));
        Files.writeString(files.resolve("download-log.json"), "[" +
            log(now - 40 * 60_000, false, "https://www.youtube.com/watch?v=x7Kq2LmP0aE", "Velvet Static (live)", "Mira Vale", "Bot",
                "WARNING: [youtube] No supported JavaScript runtime could be found.\\nERROR: [youtube] x7Kq2LmP0aE: Sign in to confirm you\\u2019re not a bot. Use --cookies-from-browser or --cookies for the authentication.")
            + "," + log(now - 55 * 60_000, true, "https://soundcloud.com/kite-and-compass/wildflower-road", "Wildflower Road", "Kite & Compass", null, null)
            + "," + log(now - 70 * 60_000, false, "https://open.spotify.com/track/4uLU6hMCjMI75M1A2tKUQC", null, null, "Unsupported",
                "ERROR: Unsupported URL: https://open.spotify.com/track/4uLU6hMCjMI75M1A2tKUQC")
            + "]");
        // The user's settings, pointed at this library, in English.
        Properties s = new Properties();
        try (Reader r = Files.newBufferedReader(Paths.get(a[2]), StandardCharsets.ISO_8859_1)) { s.load(r); }
        s.setProperty("music_folders", music.toAbsolutePath().toString());
        s.setProperty("excluded_folders", "");
        s.setProperty("language", "en");
        s.remove("download_folder");
        try (Writer w = Files.newBufferedWriter(files.resolve("settings.properties"), StandardCharsets.ISO_8859_1)) { s.store(w, null); }
        System.out.println(ids.size() + " tracks, " + plays.toString().split("\n").length + " plays");
    }

    static String playlist(long id, String name, List<Long> ids, int... idx) {
        List<Long> songs = new ArrayList<>();
        for (int i : idx) songs.add(ids.get(i));
        return "{\"id\":" + id + ",\"name\":\"" + name + "\",\"songs\":" + songs + "}";
    }

    static String log(long time, boolean ok, String url, String title, String artist, String reason, String message) {
        StringBuilder b = new StringBuilder("{\"time\":" + time + ",\"ok\":" + ok + ",\"url\":\"" + url + "\"");
        if (title != null) b.append(",\"title\":\"").append(title).append('"');
        if (artist != null) b.append(",\"artist\":\"").append(artist).append('"');
        if (reason != null) b.append(",\"reason\":\"").append(reason).append('"');
        if (message != null) b.append(",\"message\":\"").append(message).append('"');
        return b.append('}').toString();
    }

    /** Song.idFor: an FNV-style hash of the lower-cased absolute path. */
    static long idFor(File f) {
        String s = f.getAbsolutePath().toLowerCase(Locale.ROOT);
        long h = -0x340d631b7bdddcdbL;
        for (int i = 0; i < s.length(); i++) {
            h ^= s.charAt(i);
            h *= 0x100000001b3L;
        }
        return h & Long.MAX_VALUE;
    }

    /** Abstract album art: a gradient field and one motif per album, with a little grain. */
    static BufferedImage cover(Album al, int n) {
        BufferedImage img = new BufferedImage(n, n, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setPaint(new GradientPaint(0, 0, al.a(), n, n, al.a().darker()));
        g.fillRect(0, 0, n, n);
        Random r = new Random(al.title().hashCode());
        switch (al.motif()) {
            case 0 -> { // a low sun over rings
                for (int i = 9; i > 0; i--) {
                    g.setColor(mix(al.a(), al.b(), i / 10f));
                    double d = n * 0.1 * i;
                    g.fill(new Ellipse2D.Double(n / 2.0 - d / 2, n * 0.62 - d / 2, d, d));
                }
                g.setColor(al.c());
                g.fill(new Ellipse2D.Double(n * 0.39, n * 0.51, n * 0.22, n * 0.22));
            }
            case 1 -> { // soft overlapping blobs
                for (int i = 0; i < 7; i++) {
                    Color c = i % 2 == 0 ? al.b() : al.c();
                    g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 120));
                    double d = n * (0.3 + r.nextDouble() * 0.4);
                    g.fill(new Ellipse2D.Double(r.nextDouble() * n - d / 3, r.nextDouble() * n - d / 3, d, d));
                }
            }
            case 2 -> { // a perspective grid under a stripe sun
                g.setColor(al.c());
                g.fill(new Ellipse2D.Double(n * 0.25, n * 0.12, n * 0.5, n * 0.5));
                g.setColor(al.a());
                for (int i = 0; i < 6; i++) g.fillRect(0, (int) (n * (0.38 + i * 0.045)), n, (int) (n * 0.012 * (i + 1)));
                g.setStroke(new BasicStroke(n / 220f));
                g.setColor(al.b());
                for (int i = 0; i <= 12; i++) g.draw(new Line2D.Double(n / 2.0, n * 0.62, -n + i * n * 0.25, n));
                for (int i = 0; i < 7; i++) { double y = n * 0.62 + Math.pow(i / 6.0, 1.8) * n * 0.38; g.draw(new Line2D.Double(0, y, n, y)); }
            }
            case 3 -> { // bold geometry
                g.setColor(al.b());
                g.fill(new Ellipse2D.Double(n * 0.12, n * 0.14, n * 0.46, n * 0.46));
                g.setColor(al.c());
                g.fill(new Rectangle2D.Double(n * 0.42, n * 0.44, n * 0.42, n * 0.42));
                g.setColor(al.a());
                g.setStroke(new BasicStroke(n / 40f));
                g.draw(new Line2D.Double(n * 0.1, n * 0.9, n * 0.9, n * 0.1));
            }
            case 4 -> { // hills
                for (int i = 0; i < 5; i++) {
                    Path2D p = new Path2D.Double();
                    double base = n * (0.45 + i * 0.12);
                    p.moveTo(0, n);
                    for (int x = 0; x <= n; x += 8) p.lineTo(x, base + Math.sin(x / (n * 0.18) + i * 1.7) * n * 0.05);
                    p.lineTo(n, n);
                    p.closePath();
                    g.setColor(mix(al.c(), al.a(), i / 5f));
                    g.fill(p);
                }
                g.setColor(al.b());
                g.fill(new Ellipse2D.Double(n * 0.64, n * 0.16, n * 0.16, n * 0.16));
            }
            default -> { // stripes, like a faded tape label
                for (int i = 0; i < 9; i++) {
                    g.setColor(i % 3 == 0 ? al.b() : i % 3 == 1 ? al.c() : al.a());
                    g.fillRect(0, (int) (n * 0.08 + i * n * 0.06), n, (int) (n * 0.045));
                }
                g.setColor(al.c());
                g.fill(new RoundRectangle2D.Double(n * 0.14, n * 0.66, n * 0.72, n * 0.18, n * 0.04, n * 0.04));
            }
        }
        for (int i = 0; i < n * n / 6; i++) { // grain
            int x = r.nextInt(n), y = r.nextInt(n), v = r.nextInt(2) == 0 ? 0 : 255;
            g.setColor(new Color(v, v, v, 10));
            g.fillRect(x, y, 1, 1);
        }
        g.dispose();
        return img;
    }

    static Color mix(Color x, Color y, float t) {
        return new Color((int) (x.getRed() + (y.getRed() - x.getRed()) * t), (int) (x.getGreen() + (y.getGreen() - x.getGreen()) * t),
            (int) (x.getBlue() + (y.getBlue() - x.getBlue()) * t));
    }
}
