package ai.fritz.camera;

import android.graphics.Bitmap;
import android.graphics.RectF;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.util.Size;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;

import java.util.concurrent.atomic.AtomicBoolean;

import ai.fritz.core.Fritz;
import ai.fritz.poseestimationdemo.R;
import ai.fritz.vision.FritzVision;
import ai.fritz.vision.FritzVisionImage;
import ai.fritz.vision.FritzVisionOrientation;
import ai.fritz.vision.ImageOrientation;
import ai.fritz.vision.poseestimation.FritzVisionPosePredictor;
import ai.fritz.vision.poseestimation.FritzVisionPoseResult;
import ai.fritz.vision.poseestimation.HumanSkeleton;
import ai.fritz.vision.poseestimation.Pose;
import ai.fritz.vision.poseestimation.PoseOnDeviceModel;


public class MainActivity extends BaseCameraActivity implements ImageReader.OnImageAvailableListener {

    private static final Size DESIRED_PREVIEW_SIZE = new Size(1280, 960);

    private AtomicBoolean isComputing = new AtomicBoolean(false);
    private AtomicBoolean shouldSample = new AtomicBoolean(true);
    private ImageOrientation orientation;

    FritzVisionPoseResult poseResult;
    FritzVisionPosePredictor predictor;
    FritzVisionImage visionImage;

    // Preview Frame
    RelativeLayout previewFrame;
    Button snapshotButton;
    ProgressBar snapshotProcessingSpinner;

    // Snapshot Frame
    RelativeLayout snapshotFrame;
    OverlayView snapshotOverlay;
    Button closeButton;
    Button recordButton;
    ProgressBar recordSpinner;


    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Fritz.configure(this);
        PoseOnDeviceModel poseEstimationOnDeviceModel = PoseOnDeviceModel.buildFromModelConfigFile("pose_recording_model.json", new HumanSkeleton());
        predictor = FritzVision.PoseEstimation.getPredictor(poseEstimationOnDeviceModel);
    }

    @Override
    protected int getLayoutId() {
        return R.layout.main_camera;
    }

    @Override
    protected Size getDesiredPreviewFrameSize() {
        return DESIRED_PREVIEW_SIZE;
    }

    @Override
    public void onPreviewSizeChosen(final Size previewSize, final Size cameraViewSize, final int rotation) {
        orientation = FritzVisionOrientation.getImageOrientationFromCamera(this, cameraId);

        // Preview View
        previewFrame = findViewById(R.id.preview_frame);
        snapshotProcessingSpinner = findViewById(R.id.snapshot_spinner);
        snapshotButton = findViewById(R.id.take_picture_btn);
        snapshotButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!shouldSample.compareAndSet(true, false)) {
                    return;
                }

                runInBackground(
                        () -> {
                            showSpinner();
                            snapshotOverlay.postInvalidate();
                            switchToSnapshotView();
                            hideSpinner();
                        });
            }
        });
        setCallback(canvas -> {
            if (poseResult != null) {
                for (Pose pose : poseResult.getPoses()) {
                    pose.draw(canvas);
                }
            }
            isComputing.set(false);
        });

        // Snapshot View
        snapshotFrame = findViewById(R.id.snapshot_frame);
        snapshotOverlay = findViewById(R.id.snapshot_view);
        snapshotOverlay.setCallback(
                canvas -> {
                    if (poseResult != null) {
                        Bitmap bitmap = visionImage.overlaySkeletons(poseResult.getPoses());
                        canvas.drawBitmap(bitmap, null, new RectF(0, 0, cameraViewSize.getWidth(), cameraViewSize.getHeight()), null);
                    }
                });
        recordSpinner = findViewById(R.id.record_spinner);
        recordButton = findViewById(R.id.record_prediction_btn);
        recordButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                recordSpinner.setVisibility(View.VISIBLE);
                predictor.record(visionImage, poseResult, null, () -> {
                    switchPreviewView();
                    return null;
                }, () -> {
                    switchPreviewView();
                    return null;
                });
            }
        });
        closeButton = findViewById(R.id.close_btn);
        closeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                switchPreviewView();
            }
        });

    }

    private void switchToSnapshotView() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                previewFrame.setVisibility(View.GONE);
                snapshotFrame.setVisibility(View.VISIBLE);
            }
        });
    }

    private void switchPreviewView() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                recordSpinner.setVisibility(View.GONE);
                snapshotFrame.setVisibility(View.GONE);
                previewFrame.setVisibility(View.VISIBLE);
                shouldSample.set(true);
            }
        });
    }

    private void showSpinner() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                snapshotProcessingSpinner.setVisibility(View.VISIBLE);
            }
        });
    }

    private void hideSpinner() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                snapshotProcessingSpinner.setVisibility(View.GONE);
            }
        });
    }

    @Override
    public void onImageAvailable(final ImageReader reader) {
        Image image = reader.acquireLatestImage();

        if (image == null) {
            return;
        }

        if (!shouldSample.get()) {
            image.close();
            return;
        }

        if (!isComputing.compareAndSet(false, true)) {
            image.close();
            return;
        }

        visionImage = FritzVisionImage.fromMediaImage(image, orientation);
        image.close();

        runInBackground(() -> {
            poseResult = predictor.predict(visionImage);
            requestRender();
        });
    }
}
