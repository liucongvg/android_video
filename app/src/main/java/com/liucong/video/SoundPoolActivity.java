package com.liucong.video;

import android.app.Activity;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

public class SoundPoolActivity extends Activity {

    private static String TAG = "SoundPool";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_soundpool);
    }

    public void play(View view) {
        Log.d(TAG, "play...");
        new Thread(new Runnable() {
            @Override
            public void run() {
                SoundPool soundPool;
                //实例化SoundPool

                //sdk版本21是SoundPool 的一个分水岭
                if (Build.VERSION.SDK_INT >= 21) {
                    SoundPool.Builder builder = new SoundPool.Builder();
                    //传入最多播放音频数量,
                    builder.setMaxStreams(1);
                    //AudioAttributes是一个封装音频各种属性的方法
                    AudioAttributes.Builder attrBuilder = new AudioAttributes.Builder();
                    //设置音频流的合适的属性
                    attrBuilder.setLegacyStreamType(AudioManager.STREAM_MUSIC);
                    //加载一个AudioAttributes
                    builder.setAudioAttributes(attrBuilder.build());
                    soundPool = builder.build();
                } else {
                    /**
                     * 第一个参数：int maxStreams：SoundPool对象的最大并发流数
                     * 第二个参数：int streamType：AudioManager中描述的音频流类型
                     *第三个参数：int srcQuality：采样率转换器的质量。 目前没有效果。 使用0作为默认值。
                     */
                    Log.d(TAG, "new SoundPool start...");
                    soundPool = new SoundPool(1, AudioManager.STREAM_MUSIC, 0);
                    Log.d(TAG, "new SoundPool end...");
                }

                //可以通过四种途径来记载一个音频资源：
                //1.通过一个AssetFileDescriptor对象
                //int load(AssetFileDescriptor afd, int priority)
                //2.通过一个资源ID
                //int load(Context context, int resId, int priority)
                //3.通过指定的路径加载
                //int load(String path, int priority)
                //4.通过FileDescriptor加载
                //int load(FileDescriptor fd, long offset, long length, int priority)
                //声音ID 加载音频资源,这里用的是第二种，第三个参数为priority，声音的优先级*API中指出，priority参数目前没有效果，建议设置为1。
                Log.d(TAG, "soundPool.load start ...");
                final int voiceId = soundPool.load(SoundPoolActivity.this, R.raw.short_music, 1);
                Log.d(TAG, "soundPool.load end ...");
                //异步需要等待加载完成，音频才能播放成功
                soundPool.setOnLoadCompleteListener(new SoundPool.OnLoadCompleteListener() {
                    @Override
                    public void onLoadComplete(SoundPool soundPool, int sampleId, int status) {
                        if (status == 0) {
                            //第一个参数soundID
                            //第二个参数leftVolume为左侧音量值（范围= 0.0到1.0）
                            //第三个参数rightVolume为右的音量值（范围= 0.0到1.0）
                            //第四个参数priority 为流的优先级，值越大优先级高，影响当同时播放数量超出了最大支持数时SoundPool对该流的处理
                            //第五个参数loop 为音频重复播放次数，0为值播放一次，-1为无限循环，其他值为播放loop+1次
                            //第六个参数 rate为播放的速率，范围0.5-2.0(0.5为一半速率，1.0为正常速率，2.0为两倍速率)
                            Log.d(TAG, "soundPool.play start");
                            soundPool.play(voiceId, 1, 1, 1, 0, 1);
                            Log.d(TAG, "soundPool.play end");
/*
                            Log.d(TAG, "soundPool.unload start");
                            soundPool.unload(voiceId);
                            Log.d(TAG, "soundPool.unload end");
                            Log.d(TAG, "soundPool.release start");
                            soundPool.release();
                            Log.d(TAG, "soundPool.release end");*/
                        }
                    }
                });
            }
        }).start();
    }
}
