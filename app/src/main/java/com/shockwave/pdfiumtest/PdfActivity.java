package com.shockwave.pdfiumtest;

import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.net.Uri;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.shockwave.pdfium.PdfDocument;
import com.shockwave.pdfium.PdfiumCore;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class PdfActivity extends ActionBarActivity {
    private static final String TAG = PdfActivity.class.getName();
   /* Pdfium核心*/
    private PdfiumCore mPdfCore;
    /*PDF文档*/
    private PdfDocument mPdfDoc = null;
    private FileInputStream mDocFileStream = null;
//    手势
    private GestureDetector mSlidingDetector;
    private ScaleGestureDetector mZoomingDetector;
    private PaintView paintView;
    private int mCurrentPageIndex = 0;
    private int mPageCount = 0;

    private SurfaceHolder mPdfSurfaceHolder;
    private boolean isSurfaceCreated = false;

    private final Rect mPageRect = new Rect();
    private final RectF mPageRectF = new RectF();
    private final Rect mScreenRect = new Rect();
    private final Matrix mTransformMatrix = new Matrix();
    private boolean isScaling = false;
    private boolean isReset = true;
    private float testX=20f;
    private float testY=20f;
    private static final float MAX_SCALE = 2.0f;
    private static final float MIN_SCALE = 0.7f;
    private Context mContext;
    private final ExecutorService mPreLoadPageWorker = Executors.newSingleThreadExecutor();
    private final ExecutorService mRenderPageWorker = Executors.newSingleThreadExecutor();
    private Runnable mRenderRunnable;


    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pdf);
//        /*获取屏幕宽高*/
//        WindowManager manager = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
//        DisplayMetrics dm = new DisplayMetrics();
//        manager.getDefaultDisplay().getMetrics(dm);
//        paintView = new PaintView(mContext, dm.widthPixels, dm.heightPixels);

        mPdfCore = new PdfiumCore(this);

        mSlidingDetector = new GestureDetector(this, new SlidingDetector());
        mZoomingDetector = new ScaleGestureDetector(this, new ZoomingDetector());

        Intent intent = getIntent();
        Uri fileUri;
        if( (fileUri = intent.getData()) == null){
            finish();
            return ;
        }
        mRenderRunnable = new Runnable() {
            @Override
            public void run() {
                loadPageIfNeed(mCurrentPageIndex);
                resetPageFit(mCurrentPageIndex);
                mPdfCore.renderPage(mPdfDoc, mPdfSurfaceHolder.getSurface(), mCurrentPageIndex,
                        mPageRect.left, mPageRect.top,
                        mPageRect.width(), mPageRect.height());

                mPreLoadPageWorker.submit(new Runnable() {
                    @Override
                    public void run() {
                        loadPageIfNeed(mCurrentPageIndex + 1);
                        loadPageIfNeed(mCurrentPageIndex + 2);
                    }
                });
            }
        };
        SurfaceView surfaceView = (SurfaceView)findViewById(R.id.view_surface_main);
        surfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
//            private SurfaceHolder mHolder; // 用于控制SurfaceView
            private Canvas mCanvas; // 声明一张画布
            private Paint p; // 声明一支画笔
            private int x = 50, y = 50, r = 10; // 圆的坐标和半径
            /**
             * 自定义一个方法，在画布上画一个圆
             */
            public void Draw() {
//                mCanvas = mHolder.lockCanvas(); // 获得画布对象，开始对画布画画
                mCanvas=new Canvas();
                p=new Paint();
                mCanvas.drawRGB(0, 0, 0); // 把画布填充为黑色
                mCanvas.drawCircle(x, y, r, p); // 画一个圆
//                mHolder.unlockCanvasAndPost(mCanvas); // 完成画画，把画布显示在屏幕上
            }
            /**
             * 当SurfaceView创建的时候，调用此函数
             */
            public void surfaceCreated(SurfaceHolder holder) {
                isSurfaceCreated = true;
                updateSurface(holder);
                if (mPdfDoc != null) {
                    mRenderPageWorker.submit(mRenderRunnable);
                }
//                Draw();
            }
            /**
             * 当SurfaceView的视图发生改变的时候，调用此函数
             */
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                Log.w(TAG, "Surface Changed");
                updateSurface(holder);
                if(mPdfDoc != null){
                    mRenderPageWorker.submit(mRenderRunnable);
                }
            }
            /**
             * 当SurfaceView销毁的时候，调用此函数
             */
            public void surfaceDestroyed(SurfaceHolder holder) {
                isSurfaceCreated = false;
                Log.w(TAG, "Surface Destroy");
            }
        });
        try{
            mDocFileStream = new FileInputStream(fileUri.getPath());

            mPdfDoc = mPdfCore.newDocument(mDocFileStream.getFD());
            Log.d("Main", "Open Document");
            mPageCount = mPdfCore.getPageCount(mPdfDoc);
            Log.d(TAG, "Page Count: " + mPageCount);
        }catch(IOException e){
            e.printStackTrace();
            Log.e("Main", "Data uri: " + fileUri.toString());
        }
    }
    public PdfActivity(Context mContext, PaintView paintView) {
        this.mContext = mContext;
        this.paintView = paintView;
        Write();
    }
    public PdfActivity() {
        super();
    }
    public void Write() {
        /*获取屏幕宽高*/
        WindowManager manager = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics dm = new DisplayMetrics();
        manager.getDefaultDisplay().getMetrics(dm);
        paintView = new PaintView(mContext, dm.widthPixels, dm.heightPixels);

    }

   /* 加载页面如果需要*/
    private void loadPageIfNeed(final int pageIndex){
        if( pageIndex >= 0 && pageIndex < mPageCount && !mPdfDoc.hasPage(pageIndex) ){
            Log.d(TAG, "Load page: " + pageIndex);
            /*页数*/
            mPdfCore.openPage(mPdfDoc, pageIndex);
        }
    }

    private void updateSurface(SurfaceHolder holder){
        mPdfSurfaceHolder = holder;
        mScreenRect.set(holder.getSurfaceFrame());
    }
//    重置页
    private void resetPageFit(int pageIndex){
        float pageWidth = mPdfCore.getPageWidth(mPdfDoc, pageIndex);
        float pageHeight = mPdfCore.getPageHeight(mPdfDoc, pageIndex);
        float screenWidth = mPdfSurfaceHolder.getSurfaceFrame().width();
        float screenHeight = mPdfSurfaceHolder.getSurfaceFrame().height();
        Log.d("aaaaaaa", "pageWidth="+pageWidth+",pageHeight="+pageHeight);

        /**Portrait**/
        if(screenWidth < screenHeight){
            if( (pageWidth / pageHeight) < (screenWidth / screenHeight) ){
                //Situation one: fit height
                pageWidth *= (screenHeight / pageHeight);
                pageHeight = screenHeight;

                mPageRect.top = 0;
                mPageRect.left = (int)(screenWidth - pageWidth) / 2;
                mPageRect.right = (int)(mPageRect.left + pageWidth);
                mPageRect.bottom = (int)pageHeight;
            }else{
                //Situation two: fit width
                pageHeight *= (screenWidth / pageWidth);
                pageWidth = screenWidth;

                mPageRect.left = 0;
                mPageRect.top = (int)(screenHeight - pageHeight) / 2;
                mPageRect.bottom = (int)(mPageRect.top + pageHeight);
                mPageRect.right = (int)pageWidth;
            }
        }else{

            /**Landscape**/
            if( pageWidth > pageHeight ){
                //Situation one: fit height
                pageWidth *= (screenHeight / pageHeight);
                pageHeight = screenHeight;

                mPageRect.top = 0;
                mPageRect.left = (int)(screenWidth - pageWidth) / 2;
                mPageRect.right = (int)(mPageRect.left + pageWidth);
                mPageRect.bottom = (int)pageHeight;
            }else{
                //Situation two: fit width
                pageHeight *= (screenWidth / pageWidth);
                pageWidth = screenWidth;

                mPageRect.left = 0;
                mPageRect.top = 0;
                mPageRect.bottom = (int)(mPageRect.top + pageHeight);
                mPageRect.right = (int)pageWidth;
            }
        }

        isReset = true;
    }

    private void rectF2Rect(RectF inRectF, Rect outRect){
        outRect.left = (int)inRectF.left;
        outRect.right = (int)inRectF.right;
        outRect.top = (int)inRectF.top;
        outRect.bottom = (int)inRectF.bottom;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event){
        boolean ret;

        ret = mZoomingDetector.onTouchEvent(event);
        if(!isScaling) ret |= mSlidingDetector.onTouchEvent(event);
        ret |= super.onTouchEvent(event);

        return ret;
    }

    private class SlidingDetector extends GestureDetector.SimpleOnGestureListener {
            /*滑动*/
        private boolean checkFlippable(){
            return ( mPageRect.left >= mScreenRect.left &&
                        mPageRect.right <= mScreenRect.right );
        }

//        滚动
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY){
            if(!isSurfaceCreated) return false;
            Log.d(TAG, "Drag");

            distanceX *= -1f;
            distanceY *= -1f;

//            if( (mPageRect.left <= mScreenRect.left && mPageRect.right <= mScreenRect.right && distanceX < 0) ||
//                    (mPageRect.right >= mScreenRect.right && mPageRect.left >= mScreenRect.left && distanceX > 0) )
//                distanceX = 0f;
//            if( (mPageRect.top <= mScreenRect.top && mPageRect.bottom <= mScreenRect.bottom && distanceY < 0) ||
//                    (mPageRect.bottom >= mScreenRect.bottom && mPageRect.top >= mScreenRect.top && distanceY > 0) )
//                distanceY = 0f;
//            /*left加上distanceX大于，做出判断*/
//            if( (mPageRect.left+distanceX <= mScreenRect.left && mPageRect.right <= mScreenRect.right && distanceX < 0)){
//                if(mPageRect.left>=mScreenRect.left){
//                distanceX = 0f;
//                }
//            }
//            if( (mPageRect.right+distanceX >= mScreenRect.right && mPageRect.left >= mScreenRect.left && distanceX > 0)){
//                if(mPageRect.right<mScreenRect.right){
//                    distanceX = 0f;}
//            }
//            if( (mPageRect.top+distanceY <= mScreenRect.top && mPageRect.bottom <= mScreenRect.bottom && distanceY < 0)){
//                if(mPageRect.top>mScreenRect.top){
//                    distanceY = 0f;}
//            }
//            if( (mPageRect.bottom+distanceY >= mScreenRect.bottom && mPageRect.top >= mScreenRect.top && distanceY > 0)){
//                if(mPageRect.bottom>mScreenRect.bottom){
//                    distanceY = 0f;}
//            }
//
//            /*放大*/
//
//
//            if(mPageRect.left+distanceX>mScreenRect.left&&distanceX>0&&mPageRect.right>mScreenRect.right){
//                if(mPageRect.left>=mScreenRect.left){
//                  distanceX=-mPageRect.left;
//                    }else{
//                   distanceX=0f;
//                    }
//
//            }
//            if(mPageRect.right+distanceX>mScreenRect.right&&distanceX>0&&mPageRect.left>mScreenRect.left ){
//                distanceX = 0f;
//
//
//            }
//            if(mPageRect.top+distanceY>mScreenRect.top&&distanceY>0&&mPageRect.bottom>mScreenRect.bottom ){
//                distanceY = 0f;
//
//            }
//            if(mPageRect.bottom+distanceY>mScreenRect.bottom&&distanceY>0&&mPageRect.top>mScreenRect.top ){
//                distanceY= 0f;
//
//            }
          if(mPageRect.left+distanceX<mScreenRect.left&&mPageRect.right<=mScreenRect.right&&distanceX<0){
                if(mPageRect.left>=mScreenRect.left){
                    distanceX=-mPageRect.left;
                   Log.d("ddddddd","1");
                   }else{
              distanceX=0f;
                   Log.d("ddddddd","2");
                  }
                }

        if(mPageRect.right+distanceX>mScreenRect.right&&mPageRect.left>=mScreenRect.left&&distanceX>0){
             if(mPageRect.right<=mScreenRect.right){
                    distanceX=mScreenRect.right-mPageRect.right;
                  Log.d("ddddddd","3");
                    }else{
                   distanceX=0f;
                    Log.d("ddddddd","4");
                 }
                }
          if(mPageRect.right+distanceX<mScreenRect.right&&mPageRect.left<=mScreenRect.left&&distanceX<0){
                if(mPageRect.right>=mScreenRect.right){
                    distanceX=mScreenRect.right-mPageRect.right;
                  Log.d("ddddddd","9");
                   }else{
                distanceX=0f;
                  Log.d("ddddddd","10");
                }
         }
           if(mPageRect.left+distanceX>mScreenRect.left&&mPageRect.right>=mScreenRect.right&&distanceX>0){
                if(mPageRect.left<=mScreenRect.left){
                    distanceX=-mPageRect.left;
                    Log.d("ddddddd","11");
                    }else{
                  distanceX=0f;
                    Log.d("ddddddd","12");
                   }
              }

          if(mPageRect.top+distanceY<mScreenRect.top&&mPageRect.bottom<=mScreenRect.bottom&&distanceY<0){
                if(mPageRect.top>=mScreenRect.top){
                    distanceY=-mPageRect.top;
                   Log.d("ddddddd","5");
                 }else{
                 distanceY=0f;
               Log.d("ddddddd","6");
                    }
                }
            if(mPageRect.bottom+distanceY<mScreenRect.bottom&&mPageRect.top<=mScreenRect.top&&distanceY<0){
                if(mPageRect.bottom>=mScreenRect.bottom){
                distanceY=mScreenRect.bottom-mPageRect.bottom;
                    Log.d("ddddddd","13");
                }else{
                    distanceY=0f;
                  Log.d("ddddddd","14");
                    }
           }
            if(mPageRect.bottom+distanceY>mScreenRect.bottom&&mPageRect.top>=mScreenRect.top&&distanceY>0){
                if(mPageRect.bottom<=mScreenRect.bottom){
                  distanceY=mScreenRect.bottom-mPageRect.bottom;
                    Log.d("ddddddd","7");
                    }else{
                  distanceY=0f;
          Log.d("ddddddd","8");
                   }
                }
            if(mPageRect.top+distanceY>mScreenRect.top&&mPageRect.bottom>=mScreenRect.bottom&&distanceY>0){
               if(mPageRect.top<=mScreenRect.top){
                 distanceY=-mPageRect.top;
                    Log.d("ddddddd","15");
                    }else{
                  distanceY=0f;
                    Log.d("ddddddd","16");
                    }
            }
            //Portrait restriction
//               纵向限制
            if(isReset && mScreenRect.width() < mScreenRect.height()) distanceX = distanceY = 0f;
            if(isReset && mScreenRect.height() <= mScreenRect.width()) distanceX = 0f;

            if(distanceX == 0f && distanceY == 0f) return false;

            Log.d(TAG, "DistanceX: " + distanceX);
            Log.d(TAG, "DistanceY: " + distanceY);
            mPageRect.left += distanceX;
            mPageRect.right += distanceX;
            mPageRect.top += distanceY;
            mPageRect.bottom += distanceY;

            mPdfCore.renderPage(mPdfDoc, mPdfSurfaceHolder.getSurface(), mCurrentPageIndex,
                    mPageRect.left, mPageRect.top,
                    mPageRect.width(), mPageRect.height());


            return true;
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY){
            if(!isSurfaceCreated) return false;
            if(velocityX == 0f) return false;
           /* 速度*/
            if(!checkFlippable()){
                Log.d(TAG, "Not flippable");
                return false;
            }

            if(velocityX < -200f){ //Forward
                if(mCurrentPageIndex < mPageCount - 1){
                    Log.d(TAG, "Flip forward");
                    mCurrentPageIndex++;
                    Log.d(TAG, "Next Index: " + mCurrentPageIndex);

                    mRenderPageWorker.submit(mRenderRunnable);
                }
                return true;
            }

            if(velocityX > 200f){ //Backward
                Log.d(TAG, "Flip backward");
                if(mCurrentPageIndex > 0){
                    mCurrentPageIndex--;
                    Log.d(TAG, "Next Index: " + mCurrentPageIndex);

                    mRenderPageWorker.submit(mRenderRunnable);
                }
                return true;
            }

            return false;
        }
    }
    /*缩放*/
    private class ZoomingDetector extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        private float mAccumulateScale = 1f;
        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector){
            isScaling = true;
            return true;
        }
        public boolean onScale(ScaleGestureDetector detector){
            if(!isSurfaceCreated) return false;
//            积累规模
            mAccumulateScale *= detector.getScaleFactor();
            mAccumulateScale = Math.max(1f, mAccumulateScale);
//            刻度值
            float scaleValue = (mAccumulateScale > 1f)? detector.getScaleFactor() : 1f;
//            变换矩阵
//            mTransformMatrix.setScale(scaleValue, scaleValue,
//                    detector.getFocusX(), detector.getFocusY());
            //缩放比例
            float scale = detector.getScaleFactor()/5;

            if(MainActivity.SCALE*scaleValue>MAX_SCALE){
                mTransformMatrix.setScale(MAX_SCALE/MainActivity.SCALE,MAX_SCALE/MainActivity.SCALE,
                        detector.getFocusX(), detector.getFocusY());
                MainActivity.SCALE=MAX_SCALE;
            }else if(MainActivity.SCALE*scaleValue<MIN_SCALE){
                mTransformMatrix.setScale(MIN_SCALE/MainActivity.SCALE,MIN_SCALE/MainActivity.SCALE,
                        detector.getFocusX(), detector.getFocusY());
                MainActivity.SCALE=MIN_SCALE;
            }else{
                mTransformMatrix.setScale(scaleValue,scaleValue,
                        detector.getFocusX(), detector.getFocusY());
                MainActivity.SCALE=MainActivity.SCALE*scaleValue;
            }
            Log.e("倍数","MainActivity.SCALE="+MainActivity.SCALE+",scaleValue="+scaleValue);
//            mTransformMatrix.setScale(scaleValue,scaleValue,
//                    detector.getFocusX(), detector.getFocusY());

            mPageRectF.set(mPageRect);
            mTransformMatrix.mapRect(mPageRectF);
            rectF2Rect(mPageRectF, mPageRect);
            mPdfCore.renderPage(mPdfDoc, mPdfSurfaceHolder.getSurface(), mCurrentPageIndex,
                    mPageRect.left, mPageRect.top,
                    mPageRect.width(), mPageRect.height());
            isReset = false;
//            Toast.makeText(getApplicationContext(),"缩放",Toast.LENGTH_SHORT).show();
            return true;
        }
        public void onScaleEnd(ScaleGestureDetector detector){
            if(mAccumulateScale == 1f && !mScreenRect.contains(mPageRect)){
                resetPageFit(mCurrentPageIndex);

                mPdfCore.renderPage(mPdfDoc, mPdfSurfaceHolder.getSurface(), mCurrentPageIndex,
                        mPageRect.left, mPageRect.top,
                        mPageRect.width(), mPageRect.height());
            }

            isScaling = false;
        }
    }

    @Override
    public void onDestroy(){
        try{
            if(mPdfDoc != null && mDocFileStream != null){
                mPdfCore.closeDocument(mPdfDoc);
                Log.d("Main", "Close Document");

                mDocFileStream.close();
            }
        }catch(IOException e){
            e.printStackTrace();
        }finally{
            super.onDestroy();
        }
    }
}
