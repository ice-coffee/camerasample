package com.sample.camera.camera;

/**
 * @author mzp
 * date : 2020/9/15
 * desc :
 */
interface CameraInterface {

    /**
     * Camera state: Showing camera preview.
     * 相机预览状态
     */
    int STATE_PREVIEW = 0;

    /**
     * Camera state: Waiting for the focus to be locked.
     * 焦点锁定
     */
    int STATE_WAITING_LOCK = 1;

    /**
     * Camera state: Waiting for the exposure to be precapture state.
     * 等待曝光
     */
    int STATE_WAITING_PRECAPTURE = 2;

    /**
     * Camera state: Waiting for the exposure state to be something other than precapture.
     * 等待曝光
     */
    int STATE_WAITING_NON_PRECAPTURE = 3;

    /**
     * Camera state: Picture was taken.
     * 已拍照
     */
    int STATE_PICTURE_TAKEN = 4;

    /**
     * 初始化相机
     */
    void initCamera();

    /**
     * 打开相机
     */
    void openCamera();

    /**
     * 关闭相机
     */
    void closeCamera();

    /**
     * 相机预览
     */
    void cameraPreview();

    /**
     * 相机拍照
     */
    void takePicture();

    /**
     * 切换前置后置摄像
     */
    void switchCamera();
}