package com.dynamsoft.staticimage;

import android.graphics.Bitmap;
import android.graphics.Point;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.dynamsoft.cvr.CaptureVisionRouter;
import com.dynamsoft.cvr.CapturedResult;
import com.dynamsoft.cvr.EnumPresetTemplate;
import com.dynamsoft.dbr.BarcodeResultItem;
import com.dynamsoft.dbr.DecodedBarcodesResult;
import com.dynamsoft.license.LicenseManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    // Trial license from the Dynamsoft documentation. It is time-limited and needs network access.
    // Request your own key at https://www.dynamsoft.com/customer/license/trialLicense?product=dbr
    private static final String LICENSE_KEY = "DLS2eyJvcmdhbml6YXRpb25JRCI6IjIwMDAwMSJ9";

    private ImageView imageView;
    private BarcodeOverlayView overlayView;
    private View placeholder;
    private View loading;
    private Button selectButton;
    private TextView statusText;
    private TextView resultsText;

    private final ExecutorService decodeExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private CaptureVisionRouter router;
    private Bitmap currentBitmap;
    private int activeRequest;

    private final ActivityResultLauncher<String> imagePicker =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    decodeSelectedImage(uri);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        View root = findViewById(R.id.root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return windowInsets;
        });

        imageView = findViewById(R.id.image_view);
        overlayView = findViewById(R.id.overlay_view);
        placeholder = findViewById(R.id.placeholder);
        loading = findViewById(R.id.loading);
        selectButton = findViewById(R.id.select_button);
        statusText = findViewById(R.id.status_text);
        resultsText = findViewById(R.id.results_text);

        resultsText.setMovementMethod(new ScrollingMovementMethod());

        selectButton.setOnClickListener(view -> imagePicker.launch("image/*"));
        router = new CaptureVisionRouter();

        if (savedInstanceState == null) {
            LicenseManager.initLicense(LICENSE_KEY, (isSuccess, error) -> {
                if (!isSuccess) {
                    runOnUiThread(() -> Toast.makeText(
                            MainActivity.this,
                            getString(R.string.license_failed, messageOf(error)),
                            Toast.LENGTH_LONG
                    ).show());
                }
            });
        }
    }

    private void decodeSelectedImage(Uri uri) {
        int requestId = ++activeRequest;

        selectButton.setEnabled(false);
        loading.setVisibility(View.VISIBLE);
        placeholder.setVisibility(View.GONE);
        overlayView.clearBarcodes();
        statusText.setText(R.string.status_decoding);
        resultsText.setText("");
        resultsText.setVisibility(View.GONE);

        decodeExecutor.execute(() -> {
            ImageUtils.PreparedImage prepared;
            try {
                prepared = ImageUtils.prepare(this, uri);
            } catch (IOException | OutOfMemoryError e) {
                showFailure(requestId, getString(R.string.error_load_image, messageOf(e)));
                return;
            }

            mainHandler.post(() -> {
                if (requestId != activeRequest) {
                    prepared.bitmap.recycle();
                    return;
                }
                showImage(prepared.bitmap);
            });

            try {
                CapturedResult result = router.capture(prepared.bytes, EnumPresetTemplate.PT_READ_BARCODES);
                mainHandler.post(() -> {
                    if (requestId == activeRequest) {
                        if (result == null) {
                            showFailure(requestId, getString(R.string.error_decode, "No result returned"));
                        } else {
                            showDecodeResult(result);
                        }
                    }
                });
            } catch (Exception | OutOfMemoryError e) {
                showFailure(requestId, getString(R.string.error_decode, messageOf(e)));
            }
        });
    }

    private void showImage(Bitmap bitmap) {
        Bitmap previous = currentBitmap;
        currentBitmap = bitmap;
        imageView.setImageBitmap(bitmap);
        overlayView.setImageSize(bitmap.getWidth(), bitmap.getHeight());
        if (previous != null && previous != bitmap) {
            previous.recycle();
        }
    }

    private void showDecodeResult(CapturedResult result) {
        loading.setVisibility(View.GONE);
        selectButton.setEnabled(true);

        DecodedBarcodesResult decoded = result.getDecodedBarcodesResult();
        BarcodeResultItem[] items = decoded == null ? null : decoded.getItems();

        if (items == null || items.length == 0) {
            overlayView.clearBarcodes();
            if (result.getErrorCode() != 0) {
                statusText.setText(getString(
                        R.string.status_decode_error,
                        result.getErrorCode(),
                        messageOf(result.getErrorMessage())
                ));
            } else {
                statusText.setText(R.string.status_no_barcodes);
            }
            resultsText.setVisibility(View.GONE);
            return;
        }

        List<BarcodeOverlayView.BarcodeBox> boxes = new ArrayList<>();
        StringBuilder builder = new StringBuilder();

        for (int i = 0; i < items.length; i++) {
            BarcodeResultItem item = items[i];
            if (item.getLocation() != null) {
                Point[] points = item.getLocation().points;
                if (points != null && points.length >= 3) {
                    boxes.add(new BarcodeOverlayView.BarcodeBox(points, i + 1));
                }
            }

            if (builder.length() > 0) {
                builder.append("\n\n");
            }
            builder.append(i + 1).append(". ").append(item.getFormatString()).append('\n');
            builder.append(item.getText());
        }

        overlayView.setBarcodes(boxes);
        statusText.setText(getResources().getQuantityString(
                R.plurals.status_barcodes_found,
                items.length,
                items.length
        ));
        resultsText.setText(builder.toString());
        resultsText.setVisibility(View.VISIBLE);
    }

    private void showFailure(int requestId, String message) {
        mainHandler.post(() -> {
            if (requestId != activeRequest) {
                return;
            }
            loading.setVisibility(View.GONE);
            selectButton.setEnabled(true);
            overlayView.clearBarcodes();
            statusText.setText(message);
            resultsText.setVisibility(View.GONE);
        });
    }

    private static String messageOf(Throwable throwable) {
        if (throwable == null) {
            return "Unknown error";
        }
        String message = throwable.getMessage();
        return message == null || message.isEmpty() ? throwable.getClass().getSimpleName() : message;
    }

    private static String messageOf(String message) {
        return message == null || message.isEmpty() ? "Unknown error" : message;
    }

    @Override
    protected void onDestroy() {
        decodeExecutor.shutdownNow();
        super.onDestroy();
    }
}
