package com.visioncameracodescanner;

import static com.visioncameracodescanner.BarcodeConverter.convertToArray;
import static com.visioncameracodescanner.BarcodeConverter.convertToMap;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.media.Image;

import com.facebook.react.bridge.ReadableNativeArray;
import com.facebook.react.bridge.ReadableNativeMap;
import com.facebook.react.bridge.WritableNativeArray;
import com.facebook.react.bridge.WritableNativeMap;

import androidx.annotation.NonNull;
import androidx.camera.core.ImageProxy;

import com.google.android.gms.tasks.Tasks;
import com.mrousavy.camera.frameprocessor.FrameProcessorPlugin;
import com.google.android.gms.tasks.Task;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.common.internal.ImageConvertUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.mrousavy.camera.frameprocessor.Frame;
import com.mrousavy.camera.types.Orientation;
import java.util.HashMap;
import java.util.Map;
import java.util.Iterator;

public class VisionCameraCodeScannerPlugin extends FrameProcessorPlugin {
  private BarcodeScanner barcodeScanner = null;
  private int barcodeScannerFormatsBitmap = -1;

  private static final Set<Integer> barcodeFormats = new HashSet<>(Arrays.asList(
      Barcode.FORMAT_UNKNOWN,
      Barcode.FORMAT_ALL_FORMATS,
      Barcode.FORMAT_CODE_128,
      Barcode.FORMAT_CODE_39,
      Barcode.FORMAT_CODE_93,
      Barcode.FORMAT_CODABAR,
      Barcode.FORMAT_DATA_MATRIX,
      Barcode.FORMAT_EAN_13,
      Barcode.FORMAT_EAN_8,
      Barcode.FORMAT_ITF,
      Barcode.FORMAT_QR_CODE,
      Barcode.FORMAT_UPC_A,
      Barcode.FORMAT_UPC_E,
      Barcode.FORMAT_PDF417,
      Barcode.FORMAT_AZTEC));

  @Override
  public Map callback(Frame frame, Map<String, Object> data) {
    createBarcodeInstance(data);

    HashMap<String, Object> resultMap = new HashMap<>();

    @SuppressLint("UnsafeOptInUsageError")
    Image mediaImage = frame.getImage();
    Orientation orientation = Orientation.Companion.fromUnionValue(frame.getOrientation());

    if (mediaImage != null) {
      ArrayList<Task<List<Barcode>>> tasks = new ArrayList<Task<List<Barcode>>>();
      InputImage image = InputImage.fromMediaImage(mediaImage, orientation.toDegrees());

      if (data != null) {
        Set<Map.Entry<String, Object>> entrySet = data.entrySet();

        if (entrySet != null && entrySet.size() >= 2) {
          Iterator<Map.Entry<String, Object>> iterator = entrySet.iterator();
          iterator.next();
          Map.Entry<String, Object> secondEntry = iterator.next();
          Object secondEntryValue = secondEntry.getValue();

          if (secondEntryValue != null && secondEntryValue instanceof Map) {
            Map<String, Object> scannerOptions = (Map<String, Object>) secondEntryValue;
            boolean checkInverted = false;

            if (scannerOptions != null && scannerOptions.containsKey("checkInverted")) {
              Object checkInvertedObject = scannerOptions.get("checkInverted");
              if (checkInvertedObject instanceof Boolean) {
                checkInverted = (boolean) checkInvertedObject;
              } else if (checkInvertedObject instanceof String) {
                checkInverted = Boolean.parseBoolean((String) checkInvertedObject);
              }
            }
            if (checkInverted) {
              Bitmap bitmap = null;
              try {
                bitmap = ImageConvertUtils.getInstance().getUpRightBitmap(image);
                Bitmap invertedBitmap = this.invert(bitmap);
                InputImage invertedImage = InputImage.fromBitmap(invertedBitmap, 0);
                tasks.add(barcodeScanner.process(invertedImage));
              } catch (Exception e) {
                e.printStackTrace();
                return null;
              }
            }
          }
        }
      }
      tasks.add(barcodeScanner.process(image));
      try {
        ArrayList<Barcode> barcodes = new ArrayList<Barcode>();
        for (Task<List<Barcode>> task : tasks) {
          barcodes.addAll(Tasks.await(task));
        }

        WritableNativeArray array = new WritableNativeArray();
        for (Barcode barcode : barcodes) {
          array.pushMap(convertBarcode(barcode));
        }

        String arrayAsString = array.toString();
        resultMap.put("barcodes", arrayAsString);

      } catch (Exception e) {
        e.printStackTrace();
      }
    }
    return resultMap;
  }

  private void createBarcodeInstance(Map<String, Object> params) {

    Set<Map.Entry<String, Object>> entrySet = params.entrySet();
    Map.Entry<String, Object> firstEntry = entrySet.iterator().next();
    Object value = firstEntry.getValue();

    if (value instanceof ArrayList) {
      ArrayList<?> rawFormats = (ArrayList<?>) value;

      int formatsBitmap = 0;
      int formatsIndex = 0;
      int formatsSize = rawFormats.size();
      int[] formats = new int[formatsSize];

      for (int i = 0; i < formatsSize; i++) {
        Object rawValue = rawFormats.get(i);
        if (rawValue instanceof Number) {
          int format = ((Number) rawValue).intValue();
          if (barcodeFormats.contains(format)) {
            formats[formatsIndex] = format;
            formatsIndex++;
            formatsBitmap |= format;
          }
        } else {
          throw new IllegalArgumentException("Barcode format must be an instance of Number");
        }
      }

      if (formatsIndex == 0) {
        throw new ArrayIndexOutOfBoundsException("Need to provide at least one valid Barcode format");
      }

      if (barcodeScanner == null || formatsBitmap != barcodeScannerFormatsBitmap) {
        barcodeScanner = BarcodeScanning.getClient(
            new BarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                    formats[0],
                    Arrays.copyOfRange(formats, 1, formatsIndex))
                .build());
        barcodeScannerFormatsBitmap = formatsBitmap;
      }
    } else {
      throw new IllegalArgumentException("Second parameter must be an instance of ArrayList");
    }
  }

  private WritableNativeMap convertContent(@NonNull Barcode barcode) {
    WritableNativeMap map = new WritableNativeMap();

    int type = barcode.getValueType();
    map.putInt("type", type);

    switch (type) {
      case Barcode.TYPE_UNKNOWN:
      case Barcode.TYPE_ISBN:
      case Barcode.TYPE_TEXT:
        map.putString("data", barcode.getRawValue());
        break;
      case Barcode.TYPE_CONTACT_INFO:
        map.putMap("data", convertToMap(barcode.getContactInfo()));
        break;
      case Barcode.TYPE_EMAIL:
        map.putMap("data", convertToMap(barcode.getEmail()));
        break;
      case Barcode.TYPE_PHONE:
        map.putMap("data", convertToMap(barcode.getPhone()));
        break;
      case Barcode.TYPE_SMS:
        map.putMap("data", convertToMap(barcode.getSms()));
        break;
      case Barcode.TYPE_URL:
        map.putMap("data", convertToMap(barcode.getUrl()));
        break;
      case Barcode.TYPE_WIFI:
        map.putMap("data", convertToMap(barcode.getWifi()));
        break;
      case Barcode.TYPE_GEO:
        map.putMap("data", convertToMap(barcode.getGeoPoint()));
        break;
      case Barcode.TYPE_CALENDAR_EVENT:
        map.putMap("data", convertToMap(barcode.getCalendarEvent()));
        break;
      case Barcode.TYPE_DRIVER_LICENSE:
        map.putMap("data", convertToMap(barcode.getDriverLicense()));
        break;
    }

    return map;
  }

  private WritableNativeMap convertBarcode(@NonNull Barcode barcode) {
    WritableNativeMap map = new WritableNativeMap();

    Rect boundingBox = barcode.getBoundingBox();
    if (boundingBox != null) {
      map.putMap("boundingBox", convertToMap(boundingBox));
    }

    Point[] cornerPoints = barcode.getCornerPoints();
    if (cornerPoints != null) {
      map.putArray("cornerPoints", convertToArray(cornerPoints));
    }

    String displayValue = barcode.getDisplayValue();
    if (displayValue != null) {
      map.putString("displayValue", displayValue);
    }

    String rawValue = barcode.getRawValue();
    if (rawValue != null) {
      map.putString("rawValue", rawValue);
    }

    map.putMap("content", convertContent(barcode));
    map.putInt("format", barcode.getFormat());

    return map;
  }

  // Bitmap Inversion https://gist.github.com/moneytoo/87e3772c821cb1e86415
  private Bitmap invert(Bitmap src) {
    int height = src.getHeight();
    int width = src.getWidth();

    Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(bitmap);
    Paint paint = new Paint();

    ColorMatrix matrixGrayscale = new ColorMatrix();
    matrixGrayscale.setSaturation(0);

    ColorMatrix matrixInvert = new ColorMatrix();
    matrixInvert.set(new float[] {
        -1.0f, 0.0f, 0.0f, 0.0f, 255.0f,
        0.0f, -1.0f, 0.0f, 0.0f, 255.0f,
        0.0f, 0.0f, -1.0f, 0.0f, 255.0f,
        0.0f, 0.0f, 0.0f, 1.0f, 0.0f
    });
    matrixInvert.preConcat(matrixGrayscale);

    ColorMatrixColorFilter filter = new ColorMatrixColorFilter(matrixInvert);
    paint.setColorFilter(filter);

    canvas.drawBitmap(src, 0, 0, paint);
    return bitmap;
  }

  VisionCameraCodeScannerPlugin(Map<String, Object> data) {
    super(data);
  }
}
