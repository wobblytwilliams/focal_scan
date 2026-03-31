package au.edu.cqu.focalapp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import au.edu.cqu.focalapp.data.repository.FocalRepository
import au.edu.cqu.focalapp.util.TimeProvider

class FocalSamplingViewModelFactory(
    private val repository: FocalRepository,
    private val timeProvider: TimeProvider
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(FocalSamplingViewModel::class.java)) {
            return FocalSamplingViewModel(repository, timeProvider) as T
        }

        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
