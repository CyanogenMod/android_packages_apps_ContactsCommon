/*
 * Copyright (C) 2015 The CyanogenMod Project
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

package com.android.contacts.common.util;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

public class PickupGestureDetector implements SensorEventListener {
    public interface PickupListener {
        void onPickup();
    }

    private boolean mEnabled;
    private int mYOrientation = 0;
    private int mProximityValue = 0;
    private boolean mProximityChanged = true;
    private boolean mProximityInitialized = false;
    private float[] mGravity;
    private float[] mGeomagnetic;

    private final SensorManager mSensorManager;
    private final Sensor mProximitySensor;
    private final Sensor mAccelerometer;
    private final Sensor mMagneticSensor;
    private final PickupListener mListener;
    private final float mMaxProximityValue;

    public PickupGestureDetector(Context context, PickupListener listener) {
        mSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mMagneticSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        mProximitySensor = mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        mMaxProximityValue = mProximitySensor != null ? mProximitySensor.getMaximumRange() : 0;
        mEnabled = false;
        mListener = listener;
    }

    public void enable() {
        if (!mEnabled && mProximitySensor != null
                && mAccelerometer != null && mMagneticSensor != null) {
            mSensorManager.registerListener(this, mProximitySensor,
                    SensorManager.SENSOR_DELAY_NORMAL);
            mSensorManager.registerListener(this, mAccelerometer,
                    SensorManager.SENSOR_DELAY_NORMAL);
            mSensorManager.registerListener(this, mMagneticSensor,
                    SensorManager.SENSOR_DELAY_NORMAL);
            mEnabled = true;
            mYOrientation = 0;
            mProximityValue = 0;
            mProximityChanged = true;
            mProximityInitialized = false;
        }
    }

    public void disable() {
        if (mEnabled) {
            mSensorManager.unregisterListener(this, mProximitySensor);
            mSensorManager.unregisterListener(this, mAccelerometer);
            mSensorManager.unregisterListener(this, mMagneticSensor);
            mEnabled = false;
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.equals(mProximitySensor)) {
            int currentProx = (int) event.values[0];
            if (mProximityChanged) {
                mProximityValue = currentProx;
                mProximityChanged = false;
            } else if (mProximityValue > 0 && currentProx <= mMaxProximityValue) {
                mProximityInitialized = true;
            }
            mProximityValue = currentProx;
        } else if (event.sensor.equals(mAccelerometer)) {
            mGravity = event.values;
            recalcOrientation();
        } else if (event.sensor.equals(mMagneticSensor)) {
            mGeomagnetic = event.values;
            recalcOrientation();
        }

        if (rightOrientation(mYOrientation) && mProximityValue <= mMaxProximityValue
                && mProximityInitialized) {
            mListener.onPickup();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    private void recalcOrientation() {
        if (mGravity == null || mGeomagnetic == null) {
            return;
        }

        float R[] = new float[9];
        float I[] = new float[9];
        if (SensorManager.getRotationMatrix(R, I, mGravity, mGeomagnetic)) {
            float orientation[] = new float[5];
            SensorManager.getOrientation(R, orientation);
            mYOrientation = (int) (orientation[1] * 180f / Math.PI);
        }
    }

    private boolean rightOrientation(int orientation) {
        return orientation > -130 && orientation < -50;
    }
}
