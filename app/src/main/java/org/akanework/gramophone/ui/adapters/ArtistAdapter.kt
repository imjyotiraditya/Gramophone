package org.akanework.gramophone.ui.adapters

import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.widget.PopupMenu
import androidx.lifecycle.MutableLiveData
import androidx.preference.PreferenceManager
import org.akanework.gramophone.MainActivity
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.utils.MediaStoreUtils
import org.akanework.gramophone.ui.fragments.GeneralSubFragment

/**
 * [ArtistAdapter] is an adapter for displaying artists.
 */
class ArtistAdapter(
    private val mainActivity: MainActivity,
    private val artistList: MutableLiveData<MutableList<MediaStoreUtils.Artist>>,
    private val albumArtists: MutableLiveData<MutableList<MediaStoreUtils.Artist>>,
) : ItemAdapter<MediaStoreUtils.Artist>
    (mainActivity, artistList, Sorter.from(), pluralStr = R.plurals.artists) {

    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    var isAlbumArtist = prefs.getBoolean("isDisplayingAlbumArtist", false)
        private set
    override val defaultCover = R.drawable.ic_default_cover_artist

    override fun getItemViewType(position: Int): Int {
        return R.layout.adapter_list_card_larger
    }

    override fun titleOf(item: MediaStoreUtils.Artist): String {
        return item.title ?: context.getString(R.string.unknown_artist)
    }

    override fun onClick(item: MediaStoreUtils.Artist) {
        mainActivity.supportFragmentManager
            .beginTransaction()
            .addToBackStack("SUBFRAG")
            .hide(mainActivity.supportFragmentManager.fragments[0])
            .add(
                R.id.container,
                GeneralSubFragment().apply {
                    arguments =
                        Bundle().apply {
                            putInt("Position", toRawPos(item))
                            putInt("Item",
                                if (!isAlbumArtist)
                                    2
                                else
                                    5)
                            putString("Title", titleOf(item))
                        }
                },
            ).commit()
    }

    override fun onMenu(item: MediaStoreUtils.Artist, popupMenu: PopupMenu) {

        popupMenu.inflate(R.menu.more_menu_less)

        popupMenu.setOnMenuItemClickListener { it1 ->
            when (it1.itemId) {
                R.id.play_next -> {
                    val mediaController = mainActivity.getPlayer()
                    mediaController.addMediaItems(
                        mediaController.currentMediaItemIndex + 1,
                        item.songList,
                    )
                    true
                }

                /*
				R.id.share -> {
					val builder = ShareCompat.IntentBuilder(mainActivity)
					val mimeTypes = mutableSetOf<String>()
					builder.addStream(viewModel.fileUriList.value?.get(songList[holder.bindingAdapterPosition].mediaId.toLong())!!)
					mimeTypes.add(viewModel.mimeTypeList.value?.get(songList[holder.bindingAdapterPosition].mediaId.toLong())!!)
					builder.setType(mimeTypes.singleOrNull() ?: "audio/*").startChooser()
				 } */
				 */
                else -> false
            }
        }
    }

    override fun createDecorAdapter(): BaseDecorAdapter<out BaseAdapter<MediaStoreUtils.Artist>> {
        return ArtistDecorAdapter(this)
    }

    private fun setAlbumArtist(albumArtist: Boolean) {
        isAlbumArtist = albumArtist
        prefs.edit().putBoolean("isDisplayingAlbumArtist", isAlbumArtist).apply()
        if (bgHandler != null)
            liveData?.removeObserver(this)
        liveData = if (isAlbumArtist) albumArtists else artistList
        if (bgHandler != null)
            liveData?.observeForever(this)
    }

    private class ArtistDecorAdapter(
        artistAdapter: ArtistAdapter
    ) : BaseDecorAdapter<ArtistAdapter>(artistAdapter, R.plurals.artists) {

        override fun onSortButtonPressed(popupMenu: PopupMenu) {
            popupMenu.menu.findItem(R.id.album_artist).isVisible = true
            popupMenu.menu.findItem(R.id.album_artist).isChecked = adapter.isAlbumArtist
        }

        override fun onExtraMenuButtonPressed(menuItem: MenuItem): Boolean {
            return when (menuItem.itemId) {
                R.id.album_artist -> {
                    menuItem.isChecked = !menuItem.isChecked
                    adapter.setAlbumArtist(menuItem.isChecked)
                    true
                }

                else -> false
            }
        }
    }
}