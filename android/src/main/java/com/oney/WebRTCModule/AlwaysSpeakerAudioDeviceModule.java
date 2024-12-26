package com.oney.WebRTCModule;

import android.content.Context;
import android.media.AudioManager;

import org.webrtc.audio.AudioDeviceModule;
import org.webrtc.audio.JavaAudioDeviceModule;

import java.util.Set;

public class AlwaysSpeakerAudioDeviceModule implements AudioDeviceModule {
    private final JavaAudioDeviceModule wrappedAdm;
    private final AudioManager audioManager;

    public AlwaysSpeakerAudioDeviceModule(Context context) {
        audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);

        wrappedAdm = JavaAudioDeviceModule.builder(context)
                .setUseHardwareAcousticEchoCanceler(true)
                .setUseHardwareNoiseSuppressor(true)
                .setAudioDeviceEventsListener(new JavaAudioDeviceModule.AudioDeviceEventsListener() {
                    @Override
                    public void onAudioDeviceChanged(
                            JavaAudioDeviceModule.AudioDevice device,
                            Set<JavaAudioDeviceModule.AudioDevice> availableDevices
                    ) {
                        // forcibly route to speaker anytime a device change is attempted
                        forceSpeakerphone();
                    }

                    @Override
                    public void onWebRtcAudioRecordStart() {}

                    @Override
                    public void onWebRtcAudioRecordStop() {}
                })
                .createAudioDeviceModule();

        forceSpeakerphone();
    }

    private void forceSpeakerphone() {
        audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
        audioManager.setSpeakerphoneOn(true);
    }

    @Override
    public long getNativeAudioDeviceModulePointer() {
        return wrappedAdm.getNativeAudioDeviceModulePointer();
    }

    @Override
    public void release() {
        wrappedAdm.release();
    }
}
