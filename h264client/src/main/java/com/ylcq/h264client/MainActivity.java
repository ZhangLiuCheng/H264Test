package com.ylcq.h264client;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;

public class MainActivity extends AppCompatActivity {

    private final static String TAG = "H264";

    private final static String MIME_TYPE = "video/avc"; // H.264 Advanced Video
//    private final static int VIDEO_WIDTH = 1280;
//    private final static int VIDEO_HEIGHT = 720;

    private final static int VIDEO_WIDTH = 720;
    private final static int VIDEO_HEIGHT = 1280;

//    private final static int VIDEO_WIDTH = 360;
//    private final static int VIDEO_HEIGHT = 640;
    private final static int TIME_INTERNAL = 30;
    private final static int HEAD_OFFSET = 512;

    private SurfaceView mSurfaceView;
    private MediaCodec mCodec;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mSurfaceView = (SurfaceView) findViewById(R.id.surfaceView1);
        DisplayMetrics dm = getResources().getDisplayMetrics();
        mSurfaceView.getHolder().setFixedSize(dm.widthPixels, dm.heightPixels);
        mSurfaceView.getHolder().setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
    }

    public void start(View view) {
        startServer();
        initDecoder();
    }

    InputStream is;
    private void startServer() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Socket socket = new Socket("192.168.81.197", 10001);
                    is = socket.getInputStream();
                    Log.v(TAG, "连接成功");

                    while (true) {
                        byte[] head = new byte[4];
                        is.read(head);
                        int len = bufferToInt(head);
                        Log.v(TAG, "read len " + len);

                        byte[] buf = new byte[len];
                        Log.v(TAG, "read content " + buf.length);

                        DataInputStream dis = new DataInputStream(is);
                        dis.readFully(buf);
                        onFrame(buf, 0, buf.length);
                    }
                } catch (Exception ex) {

                }
            }
        }).start();
    }

    public static int bufferToInt(byte[] src) {
        int value;
        value = (int) ((src[0] & 0xFF)
                | ((src[1] & 0xFF)<<8)
                | ((src[2] & 0xFF)<<16)
                | ((src[3] & 0xFF)<<24));
        return value;
    }

    public void initDecoder() {
        try {
            mCodec = MediaCodec.createDecoderByType(MIME_TYPE);

            final MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE, VIDEO_WIDTH, VIDEO_HEIGHT);
            format.setInteger(MediaFormat.KEY_BIT_RATE,  VIDEO_WIDTH * VIDEO_HEIGHT / 2);
            format.setInteger(MediaFormat.KEY_FRAME_RATE, 15);
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);

            // 横屏
//            byte[] header_sps = {0, 0, 0, 1, 103, 66, -128, 31, -38, 1, 64, 22, -24, 6, -48, -95, 53};
//            byte[] header_pps = {0, 0 ,0, 1, 104, -50, 6, -30};

            // 竖屏
            byte[] header_sps = {0, 0, 0, 1, 103, 66, -128, 31, -38, 2, -48, 40, 104, 6, -48, -95, 53};
            byte[] header_pps = {0, 0 ,0, 1, 104, -50, 6, -30};

            format.setByteBuffer("csd-0", ByteBuffer.wrap(header_sps));
            format.setByteBuffer("csd-1", ByteBuffer.wrap(header_pps));
            mCodec.configure(format, mSurfaceView.getHolder().getSurface(),
                    null, 0);
            mCodec.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

//    int mCount = 0;
    public boolean onFrame(byte[] buf, int offset, int length) {
        // Get input buffer index
        ByteBuffer[] inputBuffers = mCodec.getInputBuffers();
        int inputBufferIndex = mCodec.dequeueInputBuffer(100);
        Log.v(TAG, " inputBufferIndex  " + inputBufferIndex);

        if (inputBufferIndex >= 0) {
            ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
            inputBuffer.clear();
            inputBuffer.put(buf, offset, length);
            mCodec.queueInputBuffer(inputBufferIndex, 0, length, System.currentTimeMillis(), 0);
//            mCount++;
        } else {
            return false;
        }
        // Get output buffer index
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        int outputBufferIndex = mCodec.dequeueOutputBuffer(bufferInfo, 100);

        while (outputBufferIndex >= 0) {
            mCodec.releaseOutputBuffer(outputBufferIndex, true);
            outputBufferIndex = mCodec.dequeueOutputBuffer(bufferInfo, 0);
        }
        return true;
    }
}
