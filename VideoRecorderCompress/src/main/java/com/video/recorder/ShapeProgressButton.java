package com.video.recorder;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.animation.LinearInterpolator;


/**
 * Created by yangc on 2017/4/24.
 * E-Mail:yangchaojiang@outlook.com
 * Deprecated:仿微信录小视频按钮
 */
public class ShapeProgressButton extends View {

    private  static  final  String TAG="ZoomProgressButton";
    //圆的半径
    private float mRadius;
    //中心圆半径
    private float mCenterRadius;
    //色带的宽度
    private float mStripeWidth;
    //总体大小
    private int mHeight;
    private int mWidth;
    //圆心坐标
    private float x;
    private float y;
    //要画的弧度
    private int mEndAngle;
    //进度的颜色
    private int mProgressColor;
    //按钮背景颜色
    private int mBackgroundColor;
    //按钮中心颜色
    private int mCenterColor;
    private Paint mPaint;
    private ProgressListener progressListener;
    public long mMaxMillSecond = 16000;//录制的最大时常，由于使用MediaRecorder录制会有提示音卡顿一下，所以进度多给1S

    public ShapeProgressButton(Context context) {
        this(context, null);
    }

    public ShapeProgressButton(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ShapeProgressButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.ShapeProgressButton, defStyleAttr, 0);
        mStripeWidth = a.getDimension(R.styleable.ShapeProgressButton_stripeWidth, Screens.dpToPx(30, context));
        mProgressColor = a.getColor(R.styleable.ShapeProgressButton_progressColor, 0xff2baefd);
        mBackgroundColor = a.getColor(R.styleable.ShapeProgressButton_backgroundColor, 0xffffffff);
        mCenterColor = a.getColor(R.styleable.ShapeProgressButton_centerColor, 0xffFF5557);
        mRadius = a.getDimensionPixelSize(R.styleable.ShapeProgressButton_zpb_radius, Screens.dpToPx(100, context));
        mPaint = new Paint();
        a.recycle();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        //获取测量模式
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        //获取测量大小
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        if (widthMode == MeasureSpec.EXACTLY && heightMode == MeasureSpec.EXACTLY) {
            mRadius = widthSize / 2;
            x = widthSize / 2;
            y = heightSize / 2;
            mWidth = widthSize;
            mHeight = heightSize;
        }

        if (widthMode == MeasureSpec.AT_MOST && heightMode == MeasureSpec.AT_MOST) {
            mWidth = (int) (mRadius * 2);
            mHeight = (int) (mRadius * 2);
            x = mRadius;
            y = mRadius;
        }
        mCenterRadius = mRadius - mStripeWidth - 10;
        setMeasuredDimension(mHeight, mHeight);//ZoomProgressButton的父布局固定了高度，widthSize等于0，不知道为什么，大神看到了给Issuer解答下
    }

    public void setProgressListener(ProgressListener progressListener) {
        this.progressListener = progressListener;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        //绘制圆形背景
        mPaint.setAntiAlias(true);
        mPaint.setColor(mBackgroundColor);
        canvas.drawCircle(x, y, mRadius, mPaint);

        //进度
        mPaint.setColor(mProgressColor);
        mPaint.setAntiAlias(true);
        RectF rect = new RectF(0, 0, mWidth, mHeight);
        canvas.drawArc(rect, 270, mEndAngle, true, mPaint);

        //绘制小圆,间隔
        mPaint.setAntiAlias(true);
        mPaint.setColor(mBackgroundColor);
        canvas.drawCircle(x, y, mRadius - mStripeWidth, mPaint);

        //绘制按钮中心
        mPaint.setAntiAlias(true);
        mPaint.setColor(mCenterColor);
        canvas.drawCircle(x, y, mCenterRadius, mPaint);
    }

    ValueAnimator valueAnimator;//全局动画对象，用于取消时候复位
    private boolean progressMax = false;//进度完成

    /**
     * 绘制进度条
     */
    public void onStartDrawProgress() {
        valueAnimator = ValueAnimator.ofInt(0, 360);
        valueAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                mEndAngle = (int) animation.getAnimatedValue();
                invalidate();
            }
        });
        valueAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                progressListener.progressEnd();
                progressMax = true;
                onResetProgress();
            }

            @Override
            public void onAnimationStart(Animator animation) {
                super.onAnimationStart(animation);
                progressListener.progressStart();
                recording = true;
                videoTime = -1;
                new Thread(mCountdownVideo).start();
            }
        });
        valueAnimator.setDuration(mMaxMillSecond);
        valueAnimator.setInterpolator(new LinearInterpolator());
        valueAnimator.start();
    }

    /**
     * 设置视频录制的最大时间
     * @param mMaxMillSecond
     */
    public void setMaxMillSecond(long mMaxMillSecond) {
        this.mMaxMillSecond = mMaxMillSecond;
    }

    /**
     * 中心圆缩放（放大）
     */
    public void onStartCenterDraw() {
        ValueAnimator valueAnimator = ValueAnimator.ofFloat(mCenterRadius, mCenterRadius - 30);
        valueAnimator.setDuration(300);
        valueAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                mCenterRadius = (float) animation.getAnimatedValue();
                postInvalidate();
            }
        });
        valueAnimator.start();
    }

    /**
     * 中心圆缩放（缩小）
     */
    public void onEndCenterDraw() {
        float tempCenterRadius;
        if (progressMax) {
            tempCenterRadius = mCenterRadius + 30;
        } else {
            tempCenterRadius = mCenterRadius;
        }
        ValueAnimator valueAnimator = ValueAnimator.ofFloat(mCenterRadius, tempCenterRadius);
        valueAnimator.setDuration(300);
        valueAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                mCenterRadius = (float) animation.getAnimatedValue();
                postInvalidate();
            }
        });
        valueAnimator.start();
    }

    /**
     * 按钮放大
     */
    public void enlargeBtn() {
        ObjectAnimator oaDownScaleX = ObjectAnimator.ofFloat(this, "scaleX", 1f, 1.5f);
        ObjectAnimator oaDownScaleY = ObjectAnimator.ofFloat(this, "scaleY", 1f, 1.5f);
        AnimatorSet enlargeSet = new AnimatorSet();
        enlargeSet.play(oaDownScaleX).with(oaDownScaleY);
        enlargeSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                onStartDrawProgress();
                progressMax = true;
            }

            @Override
            public void onAnimationStart(Animator animation) {
                super.onAnimationStart(animation);
                onStartCenterDraw();
                progressListener.onBtnStartEnlarge();
            }
        });
        enlargeSet.setDuration(300).start();
    }

    /**
     * 按钮缩小
     */
    public void narrowBtn() {
        ObjectAnimator oaUpScaleX = ObjectAnimator.ofFloat(this, "scaleX", 1.5f, 1f);
        ObjectAnimator oaUpScaleY = ObjectAnimator.ofFloat(this, "scaleY", 1.5f, 1f);
        AnimatorSet narrowSet = new AnimatorSet();
        narrowSet.play(oaUpScaleX).with(oaUpScaleY);
        narrowSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                onResetProgress();
                progressListener.onnEndNarrowBtn();
            }

            @Override
            public void onAnimationStart(Animator animation) {
                super.onAnimationStart(animation);
                onEndCenterDraw();
                recording = false;
            }
        });
        narrowSet.setDuration(300).start();
    }

    /**
     * 按钮复位
     */
    public void onResetProgress() {
        if (valueAnimator == null) return;
        valueAnimator.cancel();
        mEndAngle = 0;
        mCenterRadius = mRadius - mStripeWidth - 10;
        postInvalidate();
        progressMax = false;
        recording = false;
    }

    public interface ProgressListener {
        void progressStart();

        void progressEnd();

        void onBtnStartEnlarge();

        void onnEndNarrowBtn();
    }

    public void onDestroy() {
        recording = false;
        videoTime=-1;
    }

    public int videoTime = -1;//视频时长
    private boolean recording = false;
    private Runnable mCountdownVideo = new Runnable() {
        @Override
        public void run() {
            try {
                while (recording) {
                    Log.i(TAG, "Runnable:" + videoTime);
                    videoTime += 1;
                    Thread.sleep(1000);
                    if (!recording) break;//防止在线程休眠时recording改变
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    };

}
