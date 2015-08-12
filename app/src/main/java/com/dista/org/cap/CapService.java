/**
 *  author dista@qq.com
 *  2015
 *
 */

package com.dista.org.cap;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
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

    public static int WIDTH = 320;
    public static int HEIGHT = 240;
    public static int DENSITY = 1;
    public static int code;
    public static Intent data = null;

    public static boolean IsRunning = false;
    public CapService() {
    }

    private boolean setUpVideoCodec(){
        try {
            mediaCodec = MediaCodec.createEncoderByType("video/avc");

            /*
            WIDTH = size.y;
            HEIGHT = size.x;
            */

            int fps = 25;

            MediaFormat mediaFormat = MediaFormat.createVideoFormat("video/avc", WIDTH, HEIGHT);
            mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, 1500000);
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
                private RtmpClient setUpRtmp(){
                    try {
                        RtmpClient rc = new RtmpClient();

                        rc.connect(new InetSocketAddress("192.168.1.111", 1935), 30000, "app/stream");
                        AVMetaData meta = new AVMetaData();
                        meta.hasAudio = false;
                        meta.hasVideo = true;
                        meta.videoMIMEType = MediaFormat.MIMETYPE_VIDEO_AVC;
                        meta.videoHeight = 480;
                        meta.videoWidth = 640;
                        meta.videoDataRate = 1000;
                        meta.videoFrameRate = 25;
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
                        return;
                    }

                    long startDts = -1;
                    long timeline = 0;
                    Flv flv = new Flv();

                    while(true) {
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

                                int nalType = NalUtil.getNalType(outData);

                                if(nalType == 7){
                                    flv.feedNal(outData, 0, 0);
                                    ds.sendAVPacket(flv.getAvcHeader());
                                } else {
                                    if(startDts == -1){
                                        // XXX: make sure dts is less than pts
                                        startDts = bi.presentationTimeUs - 100000;
                                        timeline = System.currentTimeMillis() * 1000;
                                    }

                                    long dts = startDts + ((System.currentTimeMillis() * 1000)
                                            - timeline);
                                    long pts = bi.presentationTimeUs;

                                    //Log.d("", "dts: " + dts + " pts: " + pts);

                                    flv.feedNal(outData, dts, pts);
                                    if(!flv.getPkts().isEmpty()){
                                        RtmpAVPacket pkt = flv.getPkts().remove();
                                        ds.sendAVPacket(pkt);
                                    }
                                }

                                // generate av packet

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
                }
            }).start();
        }
    }

    @Deprecated
    private void startCap(){
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

                    while(true) {
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

        if(action == "Stop" && IsRunning){
            clearMp();
        } else if(action == "Start" && !IsRunning){
            setNotification();

            startCapByRtmp();
            IsRunning = true;
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
