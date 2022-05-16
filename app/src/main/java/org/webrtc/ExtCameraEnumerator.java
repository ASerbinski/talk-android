package org.webrtc;

import android.content.Context;
import android.hardware.camera2.CameraManager;

import org.jetbrains.annotations.Nullable;

public class ExtCameraEnumerator extends Camera2Enumerator {

    final Context context;
    @Nullable final CameraManager cameraManager;

    public ExtCameraEnumerator(Context context) {
        super(context);
        this.context = context;
        this.cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
    }

    @Override
    public CameraVideoCapturer createCapturer(String deviceName, CameraVideoCapturer.CameraEventsHandler eventsHandler) {

        return new ExtVideoCapturer(context, deviceName, eventsHandler);
    }
}
