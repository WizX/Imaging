package com.xingren.imaging.core;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.CornerPathEffect;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;

import com.xingren.imaging.core.clip.IMGClip;
import com.xingren.imaging.core.clip.IMGClipWindow;
import com.xingren.imaging.core.homing.IMGHoming;
import com.xingren.imaging.core.sticker.IMGSticker;
import com.xingren.imaging.core.util.IMGUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by felix on 2017/11/21 下午10:03.
 */

public class IMGImage {

    private static final String TAG = "IMGImage";

    private Bitmap mImage, mMosaicImage;

    /**
     * 完整图片边框
     */
    private RectF mFrame = new RectF();

    /**
     * 裁剪图片边框（显示的图片区域）
     */
    private RectF mClipFrame = new RectF();

    /**
     * 图片显示窗口（默认为控件大小，裁剪时为裁剪区域）
     */
    private RectF mHomeFrame = new RectF();

    /**
     * 裁剪模式时当前触摸锚点
     */
    private IMGClip.Anchor mAnchor;

    /**
     * 裁剪窗口
     */
    private IMGClipWindow mClipWin = new IMGClipWindow();

    /**
     * 编辑模式
     */
    private IMGMode mMode = IMGMode.NONE;

    /**
     * 可视区域，无Scroll 偏移区域
     */
    private RectF mWindow = new RectF();

    /**
     * 是否初始位置
     */
    private boolean isInitialHoming = false;

    /**
     * 当前选中贴片
     */
    private IMGSticker mForeSticker;

    /**
     * 为被选中贴片
     */
    private List<IMGSticker> mBackStickers = new ArrayList<>();

    /**
     * 涂鸦路径
     */
    private List<IMGPath> mDoodles = new ArrayList<>();

    /**
     * 马赛克路径
     */
    private List<IMGPath> mMosaics = new ArrayList<>();

    private static final int MIN_SIZE = 500;

    private static final int MAX_SIZE = 10000;

    private Paint mDoodlePaint, mMosaicPaint, mPaint;

    private Matrix M = new Matrix();

    private static final boolean DEBUG = true;

    private static final Bitmap DEFAULT_IMAGE;

    static {
        DEFAULT_IMAGE = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
    }

    {
        // Doodle&Mosaic 's paint
        mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(IMGPath.BASE_DOODLE_WIDTH);
        mPaint.setColor(Color.RED);
        mPaint.setPathEffect(new CornerPathEffect(IMGPath.BASE_DOODLE_WIDTH));
        mPaint.setStrokeCap(Paint.Cap.ROUND);
        mPaint.setStrokeJoin(Paint.Join.ROUND);
    }

    public IMGImage() {
        mImage = DEFAULT_IMAGE;
    }

    public void setBitmap(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) {
            return;
        }

        this.mImage = bitmap;

        // 清空马赛克图层
        if (mMosaicImage != null) {
            mMosaicImage.recycle();
        }
        this.mMosaicImage = null;

        makeMosaicBitmap();

        onImageChanged();
    }

    public IMGMode getMode() {
        return mMode;
    }

    public void setMode(IMGMode mode) {
        this.mMode = mode;

        if (mMode == IMGMode.MOSAIC) {
            makeMosaicBitmap();
        } else if (mMode == IMGMode.CLIP) {
            mClipWin.reset(mClipFrame.width(), mClipFrame.height());
        }
    }

    public boolean isMosaicEmpty() {
        return mMosaics.isEmpty();
    }

    public boolean isDoodleEmpty() {
        return mDoodles.isEmpty();
    }

    public void undoDoodle() {
        if (!mDoodles.isEmpty()) {
            mDoodles.remove(mDoodles.size() - 1);
        }
    }

    public void undoMosaic() {
        if (!mMosaics.isEmpty()) {
            mMosaics.remove(mMosaics.size() - 1);
        }
    }

    public RectF getClipFrame() {
        return mClipFrame;
    }

    public void clip(float sx, float sy) {
        RectF frame = new RectF(mClipWin.getFrame());
        frame.offset(sx, sy);
        mClipFrame.set(frame);
    }

    private void makeMosaicBitmap() {
        if (mMosaicImage != null || mImage == null) {
            return;
        }

        if (mMode == IMGMode.MOSAIC) {

            int w = Math.round(mImage.getWidth() / 64f);
            int h = Math.round(mImage.getHeight() / 64f);

            w = Math.max(w, 8);
            h = Math.max(h, 8);

            // 马赛克画刷
            if (mMosaicPaint == null) {
                mMosaicPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                mMosaicPaint.setFilterBitmap(false);
                mMosaicPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
            }

            mMosaicImage = Bitmap.createScaledBitmap(mImage, w, h, false);
        }
    }

    private void onImageChanged() {
        isInitialHoming = false;
        onWindowChanged(mWindow.width(), mWindow.height());

        if (mMode == IMGMode.CLIP) {
            mClipWin.reset(mClipFrame.width(), mClipFrame.height());
        }
    }

    public RectF getFrame() {
        return mFrame;
    }

    public IMGHoming getStartHoming(float scrollX, float scrollY) {
        return new IMGHoming(scrollX, scrollY, getScale());
    }

    public IMGHoming getEndHoming(float scrollX, float scrollY) {
        IMGHoming homing = new IMGHoming(scrollX, scrollY, getScale());
        if (mMode == IMGMode.CLIP) {
            RectF frame = new RectF(mClipWin.getFrame());
            frame.offset(scrollX, scrollY);
            if (!mClipWin.isClipping()) {
                homing.ccat(IMGUtils.fill(frame, mClipFrame));
                // 开启裁剪模式
                mClipWin.setClipping(true);
            } else {
                homing.ccat(IMGUtils.fillHoming(frame, mClipFrame));
            }
        } else {
            RectF win = new RectF(mWindow);
            win.offset(scrollX, scrollY);
            homing.ccat(IMGUtils.fitHoming(win, mClipFrame));
        }

        return homing;
    }

    public <S extends IMGSticker> void addSticker(S sticker) {
        if (sticker != null) {
            moveToForeground(sticker);
        }
    }

    public void addPath(IMGPath path, float sx, float sy) {
        if (path == null) return;

        float scale = 1f / getScale();
        M.setTranslate(sx - mClipFrame.left, sy - mClipFrame.top);
        M.postScale(scale, scale);
        path.transform(M);

        switch (path.getMode()) {
            case DOODLE:
                mDoodles.add(path);
                break;
            case MOSAIC:
                path.setWidth(path.getWidth() * scale);
                mMosaics.add(path);
                break;
        }
    }

    private void moveToForeground(IMGSticker sticker) {
        if (sticker == null) return;

        moveToBackground(mForeSticker);

        if (sticker.isShowing()) {
            mForeSticker = sticker;
            // 从BackStickers中移除
            mBackStickers.remove(sticker);
        } else sticker.show();
    }

    private void moveToBackground(IMGSticker sticker) {
        if (sticker == null) return;

        if (!sticker.isShowing()) {
            // 加入BackStickers中
            if (!mBackStickers.contains(sticker)) {
                mBackStickers.add(sticker);
            }

            if (mForeSticker == sticker) {
                mForeSticker = null;
            }
        } else sticker.dismiss();
    }

    public void onDismiss(IMGSticker sticker) {
        moveToBackground(sticker);
    }

    public void onShowing(IMGSticker sticker) {
        if (mForeSticker != sticker) {
            moveToForeground(sticker);
        }
    }

    public void onRemoveSticker(IMGSticker sticker) {
        if (mForeSticker == sticker) {
            mForeSticker = null;
        } else {
            mBackStickers.remove(sticker);
        }
    }

    public void onWindowChanged(float width, float height) {
        if (width == 0 || height == 0) {
            return;
        }

        mWindow.set(0, 0, width, height);

        if (!isInitialHoming) {
            onInitialHoming(width, height);
        } else {

            // Pivot to fit window.
            M.reset();
            M.setTranslate(mWindow.centerX() - mClipFrame.centerX(), mWindow.centerY() - mClipFrame.centerY());
            M.mapRect(mFrame);
            M.mapRect(mClipFrame);
        }

        mClipWin.setClipWinSize(width, height);
    }

    private void onInitialHoming(float width, float height) {

        mHomeFrame.set(0, 0, width, height);
        mFrame.set(0, 0, mImage.getWidth(), mImage.getHeight());
        mClipFrame.set(0, 0, mImage.getWidth(), mImage.getHeight());
        mClipWin.setClipWinSize(width, height);

        if (mFrame.width() == 0 || mFrame.height() == 0) {
            // Bitmap invalidate.
            return;
        }

        float scale = Math.min(
                width / mFrame.width(),
                height / mFrame.height()
        );

        // Scale to fit window.
        M.reset();
        M.setScale(scale, scale, mFrame.centerX(), mFrame.centerY());
        M.postTranslate(mWindow.centerX() - mFrame.centerX(), mWindow.centerY() - mFrame.centerY());
        M.mapRect(mFrame);
        M.mapRect(mClipFrame);

        isInitialHoming = true;
        onInitialHomingDone();
    }

    private void onInitialHomingDone() {
        if (mMode == IMGMode.CLIP) {
            mClipWin.reset(mClipFrame.width(), mClipFrame.height());
        }
    }

    public PointF getPivot() {
        return new PointF(mFrame.centerX(), mFrame.centerY());
    }

    public void onDrawImage(Canvas canvas) {
        canvas.clipRect(mMode == IMGMode.CLIP ? mFrame : mClipFrame);
        canvas.drawBitmap(mImage, null, mFrame, null);
    }

    public int onDrawMosaicsPath(Canvas canvas) {
        int layerCount = canvas.saveLayer(mClipFrame.left, mClipFrame.top,
                mClipFrame.right, mClipFrame.bottom, null, Canvas.ALL_SAVE_FLAG);

        if (!isMosaicEmpty()) {
            canvas.save();
            float scale = getScale();
            canvas.translate(mClipFrame.left, mClipFrame.top);
            canvas.scale(scale, scale);
            for (IMGPath path : mMosaics) {
                path.onDrawMosaic(canvas, mPaint);
            }
            canvas.restore();
        }

        return layerCount;
    }

    public void onDrawMosaic(Canvas canvas, int layerCount) {
        canvas.drawBitmap(mMosaicImage, null, mFrame, mMosaicPaint);
        canvas.restoreToCount(layerCount);
    }

    public void onDrawDoodles(Canvas canvas) {
        if (!isDoodleEmpty()) {
            canvas.save();
            float scale = getScale();
            canvas.translate(mClipFrame.left, mClipFrame.top);
            canvas.scale(scale, scale);

            for (IMGPath path : mDoodles) {
                path.onDrawDoodle(canvas, mPaint);
            }
            canvas.restore();
        }
    }
    
    public void onDrawStickers(Canvas canvas) {
        for (IMGSticker sticker : mBackStickers) {
            if (!sticker.isShowing()) {
                float tPivotX = sticker.getX() + sticker.getPivotX();
                float tPivotY = sticker.getY() + sticker.getPivotY();
                canvas.save();
                M.reset();
                M.setTranslate(sticker.getX(), sticker.getY());
                M.postScale(sticker.getScaleX(), sticker.getScaleY(), tPivotX, tPivotY);
                M.postRotate(sticker.getRotation(), tPivotX, tPivotY);
                canvas.concat(M);
                sticker.onSticker(canvas);
                canvas.restore();
            }
        }
    }

    public void onDrawClipWindow(Canvas canvas) {
        if (mMode == IMGMode.CLIP) {
            mClipWin.onDraw(canvas);
        }
    }

    public void onTouchDown(float x, float y) {
        moveToBackground(mForeSticker);
        if (mMode == IMGMode.CLIP) {
            mAnchor = mClipWin.getAnchor(x, y);
        }
    }

    public void onTouchDown() {
        moveToBackground(mForeSticker);
        if (mMode == IMGMode.CLIP) {
//            mAnchor
        }
    }

    public void onTouchUp() {
        mAnchor = null;

    }

    public void onScaleBegin() {
        onTouchDown();
    }

    public boolean onScroll(float dx, float dy) {
        if (mMode == IMGMode.CLIP) {
            if (mAnchor != null) {
                mClipWin.onScroll(mAnchor, dx, dy);
                return true;
            }
        }
        return false;
    }

    public float getScale() {
        return 1f * mFrame.width() / mImage.getWidth();
    }

    public void setScale(float scale) {
        setScale(scale, mClipFrame.centerX(), mClipFrame.centerY());
    }

    public void setScale(float scale, float focusX, float focusY) {
        onScale(scale / getScale(), focusX, focusY);
    }

    public void onScale(float factor, float focusX, float focusY) {

        if (factor == 1f) return;

        if (Math.max(mClipFrame.width(), mClipFrame.height()) >= MAX_SIZE
                || Math.min(mClipFrame.width(), mClipFrame.height()) <= MIN_SIZE) {
            factor += (1 - factor) / 2;
        }

        M.reset();
        M.setScale(factor, factor, focusX, focusY);
        M.mapRect(mFrame);
        M.mapRect(mClipFrame);

        for (IMGSticker sticker : mBackStickers) {
            M.mapRect(sticker.getFrame());
            float tPivotX = sticker.getX() + sticker.getPivotX();
            float tPivotY = sticker.getY() + sticker.getPivotY();
            sticker.setScaleX(sticker.getScaleX() * factor);
            sticker.setScaleY(sticker.getScaleY() * factor);
            sticker.setX(sticker.getX() + sticker.getFrame().centerX() - tPivotX);
            sticker.setY(sticker.getY() + sticker.getFrame().centerY() - tPivotY);
        }
    }

    public void onScaleEnd() {

    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        if (DEFAULT_IMAGE != null) {
            DEFAULT_IMAGE.recycle();
        }
    }
}
