package com.example.andromon;

import android.app.Service;
import android.content.Intent;
import android.graphics.Point;
import android.os.IBinder;
import android.widget.Toast;

public class MouseService extends Service{
	final static String Mouse_Service_Action = "MouseServiceAction";
	private Point lastPosition;
	private boolean stopRequested;

	@Override
	public IBinder onBind(Intent arg0) {
		// TODO Auto-generated method stub
		return null;
	}
	
	@Override
	public void onCreate() {
		super.onCreate();
		
		Toast.makeText(getApplicationContext(), "Start Service", Toast.LENGTH_SHORT).show();
		
//		sleepTime = Integer.parseInt(getString(R.string.SleepTime));
//		snapTime = Integer.parseInt(getString(R.string.SnapTime));
//		audioDuration = Integer.parseInt(getString(R.string.audioDuration));
//		appUtil = Factory.createInstance(getApplicationContext(), "audio");
	}
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		super.onStartCommand(intent, flags, startId);
		
		stopRequested = false;
		lastPosition = new Point(0, 0);
		byte data[] = new byte[8];
		int i = 0;
		
		Toast.makeText(getApplicationContext(), "Reading Queue", Toast.LENGTH_SHORT).show();
		while (!stopRequested){
			try {
				Point point = AndroMonActivity.mousePoints.take();
				
				data[i] = (byte) (point.x - lastPosition.x);
				data[i+1] = (byte) (point.y - lastPosition.y);
				lastPosition = point;
				
				i += 2;
				
				if (i == 8 || AndroMonActivity.mousePoints.size() == 0){
					Intent dataIntent = new Intent();
					dataIntent.setAction(Mouse_Service_Action);
					dataIntent.putExtra("DATA", data);
					sendBroadcast(dataIntent);
				}
			} 
			catch (InterruptedException e) {
				System.out.println("Mouse Point Fetching Interrupted");
			}
		}
		
		return START_STICKY;
	}
	
	private void onSto() {
		stopRequested = true;
	}
}
