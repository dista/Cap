package com.dista.org.cap.media;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
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
import android.provider.MediaStore;
import android.util.Log;
import android.view.Surface;

import androidx.core.app.ActivityCompat;

import com.dista.org.cap.exception.AVException;
import com.dista.org.cap.exception.RtmpException;
import com.dista.org.cap.net.RtmpClient;
import com.dista.org.cap.util.Capabilities;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;

/**
 * Created by dista on 2015/8/20.
 */
public class Recorder {
    public interface StateChange {
        void onStop();
    }

    private int width;
    private int height;
    private int density;
    private int videoBitrate;
    private boolean exit;

    private MediaProjectionManager mpMgr;
    public MediaProjection mp;
    private MediaCodec mediaCodec;
    private VirtualDisplay vd2;
    private Surface sf;
    private int code;
    private Intent data;

    // audio
    private AudioRecord aRec;
    private MediaCodec aEnc;
    private boolean ignoreAudio;

    // streaming
    private String domain;
    private int port;
    private String path;

    private Thread videoThread;
    private Thread audioThread;

    private StateChange st;

    private boolean connectingServer;
    private RtmpClient rtmpClient;

    private AudioCodec audioCodec;
    private int audioSampleRate;
    private int audioChannel;

    private class EncodedData {
        private byte[] data;
        private long pts;
        private int flag;
    }

    public Recorder(int width, int height, int density,
                    int videoBitrate, boolean ignoreAudio,
                    StateChange st,
                    AudioCodec audioCodec,
                    int audioSampleRate,
                    int audioChannel
    ) {
        this.width = width;
        this.height = height;
        this.density = density;
        this.videoBitrate = videoBitrate;
        this.ignoreAudio = ignoreAudio;
        this.st = st;
        this.audioCodec = audioCodec;
        this.audioSampleRate = audioSampleRate;
        this.audioChannel = audioChannel;
        connectingServer = false;
    }

    public void setMediaProjectionManagerValues(MediaProjectionManager mm,
                                                int code, Intent data) {
        this.mpMgr = mm;
        this.code = code;
        this.data = data;
    }

    public void setRtmpParams(String ip, int port, String path) {
        this.domain = ip;
        this.port = port;
        this.path = path;
    }

    private void clearAudioEncoder() {
        if (aRec != null) {
            aRec.release();
            aRec = null;
        }

        if (aEnc != null) {
            aEnc.release();
            aEnc = null;
        }
    }

    private void recordOneAudio() {
        int inputIdx = aEnc.dequeueInputBuffer(0);

        if (inputIdx >= 0) {
            ByteBuffer b = aEnc.getInputBuffer(inputIdx);
            b.clear();

            int size = aRec.read(b, b.limit());

            long time = System.currentTimeMillis() * 1000;
            aEnc.queueInputBuffer(inputIdx, 0, size, time, 0);
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
            if (d.data.length < 10 && d.pts == 0) {
                return null;
            }

            return d;
        }

        return null;
    }

    private void clearVideoEncoder() {
        if (mp != null) {
            mp.stop();
            mp = null;
        }

        if (mediaCodec != null) {
            try {
                mediaCodec.stop();
            } catch (IllegalStateException e) {
                // nothing
            }

            if (sf != null) {
                sf.release();
            }
            if (vd2 != null) {
                vd2.release();
            }
            vd2 = null;
            mediaCodec = null;
            sf = null;
        }
    }

    private void releaseResource() {
        clearVideoEncoder();
        clearAudioEncoder();

        st.onStop();
    }

    public void start() throws IOException {
        configureVideoEncoder();

        if (!ignoreAudio) {
            configureAudioEncoder();
        }

        // start video
        mediaCodec.start();

        if (!ignoreAudio) {
            // start audio
            aRec.startRecording();
            aEnc.start();
        }

        final long initTime = System.currentTimeMillis() * 1000;
        final long BASE_TIME = 100000;
        final Flv flv = new Flv();
        flv.SetAudioChannel(this.audioChannel);

        if (!ignoreAudio) {
            audioThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    boolean aacHeaderWritten = false;
                    long lastADts = -1;
                    while (!exit) {
                        try {
                            recordOneAudio();

                            while (!exit) {
                                EncodedData encodedData = pullAudio();

                                if (encodedData == null) {
                                    break;
                                }

                                //Log.i("", "pts is: " + encodedData.pts
                                //    + " size: " + encodedData.data.length);
                                // only feed aac after first video transmitted.
                                // sync audio and video
                                long audioDts = encodedData.pts - initTime + BASE_TIME;
                                long audioPts = audioDts;

                                if (!aacHeaderWritten && audioCodec == AudioCodec.AAC) {
                                    // AAC LC
                                    // 44100
                                    // CPE = 1
                                    int sfi = 4; // 44100
                                    if (audioSampleRate == 48000) {
                                        sfi = 3;
                                    }
                                    int cfg = audioChannel;
                                    RtmpAVPacket aacH = flv.buildAACHeaderExternalParams(2, sfi, cfg
                                            , audioDts);

                                    flv.getPkts().add(aacH);

                                    aacHeaderWritten = true;
                                }
                                //Log.i("", "pts is: " + audioDts
                                //    + " size: " + encodedData.data.length);

                                // MAY FIXME: audioDts may less than lastADts
                                if (lastADts != -1 && audioDts > lastADts) {
                                    flv.feedRaw(audioCodec, encodedData.data, audioDts, audioPts);
                                    lastADts = audioDts;
                                } else if (lastADts == -1) {
                                    lastADts = audioDts;
                                }
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            });

            audioThread.start();
        }

        videoThread = new Thread(new Runnable() {
            private RtmpClient setUpRtmp() {
                try {
                    RtmpClient rc = new RtmpClient();
                    rtmpClient = rc;

                    InetAddress address = InetAddress.getByName(domain);
                    String ip = address.getHostAddress();

                    String[] items = path.split("/");
                    if (items.length < 1) {
                        return null;
                    }

                    String tcUrl = String.format("rtmp://%s:%d/%s", domain, port, items[0]);

                    connectingServer = true;
                    rc.connect(new InetSocketAddress(ip, port), 30000, path);
                    connectingServer = false;

                    AVMetaData meta = new AVMetaData();
                    meta.hasVideo = true;
                    meta.videoMIMEType = MediaFormat.MIMETYPE_VIDEO_AVC;
                    meta.videoHeight = height;
                    meta.videoWidth = width;
                    meta.videoDataRate = videoBitrate / 1000;
                    meta.videoFrameRate = 25;
                    meta.hasAudio = !ignoreAudio;
                    meta.audioMIMEType = MediaFormat.MIMETYPE_AUDIO_AAC;

                    if (audioCodec == AudioCodec.OPUS) {
                        meta.audioMIMEType = MediaFormat.MIMETYPE_AUDIO_OPUS;
                    }

                    meta.audioChannels = audioChannel;
                    meta.audioDataRate = 64;
                    meta.audioSampleRate = audioSampleRate;
                    meta.encoder = Build.MODEL + "(" + "Android"
                            + Build.VERSION.RELEASE + ")" + "[Cap]";
                    rc.publish(meta, tcUrl);

                    return rc;
                } catch (RtmpException e) {
                    e.printStackTrace();
                } catch (InstantiationException e) {
                    e.printStackTrace();
                } catch (NoSuchFieldException e) {
                    e.printStackTrace();
                } catch (UnknownHostException e) {
                    e.printStackTrace();
                }

                connectingServer = false;

                return null;
            }

            public void run() {
                RtmpClient ds = setUpRtmp();
                if (ds == null) {
                    cleanUpOnVideoStopped();
                    return;
                }

                long startPts = -1;
                long startDts = -1;

                while (!exit) {
                    try {
                        MediaCodec.BufferInfo bi = new MediaCodec.BufferInfo();
                        int duration = 1000;
                        //Log.d("JKS", "start to dequeue");
                        int code = mediaCodec.dequeueOutputBuffer(bi, duration);
                        //Log.d("JKS", "done dequeue:" + code);

                        if (code == MediaCodec.INFO_TRY_AGAIN_LATER) {
                            //Log.d("JKS", "Try later");
                        } else if (code == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                            Log.d("", "Format changed");
                        } else if (code < 0) {
                            Log.d("", "dequeue error " + code);
                            break;
                        } else {
                            final ByteBuffer bf = mediaCodec.getOutputBuffer(code);

                            byte[] outData = new byte[bi.size];
                            bf.get(outData);

                            //Log.d("JKS", "GET BUF:" + outData.length);

                            int nalType = NalUtil.getNalType(outData);

                            if (nalType == 7) {
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
                                long dts = timeline - initTime + BASE_TIME - 10000;
                                if (startPts == -1) {
                                    // XXX: make sure dts is less than pts
                                    startPts = bi.presentationTimeUs;
                                    startDts = dts;
                                }

                                long pts = bi.presentationTimeUs - startPts + startDts + BASE_TIME;

                                //Log.d("", "dts: " + dts + " pts: " + pts);

                                flv.feedNal(outData, dts, pts);
                            }

                            // generate av packet

                            mediaCodec.releaseOutputBuffer(code, false);
                        }

                        while (!flv.getPkts().isEmpty() && !exit) {
                            RtmpAVPacket pkt = flv.getPkts().remove();
                            ds.sendAVPacket(pkt);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        break;
                    }
                }

                if (ds != null) {
                    ds.close();
                    ds = null;
                }

                cleanUpOnVideoStopped();
            }
        });

        videoThread.start();
    }

    private void cleanUpOnVideoStopped() {
        exit = true;

        if (audioThread != null) {
            try {
                audioThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        releaseResource();
    }

    private void configureVideoEncoder() throws IOException {
        if (this.mpMgr == null) {
            throw new IllegalArgumentException("mm is null");
        }

        mp = mpMgr.getMediaProjection(code, data);

        mediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);

        int fps = 15;

        MediaFormat mediaFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, this.width, this.height);

        MediaCodecInfo.CodecCapabilities cap = mediaCodec.getCodecInfo().getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC);
        MediaCodecInfo.EncoderCapabilities enCap = cap.getEncoderCapabilities();
        if (enCap.isBitrateModeSupported(MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR)) {
            mediaFormat.setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR);
        }

        Capabilities.CodecInfo codec = Capabilities.getAvcSupportedFormatInfo();

        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, this.videoBitrate);
        mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, fps);
        mediaFormat.setInteger(MediaFormat.KEY_CAPTURE_RATE, fps);
        //mediaFormat.setInteger(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, 1000000 / fps * 2);
        mediaFormat.setInteger(MediaFormat.KEY_PROFILE, codec.mHighestProfile);
        mediaFormat.setInteger(MediaFormat.KEY_LEVEL, codec.mHighestLevel);
        // WE DO NOT KNOWN IF IT REALLY WORK
        mediaFormat.setInteger(MediaFormat.KEY_LATENCY, 0);
        mediaFormat.setInteger(MediaFormat.KEY_PRIORITY, 0x00);
        // microseconds
        // mediaFormat.setInteger(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, 40000);

        /*
        int lowest = this.width > this.height ? this.height : this.width;

        if (lowest > 720) {
            encodeWidth = this.width * 720 / lowest;
            encodeHeight = this.height * 720 / lowest;
        }
        */

        mediaFormat.setInteger(MediaFormat.KEY_WIDTH, width);
        mediaFormat.setInteger(MediaFormat.KEY_HEIGHT, height);

        mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2);
        mediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);

        sf = mediaCodec.createInputSurface();

        vd2 = mp.createVirtualDisplay("CapRecorder", this.width, this.height, this.density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                sf, null, null
        );
    }

    @SuppressLint("MissingPermission")
    private boolean configureAudioEncoder() throws IOException {
        int sampleRate = audioSampleRate;
        int channelConfig = AudioFormat.CHANNEL_IN_MONO;
        if (audioChannel == 2) {
            channelConfig = AudioFormat.CHANNEL_IN_STEREO;
        }
        int audioFormat = AudioFormat.ENCODING_PCM_16BIT;

        aRec = new AudioRecord(MediaRecorder.AudioSource.MIC,
                sampleRate, channelConfig, audioFormat,
                AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat));

        // encoder
        MediaFormat fmt = new MediaFormat();

        String audioMime = MediaFormat.MIMETYPE_AUDIO_AAC;
        if (audioCodec == AudioCodec.OPUS) {
            audioMime = MediaFormat.MIMETYPE_AUDIO_OPUS;
        }

        fmt.setString(MediaFormat.KEY_MIME, audioMime);
        fmt.setInteger(MediaFormat.KEY_SAMPLE_RATE, sampleRate);
        fmt.setInteger(MediaFormat.KEY_BIT_RATE, 64000);
        fmt.setInteger(MediaFormat.KEY_CHANNEL_COUNT, audioChannel);

        if (audioCodec == AudioCodec.AAC) {
            fmt.setInteger(MediaFormat.KEY_AAC_PROFILE,
                    MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        }

        aEnc = MediaCodec.createEncoderByType(audioMime);
        aEnc.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);

        return true;
    }

    public void stop() throws InterruptedException {
        this.exit = true;

        if(connectingServer){
            this.rtmpClient.close();
        }

        this.videoThread.join();
    }
}
