package com.leachchen.testandroiduvctrtc;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import com.leachchen.testandroiduvctrtc.trtc.ConstantTrtc;
import com.leachchen.testandroiduvctrtc.trtc.GenerateTestUserSig;
import com.tencent.liteav.beauty.TXBeautyManager;
import com.tencent.trtc.TRTCCloud;
import com.tencent.trtc.TRTCCloudDef;

import java.nio.ByteBuffer;

import static com.tencent.trtc.TRTCCloudDef.TRTCRoleAnchor;
import static com.tencent.trtc.TRTCCloudDef.TRTC_APP_SCENE_VIDEOCALL;
import static com.tencent.trtc.TRTCCloudDef.TRTC_VIDEO_BUFFER_TYPE_BYTE_ARRAY;

public class Camera1Service extends Service {

    /**
     * 标识服务如果被杀死之后的行为
     */
    int mStartMode;

    /**
     * 绑定的客户端接口
     */
    IBinder mBinder;

    /**
     * 标识是否可以使用onRebind
     */
    boolean mAllowRebind;

    private TRTCCloud mTRTCCloud1;                 // SDK 核心类
    private String mRoomId = "123";                    // 房间Id
    private String mUserId = "123";                    // 用户Id
    private TRTCCloudDef.TRTCVideoFrame mFframe1;

    /**
     * 当服务被创建时调用.
     */
    @Override
    public void onCreate() {
        //Log.d("mytest", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa1111111111111111111111");
        enterRoom1();
    }

    private void enterRoom1() {
        Log.d("bbb", "bbbbbbbbbbbbbbbbbbbbb11:" + mRoomId+"--"+mUserId);
        mTRTCCloud1 = TRTCCloud.sharedInstance(getApplicationContext());
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

    /**
     * 调用startService()启动服务时回调
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return mStartMode;
    }

    /**
     * 通过bindService()绑定到服务的客户端
     */
    @Override
    public IBinder onBind(Intent intent) {
        return new MsgBinder();
    }

    /**
     * 通过unbindService()解除所有客户端绑定时调用
     */
    @Override
    public boolean onUnbind(Intent intent) {
        return mAllowRebind;
    }

    /**
     * 通过bindService()将客户端绑定到服务时调用
     */
    @Override
    public void onRebind(Intent intent) {

    }

    /**
     * 服务不再有用且将要被销毁时调用
     */
    @Override
    public void onDestroy() {

    }

    public class MsgBinder extends Binder {
        /**
         * 获取当前Service的实例
         *
         * @return
         */
        public Camera1Service getService() {
            return Camera1Service.this;
        }
    }

    public void updateData(ByteBuffer frame)
    {
        Log.d("mytest","aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        byte[] data=new byte[frame.remaining()];
        frame.get(data);
        mFframe1.width = 640;
        mFframe1.height = 480;
        mFframe1.pixelFormat = TRTCCloudDef.TRTC_VIDEO_PIXEL_FORMAT_I420;
        mFframe1.data = data;
        mTRTCCloud1.sendCustomVideoData(mFframe1);
    }
}