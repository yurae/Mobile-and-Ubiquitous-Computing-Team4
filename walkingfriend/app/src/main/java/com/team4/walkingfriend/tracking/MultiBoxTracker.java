package com.team4.walkingfriend.tracking;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Cap;
import android.graphics.Paint.Join;
import android.graphics.Paint.Style;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.text.TextUtils;
import android.util.Pair;
import android.util.TypedValue;
import android.view.View;
import android.widget.ImageView;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import com.team4.lib_interpreter.Detector;
import com.team4.walkingfriend.R;
import com.team4.walkingfriend.utils.BorderedText;
import com.team4.walkingfriend.utils.ImageUtils;
import com.team4.lib_interpreter.Detector.Recognition;

/**
 * A tracker that handles non-max suppression and matches existing objects to new detections.
 */
public class MultiBoxTracker {
    private static final float TEXT_SIZE_DIP = 18;
    private static final float MIN_SIZE = 16.0f;
    private static final int[] COLORS = {
            Color.BLUE,
            Color.RED,
            Color.GREEN,
            Color.YELLOW,
            Color.CYAN,
            Color.MAGENTA,
            Color.WHITE,
            Color.parseColor("#55FF55"),
            Color.parseColor("#FFA500"),
            Color.parseColor("#FF8888"),
            Color.parseColor("#AAAAFF"),
            Color.parseColor("#FFFFAA"),
            Color.parseColor("#55AAAA"),
            Color.parseColor("#AA33AA"),
            Color.parseColor("#0D0068")
    };
    final List<Pair<Float, RectF>> screenRects = new LinkedList<Pair<Float, RectF>>();

    private final Queue<Integer> availableColors = new LinkedList<Integer>();
    private final List<TrackedRecognition> trackedObjects = new LinkedList<TrackedRecognition>();
    private final Paint boxPaint = new Paint();
    private final float textSizePx;
    private final BorderedText borderedText;
    private Matrix frameToCanvasMatrix;
    private int frameWidth;
    private int frameHeight;
    private int sensorOrientation;

    public MultiBoxTracker(final Context context) {
        for (final int color : COLORS) {
            availableColors.add(color);
        }

        boxPaint.setColor(Color.RED);
        boxPaint.setStyle(Style.STROKE);
        boxPaint.setStrokeWidth(10.0f);
        boxPaint.setStrokeCap(Cap.ROUND);
        boxPaint.setStrokeJoin(Join.ROUND);
        boxPaint.setStrokeMiter(100);

        textSizePx =
                TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, context.getResources().getDisplayMetrics());
        borderedText = new BorderedText(textSizePx);
    }

    public synchronized void setFrameConfiguration(
            final int width, final int height, final int sensorOrientation) {
        frameWidth = width;
        frameHeight = height;
        this.sensorOrientation = sensorOrientation;
    }

    public synchronized void drawDebug(final Canvas canvas) {
        final Paint textPaint = new Paint();
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(60.0f);

        final Paint boxPaint = new Paint();
        boxPaint.setColor(Color.RED);
        boxPaint.setAlpha(200);
        boxPaint.setStyle(Style.STROKE);

        for (final Pair<Float, RectF> detection : screenRects) {
            final RectF rect = detection.second;
            canvas.drawRect(rect, boxPaint);
            canvas.drawText("" + detection.first, rect.left, rect.top, textPaint);
            borderedText.drawText(canvas, rect.centerX(), rect.centerY(), "" + detection.first);
        }
    }

    public synchronized void trackResults(final List<Recognition> results, final long timestamp) {
        processResults(results);
    }

    private Matrix getFrameToCanvasMatrix() {
        return frameToCanvasMatrix;
    }

    public synchronized void draw(final Canvas canvas, ImageView fairy_idle, ImageView fairy_scored) {
        final boolean rotated = sensorOrientation % 180 == 90;
        final float multiplier =
                Math.min(
                        canvas.getHeight() / (float) (rotated ? frameWidth : frameHeight),
                        canvas.getWidth() / (float) (rotated ? frameHeight : frameWidth));
        frameToCanvasMatrix =
                ImageUtils.getTransformationMatrix(
                        frameWidth,
                        frameHeight,
                        (int) (multiplier * (rotated ? frameHeight : frameWidth)),
                        (int) (multiplier * (rotated ? frameWidth : frameHeight)),
                        sensorOrientation,
                        false);
        for (final TrackedRecognition recognition : trackedObjects) {
            final RectF trackedPos = new RectF(recognition.location);

            getFrameToCanvasMatrix().mapRect(trackedPos);
            boxPaint.setColor(recognition.color);

            float cornerSize = Math.min(trackedPos.width(), trackedPos.height()) / 8.0f;
            // modify this line with character gif

            fairy_idle.setVisibility(View.INVISIBLE);
            fairy_scored.setVisibility(View.VISIBLE);
            fairy_scored.setX(trackedPos.left - cornerSize);
            fairy_scored.setY(trackedPos.top);
            fairy_scored.bringToFront();


            //modify this line with coin text
            final String labelString =
                    !TextUtils.isEmpty(recognition.title)
                            ? String.format("%s %.2f", recognition.title, (100 * recognition.detectionConfidence))
                            : String.format("%.2f", (100 * recognition.detectionConfidence));

                        borderedText.drawText(canvas, trackedPos.left + cornerSize, trackedPos.top,
             labelString);

            //final String labelString = "100 Coins!";
            //borderedText.drawText(
            //        canvas, trackedPos.left + cornerSize, trackedPos.top, labelString, boxPaint);

        }
    }


    private void processResults(final List<Recognition> results) {
        final List<Pair<Float, Recognition>> rectsToTrack = new LinkedList<Pair<Float, Recognition>>();

        screenRects.clear();
        final Matrix rgbFrameToScreen = new Matrix(getFrameToCanvasMatrix());

        for (final Recognition result : results) {
            if (result.getLocation() == null) {
                continue;
            }
            final RectF detectionFrameRect = new RectF(result.getLocation());

            final RectF detectionScreenRect = new RectF();
            rgbFrameToScreen.mapRect(detectionScreenRect, detectionFrameRect);

            screenRects.add(new Pair<Float, RectF>(result.getConfidence(), detectionScreenRect));

            if (detectionFrameRect.width() < MIN_SIZE || detectionFrameRect.height() < MIN_SIZE) {
                continue;
            }

            rectsToTrack.add(new Pair<Float, Recognition>(result.getConfidence(), result));
        }

        trackedObjects.clear();
        if (rectsToTrack.isEmpty()) {
            return;
        }

        for (final Pair<Float, Recognition> potential : rectsToTrack) {
            final TrackedRecognition trackedRecognition = new TrackedRecognition();
            trackedRecognition.detectionConfidence = potential.first;
            trackedRecognition.location = new RectF(potential.second.getLocation());
            trackedRecognition.title = potential.second.getTitle();
            trackedRecognition.color = COLORS[trackedObjects.size()];
            trackedObjects.add(trackedRecognition);

            if (trackedObjects.size() >= COLORS.length) {
                break;
            }
        }
    }

    private static class TrackedRecognition {
        RectF location;
        float detectionConfidence;
        int color;
        String title;
    }
}
