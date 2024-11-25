package org.akanework.gramophone.ui

import android.content.Intent
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.preference.PreferenceManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.getBooleanStrict
import org.akanework.gramophone.logic.playOrPause
import org.akanework.gramophone.logic.startAnimation
import org.akanework.gramophone.logic.utils.CalculationUtils.convertDurationToTimeStamp
import org.akanework.gramophone.logic.utils.exoplayer.GramophoneMediaSourceFactory
import org.akanework.gramophone.logic.utils.exoplayer.GramophoneRenderFactory
import org.akanework.gramophone.ui.components.FullBottomSheet.Companion.SLIDER_UPDATE_INTERVAL
import org.akanework.gramophone.ui.components.SquigglyProgress

@OptIn(UnstableApi::class)
class AudioPreviewActivity : AppCompatActivity() {

    private lateinit var d: AlertDialog
    private lateinit var player: ExoPlayer
    private lateinit var audioTitle: TextView
    private lateinit var artistTextView: TextView
    private lateinit var currentPositionTextView: TextView
    private lateinit var durationTextView: TextView
    private lateinit var albumArt: ImageView
    private lateinit var timeSlider: Slider
    private lateinit var timeSeekbar: SeekBar
    private lateinit var playPauseButton: MaterialButton
    private lateinit var progressDrawable: SquigglyProgress
    private lateinit var prefs: SharedPreferences

    private val handler = Handler(Looper.getMainLooper())
    private var runnableRunning = false
    private var isUserTracking = false

    private val positionRunnable = object : Runnable {
        override fun run() {
            val position = convertDurationToTimeStamp(player.currentPosition)
            val duration = player.currentMediaItem?.mediaMetadata?.durationMs

//            if (duration != null && !isUserTracking) {
//                timeSeekbar.max = duration.toInt()
//                timeSeekbar.progress = player.currentPosition.toInt()
//                timeSlider.valueTo = duration.toFloat()
//                timeSlider.value = min(player.currentPosition.toFloat(), timeSlider.valueTo)
//                currentPositionTextView.text = position
//            }

            if (player.isPlaying && runnableRunning) {
                handler.postDelayed(this, SLIDER_UPDATE_INTERVAL)
            } else {
                runnableRunning = false
            }
        }
    }

    private val prefChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            "default_progress_bar" -> updateSliderVisibility()
        }
    }

    private val touchListener =
        object : SeekBar.OnSeekBarChangeListener, Slider.OnSliderTouchListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    currentPositionTextView.text = convertDurationToTimeStamp((progress.toLong()))
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isUserTracking = true
                progressDrawable.animate = false
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val mediaId = player.currentMediaItem?.mediaId
                if (mediaId != null) {
                    if (seekBar != null) {
                        player.seekTo((seekBar.progress.toLong()))
                    }
                }
                isUserTracking = false
                progressDrawable.animate = player.isPlaying == true || player.playWhenReady == true
            }

            override fun onStartTrackingTouch(slider: Slider) {
                isUserTracking = true
            }

            override fun onStopTrackingTouch(slider: Slider) {
                val mediaId = player.currentMediaItem?.mediaId
                if (mediaId != null) {
                    player.seekTo((slider.value.toLong()))
                }
                isUserTracking = false
            }
        }

    // TODO and way to open this song in gramophone IF its part of library
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = PreferenceManager.getDefaultSharedPreferences(this)

        d = MaterialAlertDialogBuilder(this)
            .setView(R.layout.activity_audio_preview)
            .setOnDismissListener {
                runnableRunning = false
                player.release()
                handler.postDelayed(this::finish, 200)
            }
            .show()
        audioTitle = d.findViewById(R.id.title_text_view)!!
        artistTextView = d.findViewById(R.id.artist_text_view)!!
        currentPositionTextView = d.findViewById(R.id.current_position_text_view)!!
        durationTextView = d.findViewById(R.id.duration_text_view)!!
        albumArt = d.findViewById(R.id.album_art)!!
        timeSlider = d.findViewById(R.id.time_slider)!!
        timeSeekbar = d.findViewById(R.id.slider_squiggly)!!
        playPauseButton = d.findViewById(R.id.play_pause_replay_button)!!

        prefs.registerOnSharedPreferenceChangeListener(prefChangeListener)
        updateSliderVisibility()

        val seekBarProgressWavelength =
            resources.getDimensionPixelSize(R.dimen.media_seekbar_progress_wavelength).toFloat()
        val seekBarProgressAmplitude =
            resources.getDimensionPixelSize(R.dimen.media_seekbar_progress_amplitude).toFloat()
        val seekBarProgressPhase =
            resources.getDimensionPixelSize(R.dimen.media_seekbar_progress_phase).toFloat()
        val seekBarProgressStrokeWidth =
            resources.getDimensionPixelSize(R.dimen.media_seekbar_progress_stroke_width).toFloat()

        timeSeekbar.progressDrawable = SquigglyProgress().also {
            progressDrawable = it
            it.waveLength = seekBarProgressWavelength
            it.lineAmplitude = seekBarProgressAmplitude
            it.phaseSpeed = seekBarProgressPhase
            it.strokeWidth = seekBarProgressStrokeWidth
            it.transitionEnabled = true
            it.animate = false
        }

        player = ExoPlayer.Builder(
            this,
            GramophoneRenderFactory(this)
                .setEnableAudioFloatOutput(
                    prefs.getBooleanStrict("floatoutput", false)
                )
                .setEnableDecoderFallback(true)
                .setEnableAudioTrackPlaybackParams( // hardware/system-accelerated playback speed
                    prefs.getBooleanStrict("ps_hardware_acc", true)
                )
                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER),
            GramophoneMediaSourceFactory(this)
        )
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setAudioAttributes(
                AudioAttributes
                    .Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(), true
            ).build()
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                onIsPlayingChanged()
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                positionRunnable.run()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (mediaItem == null) return
                updateMediaMetadata(mediaItem.mediaMetadata)
            }

            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                updateMediaMetadata(mediaMetadata)
            }
        })
        playPauseButton.setOnClickListener {
            if (player.playbackState == Player.STATE_ENDED) player.seekToDefaultPosition()
            player.playOrPause()
        }

        timeSlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                player.seekTo(value.toLong())
                currentPositionTextView.text = convertDurationToTimeStamp(value.toLong())
            }
        }

        timeSeekbar.setOnSeekBarChangeListener(touchListener)
        timeSlider.addOnSliderTouchListener(touchListener)

        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        if (intent.action == Intent.ACTION_VIEW) {
            intent.data?.let { uri ->
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(this, uri)
                val durationMs =
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        ?.toLongOrNull()

                val mediaItem = MediaItem.Builder()
                    .setMediaId(uri.toString())
                    .setUri(uri)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setDurationMs(durationMs)
                            .build()
                    )
                    .build()

                player.setMediaItem(mediaItem)
                player.prepare()
                player.play()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        player.playWhenReady = false
    }

    override fun onDestroy() {
        prefs.unregisterOnSharedPreferenceChangeListener(prefChangeListener)
        if (d.isShowing)
            d.dismiss()
        super.onDestroy()
    }

    private fun updateSliderVisibility() {
        if (prefs.getBooleanStrict("default_progress_bar", false)) {
            timeSlider.visibility = View.VISIBLE
            timeSeekbar.visibility = View.GONE
        } else {
            timeSlider.visibility = View.GONE
            timeSeekbar.visibility = View.VISIBLE
        }
    }

    private fun onIsPlayingChanged() {
        if (player.isPlaying) {
            if (playPauseButton.getTag(R.id.play_next) as Int? != 1) {
                playPauseButton.icon = AppCompatResources.getDrawable(this, R.drawable.play_anim)
                playPauseButton.icon.startAnimation()
                playPauseButton.setTag(R.id.play_next, 1)
            }
            if (!isUserTracking) {
                progressDrawable.animate = true
            }
            if (!runnableRunning) {
                runnableRunning = true
                handler.postDelayed(positionRunnable, SLIDER_UPDATE_INTERVAL)
            }
        } else if (player.playbackState != Player.STATE_BUFFERING) {
            if (playPauseButton.getTag(R.id.play_next) as Int? != 2) {
                playPauseButton.icon =
                    AppCompatResources.getDrawable(this, R.drawable.pause_anim)
                playPauseButton.icon.startAnimation()
                playPauseButton.setTag(R.id.play_next, 2)
            }
            if (!isUserTracking) {
                progressDrawable.animate = false
            }
        }
    }

    private fun updateMediaMetadata(mediaMetadata: MediaMetadata) {
        audioTitle.text = mediaMetadata.title ?: getString(R.string.unknown_title)
        artistTextView.text = mediaMetadata.artist ?: getString(R.string.unknown_artist)
        durationTextView.text = mediaMetadata.durationMs?.let { convertDurationToTimeStamp(it) }
        mediaMetadata.artworkData?.let {
            val bitmap = BitmapFactory.decodeByteArray(it, 0, it.size)
            albumArt.setImageBitmap(bitmap)
        } ?: run {
            albumArt.setImageResource(R.drawable.ic_default_cover)
        }
    }
}
