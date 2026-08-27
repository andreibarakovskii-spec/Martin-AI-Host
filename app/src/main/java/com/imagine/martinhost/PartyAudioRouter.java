package com.imagine.martinhost;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;

/**
 * Party audio policy: keep recognition on the phone microphone and let Android
 * route MEDIA/TTS to the user's currently selected output (Bluetooth speaker,
 * wired output or phone). We deliberately do not force SCO because SCO lowers
 * playback quality and may switch the microphone away from the phone.
 */
public final class PartyAudioRouter {
    private PartyAudioRouter() {}

    public static void prepare(Context context) {
        AudioManager am=(AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
        am.setMode(AudioManager.MODE_NORMAL);
        if(Build.VERSION.SDK_INT>=31){
            try { am.clearCommunicationDevice(); } catch(Exception ignored) {}
        }
    }

    public static boolean isBluetoothOutput(Context context) {
        if(Build.VERSION.SDK_INT>=31 && context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED)
            return false;
        AudioManager am=(AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
        try {
            for(AudioDeviceInfo d:am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)){
                int t=d.getType();
                if(t==AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || t==AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                   (Build.VERSION.SDK_INT>=31 && (t==AudioDeviceInfo.TYPE_BLE_HEADSET || t==AudioDeviceInfo.TYPE_BLE_SPEAKER)))
                    return true;
            }
        }catch(SecurityException ignored){}
        return false;
    }

    /**
     * Bluetooth speakers often keep a small buffered tail after MediaPlayer/TTS reports completion.
     * Hold STT closed slightly longer there so Martin cannot transcribe his own last syllables.
     */
    public static long recommendedReleaseTailMs(Context context) {
        return isBluetoothOutput(context) ? 560L : 190L;
    }

    public static String describeOutput(Context context) {
        if(Build.VERSION.SDK_INT>=31 && context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED)
            return "Аудио: системный выход";
        AudioManager am=(AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
        try {
            for(AudioDeviceInfo d:am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)){
                int t=d.getType();
                if(t==AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || t==AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                   (Build.VERSION.SDK_INT>=31 && (t==AudioDeviceInfo.TYPE_BLE_HEADSET || t==AudioDeviceInfo.TYPE_BLE_SPEAKER))){
                    CharSequence n=d.getProductName();
                    return "Bluetooth: "+(n==null?"подключён":n.toString());
                }
            }
        }catch(SecurityException ignored){}
        return "Аудио: телефон / системный выход";
    }
}
