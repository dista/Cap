package com.dista.org.cap.proto;

import android.util.Log;

import com.dista.org.cap.exception.RtmpException;
import com.dista.org.cap.util.Util;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.AbstractMap;
import java.util.Map;
import java.util.Objects;

/**
 * Created by dista on 2015/8/11.
 */
public class Amf {
    private static final int AMF0_NUMBER = 0;
    private static final int AMF0_BOOLEAN = 1;
    private static final int AMF0_STRING = 2;
    private static final int AMF0_OBJECT = 3;
    private static final int AMF0_MOVIECLIP = 4;
    private static final int AMF0_NULL = 5;
    private static final int AMF0_UNDEFINED = 6;
    private static final int AMF0_REFERENCE = 7;
    private static final int AMF0_ECMA_ARRAY = 8;
    private static final int AMF0_OBJECT_END = 9;

    public static void write(OutputStream os, Object obj) throws IOException, IllegalAccessException {
        if(obj == null) {
            os.write(AMF0_NULL);
            return;
        }

        Class<?> cls = obj.getClass();

        if(cls.getSuperclass() == AbstractMap.class){
            os.write(AMF0_OBJECT);

            Map<String, Object> mp = (Map<String, Object>)obj;
            for (String k: mp.keySet()
                 ) {
                Object ob = mp.get(k);
                writeObjectKey(os, k);
                write(os, ob);
            }

            writeObjectKey(os, "");
            os.write(AMF0_OBJECT_END);

            return;
        }

        if(obj instanceof Number){
            Number nm = (Number)obj;

            os.write(AMF0_NUMBER);

            byte[] bytes = new byte[8];
            ByteBuffer.wrap(bytes).putDouble(nm.doubleValue());
            os.write(bytes);
            return;
        }

        String n = cls.getName();

        if(n.equals("java.lang.Boolean")){
            os.write(AMF0_BOOLEAN);

            Boolean b = (Boolean)obj;
            if(b.booleanValue()){
                os.write(1);
            } else {
                os.write(0);
            }
        } else if(n.equals("java.lang.String")){
            os.write(AMF0_STRING);

            String s = (String)obj;
            byte[] bytes = s.getBytes(StandardCharsets.US_ASCII);
            Util.OutStreamWriteInt(os, true, bytes.length, 2);
            os.write(bytes);
        } else {
            // Object
            Field[] fs = cls.getDeclaredFields();
            os.write(AMF0_OBJECT);

            for (Field f: fs
                 ) {
                f.setAccessible(true);

                writeObjectKey(os, f.getName());

                Object fo = f.get(obj);

                write(os, fo);
            }

            writeObjectKey(os, "");
            os.write(AMF0_OBJECT_END);
        }

        return;
    }

    public static String readString(ByteBuffer bf) throws RtmpException {
        int type = bf.get();

        if(type != AMF0_STRING){
            throw new RtmpException("wrong type, should be string");
        }

        int size = (int) Util.readLongFromByteBuffer(bf, true, 2);

        byte[] str = new byte[size];
        bf.get(str);

        return new String(str);
    }

    public static double readNumber(ByteBuffer bf) throws RtmpException {
        int type = bf.get();

        if(type != AMF0_NUMBER){
            throw new RtmpException("wrong type, should be number");
        }

        return bf.getDouble();
    }

    public static boolean readBoolean(ByteBuffer bf) throws RtmpException {
        int type = bf.get();

        if(type != AMF0_NUMBER){
            throw new RtmpException("wrong type, should be boolean");
        }

        byte b = bf.get();

        return !(b == 0);
    }

    public static String readObjectKey(ByteBuffer bf){
        int size = (int) Util.readLongFromByteBuffer(bf, true, 2);

        byte[] str = new byte[size];
        bf.get(str);

        return new String(str);
    }

    public static Object readObject(ByteBuffer bf, Class<?> clazz) throws IllegalAccessException, InstantiationException, RtmpException {
        Object ret = clazz.newInstance();

        int type = bf.get();

        if(type != AMF0_OBJECT && type != AMF0_NULL && type != AMF0_UNDEFINED &&
                type != AMF0_ECMA_ARRAY){
            throw new RtmpException("wrong type, should be object");
        }

        if(type == AMF0_NULL || type == AMF0_UNDEFINED){
            return null;
        }

        if(type == AMF0_ECMA_ARRAY){
            Util.readLongFromByteBuffer(bf, true, 2);
        }

        while(true){
            String key = readObjectKey(bf);

            Field f = null;
            try {
                f = clazz.getDeclaredField(key);
            } catch (NoSuchFieldException e) {
                // no such field, ignore it.
            }

            type = bf.get(bf.position());

            if(f == null){
                skip(bf);
            } else {
                f.setAccessible(true);

                switch (type){
                    case AMF0_BOOLEAN:
                        if(!f.getType().isPrimitive() && f.getType() != Boolean.TYPE){
                            throw new RtmpException("bad type");
                        }
                        if(f.getType() == Boolean.class || f.getType() == boolean.class) {
                            f.setBoolean(ret, readBoolean(bf));
                        }
                        break;
                    case AMF0_ECMA_ARRAY:
                        f.set(ret, readObject(bf, f.getClass()));
                        break;
                    case AMF0_OBJECT:
                        f.set(ret, readObject(bf, f.getClass()));
                        break;
                    case AMF0_NULL:
                        break;
                    case AMF0_UNDEFINED:
                        break;
                    case AMF0_NUMBER:
                        if(!f.getType().isPrimitive() && (f.getType().getSuperclass() != Number.class)){
                            throw new RtmpException("bad type");
                        }

                        Number n = (Number) readNumber(bf);

                        if(f.getType() == Integer.class || f.getType() == int.class){
                            f.setInt(ret, n.intValue());
                        } else if(f.getType() == Long.class || f.getType() == long.class){
                            f.setLong(ret, n.longValue());
                        } else if(f.getType() == Double.class || f.getType() == double.class){
                            f.setDouble(ret, n.doubleValue());
                        } else if(f.getType() == Short.class || f.getType() == short.class){
                            f.setShort(ret, n.shortValue());
                        } else if(f.getType() == Byte.class || f.getType() == byte.class){
                            f.setByte(ret, n.byteValue());
                        }

                        break;
                    case AMF0_STRING:
                        if(f.getType() != String.class){
                            throw new RtmpException("bad type");
                        }
                        f.set(ret, readString(bf));
                        break;
                }
            }

            if(type == AMF0_OBJECT_END){
                break;
            }
        }

        return ret;
    }

    public static void skipObjectLike(ByteBuffer bf) throws RtmpException {
        int type = bf.get();

        if(type != AMF0_OBJECT && type != AMF0_ECMA_ARRAY){
            throw new RtmpException("wrong type, should be object");
        }

        if(type == AMF0_ECMA_ARRAY){
            Util.readLongFromByteBuffer(bf, true, 2);
        }

        while(true) {
            readObjectKey(bf);

            type = bf.get(bf.position());

            skip(bf);

            if(type == AMF0_OBJECT_END){
                break;
            }
        }
    }

    public static void skip(ByteBuffer bf) throws RtmpException {
        int type = bf.get(bf.position());

        switch (type){
            case AMF0_NUMBER:
                readNumber(bf);
                break;
            case AMF0_BOOLEAN:
                readBoolean(bf);
                break;
            case AMF0_STRING:
                readString(bf);
                break;
            case AMF0_OBJECT:
                skipObjectLike(bf);
                break;
            case AMF0_NULL:
                bf.get();
                break;
            case AMF0_UNDEFINED:
                bf.get();
                break;
            case AMF0_ECMA_ARRAY:
                skipObjectLike(bf);
                break;
            case AMF0_OBJECT_END:
                bf.get();
                break;
            default:
                throw new RtmpException("skip, not supported");
        }
    }

    private static void writeObjectKey(OutputStream os, String name) throws IOException {
        byte[] key = name.getBytes(StandardCharsets.US_ASCII);
        Util.OutStreamWriteInt(os, true, key.length, 2);
        os.write(key);
    }
}
