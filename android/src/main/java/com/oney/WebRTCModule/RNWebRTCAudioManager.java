package com.oney.WebRTCModule;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;
import android.util.Log;  // <--- import the logging class

public class RNWebRTCAudioManager {
    private static final String TAG = "RNWebRTCAudioManager";

    private final Context context;
    private final AudioManager audioManager;
    private boolean isSpeakerWanted = true; // Default to speaker

    public RNWebRTCAudioManager(Context context) {
        this.context = context;
        this.audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        Log.d(TAG, "RNWebRTCAudioManager constructor called");
    }

    public void start() {
        Log.d(TAG, "start() called; setting mode to MODE_IN_COMMUNICATION");
        Log.d(TAG, "Current audio mode: " + audioManager.getMode() + ", Changing to MODE_IN_COMMUNICATION");
        audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);

        // Request audio focus
        int result = audioManager.requestAudioFocus(
                focusChange -> {
                    Log.d(TAG, "Audio focus changed: " + focusChange);
                },
                AudioManager.STREAM_VOICE_CALL,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
        );
        Log.d(TAG, "audioFocus request result: " + result + " (1 = AUDIOFOCUS_REQUEST_GRANTED)");
        Log.d(TAG, "Current audio state - Speaker: " + audioManager.isSpeakerphoneOn() 
              + ", Bluetooth SCO: " + audioManager.isBluetoothScoOn()
              + ", Wired Headset: " + hasWiredHeadset());

        // Initialize audio routing
        updateAudioRoute();
        registerWiredHeadsetReceiver();
    }

    public void stop() {
        Log.d(TAG, "stop() called; reverting mode to MODE_NORMAL");
        audioManager.setMode(AudioManager.MODE_NORMAL);
        audioManager.abandonAudioFocus(null);

        try {
            context.unregisterReceiver(wiredHeadsetReceiver);
        } catch (IllegalArgumentException e) {
            // Receiver not registered
            Log.d(TAG, "wiredHeadsetReceiver was not registered");
        }
    }

    public void setSpeakerWanted(boolean wantSpeaker) {
        Log.d(TAG, "setSpeakerWanted() called with wantSpeaker=" + wantSpeaker);
        isSpeakerWanted = wantSpeaker;
        updateAudioRoute();
    }

    private void updateAudioRoute() {
        boolean hasWiredHeadset = hasWiredHeadset();
        Log.d(TAG, "updateAudioRoute() => hasWiredHeadset=" + hasWiredHeadset
                + ", isSpeakerWanted=" + isSpeakerWanted);

        if (hasWiredHeadset) {
            // If wired headset is connected, disable speaker
            Log.d(TAG, "Wired headset detected; forcing speakerphoneOff");
            audioManager.setSpeakerphoneOn(false);
        } else {
            // Otherwise use speaker based on preference
            Log.d(TAG, "No wired headset; setting speakerphoneOn=" + isSpeakerWanted);
            audioManager.setSpeakerphoneOn(isSpeakerWanted);
        }
        
        // Log final audio route state
        String audioRoute = "unknown";
        if (audioManager.isSpeakerphoneOn()) {
            audioRoute = "SPEAKER";
        } else if (hasWiredHeadset()) {
            audioRoute = "WIRED_HEADSET";
        } else {
            audioRoute = "EARPIECE";
        }
        Log.d(TAG, "Final audio route: " + audioRoute 
              + " (Speaker: " + audioManager.isSpeakerphoneOn()
              + ", Mode: " + audioManager.getMode()
              + ", Stream volume: " + audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL)
              + "/" + audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL) + ")");
    }

    private boolean hasWiredHeadset() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            boolean wired = audioManager.isWiredHeadsetOn();
            Log.d(TAG, "hasWiredHeadset() [SDK<23]: " + wired);
            return wired;
        } else {
            AudioDeviceInfo[] devices = audioManager.getDevices(AudioManager.GET_DEVICES_ALL);
            for (AudioDeviceInfo device : devices) {
                int type = device.getType();
                if (type == AudioDeviceInfo.TYPE_WIRED_HEADSET
                        || type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES) {
                    Log.d(TAG, "hasWiredHeadset() => found wired device: " + device);
                    return true;
                }
            }
            return false;
        }
    }

    private final BroadcastReceiver wiredHeadsetReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_HEADSET_PLUG.equals(intent.getAction())) {
                Log.d(TAG, "ACTION_HEADSET_PLUG broadcast received; re-checking route");
                updateAudioRoute();
            }
        }
    };

    private void registerWiredHeadsetReceiver() {
        Log.d(TAG, "registerWiredHeadsetReceiver() called");
        IntentFilter filter = new IntentFilter(Intent.ACTION_HEADSET_PLUG);
        context.registerReceiver(wiredHeadsetReceiver, filter);
    }
}
