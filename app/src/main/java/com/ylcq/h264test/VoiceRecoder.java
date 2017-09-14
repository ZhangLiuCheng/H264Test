package com.ylcq.h264test;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Date;

import static android.media.MediaFormat.KEY_BIT_RATE;
import static android.media.MediaFormat.KEY_MAX_INPUT_SIZE;

public class VoiceRecoder {

    private static final String TAG = VoiceRecoder.class.getSimpleName();

    private int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
    private int audioChannel = AudioFormat.CHANNEL_CONFIGURATION_STEREO;

    private MediaCodec.BufferInfo aBufferInfo = new MediaCodec.BufferInfo();
    private MediaCodec aEncoder;

    private Thread audioEncoderThread;
    private boolean audioEncoderLoop;

    private AudioRecord mAudioRecord;
    private byte[] buffer;

    private int mSampleRate;
    private int mChannelConfig;
    private long presentationTimeUs;


    public interface VoiceRecoderListener {
        void onVoiceData(byte[] buf);
    }

    private VoiceRecoderListener voiceRecoderListener;

    public VoiceRecoder(VoiceRecoderListener voiceRecoderListener) {
        this.voiceRecoderListener = voiceRecoderListener;
    }

    public void start() {
        try {
            prepareAudio();
            prepareAudioEncoder();
            startAudio();
        } catch (Exception ex) {
            Log.v(TAG, "start failure" + ex);
        }
    }

    private void prepareAudioEncoder() throws IOException {
        MediaCodec aencoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
        MediaFormat format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC,
                mSampleRate, mChannelConfig);
        format.setInteger(KEY_MAX_INPUT_SIZE, 100 * 1024);
        format.setInteger(KEY_BIT_RATE, mSampleRate * mChannelConfig);
        aencoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        aEncoder = aencoder;
    }

    private void prepareAudio() {
//        int[] sampleRates = {44100, 22050, 16000, 11025};
//        for (int sampleRate : sampleRates) {
        int sampleRate = 44100;
            //编码制式
            int audioFormat = this.audioFormat;
            // stereo 立体声，
            int channelConfig = this.audioChannel;
            int buffsize = 2 * AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat);
            mAudioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, channelConfig,
                    audioFormat, buffsize);
//            if (mAudioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
//                continue;
//            }
            mSampleRate = sampleRate;
        mChannelConfig = (channelConfig == AudioFormat.CHANNEL_CONFIGURATION_STEREO ? 2 : 1);
            this.buffer = new byte[Math.min(4096, buffsize)];
//        }
    }

    private void startAudio() {
        audioEncoderThread = new Thread() {
            @Override
            public void run() {
                aEncoder.start();
                mAudioRecord.startRecording();
                presentationTimeUs = System.currentTimeMillis() * 1000;
                while (audioEncoderLoop && !Thread.interrupted()) {
                    int size = mAudioRecord.read(buffer, 0, buffer.length);
                    if (size < 0) {
                        Log.i(TAG, "audio ignore ,no data to read");
                        break;
                    }
                    if (audioEncoderLoop) {
                        byte[] audio = new byte[size];
                        System.arraycopy(buffer, 0, audio, 0, size);
                        encodeAudioData(audio);
                    }
                }

            }
        };

        audioEncoderLoop = true;
        audioEncoderThread.start();
    }

    // 音频解码
    private void encodeAudioData(byte[] data) {
        ByteBuffer[] inputBuffers = aEncoder.getInputBuffers();
        ByteBuffer[] outputBuffers = aEncoder.getOutputBuffers();
        int inputBufferId = aEncoder.dequeueInputBuffer(-1);
        if (inputBufferId >= 0) {
            ByteBuffer bb = inputBuffers[inputBufferId];
            bb.clear();
            bb.put(data, 0, data.length);
            long pts = new Date().getTime() * 1000 - presentationTimeUs;
            aEncoder.queueInputBuffer(inputBufferId, 0, data.length, pts, 0);
        }

        int outputBufferId = aEncoder.dequeueOutputBuffer(aBufferInfo, 0);
        if (outputBufferId >= 0) {
            // outputBuffers[outputBufferId] is ready to be processed or rendered.
            ByteBuffer bb = outputBuffers[outputBufferId];

            if (aBufferInfo.size == 2) {
                // 我打印发现，这里应该已经是吧关键帧计算好了，所以我们直接发送
                final byte[] bytes = new byte[2];
                bb.get(bytes);
//                mRtmpPublisher.sendAacSpec(bytes, 2);
                voiceRecoderListener.onVoiceData(bytes);

                Log.d(TAG, "解析得到 音频 head:" + Arrays.toString(bytes));

            } else {
                final byte[] bytes = new byte[aBufferInfo.size];
                bb.get(bytes);
//                Log.d(TAG, "音频:" + Arrays.toString(bytes));

//                mRtmpPublisher.sendAacData(bytes, bytes.length, aBufferInfo.presentationTimeUs / 1000);
                voiceRecoderListener.onVoiceData(bytes);
//                Log.d(TAG, "解析得到 音频  数据:" + Arrays.toString(bytes));

            }
            aEncoder.releaseOutputBuffer(outputBufferId, false);
        }
    }
}
