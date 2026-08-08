package com.musicapp.player.feature.about

import android.content.Context
import androidx.core.content.pm.PackageInfoCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.musicapp.player.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AboutMetadataTest {
    @Test
    fun installedPackageVersionAndBundledLicenseAreReadableWithoutNetwork() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val metadata = AndroidAboutMetadataSource(context).load()
        @Suppress("DEPRECATION")
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val directResourceText =
            context.resources.openRawResource(R.raw.open_source_licenses).bufferedReader().use {
                it.readText()
            }

        assertEquals(context.packageName, metadata.packageName)
        assertEquals(packageInfo.versionName.orEmpty().ifBlank { "0" }, metadata.versionName)
        assertEquals(PackageInfoCompat.getLongVersionCode(packageInfo), metadata.versionCode)
        assertEquals(directResourceText, metadata.openSourceLicenseText)
        assertTrue(metadata.openSourceLicenseText.contains("Apache License"))
        listOf(
            "kotlinx-coroutines-android:1.11.0",
            "core-ktx:1.18.0",
            "appcompat:1.7.1",
            "hilt-android:2.60.1",
            "room-runtime:2.8.4",
            "datastore-preferences:1.2.1",
            "media3-exoplayer:1.10.1",
            "Compose BOM 2026.03.01",
            "navigation3-runtime:1.0.1",
        ).forEach { notice ->
            assertTrue("missing offline notice for $notice", metadata.openSourceLicenseText.contains(notice))
        }
    }

    @Test
    fun licenseVisibilityIsDrivenByAboutState() {
        val metadata =
            AboutMetadata(
                packageName = "com.musicapp.player",
                versionName = "1.0",
                versionCode = 1,
                openSourceLicenseText = "Offline license",
            )
        val viewModel = AboutViewModel(AboutMetadataSource { metadata })

        viewModel.showLicenses()
        assertTrue(viewModel.uiState.value.isLicenseVisible)
        viewModel.dismissLicenses()
        assertFalse(viewModel.uiState.value.isLicenseVisible)
    }
}
