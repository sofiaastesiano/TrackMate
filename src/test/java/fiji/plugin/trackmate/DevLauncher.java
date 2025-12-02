package fiji.plugin.trackmate;

import ij.ImageJ;
import ij.ImagePlus;
import ij.IJ;

/**
 * Simple development launcher that opens ImageJ and TrackMate GUI directly.
 * Usage: Run this class, it will open a sample image and launch TrackMate automatically.
 */
public class DevLauncher {

    public static void main(String[] args) {
        System.out.println("================================================");
        System.out.println("  TrackMate Development Launcher");
        System.out.println("================================================");

        // Start ImageJ
        new ImageJ();
        System.out.println("✓ ImageJ started");

        // Open a sample image
        System.out.println("✓ Opening sample image...");
        ImagePlus imp = IJ.openImage("/Users/gastonminetii/Downloads/MAX230426_bCatTracking_Exp91_20230426_73604 AM_f0000_t0000.tif");

        if (imp == null) {
            System.out.println("! Could not download sample image from internet");
            System.out.println("! Please open your own image: File → Open");
            System.out.println("! Then run TrackMate manually or type 'go()' in console");
            return;
        }

        imp.show();
        System.out.println("✓ Image opened: " + imp.getTitle());

        // Give ImageJ time to fully initialize
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // Launch TrackMate
        System.out.println("✓ Launching TrackMate...");
        System.out.println("================================================\n");

        new TrackMatePlugIn().run("");
    }
}
