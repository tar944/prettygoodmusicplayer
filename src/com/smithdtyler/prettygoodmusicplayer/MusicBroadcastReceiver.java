/**
   The Pretty Good Music Player
   Copyright (C) 2014  Tyler Smith
 
   This program is free software: you can redistribute it and/or modify
   it under the terms of the GNU General Public License as published by
   the Free Software Foundation, either version 3 of the License, or
   (at your option) any later version.

   This program is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
   GNU General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.smithdtyler.prettygoodmusicplayer;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.KeyEvent;

// Still trying to figure out how to receive bluetooth button presses...
public class MusicBroadcastReceiver extends BroadcastReceiver {
	private static final String TAG = "MusicBroadcastReceiver";
	
	/**
	 * If the option to automatically resume on headphone re-connect is selected, 
	 * keep track of the time they were unplugged.
	 */
	private long resumeOnQuickReconnectDisconnectTime = 0;

	@Override
	public void onReceive(Context context, Intent intent) {
		Log.i(TAG, "got a thingy!");
		SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);

		if (Intent.ACTION_HEADSET_PLUG.equals(intent.getAction())) {
			Log.i(TAG, "Got headset plug action");
			String disconnectBehavior = sharedPref.getString("pref_disconnect_behavior", context.getString(R.string.pause_after_one_sec));
			/*
			 * state - 0 for unplugged, 1 for plugged. name - Headset type,
			 * human readable string microphone - 1 if headset has a microphone,
			 * 0 otherwise
			 */
			if(disconnectBehavior.equals(context.getString(R.string.resume_on_quick_reconnect))){
				if (intent.getIntExtra("state", -1) == 0) {
					Log.i(TAG, "headphones disconnected, pausing");
					Intent msgIntent = new Intent(context, MusicPlaybackService.class);
					msgIntent.putExtra("Message", MusicPlaybackService.MSG_PAUSE);
					context.startService(msgIntent);
					resumeOnQuickReconnectDisconnectTime = System.currentTimeMillis();
				} else if (intent.getIntExtra("state", -1) == 1) {
					long currentTime = System.currentTimeMillis();
					if(currentTime - resumeOnQuickReconnectDisconnectTime < 1000){
						// Resume
						Log.i(TAG, "headphones plugged back in within 1000ms, resuming");
						Intent msgIntent = new Intent(context, MusicPlaybackService.class);
						msgIntent.putExtra("Message", MusicPlaybackService.MSG_PLAY);
						context.startService(msgIntent);
					}
				}
			} else if(disconnectBehavior.equals(context.getString(R.string.pause_after_one_sec))){
				if (intent.getIntExtra("state", -1) == 0) {
					Log.i(TAG, "headphones disconnected, pausing in 1 seconds");
					Intent msgIntent = new Intent(context, MusicPlaybackService.class);
					msgIntent.putExtra("Message", MusicPlaybackService.MSG_PAUSE_IN_ONE_SEC);
					context.startService(msgIntent);
					// If the headphone is plugged back in quickly after being
					// unplugged, keep playing
				} else if (intent.getIntExtra("state", -1) == 1) {
					Log.i(TAG, "headphones plugged back in, cancelling disconnect");
					Intent msgIntent = new Intent(context, MusicPlaybackService.class);
					msgIntent.putExtra("Message", MusicPlaybackService.MSG_CANCEL_PAUSE_IN_ONE_SEC);
					context.startService(msgIntent);
				}
			} else {
				// Pause immediately
				Log.i(TAG, "headphones disconnected, pausing");
				Intent msgIntent = new Intent(context, MusicPlaybackService.class);
				msgIntent.putExtra("Message", MusicPlaybackService.MSG_PAUSE);
				context.startService(msgIntent);
			}
		} else if (BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED
				.equals(intent.getAction())
				|| BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(intent
						.getAction())) {
			Log.i(TAG, "Got bluetooth disconnect action");
			Intent msgIntent = new Intent(context, MusicPlaybackService.class);
			msgIntent.putExtra("Message", MusicPlaybackService.MSG_PAUSE);
			context.startService(msgIntent);
		} else if (Intent.ACTION_MEDIA_BUTTON.equals(intent.getAction())) {
			Log.i(TAG, "Media Button Receiver: received media button intent: "
					+ intent);

			KeyEvent keyEvent = (KeyEvent) intent.getExtras().get(
					Intent.EXTRA_KEY_EVENT);
			Log.i(TAG, "Got a key event");
			if (keyEvent.getAction() == KeyEvent.ACTION_UP) {
				Log.i(TAG, "Got a key up event");
				// connect to the service
				// send a message
				// Create an intent with the message type, then send it to
				// "start service"
				// Looks like it's OK to call this multiple times
				// https://stackoverflow.com/questions/13124115/starting-android-service-already-running

				int keyCode = keyEvent.getKeyCode();
				Intent msgIntent = new Intent(context,
						MusicPlaybackService.class);

				switch (keyCode) {
				case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
					// code for fast forward
					Log.i(TAG, "key pressed KEYCODE_MEDIA_FAST_FORWARD");
					break;
				case KeyEvent.KEYCODE_MEDIA_NEXT:
					// code for next
					Log.i(TAG, "key pressed KEYCODE_MEDIA_NEXT");
					msgIntent
							.putExtra("Message", MusicPlaybackService.MSG_NEXT);
					context.startService(msgIntent);
					break;
				case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
					// code for play/pause
					Log.i(TAG, "key pressed KEYCODE_MEDIA_PLAY_PAUSE");
					msgIntent.putExtra("Message",
							MusicPlaybackService.MSG_PLAYPAUSE);
					context.startService(msgIntent);
					break;
				case KeyEvent.KEYCODE_MEDIA_PAUSE:
					// code for play/pause
					Log.i(TAG, "key pressed KEYCODE_MEDIA_PAUSE");
					msgIntent.putExtra("Message",
							MusicPlaybackService.MSG_PLAYPAUSE);
					context.startService(msgIntent);
					break;
				case KeyEvent.KEYCODE_MEDIA_PLAY:
					// code for play/pause
					Log.i(TAG, "key pressed KEYCODE_MEDIA_PLAY");
					msgIntent.putExtra("Message",
							MusicPlaybackService.MSG_PLAYPAUSE);
					context.startService(msgIntent);
					break;
				case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
					Log.i(TAG, "key pressed KEYCODE_MEDIA_PREVIOUS");
					msgIntent.putExtra("Message",
							MusicPlaybackService.MSG_PREVIOUS);
					context.startService(msgIntent);
					// code for previous
					break;
				case KeyEvent.KEYCODE_MEDIA_REWIND:
					Log.i(TAG, "key pressed KEYCODE_MEDIA_REWIND");
					// code for rewind
					break;
				case KeyEvent.KEYCODE_MEDIA_STOP:
					Log.i(TAG, "key pressed KEYCODE_MEDIA_STOP");
					// Oddly enough, I think Android stops listening for pause
					// after a while, so let's use
					// stop as a "start playing if paused"
					msgIntent.putExtra("Message",
							MusicPlaybackService.MSG_PLAYPAUSE);
					context.startService(msgIntent);
					break;
				default:
					Log.i(TAG, "key pressed " + keyCode);
					// code for stop
					break;
				}

			}
		}

	}
}