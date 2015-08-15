/**
 *  author dista@qq.com
 *  2015
 *
 *  TODO: this is ugly, refactor it from service
 *
 */

package com.dista.org.cap;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.SurfaceView;

import com.dista.org.cap.exception.AVException;
import com.dista.org.cap.exception.RtmpException;
import com.dista.org.cap.media.AVMetaData;
import com.dista.org.cap.media.Flv;
import com.dista.org.cap.media.NalUtil;
import com.dista.org.cap.media.RtmpAVPacket;
import com.dista.org.cap.net.RtmpClient;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class CapService extends Service {
    private MediaProjectionManager mm;
    public static MediaProjection mp;
    private MediaCodec mediaCodec;
    private VirtualDisplay vd2;
    private Surface sf;

    // audio
    private AudioRecord aRec;
    private MediaCodec aEnc;

    public static int WIDTH = 320;
    public static int HEIGHT = 240;
    public static int DENSITY = 1;
    public static int code;
    public static Intent data = null;

    public static boolean IsRunning = false;
    private static boolean Exit = false;
    private boolean HAS_AUDIO = true;

    public CapService() {
    }

    private class EncodedData {
        private byte[] data;
        private long pts;
        private int flag;
    }

    private boolean setUpAudioEncoder() throws IOException {
        int sampleRate = 44100;
        int channelConfig = AudioFormat.CHANNEL_IN_MONO;
        int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
        aRec = new AudioRecord(MediaRecorder.AudioSource.MIC,
                sampleRate, channelConfig, audioFormat,
                AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat));

        // encoder
        // mono
        MediaFormat fmt = new MediaFormat();
        fmt.setString(MediaFormat.KEY_MIME, MediaFormat.MIMETYPE_AUDIO_AAC);
        fmt.setInteger(MediaFormat.KEY_SAMPLE_RATE, 44100);
        fmt.setInteger(MediaFormat.KEY_BIT_RATE, 64000);
        fmt.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1);
        fmt.setInteger(MediaFormat.KEY_AAC_PROFILE,
                MediaCodecInfo.CodecProfileLevel.AACObjectLC);

        aEnc = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
        aEnc.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);

        return true;
    }

    private void startAudioEncoder(){
        aRec.startRecording();
        aEnc.start();
    }

    private void clearAudioEncoder(){
        if(aRec != null){
            aRec.release();
            aRec = null;
        }

        if(aEnc != null){
            aEnc.release();
            aEnc = null;
        }
    }

    private void recordOneAudio(){
        int inputIdx = aEnc.dequeueInputBuffer(0);

        if(inputIdx >= 0){
            ByteBuffer b = aEnc.getInputBuffer(inputIdx);
            b.clear();

            int size = aRec.read(b, b.limit());
            //Log.i("", "size: " + size);

            long time = System.currentTimeMillis() * 1000;
            aEnc.queueInputBuffer(inputIdx, 0, size, time, 0);
        }
    }

    private void recordAudioAsPossible(){
        int inputIdx = aEnc.dequeueInputBuffer(0);

        while(inputIdx != MediaCodec.INFO_TRY_AGAIN_LATER){
            ByteBuffer b = aEnc.getInputBuffer(inputIdx);
            b.clear();

            int size = aRec.read(b, b.limit());
            Log.d("", "");

            long time = System.currentTimeMillis() * 1000;
            aEnc.queueInputBuffer(inputIdx, 0, size, time, 0);

            inputIdx = aEnc.dequeueInputBuffer(0);
        }
    }

    private EncodedData pullAudio() throws AVException {
        MediaCodec.BufferInfo bi = new MediaCodec.BufferInfo();
        int code = aEnc.dequeueOutputBuffer(bi, 0);

        if (code == MediaCodec.INFO_TRY_AGAIN_LATER) {
            //Log.i("", "Try later xxx");
        } else if (code == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            Log.i("", "Format changed");
        } else if (code < 0) {
            Log.i("", "dequeue error " + code);
            throw new AVException("DEQUE ERROR");
        } else {
            //Log.d("", String.format("%d: pts %d, offset %d, size %d" +
            //        "", System.currentTimeMillis(), bi.presentationTimeUs, bi.offset, bi.size));

            ByteBuffer bf = aEnc.getOutputBuffer(code);

            byte[] outData = new byte[bi.size];
            bf.get(outData);

            aEnc.releaseOutputBuffer(code, false);

            EncodedData d = new EncodedData();
            d.data = outData;
            d.pts = bi.presentationTimeUs;

            //Log.i("", "pull one " + d.pts);

            // may FIXME: we get first frame as length = 2 and pts = 0,
            // but I do not know what it is now. Simply skip it first.
            if(d.data.length < 10 && d.pts == 0){
                return null;
            }

            return d;
        }

        return null;
    }

    private boolean setUpVideoCodec(){
        try {
            mediaCodec = MediaCodec.createEncoderByType("video/avc");

            /*
            WIDTH = size.y;
            HEIGHT = size.x;
            */

            int fps = 25;

            SharedPreferences sp = getSharedPreferences("Cap", 0);

            MediaFormat mediaFormat = MediaFormat.createVideoFormat("video/avc", WIDTH, HEIGHT);
            mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, sp.getInt("bitrate", 1000) * 1000);
            mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, fps);
            mediaFormat.setInteger(MediaFormat.KEY_CAPTURE_RATE, fps);
            mediaFormat.setInteger(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, 1000000 / fps);

            //MediaCodecInfo ci = mediaCodec.getCodecInfo();
            //final MediaCodecInfo.CodecCapabilities capabilitiesForType = ci.getCapabilitiesForType("video/avc");
            mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 10);
            mediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }

    private void startCapByRtmp(){
        Exit = false;
        if(mm == null){
            mm = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        }

        mp = mm.getMediaProjection(code, data);

        /*
        vd = mp.createVirtualDisplay("cap", sv.getWidth(), sv.getHeight(), metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                sv.getHolder().getSurface(), null, null
        );
        */

        Log.d("", "Start Capture screen");

        try {
            setUpAudioEncoder();
        } catch (Exception e) {
            e.printStackTrace();
            clearMp();
            IsRunning = false;
            return;
        }

        if (setUpVideoCodec()) {
            sf = mediaCodec.createInputSurface();

            vd2 = mp.createVirtualDisplay("Xcap", WIDTH, HEIGHT, DENSITY,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    sf, null, null
            );

            mediaCodec.start();
            startAudioEncoder();

            final long initTime = System.currentTimeMillis() * 1000;
            final Flv flv = new Flv();

            final Thread ah = new Thread(new Runnable() {
                @Override
                public void run() {
                    boolean aacHeaderWritten = false;
                    long lastADts = -1;
                    while(!Exit){
                        try {
                            recordOneAudio();

                            while(!Exit) {
                                EncodedData encodedData = pullAudio();

                                if(encodedData == null) {
                                    break;
                                }

                                //Log.i("", "pts is: " + encodedData.pts
                                //    + " size: " + encodedData.data.length);
                                    // only feed aac after first video transmitted.
                                    // sync audio and video
                                long audioDts = encodedData.pts - initTime;
                                long audioPts = audioDts;

                                if(!aacHeaderWritten){
                                    // AAC LC
                                    // 44100
                                    // CPE = 1
                                    RtmpAVPacket aacH = flv.buildAACHeaderExternalParams(2, 4, 1
                                            , audioDts);

                                    flv.getPkts().add(aacH);

                                    aacHeaderWritten = true;
                                }
                                //Log.i("", "pts is: " + audioDts
                                //    + " size: " + encodedData.data.length);

                                // MAY FIXME: audioDts may less than lastADts
                                if(lastADts != -1 && audioDts > lastADts) {
                                    flv.feedRawAAC(encodedData.data, audioDts, audioPts);
                                    lastADts = audioDts;
                                } else if(lastADts == -1){
                                    lastADts = audioDts;
                                }
                            }
                        }catch (Exception e){
                            e.printStackTrace();
                        }
                    }
                }
            });

            ah.start();

            new Thread(new Runnable() {
                private RtmpClient setUpRtmp(){
                    try {
                        RtmpClient rc = new RtmpClient();

                        SharedPreferences sp = getSharedPreferences("Cap", 0);
                        String ipPort = sp.getString("ip_port", "");
                        String path = sp.getString("path", "");

                        String[] vp = ipPort.split(":");
                        String host = vp[0];
                        int port = 1935;

                        if(vp.length > 2){
                            rc.close();
                            return null;
                        }

                        if(vp.length == 2){
                            port = Integer.parseInt(vp[1]);
                        }

                        rc.connect(new InetSocketAddress(host, port), 30000, path);
                        AVMetaData meta = new AVMetaData();
                        meta.hasVideo = true;
                        meta.videoMIMEType = MediaFormat.MIMETYPE_VIDEO_AVC;
                        meta.videoHeight = 480;
                        meta.videoWidth = 640;
                        meta.videoDataRate = 1000;
                        meta.videoFrameRate = 25;
                        meta.hasAudio = HAS_AUDIO;
                        meta.audioMIMEType = MediaFormat.MIMETYPE_AUDIO_AAC;
                        meta.audioChannels = 1;
                        meta.audioDataRate = 64;
                        meta.audioSampleRate = 44100;
                        meta.encoder = Build.MODEL + "(" + "Android"
                                + Build.VERSION.RELEASE + ")" + "[Cap]";
                        rc.publish(meta);

                        return rc;
                    } catch (RtmpException e) {
                        e.printStackTrace();
                    } catch (InstantiationException e) {
                        e.printStackTrace();
                    } catch (NoSuchFieldException e) {
                        e.printStackTrace();
                    }

                    return null;
                }

                public void run() {
                    RtmpClient ds = setUpRtmp();
                    if(ds == null){
                        clearMp();
                        stopForeground(true);
                        IsRunning = false;
                        return;
                    }

                    long startPts = -1;
                    long startDts = -1;

                    while(!Exit) {
                        try {
                            MediaCodec.BufferInfo bi = new MediaCodec.BufferInfo();
                            int duration = 1000;
                            int code = mediaCodec.dequeueOutputBuffer(bi, duration);

                            if (code == MediaCodec.INFO_TRY_AGAIN_LATER) {
                                //Log.d("", "Try later");
                            } else if (code == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                                Log.d("", "Format changed");
                            } else if (code < 0) {
                                Log.d("", "dequeue error " + code);
                                break;
                            } else {
                                final ByteBuffer bf = mediaCodec.getOutputBuffer(code);

                                byte[] outData = new byte[bi.size];
                                bf.get(outData);

                                int nalType = NalUtil.getNalType(outData);

                                if(nalType == 7){
                                    flv.feedNal(outData, 0, 0);
                                    ds.sendAVPacket(flv.getAvcHeader());

                                    /*
                                    if(HAS_AUDIO){
                                        // AAC LC
                                        // 44100
                                        // CPE = 1
                                        ds.sendAVPacket(flv.buildAACHeaderExternalParams(2, 4, 1
                                                , 0));
                                    }
                                    */
                                } else {
                                    long timeline = System.currentTimeMillis() * 1000;
                                    long dts = timeline - initTime;
                                    if(startPts == -1){
                                        // XXX: make sure dts is less than pts
                                        startPts = bi.presentationTimeUs;
                                        startDts = dts;
                                    }

                                    long pts = bi.presentationTimeUs - startPts + startDts + 10000;

                                    //Log.d("", "dts: " + dts + " pts: " + pts);

                                    flv.feedNal(outData, dts, pts);
                                    while (!flv.getPkts().isEmpty() && !Exit) {
                                        RtmpAVPacket pkt = flv.getPkts().remove();
                                        ds.sendAVPacket(pkt);
                                    }
                                }

                                // generate av packet

                                mediaCodec.releaseOutputBuffer(code, false);
                            }
                        } catch (Exception e){
                            e.printStackTrace();
                            break;
                        }
                    }

                    if(ds != null){
                        ds.close();
                        ds = null;
                    }

                    if(ah != null){
                        Exit = true;
                        try {
                            ah.join();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }

                        Exit = false;
                    }

                    clearMp();
                    stopForeground(true);
                    IsRunning = false;
                    Exit = false;
                }
            }).start();
        }
    }

    @Deprecated
    private void startCap(){
        Exit = false;
        if(mm == null){
            mm = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        }

        mp = mm.getMediaProjection(code, data);

        /*
        vd = mp.createVirtualDisplay("cap", sv.getWidth(), sv.getHeight(), metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                sv.getHolder().getSurface(), null, null
        );
        */

        Log.d("", "Start Capture screen");

        if(setUpVideoCodec()){
            sf = mediaCodec.createInputSurface();

            vd2 = mp.createVirtualDisplay("Xcap", WIDTH, HEIGHT, DENSITY,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    sf, null, null
            );

            mediaCodec.start();

            new Thread(new Runnable() {
                private DatagramSocket setUpUdp(){
                    try {
                        DatagramSocket ds = new DatagramSocket();

                        ds.connect(Inet4Address.getByName("192.168.1.111"), 12345);

                        return ds;
                    } catch (SocketException e) {
                        e.printStackTrace();
                    } catch (UnknownHostException e) {
                        e.printStackTrace();
                    }

                    return null;
                }

                public void run() {
                    DatagramSocket ds = setUpUdp();
                    if(ds == null){
                        return;
                    }

                    while(!Exit) {
                        try {
                            MediaCodec.BufferInfo bi = new MediaCodec.BufferInfo();
                            int duration = 10000;
                            int code = mediaCodec.dequeueOutputBuffer(bi, duration);

                            if (code == MediaCodec.INFO_TRY_AGAIN_LATER) {
                                //Log.d("", "Try later");
                            } else if (code == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                                Log.d("", "Format changed");
                            } else if (code < 0) {
                                Log.d("", "dequeue error " + code);
                                break;
                            } else {
                                ByteBuffer bf = mediaCodec.getOutputBuffer(code);

                                byte[] outData = new byte[bi.size];
                                bf.get(outData);

                                byte[] ts = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(bi.presentationTimeUs).array();

                                String w = "";
                                byte px = 0;
                                for(int i = 0; i < ts.length; i++){
                                    if(ts[i] == 0){
                                        px |= (1 << i);
                                        ts[i] = (byte)0xFF;
                                    }
                                    w += String.format(" %02x", ts[i]);
                                }

                                //Log.d("", "TS: " + w);

                                if((outData[4] & 0x1F) == 5 || (outData[4] & 0x1F) == 1) {
                                    byte[] newData = new byte[outData.length + ts.length + 1];
                                    System.arraycopy(outData, 0, newData, 0, outData.length);
                                    newData[outData.length] = px;
                                    System.arraycopy(ts, 0, newData, outData.length + 1, ts.length);

                                    outData = newData;
                                }

                                //Log.d("", "frame: size is " + bf.limit() + " bi: " + bi.size + " time: " + bi.presentationTimeUs);

                                String p = "";
                                for(int i = 0; i < 10; i++){
                                    p += String.format(" %02x", outData[i]);
                                }
                                //Log.d("", ":: " + p);

                                if(outData.length > 4096) {
                                    int offset = 0;
                                    while(offset < outData.length){
                                        int chunkSize = 4096;
                                        if(outData.length - offset < chunkSize){
                                            chunkSize = outData.length - offset;
                                        }
                                        ds.send(new DatagramPacket(outData, offset, chunkSize));

                                        offset += chunkSize;
                                    }
                                } else {
                                    ds.send(new DatagramPacket(outData, outData.length));
                                }

                                mediaCodec.releaseOutputBuffer(code, false);
                            }
                        } catch (Exception e){
                            Log.d("", "Exception: " + e.toString());
                            break;
                        }
                    }

                    if(ds != null){
                        ds.close();
                        ds = null;
                    }

                    clearMp();
                    stopForeground(true);
                    IsRunning = false;
                    Exit = false;
                }
            }).start();
        }
    }

    private void clearMp(){
        if(mp != null) {
            mp.stop();
            mp = null;
        }
        clearVideoCodec();
        clearAudioEncoder();
    }

    @Override
    public void onCreate() {
        super.onCreate();

        IsRunning = false;
        Log.d("", "CapService created");
    }

    private void clearVideoCodec(){
        if(mediaCodec != null){
            try {
                mediaCodec.stop();
            } catch (IllegalStateException e){
                // nothing
            }

            if(sf != null) {
                sf.release();
            }
            if(vd2 != null) {
                vd2.release();
            }
            vd2 = null;
            mediaCodec = null;
            sf = null;
        }
    }

    private void setNotification(){
        Bitmap bm = BitmapFactory.decodeResource(getResources(), R.drawable.ic_toggle_radio_button_on);

        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);

        Notification notification = new Notification.Builder(this)
                .setContentTitle("Cap")
                .setContentText("Streaming...")
                .setSmallIcon(R.drawable.ic_toggle_radio_button_on)
                .setLargeIcon(bm)
                .setContentIntent(pendingIntent)
                .build();

        /*
        Notification notification = new Notification(R.mipmap.ic_launcher, "CapX",
                System.currentTimeMillis());
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
        notification.setLatestEventInfo(this, "Cap",
                ":: Recoding and streaming to trochilus ::", pendingIntent);
        */
        startForeground(222, notification);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if(intent == null){
            return START_STICKY;
        }

        String action = intent.getAction();

        if(action == "Stop"/* && IsRunning*/){
            Exit = true;
        } else if(action == "Start" && !IsRunning){
            setNotification();

            IsRunning = true;
            startCapByRtmp();
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if(IsRunning){
            stopForeground(true);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
