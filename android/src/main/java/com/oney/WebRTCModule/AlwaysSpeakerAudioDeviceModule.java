package com.oney.WebRTCModule;

import android.content.Context;
import android.media.AudioManager;
import android.util.Log;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import org.webrtc.audio.AudioDeviceModule;
import org.webrtc.audio.JavaAudioDeviceModule;

import java.util.Set;

public class AlwaysSpeakerAudioDeviceModule implements AudioDeviceModule {
    private static final String TAG = "AlwaysSpeakerAudioDeviceModule";
    private final JavaAudioDeviceModule wrappedAdm;
    private final AudioManager audioManager;
    private final ReactApplicationContext reactContext;

    public AlwaysSpeakerAudioDeviceModule(ReactApplicationContext reactContext) {
        this.reactContext = reactContext;
        audioManager = (AudioManager) reactContext.getSystemService(Context.AUDIO_SERVICE);

        wrappedAdm = JavaAudioDeviceModule.builder(reactContext)
                .setUseHardwareAcousticEchoCanceler(true)
                .setUseHardwareNoiseSuppressor(true)
                .setAudioDeviceEventsListener(new JavaAudioDeviceModule.AudioDeviceEventsListener() {
                    @Override
                    public void onAudioDeviceChanged(
                            JavaAudioDeviceModule.AudioDevice device,
                            Set<JavaAudioDeviceModule.AudioDevice> availableDevices
                    ) {
                        // Log the device change
                        Log.d(TAG, "Audio device changed. Current device: " + device);
                        sendEventToJS("audioDeviceChanged", device.toString());

                        // forcibly route to speaker anytime a device change is attempted
                        forceSpeakerphone();
                    }

                    @Override
                    public void onWebRtcAudioRecordStart() {
                        Log.d(TAG, "WebRTC Audio Record Started");
                        sendEventToJS("audioRecordState", "started");
                    }

                    @Override
                    public void onWebRtcAudioRecordStop() {
                        Log.d(TAG, "WebRTC Audio Record Stopped");
                        sendEventToJS("audioRecordState", "stopped");
                    }
                })
                .createAudioDeviceModule();

        forceSpeakerphone();
        sendInitialAudioStateToJS();
    }

    private void sendEventToJS(String eventName, String message) {
        WritableMap params = Arguments.createMap();
        params.putString("message", message);
        
        try {
            reactContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit("AudioDeviceModuleEvent_" + eventName, params);
        } catch (Exception e) {
            Log.e(TAG, "Error sending event to JS: " + e.getMessage());
        }
    }

    private void sendInitialAudioStateToJS() {
        WritableMap params = Arguments.createMap();
        params.putBoolean("speakerphoneOn", audioManager.isSpeakerphoneOn());
        params.putInt("audioMode", audioManager.getMode());
        
        try {
            reactContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit("AudioDeviceModuleEvent_initialState", params);
        } catch (Exception e) {
            Log.e(TAG, "Error sending initial state to JS: " + e.getMessage());
        }
    }

    private void forceSpeakerphone() {
        try {
            audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
            audioManager.setSpeakerphoneOn(true);
            
            Log.d(TAG, "Forced speakerphone on. Current mode: " + audioManager.getMode());
            Log.d(TAG, "Speakerphone is on: " + audioManager.isSpeakerphoneOn());
            
            sendEventToJS("speakerphoneForced", "Speakerphone forced on");
        } catch (Exception e) {
            Log.e(TAG, "Error forcing speakerphone: " + e.getMessage());
            sendEventToJS("speakerphoneForcedError", e.getMessage());
        }
    }

    @Override
    public long getNativeAudioDeviceModulePointer() {
        return wrappedAdm.getNativeAudioDeviceModulePointer();
    }

    @Override
    public void release() {
        wrappedAdm.release();
        sendEventToJS("moduleReleased", "Audio device module released");
    }
}
