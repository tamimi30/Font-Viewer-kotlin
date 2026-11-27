package com.oneui.fontviewer.ui.main.fragment.systemfont

import android.app.Application
import android.os.Build
import android.util.Log

import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

import com.oneui.fontviewer.data.common.room.FontEntity
import com.oneui.fontviewer.data.systemfont.SystemFontRepository

class SystemFontListViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: SystemFontRepository = SystemFontRepository.getInstance(application)
    val fontsLiveData: LiveData<List<FontEntity>> = repository.systemFonts
    private val isLoadingMutable = MutableLiveData(false)
    private val errorMessageLiveData = MutableLiveData<String>()
    private val isApiAvailableMutable = MutableLiveData(checkApiAvailability())

    val isLoadingLiveData: LiveData<Boolean>
        get() = isLoadingMutable

    val isApiAvailableLiveData: LiveData<Boolean>
        get() = isApiAvailableMutable

    private fun checkApiAvailability(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    }

    fun loadSystemFonts() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            errorMessageLiveData.postValue("SystemFonts API requires Android 10 (API 29) or higher")
            isApiAvailableMutable.postValue(false)
            return
        }

        val hasExistingData = fontsLiveData.value != null && !fontsLiveData.value!!.isEmpty()

        if (!hasExistingData) {
            isLoadingMutable.postValue(true)
        }

        repository.loadAndSyncSystemFonts { added, updated, deleted ->
            if (!hasExistingData) {
                isLoadingMutable.postValue(false)
            }

            val message = String.format(
                "Synced: %d added, %d updated, %d deleted",
                added, updated, deleted
            )
            Log.d(TAG, message)

            if (added == 0 && updated == 0 && deleted == 0) {
                Log.d(TAG, "No changes in system fonts")
            }
        }
    }

    fun recordFontAccess(fontPath: String?) {
        if (fontPath != null && !fontPath.isEmpty()) {
            repository.recordAccess(fontPath)
        }
    }

    companion object {
        private const val TAG = "SystemFontListViewModel"
    }

}
