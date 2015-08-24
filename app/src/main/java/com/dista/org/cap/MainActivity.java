package com.dista.org.cap;

import android.app.ActionBar;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.drawable.ColorDrawable;
import android.hardware.display.VirtualDisplay;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Bundle;
import android.os.StrictMode;
import android.text.Html;
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
    private Button bt;
    private Timer timer;

    @Override
    protected void onResume() {
        super.onResume();
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
                if(CaptureService.IsRunning){
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
                if (CaptureService.data == null) {
                    MediaProjectionManager mm = (MediaProjectionManager) ac.getSystemService(Context.MEDIA_PROJECTION_SERVICE);

                    Intent it = mm.createScreenCaptureIntent();
                    ac.startActivityForResult(it, 1);
                } else if (CaptureService.IsRunning) {
                    Intent intent = new Intent(ac, CaptureService.class);
                    intent.setAction("Stop");

                    startService(intent);
                } else {
                    // not running
                    Intent intent = new Intent(ac, CaptureService.class);
                    intent.setAction("Start");

                    startService(intent);
                }
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();

        Log.d("", "onPause");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(requestCode == 1){
            if(resultCode != Activity.RESULT_OK){
                makeText(this, R.string.start_failed, Toast.LENGTH_SHORT).show();
                return;
            }

            CaptureService.code = resultCode;
            CaptureService.data = data;

            DisplayMetrics metrics = new DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(metrics);
            CaptureService.DENSITY = metrics.densityDpi;

            Display display = getWindowManager().getDefaultDisplay();
            Point size = new Point();
            display.getSize(size);
            CaptureService.WIDTH = size.x;
            CaptureService.HEIGHT = size.y;

            Intent intent = new Intent(this, CaptureService.class);
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
