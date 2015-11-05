package eshque.com.imagebrowserlol;

import android.graphics.Bitmap;
import android.os.Build;
import android.support.v4.util.LruCache;

import java.util.ArrayList;
import java.util.zip.CRC32;

public final class ImageCache {
    private ImageCache() {}

    private static LruCache<String, Bitmap> storage;

    public static void init() {
        final int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        final int cacheSize = 5 * maxMemory / 8;
        MainActivity.logDebug("Cache max memory: "+cacheSize);
        storage = new LruCache<String, Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB_MR1) {
                    return bitmap.getRowBytes() * bitmap.getHeight() / 1024;
                } else {
                    return bitmap.getByteCount() / 1024;
                }
            }
        };
    }

    public static void addBitmap(String url, Bitmap bitmap) {
        if (getBitmap(url) == null) {
            storage.put(url, bitmap);
        }
    }

    public static Bitmap getBitmap(String key) {
        return storage.get(key);
    }

    private static class InvalidUrlEntry {
        public long crc;
        public String url;
    }

    private static ArrayList<InvalidUrlEntry> invalidUrls = new ArrayList<>();

    private static long crcForUrl(String url) {
        CRC32 crc = new CRC32();
        crc.update(url.getBytes());
        return crc.getValue();
    }

    public static void markInvalid(String url) {
        if(url == null)
            throw new NullPointerException("URL can't be null");
        InvalidUrlEntry e = new InvalidUrlEntry();
        e.crc = crcForUrl(url);
        e.url = url;
        invalidUrls.add(e);
    }

    public static boolean isInvalid(String url) {
        long crc = crcForUrl(url);
        for (int i = 0; i < invalidUrls.size(); ++i) {
            InvalidUrlEntry e = invalidUrls.get(i);
            if (e.crc == crc && e.url.equals(url)) {
                return true;
            }
        }

        return false;
    }
}
