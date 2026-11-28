package com.ramkotimartha.expensetracker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.ramkotimartha.expensetracker.data.OnboardingManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OnboardingManagerTest {

    @Test
    fun `starts incomplete on fresh install and persists completion across instances`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("onboarding_prefs", Context.MODE_PRIVATE).edit().clear().commit()

        val firstLaunchManager = OnboardingManager(context)
        assertFalse(firstLaunchManager.isCompleted)
        assertFalse(firstLaunchManager.hasCompletedOnboarding.value)

        firstLaunchManager.setCompleted()
        assertTrue(firstLaunchManager.isCompleted)
        assertTrue(firstLaunchManager.hasCompletedOnboarding.value)

        // Subsequent cold start reads persisted completion synchronously
        val secondLaunchManager = OnboardingManager(context)
        assertTrue(secondLaunchManager.isCompleted)
        assertTrue(secondLaunchManager.hasCompletedOnboarding.value)
    }
}
