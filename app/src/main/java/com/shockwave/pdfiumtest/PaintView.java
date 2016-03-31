package com.shockwave.pdfiumtest;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Environment;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Created by zhangyouyun  on 2016/3/21.
 */
public class PaintView extends View {
    /*内容*/
    private Context mContext;
    private int select_handwrite_size_index = 0;
    private int select_handwrite_color_index = 0;
    /*当前颜色，尺寸*/
    private int currentColor = Color.BLACK;
    private int currentSize = 5;
   /* 图的路径*/
    private String PngPath = Environment.getExternalStorageDirectory() + File.separator + "rj.png";
   /*txt路径*/
    private String txt = Environment.getExternalStorageDirectory() + File.separator
            + "rj.txt";
    private String json = "";
    private Bitmap mBitmap;
    private Canvas mCanvas;
    private Path mPath;
    private float x, y;
    private boolean eraserstate;
    private Paint mPaint;
    private float mX, mY;
    private static final float TOUCH_TOLERANCE = 4;
    //颜色数组
    private int paintColor[] = {Color.RED, Color.BLUE, Color.BLACK,
            Color.GREEN, Color.YELLOW, Color.CYAN, Color.LTGRAY};
    //--------------------------
    // 保存Path路径的集合
    private List<DrawPath> savePath = new ArrayList<>();
    private List<List<DrawPath>> deletePath = new ArrayList<>();
    /*记录Path路径的对象*/
    private DrawPath dp;
    public int screenWidth, screenHeight;
    /*  单个*/
    private List<PointF> points = new ArrayList<>();

    private List<List<PointF>> savePoint = new ArrayList<>();
    private List<List<List<PointF>>> deletePoints = new ArrayList<>();
    private List<List<PointF>> lastPoints = new ArrayList<>();
    private float xmax = 0, xmin = 0, ymax = 0, ymin = 0;
    Bitmap Erase;

    Bitmap mSignatureBitmap = null;
    public PaintView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        this.mContext = context;
        init();
    }

    public PaintView(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.mContext = context;
        init();
    }

    public PaintView(Context context, int screenWidth, int screenHeight) {
        super(context);
        this.mContext = context;
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        init();
    }

    //--------------------------
    public void init() {
//    // 从资源文件中生成位图
        mBitmap = Bitmap.createBitmap(screenWidth, screenHeight, Bitmap.Config.ARGB_8888);
        mCanvas = new Canvas(mBitmap);

//        mCanvas.setBitmap(bitmap);
        mCanvas.drawColor(Color.WHITE);
        /*画笔*/
        mPaint = new Paint();
        mPaint.setAntiAlias(true); // 去除锯齿
        mPaint.setStrokeWidth(5);
        mPaint.setDither(true);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setColor(Color.BLACK);
        mPaint.setStrokeJoin(Paint.Join.ROUND);
        mPaint.setStrokeCap(Paint.Cap.ROUND);
//        mPaint.setFakeBoldText(true); //true为粗体，false为非粗体
        /*变淡的效果*/
        mPaint.setAlpha(0x80);
        mPath = new Path();

    }

    /*画的方法*/
    public void onDraw(Canvas canvas) {
        canvas.drawColor(-1);
        canvas.drawBitmap(mBitmap, 0, 0, mPaint);
        if (mPath != null) {
            // 实时的显示
            canvas.drawPath(mPath, mPaint);
            /* 判断是否是橡皮擦*/
            if (!eraserstate) {
                Move(canvas);
            } else {
                MoveErase(canvas);
            }
        }
    }
    /* 移动橡皮擦*/
    public void MoveErase(Canvas canvas) {
        //移动时，显示画笔图标
        if (currentColor != Color.WHITE) {
            //设置画笔的图标
            Erase = BitmapFactory.decodeResource(this.getResources(),
                    R.drawable.eraser);
            canvas.drawBitmap(Erase, x, y - Erase.getHeight(),
                    new Paint(Paint.DITHER_FLAG));
        }
    }
    /* 移动画笔*/
    public void Move(Canvas canvas) {
        //移动时，显示画笔图标
        if (currentColor != Color.WHITE) {
            //设置画笔的图标
            Bitmap pen = BitmapFactory.decodeResource(this.getResources(),
                    R.drawable.pencil);
            canvas.drawBitmap(pen, this.mX, this.mY - pen.getHeight(),
                    new Paint(Paint.DITHER_FLAG));
        }
    }
//    //选择画笔大小
//    public void selectPaintSize(int which) {
//        int size = Integer.parseInt(this.getResources().getStringArray(R.array.paintsize)[which]);
//        currentSize = size;
//        setPaintStyle();
//    }
    //设置画笔颜色
    public void selectHandWriteColor(int which) {
        currentColor = paintColor[which];
        setPaintStyle();
    }

    //设置画笔样式
    public void setPaintStyle() {
        mPaint = new Paint();
        mPaint.setAntiAlias(true);
        mPaint.setDither(true);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeJoin(Paint.Join.ROUND);
        mPaint.setStrokeCap(Paint.Cap.ROUND);
        mPaint.setStrokeWidth(currentSize);
        mPaint.setColor(currentColor);
        /*变淡的效果*/
        mPaint.setAlpha(0x80);

    }

    /**
     * 线的起点到终点
     */
    private void touch_start(float x, float y) {
        mPath.moveTo(x, y);
        mX = x;
        mY = y;
    }

    /**
     * 与上一个点之间连线
     */
    private void touch_move(float x, float y) {
        float dx = Math.abs(x - mX);
        float dy = Math.abs(mY - y);
        if (dx >= TOUCH_TOLERANCE || dy >= TOUCH_TOLERANCE) {
            //源代码 贝塞尔曲线
            mPath.quadTo(mX, mY, (x + mX) / 2, (y + mY) / 2);
            mX = x;
            mY = y;
        }
    }

    /**
     * 连线结束，在画布上画出并保存
     */
    private void touch_up() {
        mPath.lineTo(mX, mY);
        mCanvas.drawPath(mPath, mPaint);
        //将一条完整的路径保存下来
        savePath.add(dp);
        mPath = null;// 重新置空
    }

    /**
     * 撤销的核心思想就是将画布清空，
     * 将保存下来的Path路径最后一个移除掉，
     * 重新将路径画在画布上面。
     */
    public void Revoke() {
        if (savePath != null && savePath.size() > 0) {
            List<DrawPath> deletePathItem = new ArrayList<>();
            deletePathItem.add(savePath.get(savePath.size() - 1));
            deletePath.add(deletePathItem);
            /*移除最后一条*/
            savePath.remove(savePath.size() - 1);

            List<List<PointF>> deletePointPathItem = new ArrayList<>();
            deletePointPathItem.add(savePoint.get(savePoint.size() - 1));
            deletePoints.add(deletePointPathItem);
            savePoint.remove(savePoint.size() - 1);
            redrawOnBitmap();
        }
    }

    /**
     * 清空功能
     */
    public void Clear() {
        if (savePath.size() != 0) {
            List<DrawPath> deletePathItem = new ArrayList<>();
            deletePathItem.addAll(savePath);
            deletePath.add(deletePathItem);

            List<List<PointF>> deletePointPathItem = new ArrayList<>();
            deletePointPathItem.addAll(savePoint);
            deletePoints.add(deletePointPathItem);
            savePoint.clear();
            savePath.clear();
            redrawOnBitmap();
        } else {
        }
    }

    /**
     * 保存txt和图片
     */
    public void Save() {
        if (savePath != null && savePath.size() > 0) {
            /*用json解析*/
            json = JSON.toJSONString(savePoint);
            savePhone();
            savePath.clear();
            deletePath.clear();
            savePoint.clear();
            deletePoints.clear();
            redrawOnBitmap();
        }
    }

    /**
     * 上一次功能
     */
    public void lastTime() {
        File file = new File(txt);
        if (file.exists()) {
            savePath.clear();
            deletePath.clear();
            savePoint.clear();
            deletePoints.clear();
            readTxt();
        }
    }

    /**
     * 恢复
     */
    public void Recover() {
        int size = deletePath.size();
        if (deletePath != null && deletePath.size() > 0) {
            savePath.addAll(deletePath.get(deletePath.size() - 1));
            deletePath.remove(deletePath.size() - 1);
            savePoint.addAll(deletePoints.get(size - 1));
            deletePoints.remove(size - 1);
            redrawOnBitmap();
        }
    }

    /**
     * 是不是橡皮擦
     */
    public void isEraser(boolean eraserstate) {
        this.eraserstate = eraserstate;
    }

    public void redrawOnBitmap() {
        mBitmap = Bitmap.createBitmap(screenWidth, screenHeight,
                Bitmap.Config.ARGB_8888);

        mCanvas.setBitmap(mBitmap);// 重新设置画布，相当于清空画布
        Iterator<DrawPath> iter = savePath.iterator();
        while (iter.hasNext()) {
            DrawPath drawPath = iter.next();
            mCanvas.drawPath(drawPath.path, drawPath.paint);
        }
        invalidate();// 刷新
    }

    // 缩放
    public static Bitmap resizeImage(Bitmap bitmap, int width, int height) {
        int originWidth = bitmap.getWidth();
        int originHeight = bitmap.getHeight();

        float scaleWidth = ((float) width) / originWidth;
        float scaleHeight = ((float) height) / originHeight;

        Matrix matrix = new Matrix();
        matrix.postScale(scaleWidth, scaleHeight);
        Bitmap resizedBitmap = Bitmap.createBitmap(bitmap, 0, 0, originWidth,
                originHeight, matrix, true);
        return resizedBitmap;
    }

    //--------------------------
    public boolean onTouchEvent(MotionEvent event) {
        float eventX = event.getX();
        float eventY = event.getY();
        x = event.getX();
        y = event.getY();
        PointF pointF = new PointF(x, y);
        if (!eraserstate) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    mPath = new Path();
                    dp = new DrawPath();
                    dp.path = mPath;
                    dp.paint = mPaint;
                    touch_start(x, y);
                    invalidate();
                    break;
                case MotionEvent.ACTION_MOVE:
                    points.add(pointF);
                    touch_move(x, y);
                    invalidate();
                    break;
                case MotionEvent.ACTION_UP:
                    savePoint.add(points);
                    points = new ArrayList<>();
                    touch_up();
                    invalidate();
                    break;
            }
        } else {
           /* 出现橡皮擦时的滑动处理*/
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    Eraser();
                    invalidate();
                    mPath = new Path();
                    dp = new DrawPath();
                    dp.path = mPath;
                    dp.paint = mPaint;

                    break;
                case MotionEvent.ACTION_MOVE:
                    Eraser();
                    invalidate();
                    break;
                case MotionEvent.ACTION_UP:
                    mPath = null;
                    invalidate();
                    break;
            }
        }
        return true;
    }
//
//    //弹出画笔大小选项对话框
//    public void showPaintSizeDialog(View parent) {
//        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(mContext);
//        alertDialogBuilder.setTitle("选择画笔大小：");
//        alertDialogBuilder.setSingleChoiceItems(R.array.paintsize, select_handwrite_size_index, new DialogInterface.OnClickListener() {
//            public void onClick(DialogInterface dialog, int which) {
//                select_handwrite_size_index = which;
//                selectPaintSize(which);
//                dialog.dismiss();
//            }
//        });
//        alertDialogBuilder.setNegativeButton("取消", new DialogInterface.OnClickListener() {
//            public void onClick(DialogInterface dialog, int which) {
//                dialog.dismiss();
//            }
//        });
//        alertDialogBuilder.create().show();
//    }
//
//    //弹出画笔颜色选项对话框
//    public void showPaintColorDialog(View parent) {
//        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(mContext);
//        alertDialogBuilder.setTitle("选择画笔颜色：");
//        alertDialogBuilder.setSingleChoiceItems(R.array.paintcolor, select_handwrite_color_index, new DialogInterface.OnClickListener() {
//            @Override
//            public void onClick(DialogInterface dialog, int which) {
//                select_handwrite_color_index = which;
//                selectHandWriteColor(which);
//                dialog.dismiss();
//            }
//        });
//        alertDialogBuilder.setNegativeButton("取消", new DialogInterface.OnClickListener() {
//
//            @Override
//            public void onClick(DialogInterface dialog, int which) {
//                dialog.dismiss();
//            }
//        });
//        alertDialogBuilder.create().show();
//    }

    /**
     * 保存到sd卡
     */
    public void savePhone() {
        measure();
        try {
            File file1 = new File(PngPath);
            if (file1.exists()) {
                file1.delete();
            }
            if (xmax > 0 && ymax > 0) {
                if (xmin < 0) {
                    xmin = 0;
                }
                if (ymin < 0) {
                    ymin = 0;
                }
                if (xmax > screenWidth) {
                    xmax = screenWidth;
                }
                if (ymax > screenHeight) {
                    ymax = screenHeight;
                }
                Bitmap bitmap = Bitmap.createBitmap(mBitmap, (int) xmin, (int) ymin,
                        (int) (xmax - xmin), (int) (ymax - ymin));
                FileOutputStream fos = new FileOutputStream(new File(PngPath));
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
//                byte[] b = baos.toByteArray();
//                if (b != null) {
//                    fos.write(b);
//                }
//                ByteArrayOutputStream baos = new ByteArrayOutputStream(new File(PngPath));
//                baos = new ByteArrayOutputStream();
                fos.flush();
                fos.close();
                //存放数组数据的文件
                File file = new File(txt);
                FileWriter out = new FileWriter(file);//文件写入流
                out.write(json);
                out.close();
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 读取txt文件并画出上次保存的图
     */
    public void readTxt() {
        try {
            File file = new File(txt);
            //判断文件是否存在
            if (file.isFile() && file.exists()) {
                //构造一个BufferedReader类来读取文件
                BufferedReader br = new BufferedReader(new FileReader(file));
                //接收的json
                String json = "";
                String Txt = null;
                while ((Txt = br.readLine()) != null) {
                    json = json + Txt;
                }
                br.close();
                lastPoints = JSON.parseObject(json, new TypeReference<List<List<PointF>>>() {
                });
                Log.e("txt2", "lastPointsPath" + lastPoints);
                savePoint.addAll(lastPoints);
                drawPath(lastPoints);
            } else {
                System.out.println("找不到指定的文件");
            }
        } catch (Exception e) {
            System.out.println("读取文件内容出错");
            e.printStackTrace();
        }
    }

    /**
     * 根据点阵画出图
     */
    public void drawPath(List<List<PointF>> path) {
        if (path.size() > 0) {
            //如果有坐标点，开始绘图
            Log.i("根据点阵画出图", "savePath" + savePath);
            Iterator<List<PointF>> iter = path.iterator();
            while (iter.hasNext()) {
                List<PointF> pointFs = iter.next();
                mPath = new Path();
                dp = new DrawPath();
                dp.path = mPath;
                dp.paint = mPaint;
                touch_start(pointFs.get(0).getX(), pointFs.get(0).getY());
                Iterator<PointF> iterItem = pointFs.iterator();
                while (iterItem.hasNext()) {
                    PointF pointF = iterItem.next();
                    touch_move(pointF.getX(), pointF.getY());
                }
                touch_up();
            }
            Log.e("根据点阵画出图", "savePath" + savePath);
        }
    }

    /**
     * 橡皮擦功能
     */
    public void Eraser() {
        if (savePoint.size() > 0) {
            //如果有坐标点，开始绘图
            int num = 0;
            /*遍历函数*/
            Iterator<List<PointF>> iter = savePoint.iterator();
            while (iter.hasNext()) {
                List<PointF> pointFs = iter.next();
                Iterator<PointF> iterItem = pointFs.iterator();
                while (iterItem.hasNext()) {
                    PointF pointF = iterItem.next();
                    if (((y - 30) < pointF.getY() && (y + 30) > pointF.getY())
                            && ((x - 30) < pointF.getX() && (x + 30) > pointF.getX())) {
                        List<DrawPath> deletePathItem = new ArrayList<>();
                        deletePathItem.add(savePath.get(num));
                        deletePath.add(deletePathItem);
                        savePath.remove(num);
                        List<List<PointF>> deletePointPathItem = new ArrayList<>();
                        deletePointPathItem.add(savePoint.get(num));
                        deletePoints.add(deletePointPathItem);
                        savePoint.remove(num);
                        redrawOnBitmap();
                        return;
                    };
                }
                num++;
            }
        }
    }

    /**
     * 获取点阵的x，y的最大最小值
     */
    public void measure() {
        if (savePoint.size() > 0) {
            //对x，y的最大最小值的初始化
            PointF first = null;
            try {
                first = savePoint.get(0).get(0);
            } catch (Exception e) {
                e.printStackTrace();
            }
            xmax = first.getX();
            xmin = ymax;
            ymax = first.getY();
            ymin = ymax;
            //如果有坐标点，开始绘图
            Iterator<List<PointF>> iter = savePoint.iterator();
            while (iter.hasNext()) {
                List<PointF> pointFs = iter.next();
                Iterator<PointF> iterItem = pointFs.iterator();
                while (iterItem.hasNext()) {
                    PointF pointF = iterItem.next();
                    x = pointF.getX();
                    y = pointF.getY();
                    if (x > xmax) {
                        xmax = x;
                    } else if (x < xmin) {
                        xmin = x;
                    }
                    if (y > ymax) {
                        ymax = y;
                    } else if (y < ymin) {
                        ymin = y;
                    }
                }
            }
        }
    }

}