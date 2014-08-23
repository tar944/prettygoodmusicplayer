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

package com.smith.d.tyler.notawfulmusicplayer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.AudioManager.OnAudioFocusChangeListener;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

/**
 * TODO make foreground
 */
public class MusicPlaybackService extends Service {
	static final int MSG_REGISTER_CLIENT = 1;
	static final int MSG_UNREGISTER_CLIENT = 2;
	
	// Playback control
	static final int MSG_PLAYPAUSE = 3;
	static final int MSG_NEXT = 4;
	static final int MSG_PREVIOUS = 5;
	static final int MSG_SET_PLAYLIST = 6;
	
	// State management
	static final int MSG_REQUEST_STATE = 7;
	
	static final int MSG_SERVICE_STATUS = 8;

	static final int MSG_STOP = 9;

	public enum PlaybackState{
		PLAYING,
		PAUSED
	}
	
	static final String PRETTY_SONG_NAME = "PRETTY_SONG_NAME";
	static final String PRETTY_ARTIST_NAME = "PRETTY_ARTIST_NAME";
	static final String PRETTY_ALBUM_NAME = "PRETTY_ALBUM_NAME";
	static final String ALBUM_NAME = "ALBUM_NAME";
	static final String PLAYBACK_STATE = "PLAYBACK_STATE";
	static final String TRACK_DURATION = "TRACK_DURATION";
	static final String TRACK_POSITION = "TRACK_POSITION";

	private FileInputStream fis;
	private File songFile;
	private String[] songAbsoluteFileNames;
	private int songAbsoluteFileNamesPosition;
	
	private Timer timer;

	private AudioManager am;
	private Looper mServiceLooper;
	private ServiceHandler mServiceHandler;
	private MediaPlayer mp;
	private static final String TAG = "MusicPlaybackService";
	private NotificationManager nm;
	private static boolean isRunning = false;
	
	private static int id = new String("Music Playback Service").hashCode();
	
	private OnAudioFocusChangeListener audioFocusListener = new NotAwfulAudioFocusChangeListener();

	List<Messenger> mClients = new ArrayList<Messenger>(); // Keeps track of all
															// current
															// registered
															// clients.
	int mValue = 0; // Holds last value set by a client.

	final Messenger mMessenger = new Messenger(new IncomingHandler());
	// Target we publish for clients to send messages to IncomingHandler.
	private int unique; // TODO handle this better

	// Handler that receives messages from the thread
	private final class ServiceHandler extends Handler {
		public ServiceHandler(Looper looper) {
			super(looper);
		}

		@Override
		public void handleMessage(Message msg) {
			Log.i(TAG, "ServiceHandler got a message!" + msg);
		}
	}

	@Override
	public void onCreate() {
		Log.i(TAG, "Music Playback Service Created!");
		// timer.scheduleAtFixedRate(new TimerTask(){ public void run()
		// {onTimerTick();}}, 0, 100L);
		isRunning = true;
		// Start up the thread running the service. Note that we create a
		// separate thread because the service normally runs in the process's
		// main thread, which we don't want to block. We also make it
		// background priority so CPU-intensive work will not disrupt our UI.
		mp = new MediaPlayer();

		mp.setOnCompletionListener(new OnCompletionListener() {

			@Override
			public void onCompletion(MediaPlayer mp) {
				Log.i(TAG, "Song complete");
				next();
			}

		});
		
		// https://developer.android.com/training/managing-audio/audio-focus.html
		audioFocusListener = new NotAwfulAudioFocusChangeListener();

		// Get permission to play audio
		am = (AudioManager) getBaseContext().getSystemService(
				Context.AUDIO_SERVICE);

		HandlerThread thread = new HandlerThread("ServiceStartArguments");
		thread.start();

		// Get the HandlerThread's Looper and use it for our Handler
		mServiceLooper = thread.getLooper();
		mServiceHandler = new ServiceHandler(mServiceLooper);
        
        // TODO fix the deprecated stuff
		// TODO launch the "now playing" thingy on selection of the notification
        Notification notification = new Notification(R.drawable.icon, getText(R.string.ticker_text),
                System.currentTimeMillis());
        Intent resultIntent = new Intent(this, NowPlaying.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, resultIntent, 0);
        notification.setLatestEventInfo(this, getText(R.string.notification_title),
                getText(R.string.notification_message), pendingIntent);
        startForeground(id, notification);
        
        timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask(){ public void run() {onTimerTick();}}, 0, 500L);
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Log.i("MyService", "Received start id " + startId + ": " + intent);
		int command = intent.getIntExtra("Message", -1);
		if(command != -1){
			Log.i(TAG, "I got a message! " + command);
			if(command == MSG_PLAYPAUSE){
				Log.i(TAG, "I got a playpause message");
				playPause();
			} else if(command == MSG_NEXT){
				Log.i(TAG, "I got a next message");
				next();
			} else if(command == MSG_PREVIOUS){
				Log.i(TAG, "I got a previous message");
				previous();
			} else if(command == MSG_STOP){
				Log.i(TAG, "I got a stop message");
				timer.cancel();
				mp.stop();
				mp.reset();
				mp.release();
				stopForeground(true);
				stopSelf();
			}
			return START_STICKY;
		}
		
		// For each start request, send a message to start a job and deliver the
		// start ID so we know which request we're stopping when we finish the
		// job
		// TODO I don't think we actually need to do this, since my ServiceHandler doesn't
		// actually do anything - there aren't any long running tasks to handle, since the
		// music player takes care of everything.
		Message msg = mServiceHandler.obtainMessage();
		msg.arg1 = startId;
		mServiceHandler.sendMessage(msg);
		// If we get killed, after returning from here, restart
		return START_STICKY;
	}

	@Override
	public IBinder onBind(Intent intent) {
		return mMessenger.getBinder();
	}

	// Receives messages from activities which want to control the jams
	class IncomingHandler extends Handler { // Handler of incoming messages from
											// clients.
		@Override
		public void handleMessage(Message msg) {
			Log.i(TAG, "Music Playback service got a message!");
			switch (msg.what) {
			case MSG_REGISTER_CLIENT:
				Log.i(TAG, "Got MSG_REGISTER_CLIENT");
				mClients.add(msg.replyTo);
				break;
			case MSG_UNREGISTER_CLIENT:
				Log.i(TAG, "Got MSG_UNREGISTER_CLIENT");
				mClients.remove(msg.replyTo);
				break;
			case MSG_PLAYPAUSE:
				// if we got a playpause message, assume that the user can hear
				// what's happening and wants to switch it.
				Log.i(TAG, "Got a playpause message!");
				// Assume that we're not changing songs
				playPause();
				break;
			case MSG_NEXT:
				Log.i(TAG, "Got a next message!");
				next();
				break;
			case MSG_PREVIOUS:
				Log.i(TAG, "Got a previous message!");
				previous();
				break;
			case MSG_SET_PLAYLIST:
				Log.i(TAG, "Got a set playlist message!");
				songAbsoluteFileNames = msg.getData().getStringArray(
						SongList.SONG_ABS_FILE_NAME_LIST);
				songAbsoluteFileNamesPosition = msg.getData().getInt(
						SongList.SONG_ABS_FILE_NAME_LIST_POSITION);
				songFile = new File(
						songAbsoluteFileNames[songAbsoluteFileNamesPosition]);
				startPlayingFile();
				break;
			case MSG_REQUEST_STATE:
				Log.i(TAG, "Got a state request message!");
				break;
			default:
				super.handleMessage(msg);
			}
		}
	}

	private void onTimerTick(){
		sendUpdateToClients();
	}
	
	private void sendUpdateToClients() {
		
		for(Messenger client : mClients){
			Message msg = Message.obtain(null, MSG_SERVICE_STATUS);
			Bundle b = new Bundle();
			b.putString(PRETTY_SONG_NAME, Utils.getPrettySongName(songFile));
			b.putString(PRETTY_ALBUM_NAME, songFile.getParent());
			b.putString(PRETTY_ARTIST_NAME, songFile.getParentFile().getParent());
			if(mp.isPlaying()){
				b.putInt(PLAYBACK_STATE, PlaybackState.PLAYING.ordinal());
			} else {
				b.putInt(PLAYBACK_STATE, PlaybackState.PAUSED.ordinal());
			}
			if(mp.isPlaying()){
				b.putInt(TRACK_DURATION, mp.getDuration());
				b.putInt(TRACK_POSITION, mp.getCurrentPosition());				
			}
			msg.setData(b);
			try {
				client.send(msg);
			} catch (RemoteException e) {
				e.printStackTrace();
				// TODO remove the client
				// have to use a proper iterator to do this, so we don't get another exception
			}
		}
	}

	public static boolean isRunning() {
		return isRunning;
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		// nm.cancel(R.string.service_started); // Cancel the persistent
		// notification.
		nm.cancel(unique);
		Log.i("MyService", "Service Stopped.");
		isRunning = false;
	}

	private void previous() {
		mp.stop();
		mp.reset();
		try {
			fis.close();
			songAbsoluteFileNamesPosition = songAbsoluteFileNamesPosition - 1;
			if (songAbsoluteFileNamesPosition < 0) {
				songAbsoluteFileNamesPosition = songAbsoluteFileNames.length - 1;
			}
			String next = songAbsoluteFileNames[songAbsoluteFileNamesPosition];
			songFile = new File(next);
			fis = new FileInputStream(songFile);
			mp.setDataSource(fis.getFD());
			mp.prepare();
			mp.start();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void startPlayingFile() {
		// Have we loaded a file yet?
		if(mp.getDuration() > 0){
			pause();
			mp.stop();
			mp.reset();
		}

		// open the file, pass it into the mp
		try {
			fis = new FileInputStream(songFile);
			mp.setDataSource(fis.getFD());
			mp.prepare();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
		} catch (IllegalStateException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void playPause() {
		if (mp.isPlaying()) {
			mp.pause();
		} else {
			play();
		}
	}

	private void play() {
		if (mp.isPlaying()) {
			// do nothing
		} else {
			// Request audio focus for playback
			int result = am.requestAudioFocus(
					MusicPlaybackService.this.audioFocusListener,
					// Use the music stream.
					AudioManager.STREAM_MUSIC,
					// Request permanent focus.
					AudioManager.AUDIOFOCUS_GAIN);
			Log.d(TAG, "requestAudioFocus result = " + result);
			Log.i(TAG, "About to play " + songFile);

			if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
				Log.d(TAG, "We got audio focus!");
				// am.registerMediaButtonEventReceiver(receiver); // TODO do I
				// need
				// this?
				mp.start();
			} else {
				Log.e(TAG, "Unable to get audio focus");
			}
		}
	}

	private void pause() {
		if (mp.isPlaying()) {
			mp.pause();
		} else {
			// do nothing
		}
	}
	
	// TODO synchronize on these functions to avoid starting two songs at once.
	private void next() {
		mp.stop();
		mp.reset();
		try {
			fis.close();
			songAbsoluteFileNamesPosition = (songAbsoluteFileNamesPosition + 1)
					% songAbsoluteFileNames.length;
			String next = songAbsoluteFileNames[songAbsoluteFileNamesPosition];
			songFile = new File(next);
			fis = new FileInputStream(songFile);
			mp.setDataSource(fis.getFD());
			mp.prepare();
			mp.start();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private class NotAwfulAudioFocusChangeListener implements
			AudioManager.OnAudioFocusChangeListener {

		public void onAudioFocusChange(int focusChange) {
			Log.w(TAG, "Focus change received " + focusChange);
			if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
				Log.i(TAG, "AUDIOFOCUS_LOSS_TRANSIENT");
				pause();
				// Pause playback
			} else if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
				Log.i(TAG, "AUDIOFOCUS_GAIN");
				// It bugs the crap out of me when things just start playing on
				// their own.
				// Don't start playing again till someone pushes a friggin'
				// button.
				// play();
				// Resume playback
			} else if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
				// am.unregisterMediaButtonEventReceiver(RemoteControlReceiver);
				Log.i(TAG, "AUDIOFOCUS_LOSS");
				am.abandonAudioFocus(this);
				pause();
				// Stop playback
			} else if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
				Log.i(TAG, "AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK");
				pause();
			} else if (focusChange == AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK) {
				Log.i(TAG, "AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK");
				play();
			}
		}
	}

}
