package com.example.android.sunshine.app.wear;

import android.util.Log;

import com.example.android.sunshine.app.sync.SunshineSyncAdapter;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.WearableListenerService;

/**
 * Created by mohammad on 08/09/16.
 */

public class DataListener extends WearableListenerService {




    @Override
    public void onDataChanged(DataEventBuffer dataEventBuffer) {

        for (DataEvent dataEvent : dataEventBuffer){

            DataItem dataMapItem = dataEvent.getDataItem();

            if (dataMapItem.getUri().getPath().contains(DataKeys.SYNC_REQUEST)){
                SunshineSyncAdapter.syncImmediately(getApplicationContext());

            }

        }

    }
}
