package com.dista.org.cap;

import android.app.ActionBar;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import com.dista.org.cap.R;

public class SettingActivity extends Activity {
    private EditText ipPortView;
    private EditText pathView;
    private EditText bitrateView;
    private EditText fpsView;
    private Button saveButton;
    private CheckBox cb;
    private CheckBox landscape;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(Color.rgb(70,181,255));
        setContentView(R.layout.activity_setting);

        final SharedPreferences sp = getSharedPreferences("Cap", 0);
        String ipPort = sp.getString("ip_port", "");
        final String path = sp.getString("path", "");
        int bitrate = sp.getInt("bitrate", 1000);
        int fps = sp.getInt("fps", 15);
        boolean ignoreAudio = sp.getBoolean("ignore_audio", false);
        boolean isLandscape = sp.getBoolean("landscape", false);
        String selectAudioCodec = sp.getString("audio_codec", "AAC");
        String selectAudioSampleRate = sp.getString("audio_sample_rate", "48000");
        String selectAudioChannel = sp.getString("audio_channel", "Stereo");

        ipPortView = (EditText)findViewById(R.id.ip_port);
        ipPortView.setText(ipPort);

        pathView = (EditText)findViewById(R.id.path);
        pathView.setText(path);

        bitrateView = (EditText)findViewById(R.id.bitrate);
        bitrateView.setText(Integer.toString(bitrate));

        fpsView = (EditText)findViewById(R.id.fps);
        fpsView.setText(Integer.toString(fps));

        cb = (CheckBox)findViewById(R.id.ignore_audio);
        cb.setChecked(ignoreAudio);

        landscape = (CheckBox)findViewById(R.id.landscape);
        landscape.setChecked(isLandscape);

        Spinner audio_codec = (Spinner)findViewById(R.id.audio_codec_spinner);
        ArrayAdapter<CharSequence> ac_adapter = ArrayAdapter.createFromResource(this,
                R.array.audio_codec, android.R.layout.simple_spinner_item);
        ac_adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        audio_codec.setAdapter(ac_adapter);
        audio_codec.setSelection(ac_adapter.getPosition(selectAudioCodec));

        Spinner audio_sample_rate = (Spinner)findViewById(R.id.audio_samplerate_spinner);
        ArrayAdapter<CharSequence> as_adapter = ArrayAdapter.createFromResource(this,
                R.array.audio_sample_rate, android.R.layout.simple_spinner_item);
        as_adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        audio_sample_rate.setAdapter(as_adapter);
        audio_sample_rate.setSelection(as_adapter.getPosition(selectAudioSampleRate));

        Spinner audio_channel = (Spinner)findViewById(R.id.audio_channel_spinner);
        ArrayAdapter<CharSequence> channel_adapter = ArrayAdapter.createFromResource(this,
                R.array.audio_channel, android.R.layout.simple_spinner_item);
        channel_adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        audio_channel.setAdapter(channel_adapter);
        audio_channel.setSelection(channel_adapter.getPosition(selectAudioChannel));

        final Activity self = this;

        saveButton = (Button)findViewById(R.id.save);
        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final SharedPreferences.Editor edit = sp.edit();
                edit.putString("ip_port", String.valueOf(ipPortView.getText()));
                edit.putString("path", String.valueOf(pathView.getText()));
                edit.putInt("bitrate", Integer.valueOf(String.valueOf(bitrateView.getText())));
                edit.putInt("fps", Integer.valueOf(String.valueOf(fpsView.getText())));
                edit.putBoolean("ignore_audio", cb.isChecked());
                edit.putBoolean("landscape", landscape.isChecked());
                edit.putString("audio_codec", audio_codec.getSelectedItem().toString());
                edit.putString("audio_sample_rate", audio_sample_rate.getSelectedItem().toString());
                edit.putString("audio_channel", audio_channel.getSelectedItem().toString());
                edit.commit();

                Toast.makeText(self, "保存成功", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_setting, menu);
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
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
