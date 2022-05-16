package org.webrtc;

import android.content.Context;
import android.graphics.Camera;
import android.hardware.camera2.CameraManager;

import org.jetbrains.annotations.Nullable;

public class ExtVideoCapturer extends Camera2Capturer {

    @Nullable private final CameraManager cameraManager;

    public ExtVideoCapturer(Context context, String cameraName, CameraEventsHandler eventsHandler) {
        super(context, cameraName, eventsHandler);
        cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
    }

    @Override
    protected void createCameraSession(CameraSession.CreateSessionCallback createSessionCallback,
                                       CameraSession.Events events, Context applicationContext,
                                       SurfaceTextureHelper surfaceTextureHelper, String cameraName, int width, int height,
                                       int framerate) {

        CameraSession.CreateSessionCallback myCallback = new CameraSession.CreateSessionCallback() {
            @Override
            public void onDone(CameraSession cameraSession) {
                //ExtVideoCapturer.this.cameraSession = (ExtCameraSession) cameraSession;
                createSessionCallback.onDone(cameraSession);
            }

            @Override
            public void onFailure(CameraSession.FailureType failureType, String s) {
                createSessionCallback.onFailure(failureType, s);
            }
        };

        ExtCameraSession.create(myCallback, events, applicationContext, cameraManager,
                              surfaceTextureHelper, cameraName, width, height, framerate);
    }

    public void fixupParams(){

    }
}
