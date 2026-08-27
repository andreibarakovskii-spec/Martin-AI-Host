package com.imagine.martinhost;

public interface AvatarBackend {
    void setState(AvatarState state);
    void setLipSync(float value);
    void setLook(float x, float y);
    boolean isLive2D();
    String backendName();
}
