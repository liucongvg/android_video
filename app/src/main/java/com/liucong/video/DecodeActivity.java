package com.liucong.video;

import java.io.FileDescriptor;
import java.io.IOException;
import java.nio.ByteBuffer;

import android.app.Activity;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.Rect;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

public class DecodeActivity extends Activity implements SurfaceHolder.Callback {
    //private static final String SAMPLE = "/sdcard" + "/video.mp4";
    private VideoThread mVideoThread = null;
    private AudioThread mAudioThread = null;
    private static final String TAG = "DecodeActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SurfaceView sv = new SurfaceView(this);
        sv.getHolder().addCallback(this);
        setContentView(sv);
    }

    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        Rect rect = holder.getSurfaceFrame();
        Log.d("liucong", "surface: " + (rect.right - rect.left) + " * " + (rect.top - rect.bottom));
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if (mVideoThread == null) {
            mVideoThread = new VideoThread(holder.getSurface());
            mVideoThread.start();
        }
        if (mAudioThread == null) {
            mAudioThread = new AudioThread();
            mAudioThread.start();
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if (mVideoThread != null) {
            mVideoThread.interrupt();
        }
    }

    private class VideoThread extends Thread {
        private MediaExtractor extractor;
        private MediaCodec videoDecoder;
        private Surface surface;

        public VideoThread(Surface surface) {
            this.surface = surface;
        }

        @Override
        public void run() {
            AssetFileDescriptor dataSource = null;
            try {
                AssetManager assetManager = getAssets();
                dataSource = assetManager.openFd("mumu.mp4");
            } catch (Exception e) {
                Log.e(TAG, "run: " + e.getMessage(), new Throwable());
            }
            extractor = new MediaExtractor();
            try {
                //extractor.setDataSource(SAMPLE);
                extractor.setDataSource(dataSource.getFileDescriptor(), dataSource.getStartOffset(), dataSource.getLength());
            } catch (IOException e) {
                e.printStackTrace();
            }

            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat format = extractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime.startsWith("video/")) {
                    extractor.selectTrack(i);
                    try {
                        videoDecoder = MediaCodec.createDecoderByType(mime);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    int with = format.getInteger(MediaFormat.KEY_WIDTH);
                    int height = format.getInteger(MediaFormat.KEY_HEIGHT);
                    Log.d("liucong", with + " * " + height);
                    videoDecoder.configure(format, surface, null, 0);
                    break;
                }
            }

            if (videoDecoder == null) {
                Log.e(TAG, "Can't find video info!");
                return;
            }

            videoDecoder.start();

            ByteBuffer[] inputBuffers = videoDecoder.getInputBuffers();
            ByteBuffer[] outputBuffers = videoDecoder.getOutputBuffers();
            BufferInfo info = new BufferInfo();
            boolean isEOS = false;
            long startMs = System.currentTimeMillis();

            while (!Thread.interrupted()) {
                if (!isEOS) {
                    int inIndex = videoDecoder.dequeueInputBuffer(10000);
                    if (inIndex >= 0) {
                        ByteBuffer buffer = inputBuffers[inIndex];
                        int sampleSize = extractor.readSampleData(buffer, 0);
                        if (sampleSize < 0) {
                            // We shouldn't stop the playback at this point, just pass the EOS
                            // flag to decoder, we will get it again from the
                            // dequeueOutputBuffer
                            Log.d(TAG, "InputBuffer BUFFER_FLAG_END_OF_STREAM");
                            videoDecoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            isEOS = true;
                        } else {
                            //Log.d(TAG, "InputBuffer queueInputBuffer");
                            videoDecoder.queueInputBuffer(inIndex, 0, sampleSize, extractor.getSampleTime(), 0);
                            extractor.advance();
                        }
                    }
                }

                int outIndex = videoDecoder.dequeueOutputBuffer(info, 10000);
                switch (outIndex) {
                    case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                        Log.d(TAG, "INFO_OUTPUT_BUFFERS_CHANGED");
                        outputBuffers = videoDecoder.getOutputBuffers();
                        break;
                    case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                        Log.d(TAG, "New format " + videoDecoder.getOutputFormat());
                        break;
                    case MediaCodec.INFO_TRY_AGAIN_LATER:
                        Log.d(TAG, "dequeueOutputBuffer timed out!");
                        break;
                    default:
                        ByteBuffer buffer = outputBuffers[outIndex];
                        //Log.v(TAG, "We can't use this buffer but render it due to the API limit, " + buffer);

                        // We use a very simple clock to keep the video FPS, or the video
                        // playback will be too fast
                        while (info.presentationTimeUs / 1000 > System.currentTimeMillis() - startMs) {
                            //if(true)break;
                            try {
                                sleep(10);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                                break;
                            }
                        }
                        videoDecoder.releaseOutputBuffer(outIndex, true);
                        break;
                }

                // All decoded frames have been rendered, we can stop playing now
                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    Log.d(TAG, "OutputBuffer BUFFER_FLAG_END_OF_STREAM");
                    break;
                }
            }

            videoDecoder.stop();
            videoDecoder.release();
            extractor.release();
            try {
                dataSource.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private class AudioThread extends Thread {
        private MediaExtractor extractor;
        private MediaCodec audioDecoder;
        private int mSampleRate;
        private int mChanel;
        private long mDuration;

        @Override
        public void run() {
            AssetFileDescriptor dataSource = null;
            try {
                AssetManager assetManager = getAssets();
                dataSource = assetManager.openFd("mumu.mp4");
            } catch (Exception e) {
                Log.e(TAG, "run: " + e.getMessage(), new Throwable());
            }
            extractor = new MediaExtractor();
            try {
                //extractor.setDataSource(SAMPLE);
                extractor.setDataSource(dataSource.getFileDescriptor(), dataSource.getStartOffset(), dataSource.getLength());
            } catch (IOException e) {
                e.printStackTrace();
            }

            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat format = extractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime.startsWith("audio/")) {
                    extractor.selectTrack(i);
                    mSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                    mChanel = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                    mDuration = format.getLong(MediaFormat.KEY_DURATION);
                    try {
                        audioDecoder = MediaCodec.createDecoderByType(mime);
                        audioDecoder.configure(format, null, null, 0);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }

            if (audioDecoder == null) {
                Log.e(TAG, "Can't find audio info!");
                return;
            }

            audioDecoder.start();

            ByteBuffer[] inputBuffers = audioDecoder.getInputBuffers();
            ByteBuffer[] outputBuffers = audioDecoder.getOutputBuffers();
            BufferInfo info = new BufferInfo();
            boolean isEOS = false;
            int buffsize = AudioTrack.getMinBufferSize(mSampleRate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT);
            AudioTrack audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, mSampleRate,
                    AudioFormat.CHANNEL_OUT_STEREO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    buffsize,
                    AudioTrack.MODE_STREAM);
            audioTrack.play();
            while (!Thread.interrupted()) {
                if (!isEOS) {
                    int inIndex = audioDecoder.dequeueInputBuffer(10000);
                    if (inIndex >= 0) {
                        ByteBuffer buffer = inputBuffers[inIndex];
                        int sampleSize = extractor.readSampleData(buffer, 0);
                        if (sampleSize < 0) {
                            // We shouldn't stop the playback at this point, just pass the EOS
                            // flag to decoder, we will get it again from the
                            // dequeueOutputBuffer
                            Log.d(TAG, "InputBuffer BUFFER_FLAG_END_OF_STREAM");
                            audioDecoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            isEOS = true;
                        } else {
                            Log.d(TAG, "InputBuffer queueInputBuffer");
                            audioDecoder.queueInputBuffer(inIndex, 0, sampleSize, extractor.getSampleTime(), 0);
                            extractor.advance();
                        }
                    }
                }

                int outIndex = audioDecoder.dequeueOutputBuffer(info, 10000);
                switch (outIndex) {
                    case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                        Log.d(TAG, "INFO_OUTPUT_BUFFERS_CHANGED");
                        outputBuffers = audioDecoder.getOutputBuffers();
                        break;
                    case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                        Log.d(TAG, "New format " + audioDecoder.getOutputFormat());
                        break;
                    case MediaCodec.INFO_TRY_AGAIN_LATER:
                        Log.d(TAG, "dequeueOutputBuffer timed out!");
                        break;
                    default:
                        ByteBuffer buffer = outputBuffers[outIndex];
                        final byte[] chunk = new byte[info.size];
                        buffer.get(chunk);
                        buffer.clear();
                        //Log.d(TAG, "audioTrack.write start");
                        audioTrack.write(chunk, info.offset, info.offset + info.size);
                        //Log.d(TAG, "audioTrack.write end");
                        audioDecoder.releaseOutputBuffer(outIndex, false);
                        break;
                }

                // All decoded frames have been rendered, we can stop playing now
                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    Log.d(TAG, "OutputBuffer BUFFER_FLAG_END_OF_STREAM");
                    break;
                }
            }

            audioDecoder.stop();
            audioDecoder.release();
            extractor.release();
            try {
                dataSource.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
