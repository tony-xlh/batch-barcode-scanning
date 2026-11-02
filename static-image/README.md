# Static Image Barcode Reader

An Android sample that lets the user pick a photo, decodes all barcodes in it with
[Dynamsoft Barcode Reader](https://www.dynamsoft.com/barcode-reader/docs/mobile/programming/android/),
and highlights each result directly on the image.

## Features

- Pick an image with the system photo picker (`ActivityResultContracts.GetContent`).
- Decode using `CaptureVisionRouter` with the built-in `PT_READ_BARCODES` preset.
- Draw the returned location quadrilaterals over the image with a custom overlay view.
- List the decoded barcode format and text below the image.
- Normalize EXIF orientation and downscale large photos before decoding, so overlay
  coordinates always match the displayed bitmap.

## Requirements

- Android Studio / JDK 17
- Android SDK 35
- Android 7.0 (API 24) or newer
- A Dynamsoft Barcode Reader license

## License

`MainActivity.LICENSE_KEY` contains the time-limited trial key used in the Dynamsoft
documentation. It requires network access to verify. For real use, request a key at
<https://www.dynamsoft.com/customer/license/trialLicense?product=dbr> and replace the
constant.

## Build and run

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Or open the `static-image` folder in Android Studio and run the `app` configuration.

## Dependencies

| Dependency | Version |
| --- | --- |
| Dynamsoft Barcode Reader bundle | 11.6.4000 |
| AndroidX AppCompat | 1.7.0 |
| AndroidX Activity | 1.9.3 |
| AndroidX ExifInterface | 1.4.2 |

The Dynamsoft AARs are resolved from
`https://download2.dynamsoft.com/maven/aar`.

## Project layout

- `MainActivity` - picker, background decoding, result presentation.
- `ImageUtils` - loads the URI, applies EXIF orientation, downscales to 4096 px,
  and produces JPEG bytes for both display and decoding.
- `BarcodeOverlayView` - maps barcode points from image space to `fitCenter` view
  space and draws polygons plus numbered badges.
