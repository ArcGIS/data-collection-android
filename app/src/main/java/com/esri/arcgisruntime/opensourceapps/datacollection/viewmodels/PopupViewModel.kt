/*
 * Copyright 2020 Esri
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.esri.arcgisruntime.opensourceapps.datacollection.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.esri.arcgisruntime.ArcGISRuntimeException
import com.esri.arcgisruntime.concurrent.ListenableFuture
import com.esri.arcgisruntime.data.Feature
import com.esri.arcgisruntime.data.FeatureEditResult
import com.esri.arcgisruntime.data.FeatureTable
import com.esri.arcgisruntime.data.ServiceFeatureTable
import com.esri.arcgisruntime.mapping.popup.Popup
import com.esri.arcgisruntime.mapping.popup.PopupManager
import com.esri.arcgisruntime.opensourceapps.datacollection.util.Event
import com.esri.arcgisruntime.opensourceapps.datacollection.util.raiseEvent

/**
 * The view model that represents a Popup. It supports:
 *
 * <ul>
 * <li>Viewing a GeoElement's attributes
 * <li>Editing a GeoElement's attributes as well as saving
 * </ul>
 *
 * A PopupViewModel can be bound to a PopupView for visualisation of the Popup GeoElement's
 * attributes and editing experience.
 */
class PopupViewModel(application: Application) : AndroidViewModel(application) {

    private val _popup = MutableLiveData<Popup>()
    // The Popup whose fields are viewed and edited in the PopupView
    val popup: LiveData<Popup> = _popup

    private val _popupManager = MutableLiveData<PopupManager>()
    // The manager for the Popup responsible for viewing and editing of the Popup
    val popupManager: LiveData<PopupManager> = _popupManager

    private val _isPopupInEditMode = MutableLiveData<Boolean>()
    // Depicts whether the PopupManager is currently in editing mode
    // When in edit mode the user can edit the values of fields of the Popup
    val isPopupInEditMode: LiveData<Boolean> = _isPopupInEditMode

    private val _showSavePopupErrorEvent = MutableLiveData<Event<String>>()
    // This event is raised when an error is encountered while trying to save edits on a popup.
    // It passes the exception message to all the observers in the observeEvent() method.
    val showSavePopupErrorEvent: LiveData<Event<String>> = _showSavePopupErrorEvent

    private val _showSavingProgressEvent = MutableLiveData<Event<Boolean>>()
    // This event is raised when the save operation begins/ends.
    // It passes true to all the observers in the observeEvent() method when the save operation
    // is initiated and false when it ends.
    val showSavingProgressEvent: LiveData<Event<Boolean>> = _showSavingProgressEvent

    private val _confirmCancelPopupEditingEvent = MutableLiveData<Event<Unit>>()
    // This event is raised when the user cancels the edit mode on the Popup.
    // It is used for showing confirmation dialog to the user, before calling cancelEditing()
    val confirmCancelPopupEditingEvent: LiveData<Event<Unit>> = _confirmCancelPopupEditingEvent

    private val _dismissPopupEvent = MutableLiveData<Event<Unit>>()
    // This event is raised when the user taps on the close button to dismiss the popup.
    val dismissPopupEvent: LiveData<Event<Unit>> = _dismissPopupEvent

    /**
     * Updates popup property to set the popup field values being displayed in
     * the bottom sheet. Creates PopupManager for PopupView to perform edit operations.
     *
     * @param popup
     */
    fun setPopup(popup: Popup) {
        _popup.value = popup
        _popupManager.value = PopupManager(getApplication(), _popup.value)
    }

    /**
     * Enables/disables edit mode on the PopupView
     */
    fun setEditMode(isEnabled: Boolean) {
        _isPopupInEditMode.value = isEnabled
    }

    /**
     * Clear the popup
     */
    fun clearPopup() {
        _popup.value = null
        _popupManager.value = null
    }

    /**
     * Cancels the edit mode.
     */
    fun cancelEditing() {
        _popupManager.value?.cancelEditing()
        _isPopupInEditMode.value = false
    }

    /**
     * Raises ConfirmCancelPopupEditingEvent that can be observed and used for
     * prompting user with confirmation dialog to make sure the user wants to cancel edits.
     * To be followed by cancelEditing() if the user response is positive.
     */
    fun confirmCancelEditing() {
        _confirmCancelPopupEditingEvent.raiseEvent()
    }

    /**
     * Raises an event to dismiss the popup
     */
    fun dismissPopup() {
        _dismissPopupEvent.raiseEvent()
    }

    /**
     * Saves Popup edits by applying changes to the feature service associated with a Popup's
     * feature.
     */
    fun savePopupEdits() {
        // show the Progress bar informing user that save operation is in progress
        _showSavingProgressEvent.raiseEvent(true)
        _popupManager.value?.let { popupManager ->
            // Call finishEditingAsync() to apply edit changes locally and end the popup manager
            // editing session
            val finishEditingFuture: ListenableFuture<ArcGISRuntimeException> =
                popupManager.finishEditingAsync()
            finishEditingFuture.addDoneListener {
                try {
                    finishEditingFuture.get()

                    // The edits were applied successfully to the local geodatabase,
                    // push those changes to the feature service by calling applyEditsAsync()
                    val feature: Feature = popupManager.popup.geoElement as Feature
                    val featureTable: FeatureTable = feature.featureTable
                    if (featureTable is ServiceFeatureTable) {
                        val applyEditsFuture: ListenableFuture<List<FeatureEditResult>> =
                            featureTable.applyEditsAsync()
                        applyEditsFuture.addDoneListener {
                            // dismiss the Progress bar
                            _showSavingProgressEvent.raiseEvent(false)
                            // dismiss edit mode
                            _isPopupInEditMode.value = false
                            try {
                                val featureEditResults: List<FeatureEditResult> =
                                    applyEditsFuture.get()
                                // Check for errors in FeatureEditResults
                                if (featureEditResults.any { result -> result.hasCompletedWithErrors() }) {
                                    // an error was encountered when trying to apply edits
                                    val exception =
                                        featureEditResults.filter { featureEditResult -> featureEditResult.hasCompletedWithErrors() }[0].error
                                    // show the error message to the user
                                    exception.message?.let { exceptionMessage ->
                                        _showSavePopupErrorEvent.raiseEvent(exceptionMessage)
                                    }
                                }

                            } catch (exception: Exception) {
                                // show the error message to the user
                                exception.message?.let { exceptionMessage ->
                                    _showSavePopupErrorEvent.raiseEvent(exceptionMessage)
                                }
                            }
                        }
                    }
                } catch (exception: Exception) {
                    // dismiss the Progress bar
                    _showSavingProgressEvent.raiseEvent(false)
                    // show the error message to the user
                    exception.message?.let { exceptionMessage ->
                        _showSavePopupErrorEvent.raiseEvent(exceptionMessage)
                    }
                }
            }
        }
    }
}
