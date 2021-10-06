/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings.biometrics;

import android.app.PendingIntent;
import android.content.Intent;
import android.hardware.face.FaceManager;
import android.hardware.fingerprint.FingerprintManager;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;

import com.android.settings.password.ChooseLockSettingsHelper;

/**
 * Helper for {@link BiometricEnrollActivity} when multiple sensors exist on a device.
 */
public class MultiBiometricEnrollHelper {

    private static final String TAG = "MultiBiometricEnrollHelper";

    private static final int REQUEST_FACE_ENROLL = 3000;
    private static final int REQUEST_FINGERPRINT_ENROLL = 3001;

    public static final String EXTRA_ENROLL_AFTER_FACE = "enroll_after_face";
    public static final String EXTRA_SKIP_PENDING_ENROLL = "skip_pending_enroll";

    @NonNull private final FragmentActivity mActivity;
    private final long mGkPwHandle;
    private final int mUserId;
    private final boolean mRequestEnrollFace;
    private final boolean mRequestEnrollFingerprint;

    MultiBiometricEnrollHelper(@NonNull FragmentActivity activity, int userId,
            boolean enrollFace, boolean enrollFingerprint, long gkPwHandle) {
        mActivity = activity;
        mUserId = userId;
        mGkPwHandle = gkPwHandle;
        mRequestEnrollFace = enrollFace;
        mRequestEnrollFingerprint = enrollFingerprint;
    }

    void startNextStep() {
        if (mRequestEnrollFace) {
            launchFaceEnroll();
        } else if (mRequestEnrollFingerprint) {
            launchFingerprintEnroll();
        } else {
            mActivity.setResult(BiometricEnrollIntroduction.RESULT_SKIP);
            mActivity.finish();
        }
    }

    private void launchFaceEnroll() {
        final FaceManager faceManager = mActivity.getSystemService(FaceManager.class);
        faceManager.generateChallenge(mUserId, (sensorId, userId, challenge) -> {
            final byte[] hardwareAuthToken = BiometricUtils.requestGatekeeperHat(mActivity,
                    mGkPwHandle, mUserId, challenge);
            final Intent faceIntent = BiometricUtils.getFaceIntroIntent(mActivity,
                    mActivity.getIntent());
            faceIntent.putExtra(BiometricEnrollBase.EXTRA_KEY_SENSOR_ID, sensorId);
            faceIntent.putExtra(BiometricEnrollBase.EXTRA_KEY_CHALLENGE, challenge);

            if (mRequestEnrollFingerprint) {
                // Give FaceEnroll a pendingIntent pointing to fingerprint enrollment, so that it
                // can be started when user skips or finishes face enrollment. FLAG_UPDATE_CURRENT
                // ensures it is launched with the most recent values.
                final Intent fpIntent = BiometricUtils.getFingerprintIntroIntent(mActivity,
                        mActivity.getIntent());
                fpIntent.putExtra(ChooseLockSettingsHelper.EXTRA_KEY_GK_PW_HANDLE, mGkPwHandle);
                final PendingIntent fpAfterFaceIntent = PendingIntent.getActivity(mActivity,
                        0 /* requestCode */, fpIntent,
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
                faceIntent.putExtra(EXTRA_ENROLL_AFTER_FACE, fpAfterFaceIntent);
            }

            BiometricUtils.launchEnrollForResult(mActivity, faceIntent, REQUEST_FACE_ENROLL,
                    hardwareAuthToken, mGkPwHandle, mUserId);
        });
    }

    private void launchFingerprintEnroll() {
        final FingerprintManager fingerprintManager = mActivity
                .getSystemService(FingerprintManager.class);
        fingerprintManager.generateChallenge(mUserId, ((sensorId, userId, challenge) -> {
            final byte[] hardwareAuthToken = BiometricUtils.requestGatekeeperHat(mActivity,
                    mGkPwHandle, mUserId, challenge);
            final Intent intent = BiometricUtils.getFingerprintIntroIntent(mActivity,
                    mActivity.getIntent());
            intent.putExtra(BiometricEnrollBase.EXTRA_KEY_SENSOR_ID, sensorId);
            intent.putExtra(BiometricEnrollBase.EXTRA_KEY_CHALLENGE, challenge);
            BiometricUtils.launchEnrollForResult(mActivity, intent, REQUEST_FINGERPRINT_ENROLL,
                    hardwareAuthToken, mGkPwHandle, mUserId);
        }));
    }
}
