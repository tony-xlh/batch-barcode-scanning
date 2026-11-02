package com.dynamsoft.staticimage;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws barcode location polygons on top of an ImageView using fitCenter scaling.
 */
public class BarcodeOverlayView extends View {
    private static final int[] COLORS = {
            0xFFFF8A00, // orange
            0xFF007AFF, // blue
            0xFF34C759, // green
            0xFFAF52DE, // purple
            0xFFFF2D55, // pink
            0xFF00C7BE  // teal
    };

    private final List<BarcodeBox> barcodes = new ArrayList<>();
    private final Path path = new Path();
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint badgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint numberPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;

    private int imageWidth;
    private int imageHeight;

    public BarcodeOverlayView(Context context) {
        this(context, null);
    }

    public BarcodeOverlayView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;

        fillPaint.setStyle(Paint.Style.FILL);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(3f * density);
        strokePaint.setStrokeJoin(Paint.Join.ROUND);

        numberPaint.setColor(Color.WHITE);
        numberPaint.setTextAlign(Paint.Align.CENTER);
        numberPaint.setTextSize(13f * density);
        numberPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
    }

    public void setImageSize(int width, int height) {
        imageWidth = width;
        imageHeight = height;
        invalidate();
    }

    public void setBarcodes(List<BarcodeBox> boxes) {
        barcodes.clear();
        barcodes.addAll(boxes);
        invalidate();
    }

    public void clearBarcodes() {
        barcodes.clear();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (barcodes.isEmpty() || imageWidth <= 0 || imageHeight <= 0 || getWidth() == 0 || getHeight() == 0) {
            return;
        }

        float scale = Math.min((float) getWidth() / imageWidth, (float) getHeight() / imageHeight);
        float offsetX = (getWidth() - imageWidth * scale) / 2f;
        float offsetY = (getHeight() - imageHeight * scale) / 2f;
        float badgeRadius = 12f * density;

        for (BarcodeBox barcode : barcodes) {
            Point[] points = barcode.points;
            if (points == null || points.length < 3) {
                continue;
            }

            int color = COLORS[(barcode.index - 1) % COLORS.length];
            path.reset();

            float centerX = 0f;
            float centerY = 0f;
            for (int i = 0; i < points.length; i++) {
                float x = points[i].x * scale + offsetX;
                float y = points[i].y * scale + offsetY;
                centerX += x / points.length;
                centerY += y / points.length;
                if (i == 0) {
                    path.moveTo(x, y);
                } else {
                    path.lineTo(x, y);
                }
            }
            path.close();

            fillPaint.setColor(withAlpha(color, 52));
            strokePaint.setColor(color);
            canvas.drawPath(path, fillPaint);
            canvas.drawPath(path, strokePaint);

            badgePaint.setColor(color);
            canvas.drawCircle(centerX, centerY, badgeRadius, badgePaint);

            Paint.FontMetrics metrics = numberPaint.getFontMetrics();
            float textY = centerY - (metrics.ascent + metrics.descent) / 2f;
            canvas.drawText(String.valueOf(barcode.index), centerX, textY, numberPaint);
        }
    }

    private static int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    public static final class BarcodeBox {
        public final Point[] points;
        public final int index;

        public BarcodeBox(Point[] points, int index) {
            this.points = points.clone();
            this.index = index;
        }
    }
}
