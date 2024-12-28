package com.oney.WebRTCModule;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;

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
        // Set mode for two-way communication
        audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);

        // Request audio focus
        audioManager.requestAudioFocus(
            focusChange -> {},
            AudioManager.STREAM_VOICE_CALL,
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
        );

        // Initialize audio routing
        updateAudioRoute();
        registerWiredHeadsetReceiver();
    }

    public void stop() {
        // Revert to normal mode
        audioManager.setMode(AudioManager.MODE_NORMAL);
        audioManager.abandonAudioFocus(null);
        
        try {
            context.unregisterReceiver(wiredHeadsetReceiver);
        } catch (IllegalArgumentException e) {
            // Receiver not registered
        }
    }

    public void setSpeakerWanted(boolean wantSpeaker) {
        isSpeakerWanted = wantSpeaker;
        updateAudioRoute();
    }

    private void updateAudioRoute() {
        boolean hasWiredHeadset = hasWiredHeadset();
        if (hasWiredHeadset) {
            // If wired headset is connected, disable speaker
            audioManager.setSpeakerphoneOn(false);
        } else {
            // Otherwise use speaker based on preference
            audioManager.setSpeakerphoneOn(isSpeakerWanted);
        }
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
