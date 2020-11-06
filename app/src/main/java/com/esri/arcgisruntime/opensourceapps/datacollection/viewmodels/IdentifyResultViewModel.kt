/*
 * Copyright 2020 Esri
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.esri.arcgisruntime.opensourceapps.datacollection.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.esri.arcgisruntime.data.Feature
import com.esri.arcgisruntime.layers.FeatureLayer
import com.esri.arcgisruntime.layers.LayerContent
import com.esri.arcgisruntime.mapping.GeoElement
import com.esri.arcgisruntime.mapping.view.IdentifyLayerResult
import com.esri.arcgisruntime.opensourceapps.datacollection.util.Event
import com.esri.arcgisruntime.opensourceapps.datacollection.util.raiseEvent

/**
 * The view model for IdentifyResultFragment, that is responsible for processing the result of
 * identify layer operation on MapView, highlighting the selected feature and displaying the values
 * of the display fields of result Popup in the bottom sheet.
 */
class IdentifyResultViewModel(val popupViewModel: PopupViewModel) : ViewModel() {

    private val _showIdentifiedPopupAttributesEvent = MutableLiveData<Event<Unit>>()
    val showIdentifiedPopupAttributesEvent: LiveData<Event<Unit>> = _showIdentifiedPopupAttributesEvent

    private val _identifyLayerResult = MutableLiveData<IdentifyLayerResult>()
    val identifyLayerResult: LiveData<IdentifyLayerResult> = _identifyLayerResult

    private val _showPopupAttributeListEvent = MutableLiveData<Event<Unit>>()
    val showPopupAttributeListEvent: LiveData<Event<Unit>> = _showPopupAttributeListEvent

    /**
     * Factory class to help create an instance of [IdentifyResultViewModel] with a [PopupViewModel].
     */
    class Factory(private val popupViewModel: PopupViewModel) :
        ViewModelProvider.Factory {
        override fun <T : ViewModel?> create(modelClass: Class<T>): T {
            return IdentifyResultViewModel(popupViewModel) as T
        }
    }

    /**
     * Highlights the result popup in the GeoView.
     *
     * @param identifyLayerResult
     */
    fun processIdentifyLayerResult(
        identifyLayerResult: IdentifyLayerResult
    ) {
        if (identifyLayerResult.popups.size > 0) {
            _identifyLayerResult.value = identifyLayerResult
            popupViewModel.setPopup(identifyLayerResult.popups[0])
            _showIdentifiedPopupAttributesEvent.raiseEvent()
            highlightFeatureInFeatureLayer(
                identifyLayerResult.layerContent,
                identifyLayerResult.popups[0].geoElement
            )
        }
    }

    /**
     * Resets the result of the identify operation
     */
    fun resetIdentifyLayerResult() {
        _identifyLayerResult.value = null
    }

    /**
     * Called when user taps on the identify result bottom sheet. Kicks off PopupAttributeListFragment
     */
    fun showPopupAttributeList() {
        _showPopupAttributeListEvent.raiseEvent()
    }

    /**
     * Called when user taps on the identified popup attribute list header in the expanded bottom sheet
     * Kicks off IdentifyResultFragment
     */
    fun showIdentifiedPopupAttributes() {
        _showIdentifiedPopupAttributesEvent.raiseEvent()
    }


    /**
     * Highlights the GeoElement in the GeoView.
     *
     * @param layerContent
     * @param geoelement
     */
    fun highlightFeatureInFeatureLayer(layerContent: LayerContent, geoelement: GeoElement) {
        val featureLayer: FeatureLayer? = layerContent as? FeatureLayer
        featureLayer?.selectFeature(geoelement as Feature)
    }

}
