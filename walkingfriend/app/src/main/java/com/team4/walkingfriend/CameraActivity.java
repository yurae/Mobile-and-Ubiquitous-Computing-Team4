package com.team4.walkingfriend;

import android.Manifest;
import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.media.ImageReader.OnImageAvailableListener;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Trace;
import android.util.Log;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.team4.walkingfriend.result.ResultActivity;
import com.team4.walkingfriend.utils.ImageUtils;

import java.nio.ByteBuffer;

public abstract class CameraActivity extends AppCompatActivity implements OnImageAvailableListener {
    private static final String LOGGER_TAG = "CameraActivity";
    private static final String PERMISSION_CAMERA = Manifest.permission.CAMERA;
    private static final int PERMISSIONS_REQUEST = 1;

    protected int previewWidth = 0;
    protected int previewHeight = 0;

    private boolean useCamera2API;
    private boolean isProcessingFrame = false;

    private Handler handler;
    private HandlerThread handlerThread;
    private byte[][] yuvBytes = new byte[3][];
    private int[] rgbBytes = null;
    private int yRowStride;
    private Runnable postInferenceCallback;
    private Runnable imageConverter;

    protected int coins = 0;



    LinearLayout info;
    TextView info_title;
    TextView info_distance;
    TextView info_level;
    Button endButton;


    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        Log.d(LOGGER_TAG, "onCreate " + this);
        super.onCreate(null);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_camera);
//        Toolbar toolbar = findViewById(R.id.toolbar);
//        setSupportActionBar(toolbar);
//        getSupportActionBar().setDisplayShowTitleEnabled(false);

        Bundle b = getIntent().getExtras();
        String title = b.getString("title");
        String distance = b.getString("distance");
        String level = b.getString("level");

        // Obtain Text view for route info
        info = findViewById(R.id.info);
        info_title = findViewById(R.id.info_title);
        info_distance = findViewById(R.id.info_distance);
        info_level = findViewById(R.id.info_level);

        info_title.setText(title);
        info_distance.setText(distance);
        info_level.setText(level);

        endButton = findViewById(R.id.result_button);
        endButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(view.getContext(), ResultActivity.class);
                Bundle b = new Bundle();
                b.putInt("coins", coins);
                intent.putExtras(b);
                startActivity(intent);
                finishAffinity();
            }
        });


        if (hasPermission()) {
            setFragment();
        } else {
            requestPermission();
        }
    }

    /**
     * Callback for Camera2 API
     */
    @Override
    public void onImageAvailable(final ImageReader reader) {
        // We need wait until we have some size from onPreviewSizeChosen
        if (previewWidth == 0 || previewHeight == 0) {
            return;
        }
        if (rgbBytes == null) {
            rgbBytes = new int[previewWidth * previewHeight];
        }
        try {
            final Image image = reader.acquireLatestImage();

            if (image == null) {
                return;
            }

            if (isProcessingFrame) {
                image.close();
                return;
            }
            isProcessingFrame = true;
            Trace.beginSection("imageAvailable");
            final Image.Plane[] planes = image.getPlanes();
            fillBytes(planes, yuvBytes);
            yRowStride = planes[0].getRowStride();
            final int uvRowStride = planes[1].getRowStride();
            final int uvPixelStride = planes[1].getPixelStride();

            imageConverter =
                    new Runnable() {
                        @Override
                        public void run() {
                            ImageUtils.convertYUV420ToARGB8888(
                                    yuvBytes[0],
                                    yuvBytes[1],
                                    yuvBytes[2],
                                    previewWidth,
                                    previewHeight,
                                    yRowStride,
                                    uvRowStride,
                                    uvPixelStride,
                                    rgbBytes);
                        }
                    };

            postInferenceCallback =
                    new Runnable() {
                        @Override
                        public void run() {
                            image.close();
                            isProcessingFrame = false;
                        }
                    };

            processImage();
        } catch (final Exception e) {
            Log.e(LOGGER_TAG, e.getMessage());
            Trace.endSection();
            return;
        }
        Trace.endSection();
    }

    @Override
    public synchronized void onStart() {
        Log.d(LOGGER_TAG, "onStart " + this);
        super.onStart();
    }

    @Override
    public synchronized void onResume() {
        Log.d(LOGGER_TAG, "onResume " + this);
        super.onResume();

        handlerThread = new HandlerThread("inference");
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());
    }

    @Override
    public synchronized void onPause() {
        Log.d(LOGGER_TAG, "onPause " + this);

        handlerThread.quitSafely();
        try {
            handlerThread.join();
            handlerThread = null;
            handler = null;
        } catch (final InterruptedException e) {
            Log.e(LOGGER_TAG, e.getMessage());
        }

        super.onPause();
    }

    @Override
    public synchronized void onStop() {
        Log.d(LOGGER_TAG, "onStop " + this);
        super.onStop();
    }

    @Override
    public synchronized void onDestroy() {
        Log.d(LOGGER_TAG, "onDestroy " + this);
        super.onDestroy();
    }

    protected void fillBytes(final Image.Plane[] planes, final byte[][] yuvBytes) {
        // Because of the variable row stride it's not possible to know in
        // advance the actual necessary dimensions of the yuv planes.
        for (int i = 0; i < planes.length; ++i) {
            final ByteBuffer buffer = planes[i].getBuffer();
            if (yuvBytes[i] == null) {
                Log.d(LOGGER_TAG, "Initializing buffer " + i + " at size" + buffer.capacity());
                yuvBytes[i] = new byte[buffer.capacity()];
            }
            buffer.get(yuvBytes[i]);
        }
    }

    private boolean hasPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return checkSelfPermission(PERMISSION_CAMERA) == PackageManager.PERMISSION_GRANTED;
        } else {
            return true;
        }
    }

    private void requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (shouldShowRequestPermissionRationale(PERMISSION_CAMERA)) {
                Toast.makeText(
                        CameraActivity.this,
                        "Camera permission is required for this demo",
                        Toast.LENGTH_LONG)
                        .show();
            }
            requestPermissions(new String[]{PERMISSION_CAMERA}, PERMISSIONS_REQUEST);
        }
    }

    // Returns true if the device supports the required hardware level, or better.
    private boolean isHardwareLevelSupported(
            CameraCharacteristics characteristics, int requiredLevel) {
        int deviceLevel = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
        if (deviceLevel == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY) {
            return requiredLevel == deviceLevel;
        }
        // deviceLevel is not LEGACY, can use numerical sort
        return requiredLevel <= deviceLevel;
    }

    protected void setFragment() {
        String cameraId = chooseCamera();

        Fragment fragment;
        if (useCamera2API) {
            CameraConnectionFragment camera2Fragment =
                    CameraConnectionFragment.newInstance(
                            new CameraConnectionFragment.ConnectionCallback() {
                                @Override
                                public void onPreviewSizeChosen(final android.util.Size size, final int rotation) {
                                    previewHeight = size.getHeight();
                                    previewWidth = size.getWidth();
                                    CameraActivity.this.onPreviewSizeChosen(size, rotation);
                                }
                            },
                            this,
                            getLayoutId(),
                            getDesiredPreviewFrameSize());

            camera2Fragment.setCamera(cameraId);
            fragment = camera2Fragment;
        } else {
            // TODO: legacy camera connection handling
            Log.e(LOGGER_TAG, "Exception");
            fragment = null;
        }

        getFragmentManager().beginTransaction().replace(R.id.container, fragment).commit();
    }

    protected void readyForNextImage() {
        if (postInferenceCallback != null) {
            postInferenceCallback.run();
        }
    }

    protected int getScreenOrientation() {
        switch (getWindowManager().getDefaultDisplay().getRotation()) {
            case Surface.ROTATION_270:
                return 270;
            case Surface.ROTATION_180:
                return 180;
            case Surface.ROTATION_90:
                return 90;
            default:
                return 0;
        }
    }

    protected int[] getRgbBytes() {
        imageConverter.run();
        return rgbBytes;
    }

    private String chooseCamera() {
        final CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            for (final String cameraId : manager.getCameraIdList()) {
                final CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);

                // We don't use a front facing camera in this sample.
                final Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    continue;
                }

                final StreamConfigurationMap map =
                        characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

                if (map == null) {
                    continue;
                }

                // Fallback to camera1 API for internal cameras that don't have full support.
                // This should help with legacy situations where using the camera2 API causes
                // distorted or otherwise broken previews.
                useCamera2API =
                        (facing == CameraCharacteristics.LENS_FACING_EXTERNAL)
                                || isHardwareLevelSupported(
                                characteristics, CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL);
                Log.i(LOGGER_TAG, "Camera API lv2?: " + useCamera2API);
                return cameraId;
            }
        } catch (CameraAccessException e) {
            Log.e(LOGGER_TAG, "Not allowed to access camera");
        }

        return null;
    }

    protected synchronized void runInBackground(final Runnable r) {
        if (handler != null) {
            handler.post(r);
        }
    }

    protected abstract void processImage();

    protected abstract void onPreviewSizeChosen(final android.util.Size size, final int rotation);

    protected abstract int getLayoutId();

    protected abstract android.util.Size getDesiredPreviewFrameSize();

    protected abstract void setNumThreads(int numThreads);

    protected abstract void setUseNNAPI(boolean isChecked);
}
