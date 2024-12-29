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

    // We keep track if the user wants to be on speaker or not
    private boolean isSpeakerWanted = true;

    // We also keep track of whether we've claimed audio focus. 
    // If another request to start() comes in while we have focus, we won't re-init everything
    private boolean isAudioManagerActive = false;

    // This listener logs all onAudioFocusChange events
    private final AudioManager.OnAudioFocusChangeListener focusChangeListener =
        new AudioManager.OnAudioFocusChangeListener() {
            @Override
            public void onAudioFocusChange(int focusChange) {
                // Log the focus change event
                Log.d(TAG, "Audio focus changed: " + focusChange);

                // Possible values:
                // AUDIOFOCUS_LOSS = -1, AUDIOFOCUS_GAIN = 1,
                // AUDIOFOCUS_LOSS_TRANSIENT = -2, AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK = -3
                // The OS may also forcibly change the audio mode if it sees fit.
            }
        };

    public RNWebRTCAudioManager(Context context) {
        this.context = context;
        this.audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);

        Log.d(TAG, "RNWebRTCAudioManager constructor called");
    }

    /**
     * Acquire audio focus, switch to MODE_IN_COMMUNICATION, and register receivers.
     */
    public void start() {
        if (isAudioManagerActive) {
            Log.w(TAG, "start() called but audioManager is already active. Skipping re-init.");
            return;
        }
        isAudioManagerActive = true;

        Log.d(TAG, "start() called; attempting to set mode to MODE_IN_COMMUNICATION");
        int oldMode = audioManager.getMode();
        Log.d(TAG, "Current audio mode: " + oldMode + " (0=MODE_NORMAL, 1=MODE_RINGTONE, 2=MODE_IN_CALL, 3=MODE_IN_COMMUNICATION)");

        // Force set the mode
        audioManager.setMode(AudioManager.USAGE_MEDIA);

        // Double check after setting
        int newMode = audioManager.getMode();
        Log.d(TAG, "Audio mode after set: " + newMode + " (Expected 3=MODE_IN_COMMUNICATION)");

        // Request audio focus
        Log.d(TAG, "Requesting audio focus for STREAM_VOICE_CALL with AUDIOFOCUS_GAIN_TRANSIENT");
        int result = audioManager.requestAudioFocus(
            focusChangeListener,
            AudioManager.STREAM_VOICE_CALL,
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
        );
        Log.d(TAG, "audioFocus request result: " + result + " (1 = AUDIOFOCUS_REQUEST_GRANTED)");

        Log.d(TAG, "Current audio state => "
            + "Speaker: " + audioManager.isSpeakerphoneOn()
            + ", Bluetooth SCO: " + audioManager.isBluetoothScoOn()
            + ", Wired Headset: " + hasWiredHeadset()
            + ", Mode: " + audioManager.getMode());

        // Initialize audio routing
        updateAudioRoute();

        // Register wired headset receiver
        registerWiredHeadsetReceiver();
    }

    /**
     * Release audio focus, revert mode to MODE_NORMAL, and unregister.
     */
    public void stop() {
        if (!isAudioManagerActive) {
            Log.w(TAG, "stop() called but audioManager was not active. Skipping.");
            return;
        }
        isAudioManagerActive = false;

        Log.d(TAG, "stop() called; reverting mode to MODE_NORMAL");
        audioManager.setMode(AudioManager.MODE_NORMAL);

        Log.d(TAG, "Abandoning audio focus.");
        audioManager.abandonAudioFocus(focusChangeListener);

        try {
            context.unregisterReceiver(wiredHeadsetReceiver);
            Log.d(TAG, "Successfully unregistered wiredHeadsetReceiver");
        } catch (IllegalArgumentException e) {
            // This can happen if we never registered or it was already unregistered
            Log.d(TAG, "wiredHeadsetReceiver was not registered or already unregistered");
        }
    }

    /**
     * Call this from JS to indicate that the user wants or does not want the speaker.
     */
    public void setSpeakerWanted(boolean wantSpeaker) {
        Log.d(TAG, "setSpeakerWanted() called with wantSpeaker=" + wantSpeaker);
        this.isSpeakerWanted = wantSpeaker;
        updateAudioRoute();
    }

    /**
     * Decide whether to route audio to speaker or earpiece/wired, etc.
     */
    private void updateAudioRoute() {
        // Let's log the existing mode and speaker state thoroughly
        Log.d(TAG, "updateAudioRoute() => ENTER: speakerphoneOn=" + audioManager.isSpeakerphoneOn()
                + ", isSpeakerWanted=" + isSpeakerWanted
                + ", currentMode=" + audioManager.getMode()
                + ", hasWiredHeadset=" + hasWiredHeadset());

        // If there's a wired headset, we override the user's preference, 
        // because you normally can't output to speaker while a wired headset is plugged in.
        boolean hasWired = hasWiredHeadset();
        if (hasWired) {
            Log.d(TAG, "Wired headset detected; forcing speakerphoneOff");
            audioManager.setSpeakerphoneOn(false);
        } else {
            // Otherwise, set speakerphone on/off based on user preference
            Log.d(TAG, "No wired headset => setting speakerphoneOn=" + isSpeakerWanted);
            audioManager.setSpeakerphoneOn(isSpeakerWanted);
        }

        // Now let's log the final route we ended up with
        boolean finalSpeakerOn = audioManager.isSpeakerphoneOn();
        int finalMode = audioManager.getMode();

        String route;
        if (finalSpeakerOn) {
            route = "SPEAKER";
        } else if (hasWired) {
            route = "WIRED_HEADSET";
        } else {
            route = "EARPIECE";
        }

        Log.d(TAG, "updateAudioRoute() => EXIT: Final audio route = " + route
            + " (speakerOn=" + finalSpeakerOn
            + ", mode=" + finalMode
            + ", streamVolume="
            + audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL) + "/"
            + audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL) + ")");
    }

    /**
     * Check for a wired headset.
     */
    private boolean hasWiredHeadset() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            // Deprecated but some older OS versions rely on this
            boolean wired = audioManager.isWiredHeadsetOn();
            Log.d(TAG, "hasWiredHeadset() [SDK<23], isWiredHeadsetOn() => " + wired);
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

    /**
     * A BroadcastReceiver to handle ACTION_HEADSET_PLUG events on pre-Android O.  
     * On Android O+ it’s sometimes replaced by AudioDeviceCallback, 
     * but this is still reliable cross-version.
     */
    private final BroadcastReceiver wiredHeadsetReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context ctx, Intent intent) {
            if (Intent.ACTION_HEADSET_PLUG.equals(intent.getAction())) {
                Log.d(TAG, "ACTION_HEADSET_PLUG broadcast received; re-checking route");
                updateAudioRoute();
            }
        }
    };

    /**
     * Register for wired headset changes so that if user plugs/unplugs a headset
     * we can re-route audio accordingly.
     */
    private void registerWiredHeadsetReceiver() {
        Log.d(TAG, "registerWiredHeadsetReceiver() called");
        IntentFilter filter = new IntentFilter(Intent.ACTION_HEADSET_PLUG);
        context.registerReceiver(wiredHeadsetReceiver, filter);
    }
}
