package com.dista.org.cap;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Point;
import android.hardware.display.VirtualDisplay;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Bundle;
import android.os.StrictMode;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Surface;
import android.view.View;
import android.widget.Button;
import android.media.projection.MediaProjectionManager;
import android.widget.Toast;

import com.dista.org.cap.exception.RtmpException;
import com.dista.org.cap.media.AVMetaData;
import com.dista.org.cap.net.RtmpClient;
import com.dista.org.cap.proto.Amf;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

import static android.widget.Toast.makeText;


public class MainActivity extends Activity {

    private int code;
    private Intent data;
    private MediaProjectionManager mm;
    //private VirtualDisplay vd;
    private Button bt;
    private MediaCodec mediaCodec;
    private int WIDTH = 320;
    private int HEIGHT = 240;
    private VirtualDisplay vd2;
    private Surface sf;
    private Timer timer;

    private void setButton(){
        /*
        if(CapService.IsRunning == true){
            bt.setText("Stop Cap");
        } else {
            bt.setText("Start cap");
        }
        */
    }

    @Override
    protected void onResume() {
        super.onResume();

        setButton();

        /*
        if(CapService.mp != null) {
            SurfaceView sv = (SurfaceView) findViewById(R.id.surfaceView);

            DisplayMetrics metrics = new DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(metrics);

            CapService.mp.createVirtualDisplay("cap", sv.getWidth(), sv.getHeight(), metrics.densityDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    sv.getHolder().getSurface(), null, null
            );
        }
        */
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        timer = new Timer();
        getWindow().setStatusBarColor(Color.rgb(70,181,255));
        setContentView(R.layout.activity_main);

        final MainActivity ac = this;

        Button b = (Button)findViewById(R.id.button);
        bt = b;

        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                final String t;
                if(CapService.IsRunning){
                    t = "Stop Cap";
                } else {
                    t = "Start Cap";
                }

                if(!t.equals(bt.getText())){
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            bt.setText(t);
                        }
                    });

                }
            }
        }, 10, 10);

        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (CapService.data == null) {
                    MediaProjectionManager mm = (MediaProjectionManager) ac.getSystemService(Context.MEDIA_PROJECTION_SERVICE);
                    ac.mm = mm;

                    Intent it = mm.createScreenCaptureIntent();
                    ac.startActivityForResult(it, 1);
                } else if (CapService.IsRunning) {
                    Intent intent = new Intent(ac, CapService.class);
                    intent.setAction("Stop");

                    startService(intent);
                    //bt.setText("Start Cap");
                } else {
                    // not running
                    Intent intent = new Intent(ac, CapService.class);
                    intent.setAction("Start");

                    startService(intent);
                    //bt.setText("Stop Cap");
                }
            }
        });


        // setup test
        //setUpTest();
    }

    private RtmpClient rc = null;

    /*
    private void setUpTest(){
        Button b = (Button)findViewById(R.id.test);

        final MainActivity ac = this;

        StrictMode.ThreadPolicy policy = new
                StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(rc == null){
                    rc = new RtmpClient();
                    try {
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
                    } catch (RtmpException e) {
                        e.printStackTrace();
                        Toast.makeText(ac, "rtmp失败", Toast.LENGTH_SHORT).show();
                    } catch (InstantiationException e) {
                        e.printStackTrace();
                        Toast.makeText(ac, "rtmp失败", Toast.LENGTH_SHORT).show();
                    } catch (NoSuchFieldException e) {
                        e.printStackTrace();
                        Toast.makeText(ac, "rtmp失败", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    rc.close();
                    rc = null;
                }
            }
        });
    }
    */

    @Override
    protected void onPause() {
        super.onPause();

        Log.d("", "onPause");
    }

    private void clearVideoCodec(){
        if(mediaCodec != null){
            mediaCodec.stop();
            sf.release();
            vd2.release();
            vd2 = null;
            mediaCodec = null;
            sf = null;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        /*
        if(mp != null){
            mp.stop();
            mp = null;
        }

        clearVideoCodec();
        */
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(requestCode == 1){
            if(resultCode != Activity.RESULT_OK){
                makeText(this, R.string.start_failed, Toast.LENGTH_SHORT).show();
                return;
            }

            CapService.code = resultCode;
            CapService.data = data;

            DisplayMetrics metrics = new DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(metrics);
            CapService.DENSITY = metrics.densityDpi;

            Display display = getWindowManager().getDefaultDisplay();
            Point size = new Point();
            display.getSize(size);
            CapService.WIDTH = size.x;
            CapService.HEIGHT = size.y;

            Intent intent = new Intent(this, CapService.class);
            intent.setAction("Start");

            startService(intent);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            Intent intent = new Intent(this, SettingActivity.class);
            startActivity(intent);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
