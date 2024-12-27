package com.oney.WebRTCModule;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;
import android.util.Log;

public class RNWebRTCAudioManager {
    private static final String TAG = "RNWebRTCAudioManager";

    private final Context context;
    private final AudioManager audioManager;
    private boolean isSpeakerWanted = true; // Default to speaker

    public RNWebRTCAudioManager(Context context) {
        this.context = context;
        this.audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    }

    public void start() {
        Log.d(TAG, "RNWebRTCAudioManager: Starting audio management");
        Log.d(TAG, "Current audio mode: " + audioManager.getMode());
        Log.d(TAG, "Current speaker state: " + audioManager.isSpeakerphoneOn());
        
        // Set mode for two-way communication
        audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
        Log.d(TAG, "Set audio mode to: " + audioManager.getMode());
        
        // Request audio focus
        audioManager.requestAudioFocus(
            focusChange -> {
                Log.d(TAG, "Audio focus changed: " + focusChange);
            },
            AudioManager.STREAM_VOICE_CALL,
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
        );

        // Initialize audio routing
        updateAudioRoute();
        registerWiredHeadsetReceiver();
        Log.d(TAG, "Audio management started");
    }

    public void stop() {
        Log.d(TAG, "RNWebRTCAudioManager: Stopping audio management");
        Log.d(TAG, "Current audio mode before stop: " + audioManager.getMode());
        
        // Revert to normal mode
        audioManager.setMode(AudioManager.MODE_NORMAL);
        audioManager.abandonAudioFocus(null);
        
        try {
            context.unregisterReceiver(wiredHeadsetReceiver);
            Log.d(TAG, "Unregistered headset receiver");
        } catch (IllegalArgumentException e) {
            Log.d(TAG, "Headset receiver not registered");
        }
        
        Log.d(TAG, "Audio management stopped");
    }

    public void setSpeakerWanted(boolean wantSpeaker) {
        Log.d(TAG, "Setting speaker wanted: " + wantSpeaker);
        Log.d(TAG, "Before change - Speaker state: " + audioManager.isSpeakerphoneOn());
        
        isSpeakerWanted = wantSpeaker;
        updateAudioRoute();
        
        Log.d(TAG, "After change - Speaker state: " + audioManager.isSpeakerphoneOn());
        Log.d(TAG, "Current audio mode: " + audioManager.getMode());
    }

    private void updateAudioRoute() {
        boolean hasWiredHeadset = hasWiredHeadset();
        Log.d(TAG, "Updating audio route - Wired headset present: " + hasWiredHeadset);
        Log.d(TAG, "Speaker wanted: " + isSpeakerWanted);
        
        if (hasWiredHeadset) {
            Log.d(TAG, "Using wired headset - disabling speaker");
            audioManager.setSpeakerphoneOn(false);
        } else {
            Log.d(TAG, "Setting speaker to: " + isSpeakerWanted);
            audioManager.setSpeakerphoneOn(isSpeakerWanted);
        }
        
        Log.d(TAG, "Final speaker state: " + audioManager.isSpeakerphoneOn());
    }

    private boolean hasWiredHeadset() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return audioManager.isWiredHeadsetOn();
        } else {
            AudioDeviceInfo[] devices = audioManager.getDevices(AudioManager.GET_DEVICES_ALL);
            for (AudioDeviceInfo device : devices) {
                int type = device.getType();
                if (type == AudioDeviceInfo.TYPE_WIRED_HEADSET || 
                    type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES) {
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
                updateAudioRoute();
            }
        }
    };

    private void registerWiredHeadsetReceiver() {
        IntentFilter filter = new IntentFilter(Intent.ACTION_HEADSET_PLUG);
        context.registerReceiver(wiredHeadsetReceiver, filter);
    }
}
