package com.oneui.fontviewer.data.common.list

import android.content.Context
import android.util.Log

import io.reactivex.rxjava3.schedulers.Schedulers

import com.oneui.fontviewer.data.AppDataStore

class SortPreferenceManager(context: Context) {

    private val dataStore: AppDataStore = AppDataStore.getInstance(context)

    fun loadSortTypeName(listType: String): String {
        return when (listType) {
            LIST_TYPE_SYSTEM -> dataStore.systemSortType.blockingFirst()
            LIST_TYPE_FAVORITES -> dataStore.favoritesSortType.blockingFirst()
            else -> dataStore.sortType.blockingFirst()
        }
    }

    fun loadSortAscending(listType: String): Boolean {
        return when (listType) {
            LIST_TYPE_SYSTEM -> dataStore.systemSortAscending.blockingFirst()
            LIST_TYPE_FAVORITES -> dataStore.favoritesSortAscending.blockingFirst()
            else -> dataStore.sortAscending.blockingFirst()
        }
    }

    fun saveSortType(listType: String, sortTypeName: String) {
        when (listType) {

            LIST_TYPE_SYSTEM ->
                dataStore.setSystemSortType(sortTypeName)
                    .subscribeOn(Schedulers.io())
                    .subscribe(
                        { Log.d(TAG, "[SYSTEM] Saved sort type: $sortTypeName") },
                        { error -> Log.e(TAG, "[SYSTEM] Error saving sort type", error) }
                    )

            LIST_TYPE_FAVORITES ->
                dataStore.setFavoritesSortType(sortTypeName)
                    .subscribeOn(Schedulers.io())
                    .subscribe(
                        { Log.d(TAG, "[FAVORITES] Saved sort type: $sortTypeName") },
                        { error -> Log.e(TAG, "[FAVORITES] Error saving sort type", error) }
                    )

            else ->
                dataStore.setSortType(sortTypeName)
                    .subscribeOn(Schedulers.io())
                    .subscribe(
                        { Log.d(TAG, "[LOCAL] Saved sort type: $sortTypeName") },
                        { error -> Log.e(TAG, "[LOCAL] Error saving sort type", error) }
                    )
        }
    }

    fun saveSortAscending(listType: String, ascending: Boolean) {
        when (listType) {

            LIST_TYPE_SYSTEM ->
                dataStore.setSystemSortAscending(ascending)
                    .subscribeOn(Schedulers.io())
                    .subscribe(
                        { Log.d(TAG, "[SYSTEM] Saved sort ascending: $ascending") },
                        { error -> Log.e(TAG, "[SYSTEM] Error saving sort ascending", error) }
                    )

            LIST_TYPE_FAVORITES ->
                dataStore.setFavoritesSortAscending(ascending)
                    .subscribeOn(Schedulers.io())
                    .subscribe(
                        { Log.d(TAG, "[FAVORITES] Saved sort ascending: $ascending") },
                        { error -> Log.e(TAG, "[FAVORITES] Error saving sort ascending", error) }
                    )

            else ->
                dataStore.setSortAscending(ascending)
                    .subscribeOn(Schedulers.io())
                    .subscribe(
                        { Log.d(TAG, "[LOCAL] Saved sort ascending: $ascending") },
                        { error -> Log.e(TAG, "[LOCAL] Error saving sort ascending", error) }
                    )
        }
    }

    companion object {
        private const val TAG = "SortPreferenceManager"

        const val LIST_TYPE_LOCAL = "LOCAL"
        const val LIST_TYPE_SYSTEM = "SYSTEM"
        const val LIST_TYPE_FAVORITES = "FAVORITES"
    }
}
