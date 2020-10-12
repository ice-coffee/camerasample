package com.sample.camera

import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*
import pub.devrel.easypermissions.EasyPermissions

class MainActivity : AppCompatActivity(), EasyPermissions.PermissionCallbacks {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        requestPremission()
    }

    override fun onResume() {
        super.onResume()
        if (null != cameraView) {
            cameraView.openCamera()
        }
    }

    override fun onPause() {
        super.onPause()
        if (null != cameraView) {
            cameraView.closeCamera()
        }
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    fun takeImage(view: View) {
        cameraView.takePicture()
    }

    fun requestPremission() {

        val hasPermissions = EasyPermissions.hasPermissions(this, android.Manifest.permission.CAMERA, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
        if (!hasPermissions) {
            EasyPermissions.requestPermissions(this, "请允许权限", 1, android.Manifest.permission.CAMERA, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    /**
     * 用户拒绝授权
     */
    override fun onPermissionsDenied(requestCode: Int, perms: MutableList<String>) {
        requestPremission()
    }

    /**
     * 用户授权
     */
    override fun onPermissionsGranted(requestCode: Int, perms: MutableList<String>) {
        requestPremission()
    }
}