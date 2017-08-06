package com.wm.blecontrol;


import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.SurfaceHolder.Callback;
import android.util.AttributeSet;

/**
 * Created by wangmeng on 17-7-20.
 */

public class ControlView extends SurfaceView implements Callback, Runnable {
    private Canvas canvas = null; //定义画布
    private Thread th = null;     //定义线程
    private SurfaceHolder sfh = null;
    private boolean thFlag;
    private Paint paint;        //定义画笔
    // 固定摇杆背景圆形的X,Y坐标以及半径
    private float fixedCircleX = 100;
    private float fixedCircleY = 100;
    private float fixedCircleR = 100;
    // 摇杆的X,Y坐标以及摇杆的半径
    private float rockerCircleX = 100;
    private float rockerCircleY = 100;
    private float rockerCircleR = 50;
    // 屏幕的中心坐标
    private float screenCenterX = 0;
    private float screenCenterY = 0;

    private ControlListener mListener;
    private int cmd = 0, last_cmd = 0;

    public void setListener(ControlListener listener) {
        mListener = listener;
    }

    public ControlView(Context context) {
        super(context);
        paint = new Paint();
        paint.setAntiAlias(true);      //设置消除锯齿
        paint.setColor(Color.BLUE);    //设置画笔颜色
        sfh = getHolder();
        sfh.addCallback(this);
    }
    public ControlView(Context context, AttributeSet attrs)
    {
        super(context, attrs);
        paint = new Paint();
        paint.setAntiAlias(true);      //设置消除锯齿
        paint.setColor(Color.BLUE);    //设置画笔颜色
        sfh = getHolder();
        sfh.addCallback(this);
    }

    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
        screenCenterX = this.getMeasuredWidth() / 2;
        screenCenterY = this.getMeasuredHeight() / 2;
        Log.d("Screen",String.valueOf(screenCenterX));
        fixedCircleX = screenCenterX;
        fixedCircleY = screenCenterY;
        fixedCircleR = screenCenterX/2;
        rockerCircleX = screenCenterX;
        rockerCircleY = screenCenterY;
        rockerCircleR = screenCenterX/5;
        th = new Thread(this);
        thFlag = true;
        th.start();
    }

    @Override
    public void run() {
        while(thFlag){
            try{
                myDraw();
                Thread.sleep(100);
            }catch(InterruptedException e){
                e.printStackTrace();
            }
        }

    }

    //重写onDraw方法实现绘图操作
    protected void myDraw() {
        canvas = sfh.lockCanvas();        //获取canvas实例
        canvas.drawColor(Color.WHITE);    //将屏幕设置为白色
        paint.setColor(0x70000000);
        canvas.drawCircle(fixedCircleX, fixedCircleY, fixedCircleR, paint);// 绘制背景
        paint.setColor(Color.rgb(0, 173, 220));
        canvas.drawCircle(rockerCircleX, rockerCircleY, rockerCircleR, paint);// 绘制摇杆
        sfh.unlockCanvasAndPost(canvas);   //将画好的画布提交
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float px = event.getX();
        float py = event.getY();
        boolean isFingerUp=false;
        double radian=getAngle(px, py, fixedCircleX, fixedCircleY);
        if (event.getAction() == MotionEvent.ACTION_DOWN
                || event.getAction() == MotionEvent.ACTION_MOVE){
            if (isInCircle(px,py,fixedCircleR)){
                rockerCircleX = (int) event.getX();
                rockerCircleY = (int) event.getY();
            }else{
                rockerCircleX = (float)(fixedCircleR*Math.cos(radian))+screenCenterX;
                rockerCircleY = -(float)(fixedCircleR*Math.sin(radian))+screenCenterY;

                Log.d("radian",String.valueOf(radian*180.0/3.14));
            }

        }else if (event.getAction() == MotionEvent.ACTION_UP){
            rockerCircleX = screenCenterX;
            rockerCircleY = screenCenterY;
            isFingerUp = true;
        }
        double angle, interval=45;
        angle= radian*180.0/Math.PI;
        if (angle<0){
            angle = angle + 360.0; //atan2的返回结果是-PI～PI
        }
        if (!isFingerUp){
            cmd = (int)((angle+interval/2)/interval)+1;
            if(cmd>8){
                cmd = 1;
            }

        }else{
            cmd=0;
        }
        if (mListener != null&&cmd!=last_cmd) {
            String msg = "" + cmd;
            mListener.sendMessage(msg);
            last_cmd = cmd;
        }
        return true;
    }
    private boolean isInCircle(float x, float y, float r) {
        // 得到两点X的距离
        if (Math.pow(x-screenCenterX, 2)+ Math.pow(y-screenCenterY, 2) < r*r)
        {
            return true;
        }
        else
        {
            return false;
        }
    }

    private double getAngle(float px1, float py1, float px2, float py2) {
        // 得到两点X的距离
        float x = px1 - px2;
        // 得到两点Y的距离
        float y = py2 - py1;
        // 通过反余弦定理获取到其角度的弧度
        double  radian = Math.atan2(y, x);
        return radian;
    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
        thFlag = false;
    }
}
