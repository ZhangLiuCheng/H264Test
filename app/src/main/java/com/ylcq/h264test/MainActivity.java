package com.ylcq.h264test;

import android.content.Intent;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.widget.TextView;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;

public class MainActivity extends AppCompatActivity implements ScreenCaputre.ScreenCaputreListener {

    private final static String TAG = MainActivity.class.getSimpleName();

    private ScreenCaputre screenCaputre;
    private VoiceRecoder voiceRecoder;

    private static final int REQUEST_CODE = 1;
    private MediaProjectionManager mMediaProjectionManager;
    private MediaProjection mediaProjection;

    private TextView mInfo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mInfo = (TextView) findViewById(R.id.info);
//        startServer();
        startServer1();
    }

    private OutputStream os;
    private void startServer() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    ServerSocket serverSockets = new ServerSocket(10001);
                    Socket socket = serverSockets.accept();
                    os = socket.getOutputStream();
                } catch (Exception ex) {

                }
            }
        }).start();
    }

    private MyWebSocketServer myWebSocketServer = new MyWebSocketServer(10002);
    private WebSocket mWebSocket;
    private void startServer1() {
        myWebSocketServer.start();
    }
    private class MyWebSocketServer extends WebSocketServer {

        public MyWebSocketServer(int port) {
            super(new InetSocketAddress(port));
        }

        @Override
        public void onOpen(WebSocket webSocket, ClientHandshake clientHandshake) {
            mWebSocket = webSocket;
            Log.v(TAG, "onOpen");
            setInfo("WebSocket  onOpen");

        }

        @Override
        public void onClose(WebSocket webSocket, int i, String s, boolean b) {
            Log.v(TAG, "onClose");
            setInfo("WebSocket  onClose");
        }

        @Override
        public void onMessage(WebSocket webSocket, String s) {
            Log.v(TAG, "onMessage");
            setInfo("WebSocket  onMessage: " + s);
        }

        @Override
        public void onError(WebSocket webSocket, Exception e) {
            Log.v(TAG, "onError");
            setInfo("WebSocket  onError: " + e);
        }
    }

    private Handler mHandler = new Handler();
    private void setInfo(final String message) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mInfo.setText(message);
            }
        });
    }

    public void start(View view) {
        /*
        voiceRecoder = new VoiceRecoder(new VoiceRecoder.VoiceRecoderListener() {
            @Override
            public void onVoiceData(byte[] buf) {
                if (null != os) {
                    try {
                        byte[] bytes = new byte[buf.length + 4];
                        byte[] head = intToBuffer(buf.length);
                        System.arraycopy(head, 0, bytes, 0, head.length);
                        System.arraycopy(buf, 0, bytes, head.length, buf.length);
                        os.write(bytes);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
        voiceRecoder.start();
        */
        if (null == screenCaputre) {
            prepareScreen();
        } else {
            screenCaputre.start();
        }
    }

    public void stop(View view) {
        screenCaputre.stop();
    }

    public void prepareScreen() {
        mMediaProjectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        Intent captureIntent = mMediaProjectionManager.createScreenCaptureIntent();
        startActivityForResult(captureIntent, REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode != RESULT_OK || requestCode != REQUEST_CODE) return;
        mediaProjection = mMediaProjectionManager.getMediaProjection(resultCode, data);
        if (mediaProjection == null) {
            return;
        }
        DisplayMetrics dm = getResources().getDisplayMetrics();
        screenCaputre = new ScreenCaputre(dm.widthPixels, dm.heightPixels, mediaProjection);
        screenCaputre.setScreenCaputreListener(this);
        screenCaputre.start();
    }

    @Override
    public void onImageData(byte[] buf) {
//        Log.v(TAG, "onImageData  " + buf.length + "  ------  " + os);
        if (null != os) {
            try {
                byte[] bytes = new byte[buf.length + 4];
                byte[] head = intToBuffer(buf.length);
                System.arraycopy(head, 0, bytes, 0, head.length);
                System.arraycopy(buf, 0, bytes, head.length, buf.length);
                os.write(bytes);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if (null != mWebSocket) {
            setInfo("发送H264数据成功，长度：" + buf.length);
//            Log.v(TAG, "mWebSocket  ---> 发送数据   ");
//            mWebSocket.send("服务器视频数据  " + System.currentTimeMillis());
            mWebSocket.send(buf);
        }
    }

    public static byte[] intToBuffer(int value) {
        byte[] src = new byte[4];
        src[3] = (byte) ((value>>24) & 0xFF);
        src[2] = (byte) ((value>>16) & 0xFF);
        src[1] = (byte) ((value>>8) & 0xFF);
        src[0] = (byte) (value & 0xFF);
        return src;
    }
}
