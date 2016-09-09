/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.shamdroid.wear;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;
import android.widget.Toast;


import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.Wearable;

import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class SunShineWatchFace extends CanvasWatchFaceService {
    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

    /**
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.MINUTES.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    @Override
    public Engine onCreateEngine() {
        return  new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<SunShineWatchFace.Engine> mWeakReference;

        public EngineHandler(SunShineWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            SunShineWatchFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine implements DataApi.DataListener {
        final Handler mUpdateTimeHandler = new EngineHandler(this);
        boolean mRegisteredTimeZoneReceiver = false;
        Bitmap icon;
        Paint mBackgroundPaint;
        Paint mTextPaint;
        Paint mTextGrayPaint;
        Paint mMediumTextPaint;
        Paint mMediumGrayTextPaint;
        boolean mAmbient;
        Calendar mCalendar;
        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };
        float mYOffset;

        GoogleApiClient googleApiClient;

        String min = "0";
        String max = "0";

        float iconSize;

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            googleApiClient = new GoogleApiClient.Builder(SunShineWatchFace.this)
                                .addApi(Wearable.API).addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
                        @Override
                        public void onConnected(@Nullable Bundle bundle) {

                            Wearable.DataApi.addListener(googleApiClient,Engine.this);

                            PutDataMapRequest mapRequest = PutDataMapRequest.create(DataKeys.SYNC_REQUEST + System.currentTimeMillis());
                            DataMap dataMap = mapRequest.getDataMap();
                            dataMap.putInt("",0);
                            Wearable.DataApi.putDataItem(googleApiClient,mapRequest.asPutDataRequest().setUrgent());

                        }

                        @Override
                        public void onConnectionSuspended(int i) {


                        }
                    })
                    .addOnConnectionFailedListener(new GoogleApiClient.OnConnectionFailedListener() {
                        @Override
                        public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

                        }
                    }).build();
            googleApiClient.connect();


            setWatchFaceStyle(new WatchFaceStyle.Builder(SunShineWatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .build());
            Resources resources = SunShineWatchFace.this.getResources();

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(getColorFromRes(R.color.background));

            mTextPaint = new Paint();
            mTextPaint = createTextPaint(getColorFromRes(R.color.digital_text));


            mMediumTextPaint = new Paint();
            mMediumTextPaint = createTextPaint(getColorFromRes(R.color.digital_text));


            mMediumGrayTextPaint = new Paint();
            mMediumGrayTextPaint = createTextPaint(getColorFromRes(R.color.lightGrayText));


            mTextGrayPaint = new Paint();
            mTextGrayPaint = createTextPaint(getColorFromRes(R.color.lightGrayText));

            mCalendar = Calendar.getInstance();

            iconSize = getResources().getDimension(R.dimen.weather_icon);
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        private Paint createTextPaint(int textColor) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(NORMAL_TYPEFACE);
            paint.setAntiAlias(true);
            return paint;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();

                // Update time zone in case it changed while we weren't visible.
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            } else {
                unregisterReceiver();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            SunShineWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            SunShineWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = SunShineWatchFace.this.getResources();
            boolean isRound = insets.isRound();
            float textSize = resources.getDimension(R.dimen.digital_text_size );
            float mediumTextSize = resources.getDimension(R.dimen.digital_text_size );
            float secondaryMediumTextSize = resources.getDimension(R.dimen.digital_secondary_medium_text_size);

            float secondaryTextSize = resources.getDimension(R.dimen.digital_text_size_secondary);


            mYOffset = resources.getDimension(isRound?R.dimen.digital_y_offset_round:R.dimen.digital_y_offset);

            mTextPaint.setTextSize(textSize);
            mMediumTextPaint.setTextSize(mediumTextSize);
            mMediumGrayTextPaint.setTextSize(secondaryMediumTextSize);
            mTextGrayPaint.setTextSize(secondaryTextSize);

        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    mTextPaint.setAntiAlias(!inAmbientMode);
                }

                invalidate();
            }


            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {


            // Draw the background.
            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
            } else {
                canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);
            }

            float y = mYOffset;

            String time = String.format(Locale.getDefault(), "%d:%02d", mCalendar.get(Calendar.HOUR), mCalendar.get(Calendar.MINUTE));


            Rect textBounds = new Rect();

            mTextPaint.getTextBounds(time,0,time.length(),textBounds);

            float mXOffset = bounds.width() / 2 - mTextPaint.measureText(time) / 2; // Center the Text


            mCalendar.setTimeInMillis(System.currentTimeMillis());

            canvas.drawText(time, mXOffset, y, mTextPaint);

            y += textBounds.height() ;

            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("EEE, MMM d",Locale.getDefault());

            String date = simpleDateFormat.format(mCalendar.getTime());

            mXOffset = bounds.width() / 2 - mTextGrayPaint.measureText(date) / 2; // Center the Text

            canvas.drawText(date,mXOffset,y,mTextGrayPaint);


            mTextGrayPaint.getTextBounds(date,0,date.length(),textBounds);

            y+= 2*textBounds.height();


            float lineLength= 50;
            float lineX = bounds.width()/2 - lineLength/2 ;

            canvas.drawLine(lineX, y, lineX+lineLength, y, mTextGrayPaint);

            mMediumTextPaint.getTextBounds(max, 0, max.length(), textBounds);



            y+= 2*textBounds.height();



            int bigTextWidth = textBounds.width();
            float smallTextWidth = mMediumGrayTextPaint.measureText(min);

            mXOffset = bounds.width()/2 - (bigTextWidth+smallTextWidth
                    +(icon!=null && !isInAmbientMode()? iconSize+15 : 10))/2;

            if(icon != null  && !isInAmbientMode()) {
                canvas.drawBitmap(icon, mXOffset, y -60, new Paint(Paint.FILTER_BITMAP_FLAG));
                mXOffset += iconSize + 5;
            }
            canvas.drawText(max,mXOffset,y,mMediumTextPaint);
            mXOffset+= bigTextWidth+10;
            canvas.drawText(min,mXOffset,y,mMediumGrayTextPaint);


        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }


        public int getColorFromRes(int colorRes) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                return getResources().getColor(colorRes, getTheme());
            } else {
                return getResources().getColor(colorRes);
            }
        }

        @Override
        public void onDataChanged(DataEventBuffer dataEventBuffer) {



            for (DataEvent event : dataEventBuffer){
                Uri uri = event.getDataItem().getUri();

                if (uri.getPath().contains(DataKeys.DATA_WEATHER)){
                    final DataMap dataMap = DataMapItem.fromDataItem(event.getDataItem()).getDataMap();
                    min = dataMap.getString(DataKeys.MIN);
                    max = dataMap.getString(DataKeys.MAX);


                    new MyAsyncTask().execute(dataMap.getAsset(DataKeys.ICON));

                }
            }

            invalidate();
        }


        public class MyAsyncTask extends AsyncTask<Asset,Void,Bitmap>{

            @Override
            protected Bitmap doInBackground(Asset... assets) {
                Asset asset = assets[0];
                if (asset == null) {
                    throw new IllegalArgumentException("Asset must be non-null");
                }
                ConnectionResult result =
                        googleApiClient.blockingConnect(15000, TimeUnit.MILLISECONDS);
                if (!result.isSuccess()) {
                    return null;
                }
                // convert asset into a file descriptor and block until it's ready
                InputStream assetInputStream = Wearable.DataApi.getFdForAsset(
                        googleApiClient, asset).await().getInputStream();
                googleApiClient.disconnect();

                if (assetInputStream == null) {
                    return null;
                }
                // decode the stream into a bitmap
                return BitmapFactory.decodeStream(assetInputStream);
            }

            @Override
            protected void onPostExecute(Bitmap bitmap) {

                //icon = Bitmap.createScaledBitmap(bitmap, (int)iconSize, (int)iconSize, true);

                icon = resizeBitmap(bitmap,(int)iconSize,(int)iconSize);
                invalidate();

            }

            Bitmap resizeBitmap(Bitmap bitmap,int newWidth,int newHeight) {
                Bitmap scaledBitmap = Bitmap.createBitmap(newWidth, newHeight, Bitmap.Config.ARGB_8888);

                float ratioX = newWidth / (float) bitmap.getWidth();
                float ratioY = newHeight / (float) bitmap.getHeight();
                float middleX = newWidth / 2.0f;
                float middleY = newHeight / 2.0f;

                Matrix scaleMatrix = new Matrix();
                scaleMatrix.setScale(ratioX, ratioY, middleX, middleY);

                Canvas canvas = new Canvas(scaledBitmap);
                canvas.setMatrix(scaleMatrix);
                canvas.drawBitmap(bitmap, middleX - bitmap.getWidth() / 2, middleY - bitmap.getHeight() / 2, new Paint(Paint.FILTER_BITMAP_FLAG));

                return scaledBitmap;

            }

        }


    }


}
