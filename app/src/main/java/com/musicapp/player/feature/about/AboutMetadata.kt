package com.musicapp.player.feature.about

import android.content.Context
import androidx.core.content.pm.PackageInfoCompat
import com.musicapp.player.R
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

data class AboutMetadata(
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val openSourceLicenseText: String,
) {
    init {
        require(packageName.isNotBlank()) { "packageName must not be blank" }
        require(versionName.isNotBlank()) { "versionName must not be blank" }
        require(versionCode >= 0) { "versionCode must not be negative" }
        require(openSourceLicenseText.isNotBlank()) { "openSourceLicenseText must not be blank" }
    }
}

fun interface AboutMetadataSource {
    fun load(): AboutMetadata
}

@Singleton
class AndroidAboutMetadataSource @Inject constructor(
    @ApplicationContext private val context: Context,
) : AboutMetadataSource {
    @Suppress("DEPRECATION")
    override fun load(): AboutMetadata {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val licenseText =
            context.resources.openRawResource(R.raw.open_source_licenses).bufferedReader().use {
                it.readText()
            }
        return AboutMetadata(
            packageName = context.packageName,
            versionName = packageInfo.versionName.orEmpty().ifBlank { UNKNOWN_VERSION_NAME },
            versionCode = PackageInfoCompat.getLongVersionCode(packageInfo),
            openSourceLicenseText = licenseText,
        )
    }

    private companion object {
        const val UNKNOWN_VERSION_NAME = "0"
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AboutMetadataModule {
    @Binds
    @Singleton
    abstract fun bindAboutMetadataSource(
        implementation: AndroidAboutMetadataSource,
    ): AboutMetadataSource
}
