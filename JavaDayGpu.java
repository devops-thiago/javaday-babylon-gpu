/*
 * Example written for the talk "Projeto Babylon: Rodando Java na GPU" (JavaDay).
 *
 * An animated Julia fractal in a window: each GPU thread computes the color
 * of one pixel (1.44 million threads per frame). The kernel is plain Java;
 * HAT translates its code model to CUDA C at run time.
 *
 * Run it with ./run.sh (see the README).
 */
import hat.Accelerator;
import hat.Accelerator.Compute;
import hat.ComputeContext;
import hat.NDRange;
import hat.backend.Backend;
import hat.buffer.S32Array;
import jdk.incubator.code.Reflect;

import javax.swing.JFrame;
import javax.swing.JComponent;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.lang.invoke.MethodHandles;

import static hat.KernelContext.GIX;

public class JavaDayGpu {

    static final int W = 1600, H = 900, MAX_ITER = 1000;

    // ---- runs on the GPU: one thread per pixel, returns the ARGB color ----
    @Reflect
    public static void juliaKernel(S32Array out, int w, int h, float cr, float ci) {
        if (GIX() < w * h) {
            int x = GIX() % w;
            int y = GIX() / w;
            float zr = (x - w * 0.5f) * (3.0f / h);
            float zi = (y - h * 0.5f) * (3.0f / h);
            int iter = 0;
            while (zr * zr + zi * zi < 4.0f && iter < MAX_ITER) {
                float t = zr * zr - zi * zi + cr;
                zi = 2.0f * zr * zi + ci;
                zr = t;
                iter = iter + 1;
            }
            int color = 0xFF000000;                       // interior: black
            if (iter < MAX_ITER) {
                float v = iter * 0.055f;                  // smooth cyclic coloring
                int r = (int) (127.5f * (1.0f - (float) Math.cos(v)));
                int g = (int) (127.5f * (1.0f - (float) Math.cos(v * 0.7f + 1.2f)));
                int b = (int) (127.5f * (1.0f - (float) Math.cos(v * 0.4f + 2.4f)));
                color = 0xFF000000 | (r << 16) | (g << 8) | b;
            }
            out.array(GIX(), color);
        }
    }

    @Reflect
    public static void julia(ComputeContext cc, S32Array out, int w, int h, float cr, float ci) {
        cc.dispatchKernel(NDRange.of1D(w * h), () -> juliaKernel(out, w, h, cr, ci));
    }

    // ---- runs on the JVM: window and animation loop -----------------------
    static void main() {
        var accelerator = new Accelerator(MethodHandles.lookup(), Backend.FIRST);
        var out = S32Array.create(accelerator, W * H);

        var image = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        int[] pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();

        var view = new JComponent() {
            String hud = "";
            @Override protected void paintComponent(Graphics g) {
                g.drawImage(image, 0, 0, getWidth(), getHeight(), null);
                g.setColor(java.awt.Color.WHITE);
                g.setFont(g.getFont().deriveFont(18f));
                g.drawString(hud, 16, getHeight() - 18);
            }
        };
        var frame = new JFrame("JavaDay ▸ Java on the GPU ▸ Project Babylon + HAT");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.add(view);
        frame.setSize(W, H);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        for (int f = 0; frame.isDisplayable(); f++) {
            double a = 0.008 * f;
            final float cr = (float) (-0.77 + 0.135 * Math.cos(a));
            final float ci = (float) (0.145 + 0.095 * Math.sin(a * 1.3));

            long t0 = System.nanoTime();
            accelerator.compute((@Reflect Compute) cc -> julia(cc, out, W, H, cr, ci));
            double gpuMs = (System.nanoTime() - t0) / 1e6;

            for (int i = 0; i < pixels.length; i++) pixels[i] = out.array(i);

            view.hud = String.format("%,d threads/frame on the GPU  ▸  kernel %.1f ms  ▸  c = %.3f%+.3fi  ▸  frame %d",
                    W * H, gpuMs, cr, ci, f);
            view.repaint();
            try { Thread.sleep(10); } catch (InterruptedException e) { return; }
        }
    }
}
