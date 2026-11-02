package com.dynamsoft.staticimage;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;

import androidx.exifinterface.media.ExifInterface;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Loads the selected image, applies its EXIF orientation and shrinks it to a safe size.
 * The normalized bitmap and the JPEG bytes share the same pixel coordinates, which keeps
 * the decoded barcode locations aligned with what is shown on screen.
 */
public final class ImageUtils {
    private static final int MAX_EDGE = 4096;
    private static final int JPEG_QUALITY = 95;

    private ImageUtils() {
    }

    public static PreparedImage prepare(Context context, Uri uri) throws IOException {
        ContentResolver resolver = context.getContentResolver();

        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream input = openStream(resolver, uri)) {
            BitmapFactory.decodeStream(input, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw new IOException("Unsupported or empty image");
        }

        int sampleSize = 1;
        while (Math.max(bounds.outWidth, bounds.outHeight) / sampleSize > MAX_EDGE) {
            sampleSize *= 2;
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sampleSize;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;

        Bitmap bitmap;
        try (InputStream input = openStream(resolver, uri)) {
            bitmap = BitmapFactory.decodeStream(input, null, options);
        }
        if (bitmap == null) {
            throw new IOException("Unsupported or empty image");
        }

        int orientation = ExifInterface.ORIENTATION_NORMAL;
        try (InputStream input = openStream(resolver, uri)) {
            ExifInterface exif = new ExifInterface(input);
            orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
            );
        } catch (IOException ignored) {
            // Some formats (for example PNG) may not contain EXIF data.
        }

        Bitmap normalized = applyOrientation(bitmap, orientation);
        if (normalized != bitmap) {
            bitmap.recycle();
        }

        ByteArrayOutputStream jpeg = new ByteArrayOutputStream();
        if (!normalized.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, jpeg)) {
            throw new IOException("Could not encode image");
        }

        return new PreparedImage(normalized, jpeg.toByteArray());
    }

    private static InputStream openStream(ContentResolver resolver, Uri uri) throws IOException {
        InputStream input = resolver.openInputStream(uri);
        if (input == null) {
            throw new IOException("Could not open the selected file");
        }
        return input;
    }

    private static Bitmap applyOrientation(Bitmap bitmap, int orientation) {
        Matrix matrix = new Matrix();
        switch (orientation) {
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                matrix.setScale(-1f, 1f);
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                matrix.setRotate(180f);
                break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                matrix.setRotate(180f);
                matrix.postScale(-1f, 1f);
                break;
            case ExifInterface.ORIENTATION_TRANSPOSE:
                matrix.setRotate(90f);
                matrix.postScale(-1f, 1f);
                break;
            case ExifInterface.ORIENTATION_ROTATE_90:
                matrix.setRotate(90f);
                break;
            case ExifInterface.ORIENTATION_TRANSVERSE:
                matrix.setRotate(-90f);
                matrix.postScale(-1f, 1f);
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                matrix.setRotate(-90f);
                break;
            default:
                return bitmap;
        }

        Bitmap rotated = Bitmap.createBitmap(
                bitmap,
                0,
                0,
                bitmap.getWidth(),
                bitmap.getHeight(),
                matrix,
                true
        );
        return rotated == null ? bitmap : rotated;
    }

    public static final class PreparedImage {
        public final Bitmap bitmap;
        public final byte[] bytes;

        PreparedImage(Bitmap bitmap, byte[] bytes) {
            this.bitmap = bitmap;
            this.bytes = bytes;
        }
    }
}
