package com.dista.org.cap;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Menu;
import android.widget.TextView;

import com.dista.org.cap.util.Capabilities;

public class CapabilitiesActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(70,181,255));
        setContentView(R.layout.activity_capabilities);

        Capabilities.CodecInfo codec = Capabilities.getAvcSupportedFormatInfo();

        TextView txt = (TextView)findViewById(R.id.cap_txt);

        StringBuilder sb = new StringBuilder();
        sb.append("Video\n");
        sb.append("  AVC\n");
        sb.append("    maxHeight:");
        sb.append(codec.mMaxH);
        sb.append("\n");
        sb.append("    maxWidth:");
        sb.append(codec.mMaxW);
        sb.append("\n");
        sb.append("    maxFps:");
        sb.append(codec.mFps);
        sb.append("\n");
        sb.append("    highestLevel:");
        sb.append(codec.mHighestLevelStr);
        sb.append("\n");
        sb.append("    highestProfile:");
        sb.append(codec.mHighestProfileStr);
        sb.append("\n");
        sb.append("  Screen\n");
        sb.append("    width:");
        sb.append(Capabilities.width);
        sb.append("\n");
        sb.append("    height:");
        sb.append(Capabilities.height);
        sb.append("\n");
        sb.append("    densityDpi:");
        sb.append(Capabilities.densityDpi);
        sb.append("\n");

        txt.append(sb.toString());
    }
}