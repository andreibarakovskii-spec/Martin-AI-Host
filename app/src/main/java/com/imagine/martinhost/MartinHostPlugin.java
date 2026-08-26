package com.imagine.martinhost;

import java.util.Set;

import org.godotengine.godot.Godot;
import org.godotengine.godot.plugin.GodotPlugin;
import org.godotengine.godot.plugin.SignalInfo;

/** Native -> Godot bridge for Martin's NPC performance state. */
public final class MartinHostPlugin extends GodotPlugin {
    private static final String STATE = "state_changed";
    private static final String SPEECH = "speech_level_changed";
    private static final String ACTION = "action_requested";
    private static final String LOOK = "look_changed";

    private static final SignalInfo STATE_SIGNAL = new SignalInfo(STATE, String.class);
    private static final SignalInfo SPEECH_SIGNAL = new SignalInfo(SPEECH, Float.class);
    private static final SignalInfo ACTION_SIGNAL = new SignalInfo(ACTION, String.class);
    private static final SignalInfo LOOK_SIGNAL = new SignalInfo(LOOK, Float.class, Float.class);

    public MartinHostPlugin(Godot godot) {
        super(godot);
    }

    @Override public String getPluginName() {
        return "MartinHostPlugin";
    }

    @Override public Set<SignalInfo> getPluginSignals() {
        return Set.of(STATE_SIGNAL, SPEECH_SIGNAL, ACTION_SIGNAL, LOOK_SIGNAL);
    }

    void setState(String value) {
        emitSignal(STATE, value == null ? "idle" : value);
    }

    void setSpeechLevel(float value) {
        emitSignal(SPEECH, Math.max(0f, Math.min(1f, value)));
    }

    void triggerAction(String value) {
        emitSignal(ACTION, value == null ? "" : value);
    }

    void setLook(float x, float y) {
        emitSignal(LOOK, Math.max(-1f, Math.min(1f, x)), Math.max(-1f, Math.min(1f, y)));
    }
}
