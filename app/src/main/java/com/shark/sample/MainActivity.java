package com.shark.sample;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.MediaController;
import android.widget.TextView;
import android.widget.VideoView;

import com.shark.screencapture.ScreenCapture;

public class MainActivity extends AppCompatActivity {

    private static String TAG = MainActivity.class.getName();
    private ScreenCapture mScreenCapture;

    private ImageView mImageView;
    private TextView mResultTv;
    private Button mRecordBtn;
    private VideoView mVideoView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mResultTv = (TextView) findViewById(R.id.tv_info);
        mImageView = (ImageView) findViewById(R.id.iv_img);
        mRecordBtn = (Button) findViewById(R.id.btn_record);
        mVideoView = (VideoView) findViewById(R.id.video_view);
        mVideoView.setVisibility(View.GONE);

        if (mScreenCapture == null) {
            mScreenCapture = new ScreenCapture(this);
            mScreenCapture.setCaptureListener(new ScreenCapture.OnCaptureListener() {
                @Override
                public void onScreenCaptureSuccess(Bitmap bitmap, String savePath) {
                    Log.d(TAG, "onScreenCaptureSuccess savePath:" + savePath);
                    clearViews();
                    mResultTv.setText(savePath);
                    mImageView.setImageURI(Uri.parse(savePath));
                    mImageView.setVisibility(View.VISIBLE);
                }

                @Override
                public void onScreenCaptureFailed(String errorMsg) {
                    Log.d(TAG, "onScreenCaptureFailed errorMsg:" + errorMsg);
                    mResultTv.setText(errorMsg);
                }

                @Override
                public void onScreenRecordStart() {
                    Log.d(TAG, "onScreenRecordStart");
                    clearViews();
                    mResultTv.setText("Recording");
                    mRecordBtn.setText("Stop record");
                    mImageView.setImageURI(null);
                }

                @Override
                public void onScreenRecordStop() {
                    Log.d(TAG, "onScreenRecordStop");
                    mRecordBtn.setText("Start record");
                }

                @Override
                public void onScreenRecordSuccess(String savePath) {
                    Log.d(TAG, "onScreenRecordSuccess savePath:" + savePath);
                    mResultTv.setText(savePath);
                    mVideoView.setMediaController(new MediaController(MainActivity.this));
                    mVideoView.setVideoPath(savePath);
                    mVideoView.setVisibility(View.VISIBLE);
                    mVideoView.start();
                }

                @Override
                public void onScreenRecordFailed(String errorMsg) {
                    Log.d(TAG, "onScreenRecordFailed errorMsg:" + errorMsg);
                    mResultTv.setText(errorMsg);
                }
            });
            // You can set file path or not
//            mScreenCapture.setImagePath(Environment.getExternalStorageDirectory().getPath() + "/ScreenCapture/screen_capture/", "image_screen.png");
//            mScreenCapture.setRecordPath(Environment.getExternalStorageDirectory().getPath() + "/ScreenCapture/record/", "recording_screen.mp4");
        }
    }

    public void startCapture(View v) {
        if (mScreenCapture != null) {
            mScreenCapture.screenCapture();
        }
    }

    public void startRecord(View v) {
        if (mScreenCapture != null) {
            mScreenCapture.record();
        }
    }

    /**
     * Handle permission here which caused by MediaProjectionManager.createScreenCaptureIntent()
     * @param requestCode
     * @param resultCode
     * @param data
     */
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (mScreenCapture != null) {
            mScreenCapture.onActivityResult(requestCode, resultCode, data);
        }
    }

    /**
     * Handle permission here. Like Manifest.permission.WRITE_EXTERNAL_STORAGE
     * @param requestCode
     * @param permissions
     * @param grantResults
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (mScreenCapture != null) {
            mScreenCapture.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    private void clearViews() {
        mImageView.setImageURI(null);
        mImageView.setVisibility(View.GONE);
        mResultTv.setText("");
        mVideoView.setVideoURI(null);
        mVideoView.setVisibility(View.GONE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mScreenCapture != null) {
            mScreenCapture.cleanup();
            mScreenCapture = null;
        }
    }
}
