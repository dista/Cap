package com.dista.org.cap.proto;

/**
 * Created by dista on 2015/8/11.
 */
public class ConnectResultObj2 {
    private String level;

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getObjectEncoding() {
        return objectEncoding;
    }

    public void setObjectEncoding(int objectEncoding) {
        this.objectEncoding = objectEncoding;
    }

    private String code;
    private String description;
    private int objectEncoding;
}
