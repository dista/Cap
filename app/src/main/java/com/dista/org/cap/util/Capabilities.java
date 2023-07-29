package com.dista.org.cap.util;

import android.graphics.Point;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaCodecInfo.CodecProfileLevel;
import android.util.DisplayMetrics;
import android.view.Display;

import com.dista.org.cap.CaptureService;

public class Capabilities {
    static public int densityDpi;
    static public int width;
    static public int height;

    public static class CodecInfo {
        public int mMaxW;
        public int mMaxH;
        public int mFps;
        public int mBitRate;

        public int mHighestLevel;

        public String mHighestLevelStr;

        public int mHighestProfile;

        public String mHighestProfileStr;
    };

    private static MediaCodecInfo selectCodec(String mimeType) {
        // FIXME: select codecs based on the complete use-case, not just the mime
        MediaCodecList mcl = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
        for (MediaCodecInfo info : mcl.getCodecInfos()) {
            if (!info.isEncoder()) {
                continue;
            }
            String[] types = info.getSupportedTypes();
            for (int j = 0; j < types.length; j++) {
                if (types[j].equalsIgnoreCase(mimeType)) {
                    return info;
                }
            }
        }
        return null;
    }

    private static String avcLevelToString(int level) {
        switch (level) {
            case CodecProfileLevel.AVCLevel1:
                return "AVCLevel1";
            case CodecProfileLevel.AVCLevel11:
                return "AVCLevel11";
            case CodecProfileLevel.AVCLevel12:
                return "AVCLevel12";
            case CodecProfileLevel.AVCLevel13:
                return "AVCLevel13";
            case CodecProfileLevel.AVCLevel1b:
                return "AVCLevel1b";
            case CodecProfileLevel.AVCLevel2:
                return "AVCLevel2";
            case CodecProfileLevel.AVCLevel21:
                return "AVCLevel21";
            case CodecProfileLevel.AVCLevel3:
                return "AVCLevel3";
            case CodecProfileLevel.AVCLevel31:
                return "AVCLevel31";
            case CodecProfileLevel.AVCLevel32:
                return "AVCLevel32";
            case CodecProfileLevel.AVCLevel4:
                return "AVCLevel4";
            case CodecProfileLevel.AVCLevel41:
                return "AVCLevel41";
            case CodecProfileLevel.AVCLevel42:
                return "AVCLevel42";
            case CodecProfileLevel.AVCLevel5:
                return "AVCLevel5";
            case CodecProfileLevel.AVCLevel51:
                return "AVCLevel51";
            case CodecProfileLevel.AVCLevel52:
                return "AVCLevel52";
            case CodecProfileLevel.AVCLevel6:
                return "AVCLevel6";
            case CodecProfileLevel.AVCLevel61:
                return "AVCLevel61";
            case CodecProfileLevel.AVCLevel62:
                return "AVCLevel62";
        }

        return "UnknownLevel";
    }

    static private String avcProfileToString(int profile) {
        switch (profile) {
            case CodecProfileLevel.AVCProfileBaseline:
                return "AVCProfileBaseline";
            case CodecProfileLevel.AVCProfileConstrainedBaseline:
                return "AVCProfileConstrainedBaseline";
            case CodecProfileLevel.AVCProfileConstrainedHigh:
                return "AVCProfileConstrainedHigh";
            case CodecProfileLevel.AVCProfileExtended:
                return "AVCProfileExtended";
            case CodecProfileLevel.AVCProfileHigh:
                return "AVCProfileHigh";
            case CodecProfileLevel.AVCProfileHigh10:
                return "AVCProfileHigh10";
            case CodecProfileLevel.AVCProfileHigh422:
                return "AVCProfileHigh422";
            case CodecProfileLevel.AVCProfileHigh444:
                return "AVCProfileHigh444";
            case CodecProfileLevel.AVCProfileMain:
                return "AVCProfileMain";
        }

        return "UnknownProfile";
    }

    static private int profileOrder(int profile) {
        switch (profile) {
            case CodecProfileLevel.AVCProfileBaseline:
                return 11;
            case CodecProfileLevel.AVCProfileConstrainedBaseline:
                return 10;
            case CodecProfileLevel.AVCProfileConstrainedHigh:
                return 20;
            case CodecProfileLevel.AVCProfileExtended:
                return 0;
            case CodecProfileLevel.AVCProfileHigh:
                return 30;
            case CodecProfileLevel.AVCProfileHigh10:
                return 17;
            case CodecProfileLevel.AVCProfileHigh422:
                return 18;
            case CodecProfileLevel.AVCProfileHigh444:
                return 19;
            case CodecProfileLevel.AVCProfileMain:
                return 15;
        }

        return 0;
    }

    public static CodecInfo getAvcSupportedFormatInfo() {
        MediaCodecInfo mediaCodecInfo = selectCodec(MediaFormat.MIMETYPE_VIDEO_AVC);
        MediaCodecInfo.CodecCapabilities cap = mediaCodecInfo.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC);
        if (cap == null) { // not supported
            return null;
        }
        CodecInfo info = new CodecInfo();
        int highestLevel = 0;
        int highestProfile = 0;
        int highestOrder = -1;
        for (MediaCodecInfo.CodecProfileLevel lvl : cap.profileLevels) {
            int order = profileOrder(lvl.profile);
            if (order > highestOrder) {
                highestOrder = order;
                highestProfile = lvl.profile;
                highestLevel = 0;
            }

            if (order == highestOrder && lvl.level > highestLevel) {
                highestLevel = lvl.level;
            }
        }
        int maxW = 0;
        int maxH = 0;
        int bitRate = 0;
        int fps = 0; // frame rate for the max resolution
        switch(highestLevel) {
            // Do not support Level 1 to 2.
            case CodecProfileLevel.AVCLevel1:
            case CodecProfileLevel.AVCLevel11:
            case CodecProfileLevel.AVCLevel12:
            case CodecProfileLevel.AVCLevel13:
            case CodecProfileLevel.AVCLevel1b:
            case CodecProfileLevel.AVCLevel2:
                return null;
            case CodecProfileLevel.AVCLevel21:
                maxW = 352;
                maxH = 576;
                bitRate = 4000000;
                fps = 25;
                break;
            case CodecProfileLevel.AVCLevel22:
                maxW = 720;
                maxH = 480;
                bitRate = 4000000;
                fps = 15;
                break;
            case CodecProfileLevel.AVCLevel3:
                maxW = 720;
                maxH = 480;
                bitRate = 10000000;
                fps = 30;
                break;
            case MediaCodecInfo.CodecProfileLevel.AVCLevel31:
                maxW = 1280;
                maxH = 720;
                bitRate = 14000000;
                fps = 30;
                break;
            case MediaCodecInfo.CodecProfileLevel.AVCLevel32:
                maxW = 1280;
                maxH = 720;
                bitRate = 20000000;
                fps = 60;
                break;
            case MediaCodecInfo.CodecProfileLevel.AVCLevel4: // only try up to 1080p
            default:
                maxW = 1920;
                maxH = 1080;
                bitRate = 20000000;
                fps = 30;
                break;
        }

        info.mMaxW = maxW;
        info.mMaxH = maxH;
        info.mFps = fps;
        info.mBitRate = bitRate;
        info.mHighestLevel = highestLevel;
        info.mHighestLevelStr = avcLevelToString(highestLevel);
        info.mHighestProfile = highestProfile;
        info.mHighestProfileStr = avcProfileToString(highestProfile);
        return info;
    }
}
