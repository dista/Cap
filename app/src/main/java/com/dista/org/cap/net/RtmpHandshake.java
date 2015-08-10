package com.dista.org.cap.net;

import com.dista.org.cap.exception.RtmpException;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Random;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Created by dista on 2015/8/10.
 */
public class RtmpHandshake {
    private static byte[] GEN_FP_KEY = {
            0x47, 0x65, 0x6E, 0x75, 0x69, 0x6E, 0x65, 0x20,
            0x41, 0x64, 0x6F, 0x62, 0x65, 0x20, 0x46, 0x6C,
            0x61, 0x73, 0x68, 0x20, 0x50, 0x6C, 0x61, 0x79,
            0x65, 0x72, 0x20, 0x30, 0x30, 0x31, // Genuine Adobe Flash Player 001
            (byte)0xF0, (byte)0xEE,
            (byte)0xC2, 0x4A, (byte)0x80, 0x68, (byte)0xBE, (byte)0xE8,
            0x2E, 0x00, (byte)0xD0, (byte)0xD1, 0x02, (byte)0x9E, 0x7E, 0x57,
            0x6E, (byte)0xEC, 0x5D, 0x2D, 0x29, (byte)0x80, 0x6F, (byte)0xAB,
            (byte)0x93, (byte)0xB8, (byte)0xE6, 0x36, (byte)0xCF, (byte)0xEB, 0x31,
            (byte)0xAE
    };

    private static byte[] GEN_FMS_KEY = {
            0x47, 0x65, 0x6e, 0x75, 0x69, 0x6e, 0x65, 0x20,
            0x41, 0x64, 0x6f, 0x62, 0x65, 0x20, 0x46, 0x6c,
            0x61, 0x73, 0x68, 0x20, 0x4d, 0x65, 0x64, 0x69,
            0x61, 0x20, 0x53, 0x65, 0x72, 0x76, 0x65, 0x72,
            0x20, 0x30, 0x30, 0x31, // Genuine Adobe Flash Media Server 001
            (byte)0xf0, (byte)0xee, (byte)0xc2, 0x4a, (byte)0x80, 0x68, (byte)0xbe, (byte)0xe8,
            0x2e, 0x00, (byte)0xd0, (byte)0xd1, 0x02, (byte)0x9e, 0x7e, 0x57,
            0x6e, (byte)0xec, 0x5d, 0x2d, 0x29, (byte)0x80, 0x6f, (byte)0xab,
            (byte)0x93, (byte)0xb8, (byte)0xe6, 0x36, (byte)0xcf, (byte)0xeb, 0x31, (byte)0xae
    };


    public RtmpHandshake(){

    }

    private int getDigestOffset(byte[] buf, int offset, int schemal){
        int ret = -1;

        if(schemal == 0){
            ret = (buf[8 + offset] & 0xff) + (buf[9 + offset] & 0xff) + (buf[10 + offset] & 0xff)
                    + (buf[11 + offset] & 0xff);

            ret = ret % 728 + 12;
        } else if (schemal == 1){
            ret = (buf[772 + offset] & 0xff) + (buf[773 + offset] & 0xff) + (buf[774 + offset] & 0xff)
                    + (buf[775 + offset] & 0xff);

            ret = ret % 728 + 776;
        }

        return ret;
    }

    private byte[] hmacSHA256(byte[] key, byte[] data, int offset, int len)
            throws NoSuchAlgorithmException, InvalidKeyException {
        Mac sha256Mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secKey = new SecretKeySpec(key, "HmacSHA256");

        sha256Mac.init(secKey);
        sha256Mac.update(data, offset, len);
        return sha256Mac.doFinal();
    }

    private byte[] calDigest(byte[] buf, int base, int digestPos,
                             byte[] key) throws InvalidKeyException, NoSuchAlgorithmException {
        byte[] tmp = new byte[1536 - 32];

        System.arraycopy(buf, base, tmp, 0, digestPos);
        System.arraycopy(buf, base + digestPos + 32, tmp, digestPos, tmp.length - digestPos);

        return hmacSHA256(key, tmp, 0, tmp.length);
    }

    private boolean verifyDigest(byte[] buf, int base, int digestPos, byte[] key) throws NoSuchAlgorithmException, InvalidKeyException {
        byte[] sig = calDigest(buf, base, digestPos, key);

        byte[] old = Arrays.copyOfRange(buf, base + digestPos, base + digestPos + sig.length);
        return java.util.Arrays.equals(sig, old);
    }

    public byte[] genHandshake1() throws RtmpException {
        byte[] ret = new byte[1537];

        ret[0] = 3;
        ret[5] = 11;
        ret[6] = 8;
        ret[7] = 0;
        ret[8] = 87;

        Random rd = new Random();
        for(int i = 9; i < ret.length; i++){
            ret[i] = (byte)rd.nextInt(255);
        }

        int base = 1;
        int digestPos = getDigestOffset(ret, base, 0);
        byte[] key = Arrays.copyOfRange(GEN_FP_KEY, 0, 30);
        try {
            byte[] digest = calDigest(ret, base, digestPos, key);
            System.arraycopy(digest, 0, ret, base + digestPos, digest.length);

        } catch (InvalidKeyException e) {
            throw new RtmpException("calDigest error", e);
        } catch (NoSuchAlgorithmException e) {
            throw new RtmpException("calDigest error", e);
        }

        return ret;
    }

    public byte[] genHandshake2(byte[] input) throws RtmpException {
        int base = 1;
        int digestPos = getDigestOffset(input, base, 0);

        try {
            byte[] fmsKey = Arrays.copyOfRange(GEN_FMS_KEY, 0, 36);
            if(!verifyDigest(input, base, digestPos, fmsKey)){
                digestPos = getDigestOffset(input, base, 1);

                if(!verifyDigest(input, base, digestPos, fmsKey)){
                    throw new RtmpException("genHandshake2 bad input");
                }
            }

            byte[] ret = new byte[1536];
            byte[] digestKey = hmacSHA256(GEN_FP_KEY, input, base + digestPos, 32);
            byte[] digest = hmacSHA256(GEN_FP_KEY, ret, 0, ret.length - 32);
            System.arraycopy(digest, 0, ret, ret.length - 32, digest.length);

            return ret;
        } catch (NoSuchAlgorithmException e) {
            throw new RtmpException("genHandshake2 verify Digest", e);
        } catch (InvalidKeyException e) {
            throw new RtmpException("genHandshake2 verify Digest", e);
        }
    }
}
