package com.dista.org.cap;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.dista.org.cap.media.AudioCodec;
import com.dista.org.cap.media.Recorder;

import java.io.IOException;

public class CaptureService extends Service implements Recorder.StateChange{
    private Recorder recorder;
    public static boolean IsRunning = false;

    public static int WIDTH = 320;
    public static int HEIGHT = 240;
    public static int DENSITY = 1;
    public static int code;
    public static Intent data = null;
    public static long RunningTime = 0;

    public CaptureService() {
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if(intent == null){
            return START_STICKY;
        }

        String action = intent.getAction();

        if(action == "Stop"/* && IsRunning*/){
            if(recorder != null){
                try {
                    recorder.stop();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                recorder = null;
                IsRunning = false;
            }
        } else if(action == "Start" && recorder == null){
            SharedPreferences sp = getSharedPreferences("Cap", 0);

            String ipPort = sp.getString("ip_port", "");
            String path = sp.getString("path", "");
            boolean ignoreAudio = sp.getBoolean("ignore_audio", false);
            boolean landscape = sp.getBoolean("landscape", false);
            String audioSampleRateStr = sp.getString("audio_sample_rate", "44100");
            String audioChannelStr = sp.getString("audio_channel", "Mono");
            String audioCodecStr = sp.getString("audio_codec", "AAC");

            String[] vp = ipPort.split(":");
            String host = vp[0];
            int port = 1935;

            if(vp.length > 2){
                return START_STICKY;
            }

            if(vp.length == 2){
                port = Integer.parseInt(vp[1]);
            }

            MediaProjectionManager mm = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);

            int width = WIDTH;
            int height = HEIGHT;

            if(landscape){
                width = HEIGHT;
                height = WIDTH;
            }

            AudioCodec audioCodec = AudioCodec.AAC;
            int sampleRate = Integer.parseInt(audioSampleRateStr);
            int channel = 2;

            if (audioChannelStr.equals("Mono")) {
                channel = 1;
            }

            if (audioCodecStr.equals("OPUS")) {
                audioCodec = AudioCodec.OPUS;
            }

            int fps = sp.getInt("fps", 15);
            recorder = new Recorder(width, height, DENSITY, sp.getInt("bitrate", 1000) * 1000,
                    fps, ignoreAudio, this, audioCodec, sampleRate, channel);
            recorder.setMediaProjectionManagerValues(mm, code, data);
            recorder.setRtmpParams(host, port, path);

            try {
                setNotification(startId);
                recorder.start();
                RunningTime = System.currentTimeMillis();
                IsRunning = true;
            } catch (IOException e) {
                e.printStackTrace();
                try {
                    recorder.stop();
                } catch (InterruptedException e1) {
                    e1.printStackTrace();
                }
                recorder = null;
                IsRunning = false;
                stopForeground(true);
            }
        }

        return START_STICKY;
    }

    public static String getRunningTimeDesc(){
        if(!IsRunning){
            return "00:00:00";
        } else {
            long tmp = (System.currentTimeMillis() - RunningTime) / 1000;
            return String.format("%02d:%02d:%02d", (tmp / 3600),
                    (tmp % 3600 / 60), (tmp % 3600 % 60));
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if(recorder != null){
            try {
                recorder.stop();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            recorder = null;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    private void setNotification(int startId){
        Bitmap bm = BitmapFactory.decodeResource(getResources(), R.drawable.ic_toggle_radio_button_on);

        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
                notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        NotificationChannel channel = new NotificationChannel("notification_id", "notification_name", NotificationManager.IMPORTANCE_LOW);
        notificationManager.createNotificationChannel(channel);

        Notification notification = new Notification.Builder(this)
                .setContentTitle("Cap")
                .setContentText("Streaming...")
                .setSmallIcon(R.drawable.ic_toggle_radio_button_on)
                .setLargeIcon(bm)
                .setChannelId("notification_id")
                .setContentIntent(pendingIntent)
                .setWhen(System.currentTimeMillis())
                .build();

        startForeground(startId, notification);
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void onStop() {
        recorder = null;
        IsRunning = false;
        stopForeground(true);
    }
}
