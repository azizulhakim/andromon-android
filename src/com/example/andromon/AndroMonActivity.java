package com.example.andromon;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

public class AndroMonActivity extends Activity {

	private static final String ACTION_USB_PERMISSION =
    	    "com.android.example.USB_PERMISSION";


	private PendingIntent mPermissionIntent;
	private UsbManager manager = null;
	private UsbAccessory accessory = null;
	private TextView textView = null;
	private Button getPictureButton;
	private Button connectButton;
	private ImageView imageView;
	ParcelFileDescriptor mFileDescriptor = null;
    FileInputStream mInputStream = null;
    FileOutputStream mOutputStream = null;
    FileDescriptor fd = null;
    int i = 0;
    
    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
    	 
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                	accessory = (UsbAccessory)intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY);

                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if(accessory != null){
                        	mFileDescriptor = manager.openAccessory(accessory);
                            if (mFileDescriptor != null) {
                            	textView.setText("Connected");
                                fd = mFileDescriptor.getFileDescriptor();
                                mInputStream = new FileInputStream(fd);
                                mOutputStream = new FileOutputStream(fd);
                            }
                        }
                    }
                    else {
                        //Log.d(TAG, "permission denied for accessory " + accessory);
                    	Toast.makeText(getApplicationContext(), "Permission Denied!", Toast.LENGTH_LONG).show();
                    }
                }
            }
        }
    };

	
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_andro_mon);
        
        textView = (TextView)this.findViewById(R.id.textView1);
        getPictureButton = (Button)this.findViewById(R.id.button1);
        connectButton = (Button) this.findViewById(R.id.button2);
        imageView = (ImageView)this.findViewById(R.id.imageView1);
        
        mPermissionIntent = PendingIntent.getBroadcast(getApplicationContext(), 0, new Intent(ACTION_USB_PERMISSION), 0);
    	IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
    	registerReceiver(mUsbReceiver, filter);
        
        
        getPictureButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
            	textView.setText("Reading");
            	try{
            		Toast.makeText(getApplicationContext(), "Reading", Toast.LENGTH_SHORT).show();
                	byte buffer[] = new byte[4];
                	try {
                		mInputStream.read(buffer, 0, 4);
                		
                		int byteCount = 0;
                		for (int i = 0; i < 4; i++) {
                	        int shift = (4 - 1 - i) * 8;
                	        byteCount += (buffer[i] & 0x000000FF) << shift;
                	    }                		
                		
                		Toast.makeText(getApplicationContext(), byteCount + " bytes", Toast.LENGTH_SHORT).show();
                		byteCount = 21842;
                		
                		buffer = new byte[byteCount];
                		mInputStream.read(buffer, 0, byteCount);
                		
                		Bitmap bmp = BitmapFactory.decodeByteArray(buffer, 0, buffer.length);
                		imageView.setImageBitmap(bmp);
    				} catch (Exception e1) {
    					textView.setText("Error again");
    					Toast.makeText(getApplicationContext(), "Error again", Toast.LENGTH_SHORT).show();
    					e1.printStackTrace();
    				}
                    
                    try {
    					mInputStream.close();
    				} catch (IOException e) {
    					// TODO Auto-generated catch block
    					e.printStackTrace();
    				}
                    try {
    					mOutputStream.close();
    				} catch (IOException e) {
    					// TODO Auto-generated catch block
    					e.printStackTrace();
    				}
                    try {
    					mFileDescriptor.close();
    				} catch (IOException e) {
    					// TODO Auto-generated catch block
    					e.printStackTrace();
    				}
            	}
            	catch (Exception ex){
            		textView.setText("Moha bipod" + ex.getMessage());
            	}
            	
            }
        });
        
        connectButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
            	textView.setText("connecting");
            	
            	manager = (UsbManager) getSystemService(Context.USB_SERVICE);
            	UsbAccessory[] accessoryList = manager.getAccessoryList();
            	manager.requestPermission(accessoryList[0], mPermissionIntent);
            	
            }
        });
    }
}
