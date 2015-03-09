package com.example.andromon;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
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
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
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

public class AndroMonActivity extends Activity{

	private static final String ACTION_USB_PERMISSION =
    	    "com.android.example.USB_PERMISSION";
	
	private static int AUDIO_BUFFER_SIZE = 4096*4;
	
	private final char KEYCODES[] = {
			0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
			0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
			0,0,0,0,0,0,0,0,0,0,0,'\t',' ','-','=','[',
			']','\\','\\',';','\'','`',',','.','/',0,0,0,0,0,0,0
	};

	public static SynchronousQueue<Point> mousePoints = new SynchronousQueue<Point>();
	public static SynchronousQueue<byte[]> audioData = new SynchronousQueue<byte[]>();
	
	private Thread mouseThread;
	private Thread audioThread;
	private MyReceiver myReceiver;
	private boolean stopRequested = false;
	private Point lastPosition;
	
	private AudioTrack audioTrack;
	private Handler handler;
	

	private PendingIntent mPermissionIntent;
	private UsbManager manager = null;
	private UsbAccessory accessory = null;
	private TextView textView = null;
	private Button getPictureButton;
	private Button connectButton;
	private Button writeTextButton;
	private Button getTextButton;
	private Button keyboardButton;
	private Button audioButton;
	private Button leftButton;
	private Button rightButton;
	private LinearLayout linearLayout;
	private ImageView imageView;
	ParcelFileDescriptor mFileDescriptor = null;
    FileInputStream mInputStream = null;
    FileOutputStream mOutputStream = null;
    FileDescriptor fd = null;
    int i = 0;
    
    FileOutputStream fileOutputStream;
    
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
        audioButton = (Button)this.findViewById(R.id.audioButton);
        connectButton = (Button) this.findViewById(R.id.button2);
        imageView = (ImageView)this.findViewById(R.id.imageView1);
        getTextButton = (Button)this.findViewById(R.id.getText);
        writeTextButton = (Button)this.findViewById(R.id.button3);
        keyboardButton = (Button)this.findViewById(R.id.keyboardButton);
        leftButton = (Button)this.findViewById(R.id.leftButton);
        rightButton = (Button)this.findViewById(R.id.rightButton);
        linearLayout = (LinearLayout)this.findViewById(R.id.linearLayout);
        
        mPermissionIntent = PendingIntent.getBroadcast(getApplicationContext(), 0, new Intent(ACTION_USB_PERMISSION), 0);
    	IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
    	registerReceiver(mUsbReceiver, filter);
        
    	myReceiver = new MyReceiver();
    	IntentFilter intentFilter = new IntentFilter();
    	intentFilter.addAction(MouseService.Mouse_Service_Action);
    	registerReceiver(myReceiver, intentFilter);
    	
    	audioTrack = new  AudioTrack(AudioManager.STREAM_MUSIC, 44100, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT, AUDIO_BUFFER_SIZE, AudioTrack.MODE_STREAM);
    	
    	try {
    		File file = new File("/storage/sdcard0/out.txt");
    		if (!file.exists())file.createNewFile();
			fileOutputStream = new FileOutputStream(file);
		} catch (Exception e2) {
			Toast.makeText(getApplicationContext(), "File Read: " + e2.getMessage(), Toast.LENGTH_LONG).show();
			e2.printStackTrace();
		}
    	
    	//Start service
        /*Intent intent = new Intent(AndroMonActivity.this,
          MouseService.class);
        startService(intent);*/
    	
//    	handler = new Handler() {
//    		private int count = 0;
//    	      @Override
//    	      public void handleMessage(Message msg) {
//    	    	  textView.append(" " + count);
//    	    	  count++;
//    	      }
//
//    	    };
    	
        mouseThread = new Thread(){
        	public void run(){
        		stopRequested = false;
        		lastPosition = new Point(0, 0);
        		byte data[] = new byte[8];
        		
        		while (!stopRequested){
        			try {
        				int i = 0;
        				data[i++] = (byte)getResources().getInteger(R.integer.MOUSECONTROL);	// this is mouse data
        				data[i++] = (byte)getResources().getInteger(R.integer.MOUSEMOVE);
        				
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
        
        audioThread = new Thread(){
        	public void run(){
        		stopRequested = false;
        		int offset = 0;
        		int count = 0;
        		boolean flag = false;
        		
        		audioTrack.play();
        		while (!stopRequested){
        			try {
        				byte[] data = AndroMonActivity.audioData.take();
        				if (data != null){
        					System.out.println("Playing: " + count);
        					audioTrack.write(data, offset, data.length);
        					offset += data.length;
        					offset %= AUDIO_BUFFER_SIZE;
        					//handler.sendEmptyMessage(0);
        					System.out.println("Played: " + count);
        					count++;
        				}
        			} 
        			catch (InterruptedException e) {
        				System.out.println("Mouse Point Fetching Interrupted");
        			}
        		}
        		audioTrack.stop();
        		audioTrack.release();
        	}
        };
        
        leftButton.setOnClickListener(new View.OnClickListener() {
			
			@Override
			public void onClick(View v) {
				byte[] data = {0,0,0,0,0,0,0,0};
				data[0] = (byte)getResources().getInteger(R.integer.MOUSECONTROL);	// this is mouse data
				data[1] = (byte)getResources().getInteger(R.integer.MOUSELEFT);
				
				sendMouseData(data);
				
			}
		});
        
        rightButton.setOnClickListener(new View.OnClickListener() {
			
			@Override
			public void onClick(View v) {
				byte[] data = {0,0,0,0,0,0,0,0};
				data[0] = (byte)getResources().getInteger(R.integer.MOUSECONTROL);	// this is mouse data
				data[1] = (byte)getResources().getInteger(R.integer.MOUSERIGHT);
				
				sendMouseData(data);
			}
		});
        
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
        
        audioButton.setOnClickListener(new View.OnClickListener() {
			
			@Override
			public void onClick(View v) {
				audioThread.start();
				
				new Thread(){
					public void run(){
						byte buffer[] = new byte[4096];
						int count = 1;
						while(!stopRequested){
							try {
								mInputStream.read(buffer, 0, 4096);
	        					System.out.println("Adding: " + count);
								audioData.put(buffer);
								System.out.println("Add: " + count);
								//fileOutputStream.write(buffer);
								count++;
							} catch (InterruptedException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
								
							} catch (IOException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
							
//							//Reading the file.. 
////							byte[] buffer = new byte[4096];
//							InputStream in = null;
//							int c = 0;
//							try {
//								in = getApplicationContext().getResources().openRawResource(R.raw.pcmstereo8ss44100hz);   //new FileInputStream( file );
//								//in.read(buffer, 0, 44);
//								while(in.read(buffer) != -1){
//									audioData.put(buffer);
//									c++;
//									System.out.println("data added");
//								}
//							} catch (Exception e) {
//								Toast.makeText(getApplicationContext(), "Exception: " + e.getMessage(), Toast.LENGTH_SHORT).show();
//								e.printStackTrace();
//							}finally{
//								Toast.makeText(getApplicationContext(), "Size = " + c, Toast.LENGTH_SHORT).show();
//								try {
//									in.close();
//								} catch (IOException e) {
//									// TODO Auto-generated catch block
//									e.printStackTrace();
//								}
//							}
						}
						try {
							fileOutputStream.write(count);
							fileOutputStream.close();
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}
				}.start();
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
        
        getTextButton.setOnClickListener(new View.OnClickListener() {
			
			@Override
			public void onClick(View v) {
            	textView.setText("Fetched Audio Data: ");
            	try{
            		Toast.makeText(getApplicationContext(), "Reading", Toast.LENGTH_SHORT).show();
                	byte buffer[] = new byte[4096];
                	try {
                		mInputStream.read(buffer, 0, 4096);
                		String s = "";
                		for (int i=0; i<buffer.length; i++){
                			s += "  " + (int)buffer[i] + "  ";
                		}
                		
                		textView.setText(s);
                		
    				} catch (Exception e1) {
    					textView.setText("Error again:" + e1.getMessage());
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
    	else if(event.getUnicodeChar() == '0'){
    		sendKeyboardData(event.getUnicodeChar() - '0' + 39);
    	}
    	else{
    		for (int i=0;i<KEYCODES.length; i++){
    			if (KEYCODES[i] == event.getUnicodeChar()){
    				sendKeyboardData(i);
    				break;
    			}
    		}
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
    	byte buffer[] = {0,0,0,0,0,0,0,0};
    	buffer[0] = (byte)getResources().getInteger(R.integer.KEYBOARDCONTROL);
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