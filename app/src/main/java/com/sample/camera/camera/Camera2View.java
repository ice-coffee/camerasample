package com.sample.camera.camera;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.TypedArray;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ExifInterface;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Size;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.TextureView;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;

import com.sample.camera.R;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Semaphore;

/**
 * @author mzp
 * date : 2020/9/10
 * desc :
 */
@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class Camera2View extends TextureView implements CameraInterface {

    /**
     * 闪光灯状态
     * FLASH_MODEL_OFF : 关闭闪光灯
     * FLASH_MODEL_ON : 开启闪光灯
     * FLASH_MODEL_AUTO : 在弱光条件下开启闪光灯
     * FLASH_MODEL_ALWAYS : 闪光灯常起
     */
    public static final int FLASH_MODEL_OFF = 0;
    public static final int FLASH_MODEL_ON = 1;
    public static final int FLASH_MODEL_AUTO = 2;
    public static final int FLASH_MODEL_ALWAYS = 3;

    /**
     * Max preview width that is guaranteed by Camera2 API
     */
    private static final int MAX_PREVIEW_WIDTH = 1920;
    /**
     * Max preview height that is guaranteed by Camera2 API
     */
    private static final int MAX_PREVIEW_HEIGHT = 1080;
    /**
     * An additional thread for running tasks that shouldn't block the UI.
     */
    private HandlerThread mBackgroundThread;
    /**
     * A {@link Handler} for running tasks in the background.
     */
    private Handler mBackgroundHandler;
    /**
     * A {@link Semaphore} to prevent the app from exiting before closing the camera.
     */
    private Semaphore mCameraOpenCloseLock = new Semaphore(1);
    /**
     * The {@link Size} of camera preview.
     */
    private Size mPreviewSize;
    /**
     * An {@link ImageReader} that handles still image capture.
     */
    private ImageReader mImageReader;
    /**
     * Whether the current camera device supports Flash or not.
     * 相机是否支持闪光灯
     */
    private boolean mFlashSupported;
    /**
     * The current state of camera state for taking pictures.
     *
     * @see #mCaptureCallback
     */
    private int mState = -1;
    /**
     * 闪光灯状态, 默认自动模式
     */
    private int mFlashModel;
    /**
     * 前置摄像
     */
    private boolean mIsfaceFront;

    private Context mContext;
    /**
     * 图像输出路径
     */
    private String outImagePath;
    /**
     * 输出图片方向
     */
    private int jpegOrientation;
    /**
     * 设备方向监听
     */
    private int deviceOrientation;
    private OrientationEventListener mOrientationEventListener;
    /**
     * 输出图像数据纵横比
     */
    private float aspectRatio = (float) MAX_PREVIEW_HEIGHT / MAX_PREVIEW_WIDTH;

//    private SurfaceTexture mTexture;
    private Surface mPreviewSurface;

    private CameraListener mCameraListener;
    private WindowManager mWindowManager;
    private CameraManager mCameraManager;
    private String mCameraId;
    private String frontCameraId;
    private CameraCharacteristics frontCameraCharacteristics;
    private String backCameraId;
    private CameraCharacteristics backCameraCharacteristics;

    private CameraDevice mCameraDevice;
    private CameraCaptureSession mCaptureSession;
    private CaptureRequest.Builder mPreviewRequestBuilder;

    public Camera2View(Context context) {
        super(context);
        initView(context, null);
    }

    public Camera2View(Context context, AttributeSet attrs) {
        super(context, attrs);
        initView(context, attrs);
    }

    public Camera2View(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initView(context, attrs);
    }

    private void initView(Context context, AttributeSet attrs) {

        this.mContext = context;
        this.outImagePath = mContext.getExternalFilesDir(Environment.DIRECTORY_PICTURES) + mContext.getPackageName() + ".jpg";

        boolean isCircle = false;
        int radius = 0;
        if (null != attrs) {
            TypedArray tArray = context.obtainStyledAttributes(attrs, R.styleable.Camera2View);
            mIsfaceFront = tArray.getBoolean(R.styleable.Camera2View_isFaceFront, false);
            boolean isSquare = tArray.getBoolean(R.styleable.Camera2View_isSquare, false);
            isCircle = tArray.getBoolean(R.styleable.Camera2View_isCircle, false);
            radius = tArray.getDimensionPixelSize(R.styleable.Camera2View_raidus, 0);
            boolean isFlash = tArray.getBoolean(R.styleable.Camera2View_isFlash, false);
            mFlashModel = tArray.getInt(R.styleable.Camera2View_flashModel, 0);
            if (isCircle || isSquare) {
                aspectRatio = 1.0f;
            }

            tArray.recycle();
        }

        if (isCircle || radius != 0) {
            setOutlineProvider(new MyViewOutlineProvider(isCircle, radius));
            setClipToOutline(true);
        }

        initCamera();
    }

    @Override
    public void initCamera() {

        mWindowManager = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        mCameraManager = (CameraManager) mContext.getSystemService(Context.CAMERA_SERVICE);
        checkCameras();

        //监听设备方向
        mOrientationEventListener = new OrientationEventListener(mContext) {
            @Override
            public void onOrientationChanged(int orientation) {

                deviceOrientation = orientation;
            }
        };
    }

    /**
     * 遍历设备摄像头信息,拿到前置和后置摄像头
     */
    private void checkCameras() {

        try {
            // 获取前置和后置摄像头
            for (String cameraId : mCameraManager.getCameraIdList()) {
                if (!TextUtils.isEmpty(cameraId)) {
                    CameraCharacteristics cameraCharacteristics = mCameraManager.getCameraCharacteristics(cameraId);
                    Integer facing = cameraCharacteristics.get(CameraCharacteristics.LENS_FACING);
                    if (null != facing) {
                        if (TextUtils.isEmpty(frontCameraId) && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                            frontCameraId = cameraId;
                            frontCameraCharacteristics = cameraCharacteristics;
                        } else if (TextUtils.isEmpty(backCameraId) && facing == CameraCharacteristics.LENS_FACING_BACK) {
                            backCameraId = cameraId;
                            backCameraCharacteristics = cameraCharacteristics;
                        }
                    }
                }
            }

            //初始后置摄像
            mCameraId = mIsfaceFront ? frontCameraId : backCameraId;
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void openCamera() {

        if (isAvailable()) {
            startBackgroundThread();
            setUpCameraOutputs(getWidth(), getHeight());
            configureTransform(getWidth(), getHeight());
            if (null != mCameraManager) {
                try {
                    boolean haveCameraPer = ContextCompat.checkSelfPermission(mContext, android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
                    boolean haveStoragePer = ContextCompat.checkSelfPermission(mContext, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
                    if (haveCameraPer && haveStoragePer) {
                        mCameraManager.openCamera(mCameraId, mCameraDeviceCallback, mBackgroundHandler);
                    } else if (null != mCameraListener) {
                            mCameraListener.onCameraError();
                    }
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
            }
        } else {
            setSurfaceTextureListener(mSurfaceTextureListener);
        }
    }

    @Override
    public void closeCamera() {

        stopBackgroundThread();
        try {
            if (null != mCaptureSession) {
                mCaptureSession.stopRepeating();
                mCaptureSession.close();
                mCaptureSession = null;
            }
            if (null != mCameraDevice) {
                mCameraDevice.close();
                mCameraDevice = null;
            }
            if (null != mImageReader) {
                mImageReader.close();
                mImageReader = null;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void takePicture() {

        lockFocus();
    }

    @Override
    public void switchCamera() {

        mIsfaceFront = !mIsfaceFront;
        mCameraId = mIsfaceFront ? frontCameraId : backCameraId;
        closeCamera();
        openCamera();
    }

    /**
     * Starts a background thread and its {@link Handler}.
     */
    private void startBackgroundThread() {
        mOrientationEventListener.enable();
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    /**
     * Stops the background thread and its {@link Handler}.
     */
    private void stopBackgroundThread() {
        mOrientationEventListener.disable();
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * 创建相机会话
     */
    private void createCameraPreviewSession() {

        try {
            if (null != mPreviewSize) {
                SurfaceTexture texture = getSurfaceTexture();
                texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
                mPreviewSurface = new Surface(texture);
                mCameraDevice.createCaptureSession(Arrays.asList(mPreviewSurface, mImageReader.getSurface()), mCaptureSessionCallback, mBackgroundHandler);
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void cameraPreview() {

        if (null != mCameraDevice) {
            try {
                mCaptureSession.stopRepeating();
                mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                setAutoFlash(mPreviewRequestBuilder, true);
                mPreviewRequestBuilder.addTarget(mPreviewSurface);
                CaptureRequest previewBuilder = mPreviewRequestBuilder.build();
                mCaptureSession.setRepeatingRequest(previewBuilder, mCaptureCallback, mBackgroundHandler);
                mState = STATE_PREVIEW;
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Lock the focus as the first step for a still image capture.
     * 拍照第一步 雀氏纸尿裤(锁定焦点)
     */
    private void lockFocus() {

        try {
            // This is how to tell the camera to lock focus.
            // 设置如何锁定相机焦点
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START);
            mState = STATE_WAITING_LOCK;
            mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Run the precapture sequence for capturing a still image. This method should be called when
     * we get a response in {@link #mCaptureCallback} from {@link #lockFocus()}.
     */
    private void runPrecaptureSequence() {
        try {
            // This is how to tell the camera to trigger.
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
            // Tell #mCaptureCallback to wait for the precapture sequence to be set.
            mState = STATE_WAITING_PRECAPTURE;
            mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback,
                    mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Capture a still picture. This method should be called when we get a response in
     * {@link #mCaptureCallback} from both {@link #lockFocus()}.
     */
    private void captureStillPicture() {
        try {
            if (null == mCameraDevice) {
                return;
            }
            // This is the CaptureRequest.Builder that we use to take a picture.
            final CaptureRequest.Builder captureBuilder =
                    mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(mImageReader.getSurface());

            // Use the same AE and AF modes as the preview.
            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            setAutoFlash(captureBuilder, false);

            // Orientation
            jpegOrientation = getJpegOrientation(getCameraCharacteristics(mCameraId), deviceOrientation);
            //TODO-MZP 部分手机(HUAWEI P20) 与 Matrix 设置图片矩阵冲突
//            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, jpegOrientation);

            CameraCaptureSession.CaptureCallback CaptureCallback
                    = new CameraCaptureSession.CaptureCallback() {

                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                               @NonNull CaptureRequest request,
                                               @NonNull TotalCaptureResult result) {
                    unlockFocus();
                }
            };

            mCaptureSession.stopRepeating();
            mCaptureSession.abortCaptures();
            mCaptureSession.capture(captureBuilder.build(), CaptureCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Unlock the focus. This method should be called when still image capture sequence is
     * finished.
     * 拍照成功, 放开焦点
     */
    private void unlockFocus() {

        try {
            // This is how to tell the camera to lock focus.
            // 设置如何锁定相机焦点
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
            mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback, mBackgroundHandler);
            //拍照结束, 解除焦点锁定, 重新开始预览
            cameraPreview();
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * 设置闪光灯状态
     * @param flashModel 闪光灯状态
     */
    public void setFlashModel(int flashModel) {

        this.mFlashModel = flashModel;
        cameraPreview();
    }

    /**
     * 闪光灯控制
     * 0 : CaptureRequest.CONTROL_AE_MODE_OFF : 关闭闪光灯
     * 1 : CaptureRequest.CONTROL_AE_MODE_ON : 开启闪光灯
     * 2 : CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH : 在光线微弱的情况下使用闪光灯
     * 3 : CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH : 总使用闪光灯
     */
    private void setAutoFlash(CaptureRequest.Builder mCaptureRequestBuilder, boolean isPreview) {
        if (mFlashSupported) {
            switch (mFlashModel) {
                case CaptureRequest.CONTROL_AE_MODE_OFF:
                    if (isPreview) {
                        mCaptureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
                        mCaptureRequestBuilder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_OFF);
                    } else {
                        mCaptureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
                        mCaptureRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
                    }
                    break;
                case CaptureRequest.CONTROL_AE_MODE_ON:
                    if (mIsfaceFront) {
                        if (isPreview) {
                            mCaptureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
                            mCaptureRequestBuilder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_OFF);
                        } else {
                            mCaptureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
                            mCaptureRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_SINGLE);
                        }
                    } else {
                        if (isPreview) {
                            mCaptureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
                            mCaptureRequestBuilder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_OFF);
                        } else {
                            mCaptureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
                            mCaptureRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH);
                        }
                    }
                    break;
                case CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH:
                    if (mIsfaceFront) {
                        if (isPreview) {
                            mCaptureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
                            mCaptureRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
                        } else {
                            mCaptureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
                        }
                    } else {
                        if (isPreview) {
                            mCaptureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
                            mCaptureRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
                        } else {
                            mCaptureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
                        }
                    }
                    break;
                case CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH:
                    if (isPreview) {
                        mCaptureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
                        mCaptureRequestBuilder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_TORCH);
                    } else {
                        mCaptureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
                        mCaptureRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH);
                    }
                    break;
                default:
            }
        }
    }

    /**
     * {@link TextureView.SurfaceTextureListener} handles several lifecycle events on a
     * {@link TextureView}.
     */
    private final TextureView.SurfaceTextureListener mSurfaceTextureListener
            = new TextureView.SurfaceTextureListener() {

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
            openCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
            configureTransform(width, height);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture texture) {
        }
    };

    /**
     * {@link CameraDevice.StateCallback} is called when {@link CameraDevice} changes its state.
     * 相机状态监听
     */
    private final CameraDevice.StateCallback mCameraDeviceCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            // This method is called when the camera is opened.  We start camera preview here.
            mCameraOpenCloseLock.release();
            mCameraDevice = cameraDevice;
            createCameraPreviewSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int error) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
            if (null != mCameraListener) {
                mCameraListener.onCameraError();
            }
        }
    };

    /**
     * 相机会话
     */
    private final CameraCaptureSession.StateCallback mCaptureSessionCallback = new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(@NonNull CameraCaptureSession session) {

            mCaptureSession = session;
            cameraPreview();
        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession session) {

            if (null != mCameraListener) {
                mCameraListener.onCameraError();
            }
        }
    };

    /**
     * 图像捕获回调
     */
    private CameraCaptureSession.CaptureCallback mCaptureCallback = new CameraCaptureSession.CaptureCallback() {

        private void process(CaptureResult result) {
            switch (mState) {
                case STATE_PREVIEW: {
                    // We have nothing to do when the camera preview is working normally.
                    break;
                }
                case STATE_WAITING_LOCK: {
                    Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);
                    if (afState == null) {
                        captureStillPicture();
                    } else if (CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED == afState ||
                            CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED == afState) {
                        // CONTROL_AE_STATE can be null on some devices
                        Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                        if (aeState == null ||
                                aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
                            mState = STATE_PICTURE_TAKEN;
                            captureStillPicture();
                        } else {
                            runPrecaptureSequence();
                        }
                    } else {
                        //TODO-MZP OPPO K3 前置摄像头 mState = 0, 显示对焦状态未开启
                        mState = STATE_PICTURE_TAKEN;
                        captureStillPicture();
                    }
                    break;
                }
                case STATE_WAITING_PRECAPTURE: {
                    // CONTROL_AE_STATE can be null on some devices
                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                    if (aeState == null ||
                            aeState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE ||
                            aeState == CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED) {
                        mState = STATE_WAITING_NON_PRECAPTURE;
                    }
                    break;
                }
                case STATE_WAITING_NON_PRECAPTURE: {
                    // CONTROL_AE_STATE can be null on some devices
                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                    if (aeState == null || aeState != CaptureResult.CONTROL_AE_STATE_PRECAPTURE) {
                        mState = STATE_PICTURE_TAKEN;
                        captureStillPicture();
                    }
                    break;
                }
                default:
            }
        }

        @Override
        public void onCaptureProgressed(@NonNull CameraCaptureSession session,
                                        @NonNull CaptureRequest request,
                                        @NonNull CaptureResult partialResult) {
            process(partialResult);
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                       @NonNull CaptureRequest request,
                                       @NonNull TotalCaptureResult result) {
            process(result);
        }

        @Override
        public void onCaptureFailed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureFailure failure) {
            if (null != mCameraListener) {
                mCameraListener.onCameraError();
            }
        }
    };

    /**
     * This a callback object for the {@link ImageReader}. "onImageAvailable" will be called when a
     * still image is ready to be saved.
     * 拍照获取图像监听
     */
    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener
            = new ImageReader.OnImageAvailableListener() {

        @Override
        public void onImageAvailable(ImageReader reader) {
            CameraCharacteristics characteristics = getCameraCharacteristics(mCameraId);
            boolean isFront = characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT;
//                mBackgroundHandler.post(new ImageSaver(reader.acquireNextImage(), mFile, jpegOrientation, isFront));
            Image mImage = reader.acquireNextImage();
            ByteBuffer buffer = mImage.getPlanes()[0].getBuffer();
            byte[] jpegByteArray = new byte[buffer.remaining()];
            buffer.get(jpegByteArray);

            //TODO-MZP
            int orientation = ExifUtilsKt.computeExifOrientation(jpegOrientation, isFront);

            FileOutputStream output = null;
            try {
                output = new FileOutputStream(outImagePath);
                output.write(jpegByteArray);

                //图片添加标签
                ExifInterface exifInterface = new ExifInterface(outImagePath);
                exifInterface.setAttribute(ExifInterface.TAG_ORIENTATION, String.valueOf(orientation));
                exifInterface.saveAttributes();

                if (null != mCameraListener) {
                    mCameraListener.onTakePicture(outImagePath);
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                mImage.close();
                if (null != output) {
                    try {
                        output.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    };

    /**
     * Sets up member variables related to camera.
     * 设置与摄像机相关的成员变量。主要是设置在横屏和竖屏状态下的预览尺寸
     * @param width  The width of available size for camera preview
     * @param height The height of available size for camera preview
     */
    @SuppressWarnings("SuspiciousNameCombination")
    private void setUpCameraOutputs(int width, int height) {
        if (!TextUtils.isEmpty(mCameraId)) {
            CameraCharacteristics characteristics = getCameraCharacteristics(mCameraId);

            // 获取Camera支持的输出格式和尺寸信息
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (map == null) {
                return;
            }

            // For still image captures, we use the largest available size.
            // 使用jepg格式最大可用尺寸捕获图像
            Size largest = Collections.max(Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)), new CompareSizesByArea());
            mImageReader = ImageReader.newInstance(largest.getWidth(), largest.getHeight(), ImageFormat.JPEG, 2);
            mImageReader.setOnImageAvailableListener(mOnImageAvailableListener, mBackgroundHandler);

            if (width > MAX_PREVIEW_WIDTH) {
                width = MAX_PREVIEW_WIDTH;
            }

            if (height > MAX_PREVIEW_HEIGHT) {
                height = MAX_PREVIEW_HEIGHT;
            }

            // Danger, W.R.! Attempting to use too large a preview size could  exceed the camera
            // bus' bandwidth limitation, resulting in gorgeous previews but the storage of
            // garbage capture data.
            // 获取与预览控件最接近的预览Size尺寸, 若预览尺寸太大会导致预览效果超棒, 但捕获的静态图像很糟糕
            mPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class), width, height, aspectRatio);

            // Check if the flash is supported.
            // 是否支持flash
            Boolean available = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
            mFlashSupported = available == null ? false : available;
        }
    }

    /**
     * Configures the necessary {@link Matrix} transformation to `mTextureView`.
     * This method should be called after the camera preview size is determined in
     * setUpCameraOutputs and also the size of `mTextureView` is fixed.
     * 通过Matrix调整预览界面, 可缩放\平移等操作
     * @param viewWidth  The width of `mTextureView`
     * @param viewHeight The height of `mTextureView`
     */
    private void configureTransform(int viewWidth, int viewHeight) {
        if (null == mPreviewSize) {
            return;
        }
        int rotation = mWindowManager.getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, mPreviewSize.getHeight(), mPreviewSize.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) viewHeight / mPreviewSize.getHeight(),
                    (float) viewWidth / mPreviewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180, centerX, centerY);
        }
        setTransform(matrix);
    }

    /**
     * Given {@code choices} of {@code Size}s supported by a camera, choose the smallest one that
     * is at least as large as the respective texture view size, and that is at most as large as the
     * respective max size, and whose aspect ratio matches with the specified value. If such size
     * doesn't exist, choose the largest one that is at most as large as the respective max size,
     * and whose aspect ratio matches with the specified value.
     *
     * @param choices           The list of sizes that the camera supports for the intended output
     *                          class
     * @param textureViewWidth  The width of the texture view relative to sensor coordinate
     * @param textureViewHeight The height of the texture view relative to sensor coordinate
     * @param aspectRatio       The aspect ratio
     * @return The optimal {@code Size}, or an arbitrary one if none were big enough
     */
    private static Size chooseOptimalSize(Size[] choices, int textureViewWidth, int textureViewHeight, float aspectRatio) {

        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Size> bigEnough = new ArrayList<>();
        // Collect the supported resolutions that are smaller than the preview Surface
        List<Size> notBigEnough = new ArrayList<>();
        for (Size option : choices) {
            if (option.getWidth() == option.getHeight() * aspectRatio) {
                if (option.getWidth() >= textureViewWidth && option.getHeight() >= textureViewHeight) {
                    bigEnough.add(option);
                } else {
                    notBigEnough.add(option);
                }
            }
        }

        // Pick the smallest of those big enough. If there is no one big enough, pick the
        // largest of those not big enough.
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else if (notBigEnough.size() > 0) {
            return Collections.max(notBigEnough, new CompareSizesByArea());
        } else {
            return choices[0];
        }
    }

    /**
     * 根据使用相机前置或后置摄像头 与 设备方向 判断输出拍照图片方向
     * @param cameraCharacteristics 当前使用设备信息设备信息
     * @param deviceOrientation     设备方向
     */
    private int getJpegOrientation(CameraCharacteristics cameraCharacteristics, int deviceOrientation) {

        if (null != cameraCharacteristics) {
            if (deviceOrientation == OrientationEventListener.ORIENTATION_UNKNOWN) {
                return 0;
            }
            int sensorOrientation = cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);

            // Round device orientation to a multiple of 90
            deviceOrientation = (deviceOrientation + 45) / 90 * 90;

            // Reverse device orientation for front-facing cameras
            boolean facingFront = cameraCharacteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT;
            if (facingFront) {
                deviceOrientation = -deviceOrientation;
            }

            // Calculate desired JPEG orientation relative to camera orientation to make
            // the image upright relative to the device orientation
            return (sensorOrientation + deviceOrientation + 360) % 360;
        }
        return 0;
    }

    /**
     * 获取相机信息
     * @param cameraId 相机ID
     */
    private CameraCharacteristics getCameraCharacteristics(String cameraId) {

        if (!TextUtils.isEmpty(cameraId)) {
            if (cameraId.equals(backCameraId)) {
                return backCameraCharacteristics;
            } else if (cameraId.equals(frontCameraId)) {
                return frontCameraCharacteristics;
            }
        }

        return null;
    }

    /**
     * Compares two {@code Size}s based on their areas.
     */
    static class CompareSizesByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }
    }

    /*----------------------------------*/

    public void setCameraListener(CameraListener listener) {
        this.mCameraListener = listener;
    }

    public void setOutImagePath(String outImagePath) {
        this.outImagePath = outImagePath;
    }
}