package com.shark.screencapture;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Surface;
import android.view.WindowManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by panj on 2017/5/22.
 */

public class ScreenCapture {

    private static String TAG = ScreenCapture.class.getName();
    private AppCompatActivity mActivity;

    private final int REQUEST_CODE_SAVE_IMAGE_FILE = 110;

    private int mWindowWidth;
    private int mWindowHeight;
    private int mScreenDensity;

    private String mImageName;
    private String mImagePath;

    private VirtualDisplay mVirtualDisplay;
    private WindowManager mWindowManager;
    private ImageReader mImageReader;

    private MediaProjectionManager mMediaProjectionManager;
    private MediaProjection mMediaProjection;

    private int mResultCode;
    private Intent mResultData;
    private static final int REQUEST_MEDIA_PROJECTION = 1;
    private Bitmap mBitmap;
    private boolean isSaveImageEnable = true;

    // record
    private String mVideoPath;
    private String mVideoName;
    private Surface mSurface;
    private MediaCodec mMediaCodec;
    private MediaMuxer mMuxer;
    private boolean isRecordOn;

    private AtomicBoolean mIsQuit = new AtomicBoolean(false);
    private MediaCodec.BufferInfo mBufferInfo = new MediaCodec.BufferInfo();
    private boolean mMuxerStarted = false;
    private int mVideoTrackIndex = -1;

    private boolean isScreenshot = false;
    private OnCaptureListener mCaptureListener = null;

    public interface OnCaptureListener {
        void onScreenCaptureSuccess(Bitmap bitmap, String savePath);

        void onScreenCaptureFailed(String errorMsg);

        void onScreenRecordStart();

        void onScreenRecordStop();

        void onScreenRecordSuccess(String savePath);

        void onScreenRecordFailed(String errorMsg);
    }

    public void setCaptureListener(OnCaptureListener captureListener) {
        this.mCaptureListener = captureListener;
    }

    public static ScreenCapture newInstance(AppCompatActivity activity) {
        return new ScreenCapture(activity);
    }

    public ScreenCapture(AppCompatActivity activity) {
        this.mActivity = activity;
        createEnvironment();
    }

    private void createEnvironment() {
        mImagePath = mActivity.getExternalCacheDir() + "/ScreenCapture/screenshot/";
        mWindowManager = (WindowManager) mActivity.getSystemService(Context.WINDOW_SERVICE);
        mWindowWidth = mWindowManager.getDefaultDisplay().getWidth();
        mWindowHeight = mWindowManager.getDefaultDisplay().getHeight();
        DisplayMetrics displayMetrics = new DisplayMetrics();
        mWindowManager.getDefaultDisplay().getMetrics(displayMetrics);
        mScreenDensity = displayMetrics.densityDpi;
        mImageReader = ImageReader.newInstance(mWindowWidth, mWindowHeight, 0x1, 2);

        mMediaProjectionManager = (MediaProjectionManager) mActivity.
                getSystemService(Context.MEDIA_PROJECTION_SERVICE);

        mVideoPath = mActivity.getExternalCacheDir() + "/ScreenCapture/record/";
    }

    public void setImagePath(String path) {
        mImagePath = path;
    }

    public void setImagePath(String path, String imageName) {
        mImagePath = path;
        mImageName = imageName;
    }

    public void setRecordPath(String path) {
        mVideoPath = path;
    }

    public void setRecordPath(String path, String videoName) {
        mVideoPath = path;
        mVideoName = videoName;
    }

    public void screenCapture() {
        if (isRecordOn) {
            if (mCaptureListener != null) {
                mCaptureListener.onScreenCaptureFailed("Recording is in progress.");
            }
            return;
        }
        isScreenshot = true;
        if (startScreenCapture()) {
            Handler handler = new Handler();
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    Log.d(TAG, "start startCapture");
                    startCapture();
                }
            }, 200);
        }
    }

    public void record() {
        isScreenshot = false;
        checkPermission();
    }

    private void startCapture() {
        if (TextUtils.isEmpty(mImageName)) {
            mImageName = System.currentTimeMillis() + ".png";
        }
        Log.i(TAG, "image name is : " + mImageName);
        Image image = mImageReader.acquireLatestImage();
        if (image == null) {
            Log.e(TAG, "image is null.");
            return;
        }
        int width = image.getWidth();
        int height = image.getHeight();
        final Image.Plane[] planes = image.getPlanes();
        final ByteBuffer buffer = planes[0].getBuffer();
        int pixelStride = planes[0].getPixelStride();
        int rowStride = planes[0].getRowStride();
        int rowPadding = rowStride - pixelStride * width;
        mBitmap = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888);
        mBitmap.copyPixelsFromBuffer(buffer);
        mBitmap = Bitmap.createBitmap(mBitmap, 0, 0, width, height);
        image.close();

        stopScreenCapture();
        if (mBitmap != null) {
            Log.d(TAG, "bitmap create success");
            if (isSaveImageEnable) {
                checkPermission();
            } else {
                if (mCaptureListener != null) {
                    mCaptureListener.onScreenCaptureSuccess(mBitmap, null);
                }
            }
        } else {
            Log.d(TAG, "bitmap is null");
            if (mCaptureListener != null) {
                mCaptureListener.onScreenCaptureFailed("Get bitmap failed.");
            }
        }
    }

    private void stopScreenCapture() {
        if (mVirtualDisplay != null) {
            mVirtualDisplay.release();
            mVirtualDisplay = null;
        }
    }

    private boolean startScreenCapture() {
        Log.d(TAG, "startScreenCapture");
        if (this == null) {
            return false;
        }
        if (mMediaProjection != null) {
            setUpVirtualDisplay();
            return true;
        } else if (mResultCode != 0 && mResultData != null) {
            setUpMediaProjection();
            setUpVirtualDisplay();
            return true;
        } else {
            Log.d(TAG, "Requesting confirmation");
            // This initiates a prompt dialog for the user to confirm screen projection.
            mActivity.startActivityForResult(
                    mMediaProjectionManager.createScreenCaptureIntent(),
                    REQUEST_MEDIA_PROJECTION);
            return false;
        }
    }

    private void setUpVirtualDisplay() {
        if (isScreenshot) {
            mVirtualDisplay = mMediaProjection.createVirtualDisplay("ScreenCapture",
                    mWindowWidth, mWindowHeight, mScreenDensity,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    mImageReader.getSurface(), null, null);
        } else {
            mVirtualDisplay = mMediaProjection.createVirtualDisplay("record_screen",
                    mWindowWidth, mWindowHeight, mScreenDensity,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    mSurface, null, null);
        }
    }

    private void setUpMediaProjection() {
        mMediaProjection = mMediaProjectionManager.getMediaProjection(mResultCode, mResultData);
    }

    private void checkPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (AndroidUtil.checkSelfPermission(mActivity, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                if (ActivityCompat.shouldShowRequestPermissionRationale(mActivity, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                    mActivity.requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_CODE_SAVE_IMAGE_FILE);
                } else {
                    mActivity.requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_CODE_SAVE_IMAGE_FILE);
                }
                return;
            } else {
                if (isScreenshot) {
                    saveToFile();
                } else {
                    recordClick();
                }
            }
        } else {
            if (isScreenshot) {
                saveToFile();
            } else {
                recordClick();
            }
        }
    }

    private void saveToFile() {
        try {
            File fileFolder = new File(mImagePath);
            if (!fileFolder.exists())
                fileFolder.mkdirs();
            File file = new File(mImagePath, mImageName);
            if (!file.exists()) {
                file.createNewFile();
            }
            FileOutputStream out = new FileOutputStream(file);
            mBitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            out.flush();
            out.close();
            if (mCaptureListener != null) {
                mCaptureListener.onScreenCaptureSuccess(mBitmap, file.getAbsolutePath());
            }
        } catch (IOException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
        }
    }

    // record
    private void configureMedia() {
        MediaFormat mediaFormat = MediaFormat.createVideoFormat("video/avc", mWindowWidth, mWindowHeight);
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, 6000000);
        mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 30);
        mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2);
        try {
            mMediaCodec = MediaCodec.createEncoderByType("video/avc");
        } catch (IOException e) {
            e.printStackTrace();
        }
        mMediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mSurface = mMediaCodec.createInputSurface();
        mMediaCodec.start();
    }

    private void recordClick() {
        isRecordOn = !isRecordOn;
        if (isRecordOn) {
            Log.d(TAG, "Record start");
            if (mCaptureListener != null) {
                mCaptureListener.onScreenRecordStart();
            }
            recordStart();
        } else {
            Log.d(TAG, "Record stop");
            if (mCaptureListener != null) {
                mCaptureListener.onScreenRecordStop();
            }
            recordStop();
        }
    }

    private void recordStart() {
        configureMedia();
        if (startScreenCapture()) {
            new Thread() {
                @Override
                public void run() {
                    startRecord();
                }
            }.start();
        }
    }

    private void startRecord() {
        try {
            if (!mVideoPath.substring(mVideoPath.length() - 1, mVideoPath.length()).equals("/")) {
                mVideoPath = mVideoPath + "/";
            }
            File fileFolder = new File(mVideoPath);
            if (!fileFolder.exists())
                fileFolder.mkdirs();
            if (TextUtils.isEmpty(mVideoName)) {
                mVideoName = System.currentTimeMillis() + ".mp4";
            }
            File file = new File(mVideoPath, mVideoName);
            if (!file.exists()) {
                file.createNewFile();
            }
            mMuxer = new MediaMuxer(mVideoPath + mVideoName, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            //根据录制的方向可自行设置旋转保存视频
            //mMuxer.setOrientationHint(90);
            recordVirtualDisplay();
        } catch (IOException e) {
            if (mCaptureListener != null) {
                mCaptureListener.onScreenRecordFailed(e.getMessage());
            }
            e.printStackTrace();
        } finally {
            release();
        }
    }

    private void recordStop() {
        mIsQuit.set(true);
        if (mCaptureListener != null) {
            mCaptureListener.onScreenRecordSuccess(mVideoPath + mVideoName);
        }
    }

    private void recordVirtualDisplay() {
        while (!mIsQuit.get()) {
           if (this.mMediaCodec == null) {//防止退出nullPoint
                break;
            }
            int index = mMediaCodec.dequeueOutputBuffer(mBufferInfo, 10000);
            Log.d(TAG, "dequeue output buffer index=" + index);
            if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {//后续输出格式变化
                resetOutputFormat();
            } else if (index == MediaCodec.INFO_TRY_AGAIN_LATER) {//请求超时
                Log.d(TAG, "retrieving buffers time out!");
                try {
                    // wait 10ms
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                }
            } else if (index >= 0) {//有效输出
                if (!mMuxerStarted) {
                    throw new IllegalStateException("MediaMuxer dose not call addTrack(format) ");
                }
                encodeToVideoTrack(index);
                mMediaCodec.releaseOutputBuffer(index, false);
            }
        }
    }

    private void resetOutputFormat() {
        // should happen before receiving buffers, and should only happen once
        if (mMuxerStarted) {
            throw new IllegalStateException("output format already changed!");
        }
        MediaFormat newFormat = mMediaCodec.getOutputFormat();

        Log.d(TAG, "output format changed.\n new format: " + newFormat.toString());
        mVideoTrackIndex = mMuxer.addTrack(newFormat);
        mMuxer.start();
        mMuxerStarted = true;
        Log.i(TAG, "started media muxer, videoIndex=" + mVideoTrackIndex);
    }

    private void encodeToVideoTrack(int index) {
        ByteBuffer encodedData = mMediaCodec.getOutputBuffer(index);

        if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {//是编码需要的特定数据，不是媒体数据
            // The codec config data was pulled out and fed to the muxer when we got
            // the INFO_OUTPUT_FORMAT_CHANGED status.
            // Ignore it.
            Log.d(TAG, "ignoring BUFFER_FLAG_CODEC_CONFIG");
            mBufferInfo.size = 0;
        }
        if (mBufferInfo.size == 0) {
            Log.d(TAG, "info.size == 0, drop it.");
            encodedData = null;
        } else {
            Log.d(TAG, "got buffer, info: size=" + mBufferInfo.size
                    + ", presentationTimeUs=" + mBufferInfo.presentationTimeUs
                    + ", offset=" + mBufferInfo.offset);
        }
        if (encodedData != null) {
            encodedData.position(mBufferInfo.offset);
            encodedData.limit(mBufferInfo.offset + mBufferInfo.size);
            mMuxer.writeSampleData(mVideoTrackIndex, encodedData, mBufferInfo);//写入
            Log.i(TAG, "sent " + mBufferInfo.size + " bytes to muxer...");
        }
    }

    private void release() {
        mIsQuit.set(false);
        mMuxerStarted = false;
        Log.i(TAG, " release() ");
        if (mMediaCodec != null) {
            mMediaCodec.stop();
            mMediaCodec.release();
            mMediaCodec = null;
        }
        if (mVirtualDisplay != null) {
            mVirtualDisplay.release();
            mVirtualDisplay = null;
        }
        if (mMuxer != null) {
            mMuxer.stop();
            mMuxer.release();
            mMuxer = null;
        }
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d(TAG, "onActivityResult requestCode:" + requestCode + " resultCode:" + resultCode);
        if (requestCode == REQUEST_MEDIA_PROJECTION) {
            if (resultCode != Activity.RESULT_OK) {
                Log.w(TAG, "User cancelled.");
                return;
            }
            if (this == null) {
                return;
            }
            mResultCode = resultCode;
            mResultData = data;
            if (isScreenshot) {
                screenCapture();
            } else {
                recordStart();
            }
        }
    }

    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case REQUEST_CODE_SAVE_IMAGE_FILE: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.
                    if (isScreenshot) {
                        saveToFile();
                    } else {
                        recordClick();
                    }
                } else {
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
//                    Toast.makeText(mActivity, "Permission denied", Toast.LENGTH_SHORT).show();
                    if (mCaptureListener != null) {
                        mCaptureListener.onScreenCaptureFailed("Permission denied");
                    }
                }
                break;
            }
        }
    }

    public void cleanup() {
        if (mBitmap != null) {
            mBitmap.recycle();
            mBitmap = null;
        }
        release();
        if (mMediaProjection != null) {
            mMediaProjection.stop();
        }
    }
}
