/*
 *  UVCCamera
 *  library and sample to access to UVC web camera on non-rooted Android device
 *
 * Copyright (c) 2014-2017 saki t_saki@serenegiant.com
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 *  All files in the folder are under this Apache License, Version 2.0.
 *  Files in the libjpeg-turbo, libusb, libuvc, rapidjson folder
 *  may have a different license, see the respective files.
 */

package com.leachchen.testandroiduvctrtc;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.SurfaceTexture;
import android.hardware.usb.UsbDevice;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.text.TextUtils;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView.SurfaceTextureListener;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ImageButton;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.leachchen.testandroiduvctrtc.trtc.ConstantTrtc;
import com.leachchen.testandroiduvctrtc.trtc.GenerateTestUserSig;
import com.leachchen.testandroiduvctrtc.video.Encoder;
import com.leachchen.testandroiduvctrtc.video.SurfaceEncoder;
import com.serenegiant.common.BaseActivity;
import com.serenegiant.usb.CameraDialog;
import com.serenegiant.usb.IFrameCallback;
import com.serenegiant.usb.USBMonitor;
import com.serenegiant.usb.USBMonitor.OnDeviceConnectListener;
import com.serenegiant.usb.USBMonitor.UsbControlBlock;
import com.serenegiant.usb.UVCCamera;
import com.tencent.liteav.beauty.TXBeautyManager;
import com.tencent.rtmp.ui.TXCloudVideoView;
import com.tencent.trtc.TRTCCloud;
import com.tencent.trtc.TRTCCloudDef;
import com.tencent.trtc.TRTCCloudListener;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.GregorianCalendar;
import java.util.Locale;

import static com.tencent.trtc.TRTCCloudDef.TRTCRoleAnchor;
import static com.tencent.trtc.TRTCCloudDef.TRTC_APP_SCENE_VIDEOCALL;
import static com.tencent.trtc.TRTCCloudDef.TRTC_VIDEO_BUFFER_TYPE_BYTE_ARRAY;

public final class MainActivity extends BaseActivity implements CameraDialog.CameraDialogParent {
    private static final boolean DEBUG = true;	// set false when releasing
    private static final String TAG = "MainActivity";

    private static final int CAPTURE_STOP = 0;
    private static final int CAPTURE_PREPARE = 1;
    private static final int CAPTURE_RUNNING = 2;

    private final Object mSync = new Object();
    // for accessing USB and USB camera
    private USBMonitor mUSBMonitor1;
    private USBMonitor mUSBMonitor2;
    private UVCCamera mUVCCamera1;
    private UVCCamera mUVCCamera2;
    private SimpleUVCCameraTextureView mUVCCamera1View1;
    private SimpleUVCCameraTextureView mUVCCamera1View2;
    // for open&start / stop&close camera preview
    private ToggleButton mCameraButton;
    // for start & stop movie capture
    private ImageButton mCaptureButton;

    private int mCaptureState = 0;
    private Surface mPreviewSurface1;
    private Surface mPreviewSurface2;


    private boolean isFirstCamOpen = false;

    private TRTCCloud mTRTCCloud1;                 // SDK 核心类
    private TRTCCloud mTRTCCloud2;                 // SDK 核心类
    private String                          mRoomId = "123";                    // 房间Id
    private String                          mUserId = "123";                    // 用户Id
    private TRTCCloudDef.TRTCVideoFrame mFframe1;
    private TRTCCloudDef.TRTCVideoFrame mFframe2;
    private TXCloudVideoView mLocalPreviewView;          //【控件】本地画面View


    private Camera1Service mCamera1Service;
    private Messenger  mCamera2Service;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_main);

        mCameraButton = (ToggleButton)findViewById(R.id.camera_button);
        mCameraButton.setOnCheckedChangeListener(mOnCheckedChangeListener);

        mCaptureButton = (ImageButton)findViewById(R.id.capture_button);
        mCaptureButton.setOnClickListener(mOnClickListener);

        mUVCCamera1View1 = (SimpleUVCCameraTextureView)findViewById(R.id.UVCCameraTextureView1);
        mUVCCamera1View1.setAspectRatio(UVCCamera.DEFAULT_PREVIEW_WIDTH / (float)UVCCamera.DEFAULT_PREVIEW_HEIGHT);
        mUVCCamera1View1.setSurfaceTextureListener(mSurfaceTextureListener);

        mUSBMonitor1 = new USBMonitor(this, mOnDeviceConnectListener);

        mUVCCamera1View2 = (SimpleUVCCameraTextureView)findViewById(R.id.UVCCameraTextureView2);
        mUVCCamera1View2.setAspectRatio(UVCCamera.DEFAULT_PREVIEW_WIDTH / (float)UVCCamera.DEFAULT_PREVIEW_HEIGHT);
        mUVCCamera1View2.setSurfaceTextureListener(mSurfaceTextureListener);

        mUSBMonitor2 = new USBMonitor(this, mOnDeviceConnectListener);




        mLocalPreviewView   = findViewById(R.id.trtc_tc_cloud_view_main);

        /*new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                final List<DeviceFilter> filter = DeviceFilter.getDeviceFilters(MainActivity.this, R.xml.device_filter);
                List<UsbDevice> devices = mUSBMonitor1.getDeviceList(filter.get(0));
                mUSBMonitor1.requestPermission((UsbDevice)devices.get(0));
            }
        },1000);*/
        //startService(new Intent(getBaseContext(), Camera1Service.class));

        //绑定Service
        Intent intent1 = new Intent(this,Camera1Service.class);
        intent1.putExtra("RoomId","123");
        intent1.putExtra("UserName","111");
        intent1.putExtra("AppId",GenerateTestUserSig.SDKAPPID);
        bindService(intent1, connService1, Context.BIND_AUTO_CREATE);

        Intent intent2 = new Intent(this,Camera2Service.class);
        intent2.putExtra("RoomId","123");
        intent2.putExtra("UserName","222");
        intent2.putExtra("AppId",GenerateTestUserSig.SDKAPPID);
        bindService(intent2, connService2, Context.BIND_AUTO_CREATE);
    }

    ServiceConnection connService1 = new ServiceConnection() {

        @Override
        public void onServiceDisconnected(ComponentName name) {

        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            //返回一个MsgService对象
            mCamera1Service = ((Camera1Service.MsgBinder) service).getService();
        }
    };

    private ServiceConnection connService2 = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            // Activity已经绑定了Service
            // 通过参数service来创建Messenger对象，这个对象可以向Service发送Message，与Service进行通信
            mCamera2Service = new Messenger(service);

            /*Message message = Message.obtain(null, Camera2Service.MSG_SAY_HELLO);
            Bundle data = new Bundle();
            data.putString("msg", "I am from the client.");
            message.setData(data);
            try{
                mCamera2Service.send(message);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
            Log.d("mytest","aaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");*/
        }

        public void onServiceDisconnected(ComponentName className) {
        }
    };

    /*ServiceConnection connService2 = new ServiceConnection() {

        @Override
        public void onServiceDisconnected(ComponentName name) {

        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            //返回一个MsgService对象
            mCamera2Service = ((Camera2Service.MsgBinder) service).getService();
        }
    };*/


        private void enterRoom1() {
        Log.d("bbb","bbbbbbbbbbbbbbbbbbbbb:"+mRoomId);
        mTRTCCloud1 = TRTCCloud.sharedInstance(getApplicationContext());
        mTRTCCloud1.setListener(new TRTCCloudImplListener(MainActivity.this));

        /*try {
            Class<?> classBook = Class.forName("com.tencent.liteav.trtc.impl.TRTCCloudImpl");
            Constructor<?> declaredConstructorBook = classBook.getDeclaredConstructor(Context.class);
            declaredConstructorBook.setAccessible(true);
            Object trtcCloudObj = declaredConstructorBook.newInstance(getApplicationContext());
            mTRTCCloud1 = (TRTCCloud) trtcCloudObj;
        }catch (Exception e)
        {

        }*/

        // 初始化配置 SDK 参数
        TRTCCloudDef.TRTCParams trtcParams = new TRTCCloudDef.TRTCParams();
        trtcParams.sdkAppId = GenerateTestUserSig.SDKAPPID;
        trtcParams.userId = mUserId;
        trtcParams.roomId = Integer.parseInt(mRoomId);
        // userSig是进入房间的用户签名，相当于密码（这里生成的是测试签名，正确做法需要业务服务器来生成，然后下发给客户端）
        trtcParams.userSig = GenerateTestUserSig.genTestUserSig(trtcParams.userId);
        trtcParams.role = TRTCRoleAnchor;

        // 进入通话
        mTRTCCloud1.enterRoom(trtcParams, TRTC_APP_SCENE_VIDEOCALL);
        // 开启本地声音采集并上行
        //mTRTCCloud1.startLocalAudio();
        // 开启本地画面采集并上行
        //mTRTCCloud1.startLocalPreview(true, mLocalPreviewView);
        mTRTCCloud1.enableCustomVideoCapture(true);


        mFframe1 = new TRTCCloudDef.TRTCVideoFrame();
        mFframe1.bufferType = TRTC_VIDEO_BUFFER_TYPE_BYTE_ARRAY;

        /**
         * 设置默认美颜效果（美颜效果：自然，美颜级别：5, 美白级别：1）
         * 美颜风格.三种美颜风格：0 ：光滑  1：自然  2：朦胧
         * 视频通话场景推荐使用“自然”美颜效果
         */
        TXBeautyManager beautyManager = mTRTCCloud1.getBeautyManager();
        beautyManager.setBeautyStyle(ConstantTrtc.BEAUTY_STYLE_NATURE);
        beautyManager.setBeautyLevel(5);
        beautyManager.setWhitenessLevel(1);

        TRTCCloudDef.TRTCVideoEncParam encParam = new TRTCCloudDef.TRTCVideoEncParam();
        encParam.videoResolution = TRTCCloudDef.TRTC_VIDEO_RESOLUTION_640_360;
        encParam.videoFps = ConstantTrtc.VIDEO_FPS;
        encParam.videoBitrate = ConstantTrtc.RTC_VIDEO_BITRATE;
        encParam.videoResolutionMode = TRTCCloudDef.TRTC_VIDEO_RESOLUTION_MODE_PORTRAIT;
        mTRTCCloud1.setVideoEncoderParam(encParam);
    }

    private void enterRoom2() {
        Log.d("bbb","bbbbbbbbbbbbbbbbbbbbb:"+mRoomId);
        //mTRTCCloud2 = TRTCCloud.sharedInstance(getApplicationContext());
        //mTRTCCloud2.setListener(new TRTCCloudImplListener(MainActivity.this));

        try {
            Class<?> classBook = Class.forName("com.tencent.liteav.trtc.impl.TRTCCloudImpl");
            Constructor<?> declaredConstructorBook = classBook.getDeclaredConstructor(Context.class);
            declaredConstructorBook.setAccessible(true);
            Object trtcCloudObj = declaredConstructorBook.newInstance(getApplicationContext());
            mTRTCCloud2 = (TRTCCloud) trtcCloudObj;
        }catch (Exception e)
        {

        }

        // 初始化配置 SDK 参数
        TRTCCloudDef.TRTCParams trtcParams = new TRTCCloudDef.TRTCParams();
        trtcParams.sdkAppId = GenerateTestUserSig.SDKAPPID;
        trtcParams.userId = "1001";
        trtcParams.roomId = Integer.parseInt(mRoomId);
        // userSig是进入房间的用户签名，相当于密码（这里生成的是测试签名，正确做法需要业务服务器来生成，然后下发给客户端）
        trtcParams.userSig = GenerateTestUserSig.genTestUserSig(trtcParams.userId);
        trtcParams.role = TRTCRoleAnchor;

        // 进入通话
        mTRTCCloud2.enterRoom(trtcParams, TRTC_APP_SCENE_VIDEOCALL);
        // 开启本地声音采集并上行
        //mTRTCCloud2.startLocalAudio();
        // 开启本地画面采集并上行
        //mTRTCCloud2.startLocalPreview(true, mLocalPreviewView);
        mTRTCCloud2.enableCustomVideoCapture(true);


        mFframe2 = new TRTCCloudDef.TRTCVideoFrame();
        mFframe2.bufferType = TRTC_VIDEO_BUFFER_TYPE_BYTE_ARRAY;

        /**
         * 设置默认美颜效果（美颜效果：自然，美颜级别：5, 美白级别：1）
         * 美颜风格.三种美颜风格：0 ：光滑  1：自然  2：朦胧
         * 视频通话场景推荐使用“自然”美颜效果
         */
        TXBeautyManager beautyManager = mTRTCCloud2.getBeautyManager();
        beautyManager.setBeautyStyle(ConstantTrtc.BEAUTY_STYLE_NATURE);
        beautyManager.setBeautyLevel(5);
        beautyManager.setWhitenessLevel(1);

        TRTCCloudDef.TRTCVideoEncParam encParam = new TRTCCloudDef.TRTCVideoEncParam();
        encParam.videoResolution = TRTCCloudDef.TRTC_VIDEO_RESOLUTION_640_360;
        encParam.videoFps = ConstantTrtc.VIDEO_FPS;
        encParam.videoBitrate = ConstantTrtc.RTC_VIDEO_BITRATE;
        encParam.videoResolutionMode = TRTCCloudDef.TRTC_VIDEO_RESOLUTION_MODE_PORTRAIT;
        mTRTCCloud2.setVideoEncoderParam(encParam);
    }


    @Override
    protected void onStart() {
        super.onStart();
        synchronized (mSync) {
            if (mUSBMonitor1 != null) {
                mUSBMonitor1.register();
            }
            if (mUVCCamera1 != null)
                mUVCCamera1.startPreview();
        }
        synchronized (mSync) {
            if (mUSBMonitor2 != null) {
                mUSBMonitor2.register();
            }
            if (mUVCCamera2 != null)
                mUVCCamera2.startPreview();
        }
        setCameraButton(false);
        updateItems();
    }

    @Override
    protected void onStop() {
        synchronized (mSync) {
            if (mUVCCamera1 != null) {
                stopCapture();
                mUVCCamera1.stopPreview();
            }
            mUSBMonitor1.unregister();

            if (mUVCCamera2 != null) {
                stopCapture();
                mUVCCamera2.stopPreview();
            }
            mUSBMonitor2.unregister();
        }
        setCameraButton(false);
        super.onStop();
    }

    @Override
    public void onDestroy() {
        synchronized (mSync) {
            if (mUVCCamera1 != null) {
                mUVCCamera1.destroy();
                mUVCCamera1 = null;
            }
            if (mUSBMonitor1 != null) {
                mUSBMonitor1.destroy();
                mUSBMonitor1 = null;
            }

            if (mUVCCamera2 != null) {
                mUVCCamera2.destroy();
                mUVCCamera2 = null;
            }
            if (mUSBMonitor2 != null) {
                mUSBMonitor2.destroy();
                mUSBMonitor2 = null;
            }
        }
        mCameraButton = null;
        mCaptureButton = null;
        mUVCCamera1View1 = null;
        mUVCCamera1View2 = null;
        super.onDestroy();
    }

    private final OnCheckedChangeListener mOnCheckedChangeListener = new OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(final CompoundButton buttonView, final boolean isChecked) {
            synchronized (mSync) {
                /*if (isChecked && mUVCCamera1 == null) {
                    CameraDialog.showDialog(MainActivity.this);
                } else if (mUVCCamera1 != null) {
                    mUVCCamera1.destroy();
                    mUVCCamera1 = null;
                }

                if (isChecked && mUVCCamera2 == null) {
                    CameraDialog.showDialog(MainActivity.this);
                } else if (mUVCCamera2 != null) {
                    mUVCCamera2.destroy();
                    mUVCCamera2 = null;
                }*/
                CameraDialog.showDialog(MainActivity.this);
            }
            updateItems();
        }
    };

    private final OnClickListener mOnClickListener = new OnClickListener() {
        @Override
        public void onClick(final View v) {
            if (checkPermissionWriteExternalStorage()) {
                if (mCaptureState == CAPTURE_STOP) {
                    startCapture();
                } else {
                    stopCapture();
                }
            }
        }
    };

    private final OnDeviceConnectListener mOnDeviceConnectListener = new OnDeviceConnectListener() {
        @Override
        public void onAttach(final UsbDevice device) {
            Toast.makeText(MainActivity.this, "USB_DEVICE_ATTACHED", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onConnect(final UsbDevice device, final UsbControlBlock ctrlBlock, final boolean createNew) {
            /*synchronized (mSync) {
                if (mUVCCamera1 != null) {
                    mUVCCamera1.destroy();
                    mUVCCamera1 = null;
                }
            }
            synchronized (mSync) {
                if (mUVCCamera2 != null) {
                    mUVCCamera2.destroy();
                    mUVCCamera2 = null;
                }
            }*/
            queueEvent(new Runnable() {
                @Override
                public void run() {
                    if(!isFirstCamOpen) {
                        //enterRoom1();
                        isFirstCamOpen = true;
                        final UVCCamera camera = new UVCCamera();
                        camera.open(ctrlBlock);
                        if (DEBUG) Log.i(TAG, "supportedSize:" + camera.getSupportedSize());
                        if (mPreviewSurface1 != null) {
                            mPreviewSurface1.release();
                            mPreviewSurface1 = null;
                        }
                        try {
                            camera.setPreviewSize(UVCCamera.DEFAULT_PREVIEW_WIDTH, UVCCamera.DEFAULT_PREVIEW_HEIGHT, UVCCamera.FRAME_FORMAT_MJPEG);
                        } catch (final IllegalArgumentException e) {
                            try {
                                // fallback to YUV mode
                                camera.setPreviewSize(UVCCamera.DEFAULT_PREVIEW_WIDTH, UVCCamera.DEFAULT_PREVIEW_HEIGHT, UVCCamera.DEFAULT_PREVIEW_MODE);
                            } catch (final IllegalArgumentException e1) {
                                camera.destroy();
                                return;
                            }
                        }
                        final SurfaceTexture st = mUVCCamera1View1.getSurfaceTexture();
                        if (st != null) {
                            mPreviewSurface1 = new Surface(st);
                            camera.setPreviewDisplay(mPreviewSurface1);
                            camera.startPreview();
                        }

                        camera.setFrameCallback(mIFrameCallback1, UVCCamera.PIXEL_FORMAT_YUV420SP);
                        synchronized (mSync) {
                            mUVCCamera1 = camera;
                        }
                    }else{
                        //enterRoom2();
                        final UVCCamera camera = new UVCCamera();
                        camera.open(ctrlBlock);
                        if (DEBUG) Log.i(TAG, "supportedSize:" + camera.getSupportedSize());
                        if (mPreviewSurface2 != null) {
                            mPreviewSurface2.release();
                            mPreviewSurface2 = null;
                        }
                        try {
                            camera.setPreviewSize(UVCCamera.DEFAULT_PREVIEW_WIDTH, UVCCamera.DEFAULT_PREVIEW_HEIGHT, UVCCamera.FRAME_FORMAT_MJPEG);
                        } catch (final IllegalArgumentException e) {
                            try {
                                // fallback to YUV mode
                                camera.setPreviewSize(UVCCamera.DEFAULT_PREVIEW_WIDTH, UVCCamera.DEFAULT_PREVIEW_HEIGHT, UVCCamera.DEFAULT_PREVIEW_MODE);
                            } catch (final IllegalArgumentException e1) {
                                camera.destroy();
                                return;
                            }
                        }
                        final SurfaceTexture st = mUVCCamera1View2.getSurfaceTexture();
                        if (st != null) {
                            mPreviewSurface2 = new Surface(st);
                            camera.setPreviewDisplay(mPreviewSurface2);
                            camera.startPreview();
                        }

                        camera.setFrameCallback(mIFrameCallback2, UVCCamera.PIXEL_FORMAT_YUV420SP);
                        synchronized (mSync) {
                            mUVCCamera2 = camera;
                        }
                    }
                }
            }, 0);
        }

        @Override
        public void onDisconnect(final UsbDevice device, final UsbControlBlock ctrlBlock) {
            // XXX you should check whether the comming device equal to camera device that currently using
            queueEvent(new Runnable() {
                @Override
                public void run() {
                    synchronized (mSync) {
                        if (mUVCCamera1 != null) {
                            mUVCCamera1.close();
                        }
                    }
                    if (mPreviewSurface1 != null) {
                        mPreviewSurface1.release();
                        mPreviewSurface1 = null;
                    }

                    synchronized (mSync) {
                        if (mUVCCamera2 != null) {
                            mUVCCamera2.close();
                        }
                    }
                    if (mPreviewSurface2 != null) {
                        mPreviewSurface2.release();
                        mPreviewSurface2 = null;
                    }
                }
            }, 0);
            setCameraButton(false);
        }

        @Override
        public void onDettach(final UsbDevice device) {
            Toast.makeText(MainActivity.this, "USB_DEVICE_DETACHED", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onCancel(final UsbDevice device) {
            setCameraButton(false);
        }
    };

    /**
     * to access from CameraDialog
     * @return
     */
    @Override
    public USBMonitor getUSBMonitor() {
        return mUSBMonitor1;
    }

    @Override
    public void onDialogResult(boolean canceled) {
        if (canceled) {
            setCameraButton(false);
        }
    }

    private void setCameraButton(final boolean isOn) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mCameraButton != null) {
                    try {
                        mCameraButton.setOnCheckedChangeListener(null);
                        mCameraButton.setChecked(isOn);
                    } finally {
                        mCameraButton.setOnCheckedChangeListener(mOnCheckedChangeListener);
                    }
                }
                if (!isOn && (mCaptureButton != null)) {
                    mCaptureButton.setVisibility(View.INVISIBLE);
                }
            }
        }, 0);
    }

    //**********************************************************************
    private final SurfaceTextureListener mSurfaceTextureListener = new SurfaceTextureListener() {

        @Override
        public void onSurfaceTextureAvailable(final SurfaceTexture surface, final int width, final int height) {
        }

        @Override
        public void onSurfaceTextureSizeChanged(final SurfaceTexture surface, final int width, final int height) {
        }

        @Override
        public boolean onSurfaceTextureDestroyed(final SurfaceTexture surface) {
            if (mPreviewSurface1 != null) {
                mPreviewSurface1.release();
                mPreviewSurface1 = null;
            }
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(final SurfaceTexture surface) {
            if (mEncoder != null && mCaptureState == CAPTURE_RUNNING) {
                mEncoder.frameAvailable();
            }
        }
    };

    private Encoder mEncoder;
    /**
     * start capturing
     */
    private final void startCapture() {
        if (DEBUG) Log.v(TAG, "startCapture:");
        if (mEncoder == null && (mCaptureState == CAPTURE_STOP)) {
            mCaptureState = CAPTURE_PREPARE;
            queueEvent(new Runnable() {
                @Override
                public void run() {
                    final String path = getCaptureFile(Environment.DIRECTORY_MOVIES, ".mp4");
                    if (!TextUtils.isEmpty(path)) {
                        mEncoder = new SurfaceEncoder(path);
                        mEncoder.setEncodeListener(mEncodeListener);
                        try {
                            mEncoder.prepare();
                            mEncoder.startRecording();
                        } catch (final IOException e) {
                            mCaptureState = CAPTURE_STOP;
                        }
                    } else
                        throw new RuntimeException("Failed to start capture.");
                }
            }, 0);
            updateItems();
        }
    }

    /**
     * stop capture if capturing
     */
    private final void stopCapture() {
        if (DEBUG) Log.v(TAG, "stopCapture:");
        queueEvent(new Runnable() {
            @Override
            public void run() {
                synchronized (mSync) {
                    if (mUVCCamera1 != null) {
                        mUVCCamera1.stopCapture();
                    }
                }
                if (mEncoder != null) {
                    mEncoder.stopRecording();
                    mEncoder = null;
                }
            }
        }, 0);
    }

    /**
     * callbackds from Encoder
     */
    private final Encoder.EncodeListener mEncodeListener = new Encoder.EncodeListener() {
        @Override
        public void onPreapared(final Encoder encoder) {
            if (DEBUG) Log.v(TAG, "onPreapared:");
            synchronized (mSync) {
                if (mUVCCamera1 != null) {
                    mUVCCamera1.startCapture(((SurfaceEncoder)encoder).getInputSurface());
                }
            }
            mCaptureState = CAPTURE_RUNNING;
        }

        @Override
        public void onRelease(final Encoder encoder) {
            if (DEBUG) Log.v(TAG, "onRelease:");
            synchronized (mSync) {
                if (mUVCCamera1 != null) {
                    mUVCCamera1.stopCapture();
                }
            }
            mCaptureState = CAPTURE_STOP;
            updateItems();
        }
    };

    private void updateItems() {
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mCaptureButton.setVisibility(mCameraButton.isChecked() ? View.VISIBLE : View.INVISIBLE);
                mCaptureButton.setColorFilter(mCaptureState == CAPTURE_STOP ? 0 : 0xffff0000);
            }
        });
    }

    /**
     * create file path for saving movie / still image file
     * @param type Environment.DIRECTORY_MOVIES / Environment.DIRECTORY_DCIM
     * @param ext .mp4 / .png
     * @return return null if can not write to storage
     */
    private static final String getCaptureFile(final String type, final String ext) {
        final File dir = new File(Environment.getExternalStoragePublicDirectory(type), "USBCameraTest");
        dir.mkdirs();	// create directories if they do not exist
        if (dir.canWrite()) {
            return (new File(dir, getDateTimeString() + ext)).toString();
        }
        return null;
    }

    private static final SimpleDateFormat sDateTimeFormat = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US);
    private static final String getDateTimeString() {
        final GregorianCalendar now = new GregorianCalendar();
        return sDateTimeFormat.format(now.getTime());
    }

    private final IFrameCallback mIFrameCallback1 = new IFrameCallback() {
        @Override
        public void onFrame(final ByteBuffer frame) {
            /*byte[] data=new byte[frame.remaining()];
            frame.get(data);
            mFframe1.width = 640;
            mFframe1.height = 480;
            mFframe1.pixelFormat = TRTCCloudDef.TRTC_VIDEO_PIXEL_FORMAT_I420;
            mFframe1.data = data;
            Log.d("mytest","aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa:"+data.length);
            mTRTCCloud1.sendCustomVideoData(mFframe1);*/
            mCamera1Service.updateData(frame);
        }
    };

    private final IFrameCallback mIFrameCallback2 = new IFrameCallback() {
        @Override
        public void onFrame(final ByteBuffer frame) {

            /*byte[] data = new byte[frame.remaining()];
            frame.get(data);
            mFframe2.width = 640;
            mFframe2.height = 480;
            mFframe2.pixelFormat = TRTCCloudDef.TRTC_VIDEO_PIXEL_FORMAT_I420;
            mFframe2.data = data;
            Log.d("mytest", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa1111111111:" + data.length);
            mTRTCCloud2.sendCustomVideoData(mFframe2);*/
            //mCamera2Service.updateData(frame);

            Message msg = Message.obtain(null, Camera2Service.MSG_SAY_HELLO);
            try {
                Bundle data = new Bundle();
                data.putString("msg", "I am from the client.");
                byte[] dataArray=new byte[frame.remaining()];
                frame.get(dataArray);
                data.putByteArray("key1",dataArray);
                msg.setData(data);
                mCamera2Service.send(msg);
                Log.d("mytest","ccccccccccccccccccccccccc:"+dataArray.length);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    };

    private class TRTCCloudImplListener extends TRTCCloudListener {

        private WeakReference<MainActivity>      mContext;

        public TRTCCloudImplListener(MainActivity activity) {
            super();
            mContext = new WeakReference<>(activity);
        }

        @Override
        public void onUserVideoAvailable(String userId, boolean available) {
            /*Log.d(TAG, "onUserVideoAvailable userId " + userId + ", mUserCount " + mUserCount + ",available " + available);
            int index = mRemoteUidList.indexOf(userId);
            if (available) {
                if (index != -1) { //如果mRemoteUidList有，就不重复添加
                    return;
                }
                mRemoteUidList.add(userId);
                refreshRemoteVideoViews();
            } else {
                if (index == -1) { //如果mRemoteUidList没有，说明已关闭画面
                    return;
                }
                /// 关闭用户userId的视频画面
                mTRTCCloud1.stopRemoteView(userId);
                mRemoteUidList.remove(index);
                refreshRemoteVideoViews();
            }*/

        }

        private void refreshRemoteVideoViews() {
            /*for (int i = 0; i < mRemoteViewList.size(); i++) {
                if (i < mRemoteUidList.size()) {
                    String remoteUid = mRemoteUidList.get(i);
                    mRemoteViewList.get(i).setVisibility(View.VISIBLE);
                    // 开始显示用户userId的视频画面
                    mTRTCCloud1.startRemoteView(remoteUid, mRemoteViewList.get(i));
                } else {
                    mRemoteViewList.get(i).setVisibility(View.GONE);
                }
            }*/
        }

        // 错误通知监听，错误通知意味着 SDK 不能继续运行
        @Override
        public void onError(int errCode, String errMsg, Bundle extraInfo) {
           /* Log.d(TAG, "sdk callback onError");
            MainActivity activity = mContext.get();
            if (activity != null) {
                Toast.makeText(activity, "onError: " + errMsg + "[" + errCode+ "]" , Toast.LENGTH_SHORT).show();
                if (errCode == TXLiteAVCode.ERR_ROOM_ENTER_FAIL) {
                    activity.exitRoom();
                }
            }*/
        }
    }

}
