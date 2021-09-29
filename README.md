# ScreenCapture
Use MediaProjection to get Screen Capture.


### 项目地址 ###
[https://github.com/etcparking/ScreenCapture](https://github.com/etcparking/ScreenCapture)

如果对你有帮助，请Star，谢谢！
### Gradle 引用 ###

```
compile 'com.shark:screencapture:1.0.0'
```

### 创建实例 ###

```
ScreenCapture mScreenCapture = ScreenCapture.newInstance(this);
```
### 事件监听 ###

```
mScreenCapture.setCaptureListener(new ScreenCapture.OnCaptureListener() {
                @Override
                public void onScreenCaptureSuccess(Bitmap bitmap, String savePath) {
                    Log.d(TAG, "onScreenCaptureSuccess savePath:" + savePath);
                }

                @Override
                public void onScreenCaptureFailed(String errorMsg) {
                    Log.d(TAG, "onScreenCaptureFailed errorMsg:" + errorMsg);
                }

                @Override
                public void onScreenRecordStart() {
                    Log.d(TAG, "onScreenRecordStart");
                }

                @Override
                public void onScreenRecordStop() {
                    Log.d(TAG, "onScreenRecordStop");
                }

                @Override
                public void onScreenRecordSuccess(String savePath) {
                    Log.d(TAG, "onScreenRecordSuccess savePath:" + savePath);
                }

                @Override
                public void onScreenRecordFailed(String errorMsg) {
                    Log.d(TAG, "onScreenRecordFailed errorMsg:" + errorMsg);
                }
            });
```
### 设置图片和视频保存路径 ###
可不设置，即使用默认路径

```
mScreenCapture.setImagePath(Environment.getExternalStorageDirectory().getPath() + "/ScreenCapture/screen_capture/", "image_screen.png");
mScreenCapture.setRecordPath(Environment.getExternalStorageDirectory().getPath() + "/ScreenCapture/record/", "recording_screen.mp4");
        
```
### 开始截屏 ###

```
if (mScreenCapture != null) {
	mScreenCapture.screenCapture();
}
```
### 开始录屏 ###

```
if (mScreenCapture != null) {
	mScreenCapture.record();
}
```
### 权限相关 ###

```
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
```

### 最后记得cleanup ###

```
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mScreenCapture != null) {
            mScreenCapture.cleanup();
            mScreenCapture = null;
        }
    }
```
