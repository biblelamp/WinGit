import java.awt.RenderingHints;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import com.github.weisj.jsvg.SVGDocument;
import com.github.weisj.jsvg.attributes.ViewBox;
import com.github.weisj.jsvg.parser.SVGLoader;

/**
 * Build tool (not part of the app): renders the application icon SVG into a multi-size
 * Windows ICO (PNG-compressed entries) for jpackage, optionally also a 256 px PNG.
 * <p>
 * Run via {@code mvn -q -pl app -am -Picons -DskipTests package}, or directly:
 * {@code java -cp <jsvg.jar> IconExport.java <in.svg> <out.ico> [out.png]}.
 */
public final class IconExport {
    public static void main(String[] args) throws Exception {
        Path svg = Path.of(args[0]);
        Path ico = Path.of(args[1]);
        Path png = args.length > 2 ? Path.of(args[2]) : null;
        SVGDocument doc = new SVGLoader().load(svg.toUri().toURL());
        int[] sizes = {16, 20, 24, 32, 40, 48, 64, 128, 256};
        List<byte[]> pngs = new ArrayList<>();
        for (int s : sizes) {
            BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            doc.render(null, g, new ViewBox(0, 0, s, s));
            g.dispose();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(img, "png", out);
            pngs.add(out.toByteArray());
            if (s == 256 && png != null) {
                ImageIO.write(img, "png", png.toFile());
            }
        }
        int headerSize = 6 + 16 * sizes.length;
        int total = headerSize + pngs.stream().mapToInt(b -> b.length).sum();
        ByteBuffer buf = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN);
        buf.putShort((short) 0).putShort((short) 1).putShort((short) sizes.length);
        int offset = headerSize;
        for (int i = 0; i < sizes.length; i++) {
            int s = sizes[i];
            buf.put((byte) (s >= 256 ? 0 : s)).put((byte) (s >= 256 ? 0 : s));
            buf.put((byte) 0).put((byte) 0);
            buf.putShort((short) 1).putShort((short) 32);
            buf.putInt(pngs.get(i).length).putInt(offset);
            offset += pngs.get(i).length;
        }
        for (byte[] b : pngs) {
            buf.put(b);
        }
        Files.write(ico, buf.array());
    }
}
