package com.orion.store

import android.os.VibratorManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.orion.store.ui.H
import com.orion.store.ui.MainViewModel
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Haptics must reach the device vibrator. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class HapticsTest {
    @Test fun hapticVibrates() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val vm = MainViewModel(app)
        vm.haptic(H.MEDIUM)
        val v = app.getSystemService(VibratorManager::class.java).defaultVibrator
        assertTrue(shadowOf(v).isVibrating)
    }
}
