package com.team4.walkingfriend;

import static java.lang.Math.round;

import android.animation.ObjectAnimator;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.media.ImageReader.OnImageAvailableListener;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;
import android.util.Size;
import android.view.View;
import android.view.animation.PathInterpolator;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.team4.lib_interpreter.Detector;
import com.team4.lib_interpreter.TFLiteObjectDetectionAPIModel;
import com.team4.walkingfriend.customview.OverlayView;
import com.team4.walkingfriend.customview.OverlayView.DrawCallback;
import com.team4.walkingfriend.tracking.MultiBoxTracker;
import com.team4.walkingfriend.utils.ImageUtils;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

public class DetectorActivity extends CameraActivity implements OnImageAvailableListener {

    private static final String LOGGER_TAG = "DetectorActivity";
    private static final int TF_OD_API_INPUT_SIZE = 300;
    private static final boolean MAINTAIN_ASPECT = false;
    private static final Size DESIRED_PREVIEW_SIZE = new Size(640, 480);
    private static final boolean SAVE_PREVIEW_BITMAP = false;
    OverlayView trackingOverlay;
    ImageView fairy_idle;
    ImageView fairy_scored;
    private Integer sensorOrientation;


    private static final float MINIMUM_CONFIDENCE_TF_OD_API = 0.65f;
    private static final boolean TF_OD_API_IS_QUANTIZED = true;
    private static final String TF_OD_API_MODEL_FILE = "detect.tflite";
    private static final String TF_OD_API_LABELS_FILE = "labelmap.txt";
    private Detector detector;
    private MultiBoxTracker tracker;

    private long lastProcessingTimeMs;
    private Bitmap rgbFrameBitmap = null;
    private Bitmap croppedBitmap = null;
    private Bitmap cropCopyBitmap = null;

    private boolean computingDetection = false;

    private long timestamp = 0;


    private Matrix frameToCropTransform;
    private Matrix cropToFrameTransform;

    List<String> possibleClasses = new ArrayList<String>();
    HashMap<String, Integer> classAppearance = new HashMap<String, Integer>();

    @Override
    public void onPreviewSizeChosen(final Size size, final int rotation) {
        tracker = new MultiBoxTracker(this);
        int cropSize = TF_OD_API_INPUT_SIZE;
        try {
            detector =
                    TFLiteObjectDetectionAPIModel.create(
                            this,
                            TF_OD_API_MODEL_FILE,
                            TF_OD_API_LABELS_FILE,
                            TF_OD_API_INPUT_SIZE,
                            TF_OD_API_IS_QUANTIZED);
            cropSize = TF_OD_API_INPUT_SIZE;
        } catch (final IOException e) {
            e.printStackTrace();
            Log.e(LOGGER_TAG, "Exception initializing Detector!");
            Toast toast =
                    Toast.makeText(
                            getApplicationContext(), "Detector could not be initialized", Toast.LENGTH_SHORT);
            toast.show();
            finish();
        }

        previewWidth = size.getWidth();
        previewHeight = size.getHeight();

        sensorOrientation = rotation - getScreenOrientation();
        Log.i(LOGGER_TAG, "Camera orientation relative to screen canvas: " + sensorOrientation);

        Log.i(LOGGER_TAG, "Initializing at size " + previewWidth + "" + previewHeight);
        rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Bitmap.Config.ARGB_8888);
        croppedBitmap = Bitmap.createBitmap(cropSize, cropSize, Bitmap.Config.ARGB_8888);

        frameToCropTransform =
                ImageUtils.getTransformationMatrix(
                        previewWidth, previewHeight,
                        cropSize, cropSize,
                        sensorOrientation, MAINTAIN_ASPECT);

        cropToFrameTransform = new Matrix();
        frameToCropTransform.invert(cropToFrameTransform);

        trackingOverlay = (OverlayView) findViewById(R.id.tracking_overlay);

        fairy_idle = (ImageView) findViewById(R.id.fairy_idle);
        fairy_scored = (ImageView) findViewById(R.id.fairy_scored);
        fairy_idle.getLayoutParams().width = 300;
        fairy_idle.getLayoutParams().height = 300;
        fairy_scored.getLayoutParams().width = 300;
        fairy_scored.getLayoutParams().height = 300;
        Glide.with(this).load(R.drawable.fairy_idle).into(fairy_idle);
        Glide.with(this).load(R.drawable.fairy_scored).into(fairy_scored);
        fairy_idle.bringToFront();

        trackingOverlay.addCallback(
                new DrawCallback() {
                    @Override
                    public void drawCallback(final Canvas canvas) {
                        tracker.draw(canvas, fairy_idle, fairy_scored);
                    }
                });

        tracker.setFrameConfiguration(previewWidth, previewHeight, sensorOrientation);
    }

    @Override
    protected void processImage() {

        possibleClasses.add("person");
        possibleClasses.add("bicycle");
        possibleClasses.add("car");
        possibleClasses.add("bench");
        possibleClasses.add("stop sign");
        possibleClasses.add("bird");
        possibleClasses.add("cat");
        possibleClasses.add("dog");


        classAppearance.put("person", 3);
        classAppearance.put("bicycle", 5);
        classAppearance.put("car", 3);
        classAppearance.put("bench", 10);
        classAppearance.put("stop sign", 5);
        classAppearance.put("bird", 10);
        classAppearance.put("cat", 10);
        classAppearance.put("dog", 10);




        ++timestamp;
        final long currTimestamp = timestamp;
        trackingOverlay.postInvalidate();

        if (computingDetection) {
            readyForNextImage();
            return;
        }
        computingDetection = true;
        rgbFrameBitmap.setPixels(getRgbBytes(), 0, previewWidth, 0, 0, previewWidth, previewHeight);

        readyForNextImage();

        final Canvas canvas = new Canvas(croppedBitmap);
        canvas.drawBitmap(rgbFrameBitmap, frameToCropTransform, null);
        // For examining the actual TF input.
        if (SAVE_PREVIEW_BITMAP) {
            ImageUtils.saveBitmap(croppedBitmap);
        }

        runInBackground(
                new Runnable() {
                    @Override
                    public void run() {
                        Log.i(LOGGER_TAG, "Running detection on image " + currTimestamp);
                        final long startTime = SystemClock.uptimeMillis();
                        final List<Detector.Recognition> results = detector.recognizeImage(croppedBitmap);
                        lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime;

                        cropCopyBitmap = Bitmap.createBitmap(croppedBitmap);
                        final Canvas canvas = new Canvas(cropCopyBitmap);
                        final Paint paint = new Paint();
                        paint.setColor(Color.RED);
                        paint.setStyle(Paint.Style.STROKE);
                        paint.setStrokeWidth(2.0f);

                        Collections.sort(results,
                                new Comparator<Detector.Recognition>() {
                                    @Override
                                    public int compare(Detector.Recognition o1, Detector.Recognition o2) {
                                        return o2.getConfidence().compareTo(o1.getConfidence());
                                    }
                                });

                        float minimumConfidence = MINIMUM_CONFIDENCE_TF_OD_API;
                        final List<Detector.Recognition> mappedRecognitions =
                                new ArrayList<Detector.Recognition>();



                        for (final Detector.Recognition result : results) {

                            final RectF location = result.getLocation();

                            Log.i(LOGGER_TAG, "class id:" + result.getTitle());
                            if(possibleClasses.contains(result.getTitle()) && (classAppearance.get(result.getTitle()) > 0)){
                                if (location != null && result.getConfidence() >= minimumConfidence) {
                                    canvas.drawRect(location, paint);

                                    cropToFrameTransform.mapRect(location);

                                    result.setLocation(location);
                                    mappedRecognitions.add(result);
                                    coins += 100;
                                    break;
                                }
                            }
                        }



                        Log.i(LOGGER_TAG,"#### coins : "+coins);

                        tracker.trackResults(mappedRecognitions, currTimestamp);
                        trackingOverlay.postInvalidate();

                        computingDetection = false;

                        if(mappedRecognitions.size() > 0) {
                                        try {
                                            Log.i(LOGGER_TAG,"#### detection succeed! sleep....5s");
                                            Thread.sleep(5000);
                                            //fairy_idle.setVisibility(View.VISIBLE);
                                            //fairy_scored.setVisibility(View.INVISIBLE);
                                        } catch (InterruptedException e) {
                                            e.printStackTrace();
                                        }
                                    }
                                }
                    });

        fairy_idle.setVisibility(View.VISIBLE);
        fairy_scored.setVisibility(View.INVISIBLE);
        fairy_idle.bringToFront();
    }


    @Override
    protected int getLayoutId() {
        return R.layout.camera_connection_fragment_tracking;
    }

    @Override
    protected Size getDesiredPreviewFrameSize() {
        return DESIRED_PREVIEW_SIZE;
    }

    @Override
    protected void setUseNNAPI(final boolean isChecked) {
        runInBackground(
                () -> {
                    try {
                        detector.setUseNNAPI(isChecked);
                    } catch (UnsupportedOperationException e) {
                        Log.e(LOGGER_TAG, "Failed to set \"Use NNAPI\".");
                        runOnUiThread(
                                () -> {
                                    Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                                });
                    }
                });
    }

    @Override
    protected void setNumThreads(final int numThreads) {
        runInBackground(() -> detector.setNumThreads(numThreads));
    }
}
