package au.edu.cqu.focalapp

import android.app.Application
import au.edu.cqu.focalapp.data.local.FocalDatabase
import au.edu.cqu.focalapp.data.repository.FocalRepository
import au.edu.cqu.focalapp.util.SystemTimeProvider
import au.edu.cqu.focalapp.util.TimeProvider

class FocalSamplingApplication : Application() {
    val repository: FocalRepository by lazy {
        FocalRepository(FocalDatabase.getInstance(this).focalDao())
    }

    val timeProvider: TimeProvider = SystemTimeProvider
}
