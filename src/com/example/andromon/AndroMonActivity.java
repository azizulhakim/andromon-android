package com.example.andromon;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.SynchronousQueue;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class AndroMonActivity extends Activity {

	private static final String ACTION_USB_PERMISSION =
    	    "com.android.example.USB_PERMISSION";

	public static SynchronousQueue<Point> mousePoints = new SynchronousQueue<Point>();
	private Thread mouseThread;
	private MyReceiver myReceiver;
	private boolean stopRequested = false;
	private Point lastPosition;
	

	private PendingIntent mPermissionIntent;
	private UsbManager manager = null;
	private UsbAccessory accessory = null;
	private TextView textView = null;
	private Button getPictureButton;
	private Button connectButton;
	private Button writeTextButton;
	private Button keyboardButton;
	private LinearLayout linearLayout;
	private ImageView imageView;
	ParcelFileDescriptor mFileDescriptor = null;
    FileInputStream mInputStream = null;
    FileOutputStream mOutputStream = null;
    FileDescriptor fd = null;
    int i = 0;
    
    float downx, downy, upx, upy;
    
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
                                
                                AsyncTask<FileOutputStream, Integer, Integer> task = new AsyncTask<FileOutputStream, Integer, Integer>() {

									@Override
									protected Integer doInBackground(
											FileOutputStream... params) {
										byte data[] = {3,1,0,0,0,0,0,0};
										data[2] = (byte)((GlobalAttributes.DISPLAYHEIGHT & 0xFF00) >> 8);
										data[3] = (byte)(GlobalAttributes.DISPLAYHEIGHT & 0xFF);
										
										data[4] = (byte)((GlobalAttributes.DISPLAYWIDTH & 0xFF00) >> 8);
										data[5] = (byte)(GlobalAttributes.DISPLAYWIDTH & 0xFF);
										
										try{
											params[0].write(data);
										}
										catch(Exception ex){
											return -1;
										}
										
										return 0;
									}
                                	
									@Override
									protected void onPostExecute(Integer result) {
										
									}
								};

								task.execute(mOutputStream);
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
        writeTextButton = (Button)this.findViewById(R.id.button3);
        keyboardButton = (Button)this.findViewById(R.id.keyboardButton);
        linearLayout = (LinearLayout)this.findViewById(R.id.linearLayout);
        
        mPermissionIntent = PendingIntent.getBroadcast(getApplicationContext(), 0, new Intent(ACTION_USB_PERMISSION), 0);
    	IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
    	registerReceiver(mUsbReceiver, filter);
        
    	myReceiver = new MyReceiver();
    	IntentFilter intentFilter = new IntentFilter();
    	intentFilter.addAction(MouseService.Mouse_Service_Action);
    	registerReceiver(myReceiver, intentFilter);
    	
    	//Start service
        /*Intent intent = new Intent(AndroMonActivity.this,
          MouseService.class);
        startService(intent);*/
    	
        mouseThread = new Thread(){
        	public void run(){
        		stopRequested = false;
        		lastPosition = new Point(0, 0);
        		byte data[] = new byte[8];
        		
        		
        		while (!stopRequested){
        			try {
        				int i = 0;
        				data[i++] = 2;	// this is mouse data
        				data[i++] = 0;
        				
        				Point point = AndroMonActivity.mousePoints.take();
        				
        				data[i+1] = (byte) (point.x);// - lastPosition.x);
        				data[i+3] = (byte) (point.y);// - lastPosition.y);
        				data[i+0] = point.x < 0.0 ? (byte)1 : (byte)0; 
        				data[i+2] = point.y < 0.0 ? (byte)1 : (byte)0;
        				data[i+1] = (byte) Math.abs(point.x);
        				data[i+3] = (byte) Math.abs(point.y);
        				lastPosition = point;
        				
        				i += 2;
        				sendMouseData(data);
        			} 
        			catch (InterruptedException e) {
        				System.out.println("Mouse Point Fetching Interrupted");
        			}
        		}
        	}
        };
        
        keyboardButton.setOnClickListener(new View.OnClickListener() {
			
			@Override
			public void onClick(View v) {
				InputMethodManager inputMethodManager=(InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
			    inputMethodManager.toggleSoftInputFromWindow(linearLayout.getApplicationWindowToken(), InputMethodManager.SHOW_FORCED, 0);
				
			}
		});
        
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
    					e.printStackTrace();
    				}
                    try {
    					mOutputStream.close();
    				} catch (IOException e) {
    					e.printStackTrace();
    				}
                    try {
    					mFileDescriptor.close();
    				} catch (IOException e) {
    					e.printStackTrace();
    				}
            	}
            	catch (Exception ex){
            		textView.setText("Moha bipod" + ex.getMessage());
            	}
            	
            }
        });
        
        writeTextButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
            	textView.setText("Writing");
            	try{
            		Toast.makeText(getApplicationContext(), "Writing", Toast.LENGTH_SHORT).show();
                	byte buffer[] = {64,65,66,67};
                	try {
                		mOutputStream.write(buffer);
                		
    				} catch (Exception e1) {
    					textView.setText("Error again");
    					Toast.makeText(getApplicationContext(), "Error again", Toast.LENGTH_SHORT).show();
    					e1.printStackTrace();
    				}
            	}
            	catch (Exception ex){
            		textView.setText("Moha bipod" + ex.getMessage());
            	}
            	
            }
        });
        
        connectButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
            	GlobalAttributes.DISPLAYHEIGHT = imageView.getHeight();
                GlobalAttributes.DISPLAYWIDTH = imageView.getWidth();
                
            	textView.setText("connecting");
            	
            	manager = (UsbManager) getSystemService(Context.USB_SERVICE);
            	UsbAccessory[] accessoryList = manager.getAccessoryList();
            	Toast.makeText(getApplicationContext(), "" + accessoryList.length, Toast.LENGTH_LONG).show();
            	manager.requestPermission(accessoryList[0], mPermissionIntent);
            	
            }
        });
        
        imageView.setOnTouchListener(new OnTouchListener() {
			
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				int eid = event.getAction();
				switch (eid){
					case MotionEvent.ACTION_DOWN:
						downx = event.getX();
						downy = event.getY();
						break;
						
					case MotionEvent.ACTION_MOVE:
					case MotionEvent.ACTION_UP:
						upx = event.getX();
						upy = event.getY();
						
						//Toast.makeText(getApplicationContext(), "x=" + upx + "y=" + upy, Toast.LENGTH_SHORT).show();
						
						int x = (int)Math.ceil((double)(upx - downx));
						int y = (int)Math.ceil((double)(upy - downy));
						
						if (Math.abs(x) > 0) downx = upx;
						if (Math.abs(y) > 0) downy = upy;
						
						if ((Math.abs(x) > 0 || Math.abs(y) > 0) && mousePoints.size() < 1000);
						{
							try{
								mousePoints.add(new Point(x, y));
							}
							catch(Exception ex){
								
							}
						}
							
						break;
					
					default:
						break;
				}	
					
				return true;
			}
		});
        
        mouseThread.start();
    }
    
    @Override
    protected void onStop() {
        try {
			mInputStream.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
        try {
			mOutputStream.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
        try {
			mFileDescriptor.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
        
        stopRequested = true;
        
    	super.onStop();
    }
    
    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
    	
    	Toast.makeText(getApplicationContext(), "" + (char)event.getUnicodeChar(), Toast.LENGTH_SHORT).show();
    	
    	if (event.getUnicodeChar() >= 'A' && event.getUnicodeChar() <= 'Z'){
    		sendKeyboardData(event.getUnicodeChar() - 'A' + 4);
    	}
    	else if(event.getUnicodeChar() >= 'a' && event.getUnicodeChar() <= 'z'){
    		sendKeyboardData(event.getUnicodeChar() - 'a' + 4);
    	}
    	else if(event.getUnicodeChar() >= '1' && event.getUnicodeChar() <= '9'){
    		sendKeyboardData(event.getUnicodeChar() - '0' + 30);
    	}
    	else if(event.getUnicodeChar() >= '0' && event.getUnicodeChar() <= '\\'){
    		sendKeyboardData(event.getUnicodeChar() - '0' + 39);
    	}
    	else if(event.getUnicodeChar() >= ';' && event.getUnicodeChar() <= '/'){
    		sendKeyboardData(event.getUnicodeChar() - ';' + 51);
    	}
    	//sendKeyboardData();
    	
    	return super.onKeyUp(keyCode, event);
    }
    
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
    	// TODO Auto-generated method stub
    	return super.onKeyDown(keyCode, event);
    }
    
    private void sendMouseData(byte data[]){
    	byte buffer[] = {0,0,0,0,0,0,0,0};

		if (data.length < buffer.length){
			//System.arraycopy(data, 0, buffer, 0, data.length);
		}
		
		try{
        	try {
        		mOutputStream.write(data);
        		
			} catch (Exception e1) {
				e1.printStackTrace();
			}
    	}
    	catch (Exception ex){
    	}
    }
    
    private void sendKeyboardData(int keyIndex){
    	byte buffer[] = {1,0,0,0,0,0,0,0};
    	buffer[2] = (byte)keyIndex;

		Toast.makeText(getApplicationContext(), "Receiver", Toast.LENGTH_SHORT).show();
		
		try{
        	try {
        		mOutputStream.write(buffer);
        		
			} catch (Exception e1) {
				textView.setText("Error again");
				Toast.makeText(getApplicationContext(), "Error again", Toast.LENGTH_SHORT).show();
				e1.printStackTrace();
			}
    	}
    	catch (Exception ex){
    		textView.setText("Moha bipod" + ex.getMessage());
    	}

    }
    
    private class MyReceiver extends BroadcastReceiver{

		@Override
		public void onReceive(Context arg0, Intent arg1) {
			byte buffer[] = {0,0,0,0,0,0,0,0};
			byte data[] = arg1.getByteArrayExtra("DATA");

			Toast.makeText(getApplicationContext(), "Receiver", Toast.LENGTH_SHORT).show();
			if (data.length < buffer.length){
				System.arraycopy(data, 0, buffer, 0, data.length);
			}
			
			try{
            	try {
            		mOutputStream.write(buffer);
            		
				} catch (Exception e1) {
					textView.setText("Error again");
					Toast.makeText(getApplicationContext(), "Error again", Toast.LENGTH_SHORT).show();
					e1.printStackTrace();
				}
        	}
        	catch (Exception ex){
        		textView.setText("Moha bipod" + ex.getMessage());
        	}
		}
    	
    }
}
