package com.cliptracer.ClipTracer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.PowerManager.WakeLock
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.media.session.MediaButtonReceiver


class SilentAudioService : Service() {

    private var appBusinessLogic: AppBusinessLogic? = null

    // Other existing code

    fun setAppBusinessLogic(appBusinessLogic: AppBusinessLogic) {
        this.appBusinessLogic = appBusinessLogic
    }


    private var mediaPlayer: MediaPlayer? = null
    private var mediaSession: MediaSessionCompat? = null
    private var wakeLock: WakeLock? = null
    private val handler = Handler(Looper.getMainLooper())
    var playerArtistText: String = "Not connected"
    var playerTitleText: String = "Not connected"

    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): SilentAudioService = this@SilentAudioService
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.channel_name) // Customize these for your app
            val descriptionText = getString(R.string.channel_description)
            val importance = NotificationManager.IMPORTANCE_NONE
            val channel = NotificationChannel("YOUR_CHANNEL_ID2", name, importance).apply {
                description = descriptionText
            }


            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)

        }
    }

    private fun createNotification(): Notification {
        createNotificationChannel() // Make sure the channel is created

        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)




        return NotificationCompat.Builder(this, "YOUR_CHANNEL_ID2")
            .setContentTitle("Playback Information")
            .setContentText("Your app is playing music")
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Replace with your notification icon
            .setContentIntent(pendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // Ensure visibility on lock screen

            .setStyle(androidx.media.app.NotificationCompat.MediaStyle()
                .setShowActionsInCompactView(0, 1, 2) // Specify the indices of actions to show in the compact view
                .setMediaSession(mediaSession!!.sessionToken)) // Integrate MediaStyle with your media session
            .addAction(NotificationCompat.Action(
                R.drawable.myprev, "Previous",
                MediaButtonReceiver.buildMediaButtonPendingIntent(this,
                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)))
            .addAction(NotificationCompat.Action(
                R.drawable.mypause, "Pause",
                MediaButtonReceiver.buildMediaButtonPendingIntent(this,
                    PlaybackStateCompat.ACTION_PLAY_PAUSE)))
            .addAction(NotificationCompat.Action(
                R.drawable.mynext, "Next",
                MediaButtonReceiver.buildMediaButtonPendingIntent(this,
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT)))
            .build()


    }

    fun playIfNotYet(){
        mediaPlayer?.let { player ->
        if (!player.isPlaying) {
            player.start()
            updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
        }
        }
    }

    fun seekToZeroOnStartRecording(){
        mediaPlayer?.let { player ->
            if (player.isPlaying) {
                updatePlaybackState(PlaybackStateCompat.STATE_PAUSED)
                handler.postDelayed({
                    player.seekTo(0) // Rewind to the beginning
                    updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
                }, 100) // Adjust this delay as needed

            }

        }
    }
    fun pauseIfNotYet(){
        mediaPlayer?.let { player ->
            if (player.isPlaying) {
                player.pause()
                player.seekTo(0) // Rewind to the beginning
                updatePlaybackState(PlaybackStateCompat.STATE_PAUSED)
            }
        }
    }
    private val playSongRunnable = object : Runnable {
        override fun run() {
            mediaPlayer?.let { player ->
                if (!player.isPlaying) {
                    player.start()
                    updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
                    // Delay a bit longer to ensure the track starts playing
                    handler.postDelayed({
                        if (player.isPlaying) {
                            player.pause()
                            player.seekTo(0) // Rewind to the beginning
                            updatePlaybackState(PlaybackStateCompat.STATE_PAUSED)
                        }
                    }, 500) // Adjust this delay as needed
                }
            }
            handler.postDelayed(this, 60 * 1000) // Run every minute
        }
    }

    private fun stopplaySongRunnable() {
        handler.removeCallbacks(playSongRunnable)
    }



    override fun onCreate() {
        super.onCreate()

        Log.i("audioservice","oncreate entered")


        // Initialize and start the media player
        initializeMediaPlayer()

        // Initialize the media session
        initMediaSession()

        // Acquire the wake lock to keep the service running when the screen is off
        acquireWakeLock()

        Log.i("audioservice","startUpdatingPlaybackState started")

        // Start updating the playback state to reflect the current playback progress
        startUpdatingPlaybackState()

        handler.post(playSongRunnable)

        var notification = createNotification()
        startForeground(1, notification)
    }



    private fun stopUpdatingPlaybackState() {
        handler.removeCallbacks(updatePlaybackStateRunnable)
    }

    private fun startUpdatingPlaybackState() {
        Log.i("audioservice","startUpdatingPlaybackState entered")

        if (mediaPlayer != null && mediaSession != null) {
            handler.post(updatePlaybackStateRunnable)
        } else {
            Log.w("","SilentAudioService MediaPlayer or MediaSession is null, cannot start updating playback state")
        }
    }


    private fun initQueue() {
        val realQueueItems = mutableListOf<MediaSessionCompat.QueueItem>()

        // Assuming you have a list of real track IDs or URIs
        val trackList = listOf("pulse_audio.mp3", "silent_track.mp3") // Replace with your track identifiers

        trackList.forEachIndexed { index, trackId ->
            val description = MediaDescriptionCompat.Builder()
                .setMediaId(trackId)
                .setTitle("Track Title $index") // Replace with actual track title
                .build()
            val queueItem = MediaSessionCompat.QueueItem(description, index.toLong())
            realQueueItems.add(queueItem)
        }

        // Set the queue to MediaSession
        mediaSession?.setQueue(realQueueItems)
    }

    private fun initializeMediaPlayer() {

        val resourceId = R.raw.pulse_audio
        val afd = applicationContext.resources.openRawResourceFd(resourceId)
        if (afd == null) {
            Log.e("SilentAudioService", "Resource not found: $resourceId")
            return
        }

        mediaPlayer = MediaPlayer().apply {

            setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            afd.close()
            setLooping(true)
            setVolume(1.0f, 1.0f)
            prepare()
        }

    }



    private fun updatePlaybackState(state: Int) {
        val playbackState = PlaybackStateCompat.Builder()
            .setActions(PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE)
            .setState(state, mediaPlayer!!.currentPosition.toLong(), 1f)
            .build()
        mediaSession!!.setPlaybackState(playbackState)
    }


    private fun initMediaSession() {
        mediaSession = MediaSessionCompat(this, "SilentAudioService")
        mediaSession!!.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS
                or MediaSessionCompat.FLAG_HANDLES_QUEUE_COMMANDS
                or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)
        mediaSession!!.setCallback(object : MediaSessionCompat.Callback() {
            override fun onSkipToNext() {
                print("onSkipToNext")
                appBusinessLogic?.addHighlight()
            }

            override fun onSkipToPrevious() {
                print("onSkipToPrevious")
                appBusinessLogic?.addHighlight()
            }


            override fun onPlay() {
                println("OnPlay")
                appBusinessLogic?.startRecording()
            }

            override fun onPause() {
                println("OnPause")
                appBusinessLogic?.stopRecording()

            }

            override fun onStop() {
                println("OnStop")
                if (mediaPlayer != null) {
                    mediaPlayer!!.stop()
                    updatePlaybackState(PlaybackStateCompat.STATE_STOPPED)
                }
            }
        })
        val stateBuilder = PlaybackStateCompat.Builder()
            .setActions(PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PLAY_PAUSE or PlaybackStateCompat.ACTION_PAUSE)
            .setState(PlaybackStateCompat.STATE_PLAYING, mediaPlayer!!.currentPosition.toLong(), 1f)
        mediaSession!!.setPlaybackState(stateBuilder.build())
        mediaSession!!.isActive = true

        val metadata = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "Artist Name")
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, "Song Title")
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, mediaPlayer!!.duration.toLong())
            // You can add album art as well
            // .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, yourAlbumArtBitmap)
            .build()
        mediaSession!!.setMetadata(metadata)

        initQueue()

    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        val localWakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "MyApp::SilentAudioServiceWakelockTag"
        )
        localWakeLock.acquire()
        wakeLock = localWakeLock
    }



    fun updateMetadataAndNotification(artist: String, title: String) {
        playerArtistText = artist
        playerTitleText = title
        appBusinessLogic?.populateBusinessState()
        // Update the metadata for the MediaSession
        val metadata = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
            .build()
        mediaSession?.setMetadata(metadata)

        // Update the notification
        val notification = createNotification() // createNotification should be modified to use the updated metadata
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(1, notification) // use the same ID that was used in startForeground
    }



    private val updatePlaybackStateRunnable = object : Runnable {
        override fun run() {
            mediaPlayer?.let { mp ->
                try {
                    updateMetadataAndNotification(playerArtistText, playerTitleText)
                } catch (e: Exception) {
                    Log.w("SilentAudioService", "Error updating playback state or notification: ${e.message}")
                }
            }

            try {
                handler.postDelayed(this, 1000) // Adjust the delay as needed
            } catch (e: Exception) {
                Log.w("SilentAudioService", "Error in posting updatePlaybackStateRunnable: ${e.message}")
            }
        }
    }

    companion object {
        const val ACTION_STOP_SERVICE = "com.cliptracer.ClipTracer.STOP_SERVICE"
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_SERVICE -> {
                cleanupService()
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                MediaButtonReceiver.handleIntent(mediaSession, intent)
                return START_STICKY
            }
        }
    }



    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)

        // Stop the foreground service and remove the notification first
        stopForeground(true)

        // Perform the remaining cleanup tasks
        cleanupService()
    }

    fun cleanupService() {
        // Stop Runnables to prevent them from running after resources are released
        stopUpdatingPlaybackState()
        stopplaySongRunnable()
        // Release and nullify the MediaPlayer
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (e: Exception) {
            //Log.w("","SilentAudioService Error releasing MediaPlayer ${e.message}")
        } finally {
            mediaPlayer = null
        }

        // Release and nullify the MediaSession
        try {
            mediaSession?.release()
        } catch (e: Exception) {
            //Log.w("","SilentAudioService Error releasing MediaSession ${e.message}")
        } finally {
            mediaSession = null
        }

        // Release the WakeLock, if held
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        wakeLock = null // Nullify after releasing

        // Stop the service from being a foreground service and remove the notification
        stopForeground(true)

        // Stop the service itself
        stopSelf()
    }


    override fun onDestroy() {
        super.onDestroy()
        cleanupService()
    }


}
