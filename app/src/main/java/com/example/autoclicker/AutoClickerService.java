package com.example.autoclicker;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.hardware.display.VirtualDisplay;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.util.DisplayMetrics;
import android.net.Uri;
import android.provider.MediaStore;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

public class AutoClickerService extends AccessibilityService {

    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;

    private Bitmap targetBitmap;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // This method is called when an accessibility event occurs.
        // We can use this to detect changes in the screen content.

        AccessibilityNodeInfo source = event.getSource();
        if (source != null) {
            // Recursively search for the target image
            findAndClick(source);
        }
    }

    private void findAndClick(AccessibilityNodeInfo nodeInfo) {
        if (nodeInfo == null) {
            return;
        }

        // Check if the node is visible
        if (nodeInfo.isVisibleToUser()) {
            Rect bounds = new Rect();
            nodeInfo.getBoundsInScreen(bounds);

            // TODO: Implement image detection logic here
            // For now, we'll just click in the center of the screen
            if (isTargetImage(bounds)) {
                performClick(bounds.centerX(), bounds.centerY());
            }
        }

        for (int i = 0; i < nodeInfo.getChildCount(); i++) {
            findAndClick(nodeInfo.getChild(i));
        }
    }

    private boolean isTargetImage(Rect bounds) {
        if (targetBitmap == null) {
            return false;
        }

        try {
            // Take a screenshot of the specified bounds
            Bitmap screenshot = takeScreenshot(bounds);
            if (screenshot == null) {
                return false;
            }

            // Resize the target bitmap to match the screenshot size for comparison
            Bitmap scaledTarget = Bitmap.createScaledBitmap(targetBitmap, 
                screenshot.getWidth(), screenshot.getHeight(), true);

            // Compare the images using a simple pixel comparison
            // In a real application, you would want to use a more sophisticated
            // image matching algorithm here
            return compareImages(screenshot, scaledTarget);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private Bitmap takeScreenshot(Rect bounds) {
        if (imageReader == null) {
            return null;
        }
        try {
            Bitmap latestBitmap = imageReader.acquireLatestImage().getPlanes()[0].getBuffer().asReadOnlyBuffer().toBitmap();
            return Bitmap.createBitmap(latestBitmap, bounds.left, bounds.top, bounds.width(), bounds.height());
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private boolean compareImages(Bitmap img1, Bitmap img2) {
        // Simple pixel-by-pixel comparison
        // In a real application, you would want to use a more sophisticated
        // comparison algorithm that can handle slight variations
        if (img1.getWidth() != img2.getWidth() || img1.getHeight() != img2.getHeight()) {
            return false;
        }

        int matchingPixels = 0;
        int totalPixels = img1.getWidth() * img1.getHeight();

        for (int x = 0; x < img1.getWidth(); x++) {
            for (int y = 0; y < img1.getHeight(); y++) {
                if (img1.getPixel(x, y) == img2.getPixel(x, y)) {
                    matchingPixels++;
                }
            }
        }

        // Consider it a match if 90% of pixels match
        return (matchingPixels / (float) totalPixels) > 0.9;
    }

    private void performClick(int x, int y) {
        Path path = new Path();
        path.moveTo(x, y);
        GestureDescription.Builder builder = new GestureDescription.Builder();
        GestureDescription gesture = builder.addStroke(new GestureDescription.StrokeDescription(path, 0, 1)).build();
        dispatchGesture(gesture, null, null);
    }

    private void createVirtualDisplay() {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int density = metrics.densityDpi;
        int width = metrics.widthPixels;
        int height = metrics.heightPixels;

        imageReader = ImageReader.newInstance(width, height, android.graphics.PixelFormat.RGBA_8888, 2);
        virtualDisplay = mediaProjection.createVirtualDisplay("ScreenCapture",
                width, height, density, 
                android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(), null, null);
    }

    @Override
    public void onInterrupt() {
        // This method is called when the service is interrupted.
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.hasExtra("resultCode")) {
            int resultCode = intent.getIntExtra("resultCode", 0);
            Intent data = intent.getParcelableExtra("data");
            MediaProjectionManager mediaProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data);
            createVirtualDisplay();
        } else if (intent != null && intent.hasExtra("imageUri")) {
            String imageUriStr = intent.getStringExtra("imageUri");
            try {
                Uri imageUri = Uri.parse(imageUriStr);
                targetBitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), imageUri);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return super.onStartCommand(intent, flags, startId);
    }
}