package com.sample.camera.camera;

/**
 * @author mzp
 * date : 2020/9/15
 * desc :
 */
public interface CameraListener {

    int CAMERA_OPEN_ERROR = 1;
    int CAMERA_SESSION_ERROR = 2;
    int CAMERA_CAPTURE_ERROR = 3;

    void onCameraError();

    void onTakePicture(String filePath);
}