package com.liucong.video;


import android.app.Activity;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.ProgressBar;

import java.io.IOException;

public class VideoActivity extends Activity {
    private SurfaceView surfaceView;
    private MediaPlayer player;
    private SurfaceHolder holder;
    private ProgressBar progressBar;
    private static final String TAG = "VideoActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        surfaceView = findViewById(R.id.surfaceView);
        progressBar = findViewById(R.id.progressBar);
        //String path= "/sdcard/c9aa493839d9aade84f7f7b4d902507bqt.mp4";
        AssetFileDescriptor dataSource = null;
        try {
            AssetManager assetManager = getAssets();
            dataSource = assetManager.openFd("mumu.mp4");
        } catch (Exception e) {
            Log.e(TAG, "onCreate: " + e.getMessage(), new Throwable());
        }

        player = new MediaPlayer();
        try {
            player.setDataSource(dataSource.getFileDescriptor(), dataSource.getStartOffset(), dataSource.getLength());
            holder = surfaceView.getHolder();
            holder.addCallback(new MyCallBack());
            player.prepare();
            player.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    progressBar.setVisibility(View.INVISIBLE);
                    player.start();
                    player.setLooping(true);
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private class MyCallBack implements SurfaceHolder.Callback {
        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            player.setDisplay(holder);
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {

        }
    }
}