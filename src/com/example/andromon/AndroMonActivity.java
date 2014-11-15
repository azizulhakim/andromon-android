package com.example.andromon;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.widget.Toast;

public class AndroMonActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_andro_mon);
        
        try{
        	Intent intent = getIntent();
            UsbAccessory accessory = (UsbAccessory)intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY);
            UsbManager manager = (UsbManager) getSystemService(Context.USB_SERVICE);
            UsbAccessory[] accessoryList = manager.getAccessoryList();
            String manu = accessoryList[0].getModel();
            Toast.makeText(getApplicationContext(), manu, Toast.LENGTH_LONG).show();
        }
        catch(Exception ex){
        	Toast.makeText(getApplicationContext(), "Error", Toast.LENGTH_SHORT).show();
        }
    }
}
