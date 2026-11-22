import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import com.github.weisj.jsvg.SVGDocument;
import com.github.weisj.jsvg.attributes.ViewBox;
import com.github.weisj.jsvg.parser.SVGLoader;

/**
 * Build tool (not part of the app): renders the application icon SVG into the PNG images
 * an MSIX package needs (Start menu tiles, taskbar, Store logo), all on a transparent background.
 * <p>
 * Run via {@code mvn -q -pl app -am -Picons -DskipTests package}, or directly:
 * {@code java -cp <jsvg.jar> MsixAssets.java <in.svg> <out-dir>}.
 */
public final class MsixAssets {

    private static SVGDocument doc;

    public static void main(String[] args) throws Exception {
        doc = new SVGLoader().load(Path.of(args[0]).toUri().toURL());
        Path out = Path.of(args[1]);
        Files.createDirectories(out);
        for (int scale : new int[] {100, 200}) {
            String suffix = scale == 100 ? "" : ".scale-" + scale;
            double f = scale / 100.0;
            // tiles: the icon takes about two thirds of the tile height, centered
            write(out, "Square150x150Logo" + suffix, (int) (150 * f), (int) (150 * f), (int) (96 * f));
            write(out, "Wide310x150Logo" + suffix, (int) (310 * f), (int) (150 * f), (int) (96 * f));
            write(out, "Square44x44Logo" + suffix, (int) (44 * f), (int) (44 * f), (int) (44 * f));
            write(out, "StoreLogo" + suffix, (int) (50 * f), (int) (50 * f), (int) (50 * f));
        }
        // taskbar, Start menu list, Explorer: exact sizes, "unplated" = no accent-colored plate behind
        for (int size : new int[] {16, 24, 32, 48, 256}) {
            write(out, "Square44x44Logo.targetsize-" + size, size, size, size);
            write(out, "Square44x44Logo.targetsize-" + size + "_altform-unplated", size, size, size);
        }
    }

    private static void write(Path dir, String name, int width, int height, int icon) throws Exception {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        doc.render(null, g, new ViewBox((width - icon) / 2f, (height - icon) / 2f, icon, icon));
        g.dispose();
        ImageIO.write(img, "png", dir.resolve(name + ".png").toFile());
    }
}
