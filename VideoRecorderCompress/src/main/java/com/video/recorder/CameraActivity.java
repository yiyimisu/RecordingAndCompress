package com.video.recorder;

import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.video.utils.CheckPermissionsUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static android.view.View.VISIBLE;

/**
 /**
 * Created by yangc on 2017/4/24.
 * E-Mail:yangchaojiang@outlook.com
 * Deprecated: 由于Camera2这个类要求minSDK大于21, 所以依旧使用Camera这个类进行实现
 */
public class CameraActivity extends Activity implements View.OnClickListener, ShapeProgressButton.ProgressListener {
    private static final String TAG = CameraActivity.class.getSimpleName();
    private static final int FOCUS_AREA_SIZE = 500;
    private Camera mCamera;
    private CameraPreview mPreview;
    private MediaRecorder mediaRecorder;
    private String url_file;
    private static boolean cameraFront = false;
    private static boolean flash = false;
    private int quality = CamcorderProfile.QUALITY_720P;
    private ImageView button_changeCamera, buttonFlash, img_repleal, img_selected, img_back;
    private ShapeProgressButton mZoomProgressBtn;
    private RelativeLayout re_video_control_panel;
    private FrameLayout camera_preview;
    private RelativeLayout re_video_record_panel;
    private TextView txt_touch_shoot;
    private boolean recording = false;

    // 对焦动画视图
    private ImageView mFocusAnimationView;
    private Animation mFocusAnimation;

    public static void startRecordActivity(Activity activity) {
        Intent intent = new Intent(activity, CameraActivity.class);
        activity.startActivityForResult(intent, 3);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_camera);
        //////////////////////////////////////////////////////////////////
        //Andriod6.0 running permission
        CheckPermissionsUtil checkPermissionsUtil = new CheckPermissionsUtil(this);
        checkPermissionsUtil.requestAllPermission(this);
        this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        button_changeCamera = (ImageView) findViewById(R.id.button_ChangeCamera);
        buttonFlash = (ImageView) findViewById(R.id.buttonFlash);
        img_repleal = (ImageView) findViewById(R.id.img_repleal);
        img_selected = (ImageView) findViewById(R.id.img_selected);
        img_back = (ImageView) findViewById(R.id.img_back);
        camera_preview = (FrameLayout) findViewById(R.id.camera_preview);
        re_video_control_panel = (RelativeLayout) findViewById(R.id.re_video_control_panel);
        re_video_record_panel = (RelativeLayout) findViewById(R.id.re_video_record_panel);
        txt_touch_shoot = (TextView) findViewById(R.id.txt_touch_shoot);
        button_changeCamera.setVisibility(VISIBLE);
        initialize();
        initFocusView();
    }

    private void initFocusView() {
        // 添加对焦动画视图
        mFocusAnimationView = new ImageView(this);
        mFocusAnimationView.setVisibility(View.INVISIBLE);
        mFocusAnimationView.setImageResource(R.mipmap.icon_camera_focus);
        camera_preview.addView(mFocusAnimationView, new ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        // 定义对焦动画
        mFocusAnimation = AnimationUtils.loadAnimation(this, R.anim.focus_animation);
        mFocusAnimation.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {

            }

            @Override
            public void onAnimationEnd(Animation animation) {
                mFocusAnimationView.setVisibility(View.INVISIBLE);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });
    }


    /**
     * 找前置摄像头,没有则返回-1
     *
     * @return cameraId
     */
    private int findFrontFacingCamera() {
        int cameraId = -1;
        //获取摄像头个数
        int numberOfCameras = Camera.getNumberOfCameras();
        for (int i = 0; i < numberOfCameras; i++) {
            Camera.CameraInfo info = new Camera.CameraInfo();
            Camera.getCameraInfo(i, info);
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                cameraId = i;
                cameraFront = true;
                break;
            }
        }
        return cameraId;
    }

    /**
     * 找后置摄像头,没有则返回-1
     *
     * @return cameraId
     */
    private int findBackFacingCamera() {
        int cameraId = -1;
        //获取摄像头个数
        int numberOfCameras = Camera.getNumberOfCameras();
        for (int i = 0; i < numberOfCameras; i++) {
            Camera.CameraInfo info = new Camera.CameraInfo();
            Camera.getCameraInfo(i, info);
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                cameraId = i;
                cameraFront = false;
                break;
            }
        }
        return cameraId;
    }

    @Override
    protected void onStart() {
        super.onStart();
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                preparCamera();
            }
        }, 500);
    }

    //点击对焦
    public void initialize() {
        mPreview = new CameraPreview(CameraActivity.this, mCamera);
        camera_preview.addView(mPreview);
        mZoomProgressBtn = (ShapeProgressButton) findViewById(R.id.button_capture);
        mZoomProgressBtn.setMaxMillSecond(30000);
        mZoomProgressBtn.setOnTouchListener(mOnVideoControllerTouchListener);
        button_changeCamera.setOnClickListener(switchCameraListener);
        buttonFlash.setOnClickListener(flashListener);
        img_back.setOnClickListener(this);
        img_repleal.setOnClickListener(this);
        img_selected.setOnClickListener(this);
        camera_preview.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    try {
                        focusOnTouch(event);
                    } catch (Exception e) {
                        Log.i(TAG, getString(R.string.fail_when_camera_try_autofocus, e.toString()));
                    }
                }
                return true;
            }
        });
        mZoomProgressBtn.setProgressListener(this);
    }

    public void preparCamera() {
        if (!hasCamera(getApplicationContext())) {
            //这台设备没有发现摄像头
            Toast.makeText(getApplicationContext(), R.string.dont_have_camera_error, Toast.LENGTH_SHORT).show();
            releaseCamera();
            releaseMediaRecorder();
            finish();
        }
        if (mCamera == null) {
            releaseCamera();
            final boolean frontal = cameraFront;
            int cameraId = findFrontFacingCamera();
            if (cameraId < 0) {
                //前置摄像头不存在
                switchCameraListener = new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Toast.makeText(CameraActivity.this, R.string.dont_have_front_camera, Toast.LENGTH_SHORT).show();
                    }
                };
                //尝试寻找后置摄像头
                cameraId = findBackFacingCamera();
                if (flash) {
                    mPreview.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
                    buttonFlash.setImageResource(R.mipmap.ic_launcher);
                }
            } else if (!frontal) {
                cameraId = findBackFacingCamera();
                if (flash) {
                    mPreview.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
                    buttonFlash.setImageResource(R.mipmap.ic_launcher);
                }
            }

            mCamera = Camera.open(cameraId);
            mPreview.refreshCamera(mCamera);
            reloadQualities(cameraId);
        }
    }

    //reload成像质量
    private void reloadQualities(int idCamera) {
        SharedPreferences prefs = getSharedPreferences("RECORDING", Context.MODE_PRIVATE);
        quality = prefs.getInt("QUALITY", CamcorderProfile.QUALITY_480P);
        changeVideoQuality(quality);
        final ArrayList<String> list = new ArrayList<>();
        int maxQualitySupported = CamcorderProfile.QUALITY_480P;
        if (CamcorderProfile.hasProfile(idCamera, CamcorderProfile.QUALITY_480P)) {
            list.add("480p");
            maxQualitySupported = CamcorderProfile.QUALITY_480P;
        }
        if (CamcorderProfile.hasProfile(idCamera, CamcorderProfile.QUALITY_720P)) {
            list.add("720p");
            maxQualitySupported = CamcorderProfile.QUALITY_720P;
        }
        if (CamcorderProfile.hasProfile(idCamera, CamcorderProfile.QUALITY_1080P)) {
            list.add("1080p");
            maxQualitySupported = CamcorderProfile.QUALITY_1080P;
        }
        if (CamcorderProfile.hasProfile(idCamera, CamcorderProfile.QUALITY_2160P)) {
            list.add("2160p");
            maxQualitySupported = CamcorderProfile.QUALITY_2160P;
        }
        if (!CamcorderProfile.hasProfile(idCamera, quality)) {
            quality = maxQualitySupported;
        }
    }

    //闪光灯
    View.OnClickListener flashListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (!cameraFront) {
                if (flash) {
                    flash = false;
                    buttonFlash.setImageResource(R.mipmap.ic_launcher);
                    setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
                } else {
                    flash = true;
                    buttonFlash.setImageResource(R.mipmap.ic_launcher);
                    setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
                }
            }
        }
    };

    //切换前置后置摄像头
    View.OnClickListener switchCameraListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (!recording) {
                int camerasNumber = Camera.getNumberOfCameras();
                if (camerasNumber > 1) {
                    releaseCamera();
                    chooseCamera();
                } else {
                    //只有一个摄像头不允许切换
                    Toast.makeText(getApplicationContext(), R.string.only_have_one_camera
                            , Toast.LENGTH_SHORT).show();
                }
            }
        }
    };


    //选择摄像头
    public void chooseCamera() {
        if (cameraFront) {
            //当前是前置摄像头
            int cameraId = findBackFacingCamera();
            if (cameraId >= 0) {
                mCamera = Camera.open(cameraId);
                mPreview.refreshCamera(mCamera);
                reloadQualities(cameraId);
            }
        } else {
            //当前为后置摄像头
            int cameraId = findFrontFacingCamera();
            if (cameraId >= 0) {
                mCamera = Camera.open(cameraId);
                if (flash) {
                    flash = false;
                    buttonFlash.setImageResource(R.mipmap.ic_launcher);
                    mPreview.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
                }
                mPreview.refreshCamera(mCamera);
                reloadQualities(cameraId);
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        releaseCamera();
    }

    //检查设备是否有摄像头
    private boolean hasCamera(Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA);
    }

    public void startRecord() {
        //准备开始录制视频
        if (!prepareMediaRecorder()) {
            Toast.makeText(CameraActivity.this, getString(R.string.camera_init_fail), Toast.LENGTH_SHORT).show();
            releaseCamera();
            releaseMediaRecorder();
            finish();
        }
        //开始录制视频
        runOnUiThread(new Runnable() {
            public void run() {
                try {
                    mediaRecorder.start();
                    if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
                        changeRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                    } else {
                        changeRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                    }
                } catch (final Exception ex) {
                    releaseCamera();
                    releaseMediaRecorder();
                    finish();
                }
            }
        });
        recording = true;
    }

    public void stopRecord() {
        if (recording) {
            //如果正在录制点击这个按钮表示录制完成
            mediaRecorder.stop(); //停止
            changeRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
            releaseMediaRecorder();
            recording = false;
            releaseCamera();
            releaseMediaRecorder();
        }
    }

    /**
     * 切换视频录制操作栏
     *
     * @param flag 显示隐藏操作栏和发送view
     */
    private void switchControlView(boolean flag) {
        if (flag) {
            ObjectAnimator.ofFloat(img_repleal, "translationX", 0f).start();
            ObjectAnimator.ofFloat(img_selected, "translationX", 0f).start();
            ObjectAnimator.ofFloat(txt_touch_shoot, "alpha", 0F, 1F).setDuration(100).start();
            button_changeCamera.setVisibility(VISIBLE);
            re_video_record_panel.setVisibility(VISIBLE);
            re_video_control_panel.setVisibility(View.GONE);
        } else {
            button_changeCamera.setVisibility(View.GONE);
            re_video_record_panel.setVisibility(View.GONE);
            re_video_control_panel.setVisibility(VISIBLE);
            ObjectAnimator.ofFloat(img_repleal, "translationX", -(float) (Screens.getScreenWidth(this) / 3.5)).start();
            ObjectAnimator.ofFloat(img_selected, "translationX", (float) (Screens.getScreenWidth(this) / 3.5)).start();
        }
    }

    private boolean videoTimeTooShort = false;//视频时长太短

    private Runnable delayTouch = new Runnable() {
        @Override
        public void run() {
            mZoomProgressBtn.setOnTouchListener(mOnVideoControllerTouchListener);
        }
    };

    private View.OnTouchListener mOnVideoControllerTouchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View view, MotionEvent motionEvent) {
            switch (motionEvent.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    mZoomProgressBtn.enlargeBtn();
                    break;
                case MotionEvent.ACTION_UP:
                    if (mZoomProgressBtn.videoTime < 1) {
                        videoTimeTooShort = true;
                        ObjectAnimator.ofFloat(txt_touch_shoot, "alpha", 0F, 1F).setDuration(100).start();
                        Toast.makeText(CameraActivity.this, "拍摄时间太短", Toast.LENGTH_LONG).show();
                        preparCamera();//重新初始化摄像头
                        mZoomProgressBtn.setOnTouchListener(null);//因为MediaRecorder的创建和释放会有卡顿，在这里做延迟touch操作，体验好一点
                        mZoomProgressBtn.postDelayed(delayTouch, 1000);
                    } else {
                        videoTimeTooShort = false;
                    }
                    mZoomProgressBtn.narrowBtn();
                    break;
            }
            return true;
        }
    };

    private void releaseMediaRecorder() {
        Log.i("Sunmeng", "releaseMediaRecorder :" + mediaRecorder + "    mCamera : " + mCamera);
        if (mediaRecorder != null) {
            mediaRecorder.reset();
            mediaRecorder.release();
            mediaRecorder = null;
            if (mCamera != null)
                mCamera.lock();
        }
    }

    private void changeRequestedOrientation(int orientation) {
        setRequestedOrientation(orientation);
    }

    private boolean prepareMediaRecorder() {
        try {
            mediaRecorder = new MediaRecorder();
            mCamera.unlock();
            mediaRecorder.setCamera(mCamera);
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
            mediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
            if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
                if (cameraFront) {
                    mediaRecorder.setOrientationHint(270);
                } else {
                    mediaRecorder.setOrientationHint(90);
                }
            }
            mediaRecorder.setProfile(CamcorderProfile.get(quality));
            url_file = getExternalFilesDir(Environment.DIRECTORY_DCIM)+"/" + System.currentTimeMillis() + "_in.mp4";
//            url_file = FileUtils.getDiskFileDir(this, Environment.DIRECTORY_MOVIES) + File.separator + FileUtils.getFileKey() + ".mp4";
            File file1 = new File(url_file);
            if (file1.exists()) {
                file1.delete();
            }
            mediaRecorder.setOutputFile(url_file);
            mediaRecorder.prepare();
        } catch (IllegalStateException e) {
            e.printStackTrace();
            releaseMediaRecorder();
            return false;
        } catch (IOException e) {
            e.printStackTrace();
            releaseMediaRecorder();
            return false;
        }
        return true;
    }

    private void releaseCamera() {
        if (mCamera != null) {
            mCamera.release();
            mCamera = null;
        }
    }

    //修改录像质量
    private void changeVideoQuality(int quality) {
        SharedPreferences prefs = getSharedPreferences("RECORDING", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt("QUALITY", quality);
        editor.apply();
        this.quality = quality;
    }


    //闪光灯
    public void setFlashMode(String mode) {
        try {
            if (getPackageManager().hasSystemFeature(
                    PackageManager.FEATURE_CAMERA_FLASH)
                    && mCamera != null
                    && !cameraFront) {
                mPreview.setFlashMode(mode);
                mPreview.refreshCamera(mCamera);

            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(getApplicationContext(), R.string.changing_flashLight_mode,
                    Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onBackPressed() {
        releaseVideoRecorder();
    }

    public void releaseVideoRecorder() {
        if (null != mediaRecorder && recording) {
            mediaRecorder.stop();
            releaseMediaRecorder();
            recording = false;
        }
        releaseCamera();
        releaseMediaRecorder();
        finish();
    }

    private void focusOnTouch(MotionEvent event) {
        if (mCamera != null) {
            Camera.Parameters parameters = mCamera.getParameters();
            if (parameters.getMaxNumMeteringAreas() > 0) {
                Rect rect = calculateFocusArea(event.getX(), event.getY());
                parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
                List<Camera.Area> meteringAreas = new ArrayList<Camera.Area>();
                meteringAreas.add(new Camera.Area(rect, 800));
                parameters.setFocusAreas(meteringAreas);
                mCamera.setParameters(parameters);
                mCamera.autoFocus(mAutoFocusTakePictureCallback);
            } else {
                mCamera.autoFocus(mAutoFocusTakePictureCallback);
            }
            mFocusAnimation.cancel();
            mFocusAnimationView.clearAnimation();
            int left = (int) (event.getX() - mFocusAnimationView.getWidth() / 2f);
            int top = (int) (event.getY() - mFocusAnimationView.getHeight() / 2f);
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) mFocusAnimationView.getLayoutParams();
            params.setMargins(left, top, 0, 0);
            mFocusAnimationView.setLayoutParams(params);
            mFocusAnimationView.setVisibility(VISIBLE);
            mFocusAnimationView.startAnimation(mFocusAnimation);
        }
    }

    private Rect calculateFocusArea(float x, float y) {
        int left = clamp(Float.valueOf((x / mPreview.getWidth()) * 2000 - 1000).intValue(), FOCUS_AREA_SIZE);
        int top = clamp(Float.valueOf((y / mPreview.getHeight()) * 2000 - 1000).intValue(), FOCUS_AREA_SIZE);
        return new Rect(left, top, left + FOCUS_AREA_SIZE, top + FOCUS_AREA_SIZE);
    }

    private int clamp(int touchCoordinateInCameraReper, int focusAreaSize) {
        int result;
        if (Math.abs(touchCoordinateInCameraReper) + focusAreaSize / 2 > 1000) {
            if (touchCoordinateInCameraReper > 0) {
                result = 1000 - focusAreaSize / 2;
            } else {
                result = -1000 + focusAreaSize / 2;
            }
        } else {
            result = touchCoordinateInCameraReper - focusAreaSize / 2;
        }
        return result;
    }

    private Camera.AutoFocusCallback mAutoFocusTakePictureCallback = new Camera.AutoFocusCallback() {
        @Override
        public void onAutoFocus(boolean success, Camera camera) {
            if (success) {
                Log.i("tap_to_focus", "success!");
            } else {
                Log.i("tap_to_focus", "fail!");
            }
        }
    };

    @Override
    public void onClick(View view) {
        int i = view.getId();
        if (i == R.id.img_back) {
            releaseVideoRecorder();
        } else if (i == R.id.img_repleal) {
            preparCamera();
            switchControlView(true);
        } else if (i == R.id.img_selected) {
            Bundle bundle = getIntent().getExtras();
            if (bundle == null)
                bundle = new Bundle();
            bundle.putString(OUT_PATH, url_file);
            bundle.putLong("time", mZoomProgressBtn.videoTime);
            bundle.putLong("size", new File(url_file).length());
            Log.i("Sunmeng", "output : " + url_file + " time : " + mZoomProgressBtn.videoTime + "   size : " + new File(url_file).length());
            Intent intent = new Intent();
            intent.putExtras(bundle);
            setResult(RESULT_CODE, intent);
            finish();
        }
    }

    public static int RESULT_CODE = 200;
    public static String OUT_PATH = "output";

    /**
     * 按下拍摄文字描述隐藏
     */
    private void onTxtDisappear() {
        ObjectAnimator.ofFloat(txt_touch_shoot, "alpha", 1F, 0F).setDuration(100).start();
    }

    @Override
    public void progressStart() {
        startRecord();
    }

    @Override
    public void progressEnd() {
        Log.i("Sunmeng", "progressEnd-videoTimeTooShort：" + videoTimeTooShort);
        if (!videoTimeTooShort) {
            stopRecord();
            //录制时长等于最大时长，强制停止
            if (mZoomProgressBtn.videoTime >= mZoomProgressBtn.mMaxMillSecond / 1000) {
                mZoomProgressBtn.narrowBtn();
            }
        }
    }

    @Override
    public void onBtnStartEnlarge() {
        //按钮开始放大，文字隐藏
        onTxtDisappear();
    }

    @Override
    public void onnEndNarrowBtn() {
        //按钮缩放动画结束，显示操作栏
//        Log.i("Sunmeng", "onnEndNarrowBtn.videoTimeTooShort:" + videoTimeTooShort);
        releaseMediaRecorder();//释放MediaRecorder对象
        if (!videoTimeTooShort)
            switchControlView(false);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mZoomProgressBtn.onDestroy();
    }
}

