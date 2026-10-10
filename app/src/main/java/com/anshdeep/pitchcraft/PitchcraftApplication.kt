package com.anshdeep.pitchcraft

import android.app.Application
import com.anshdeep.pitchcraft.data.FaceDatabase

class PitchcraftApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        FaceDatabase.get(this)
    }
}
