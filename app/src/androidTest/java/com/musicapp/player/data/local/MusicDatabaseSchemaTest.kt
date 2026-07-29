package com.musicapp.player.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.IOException
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MusicDatabaseSchemaTest {
  @get:Rule
  val migrationHelper =
    MigrationTestHelper(
      InstrumentationRegistry.getInstrumentation(),
      MusicDatabase::class.java,
    )

  @Test
  @Throws(IOException::class)
  fun exportedVersionOneSchema_createsDatabase() {
    migrationHelper.createDatabase(DATABASE_NAME, 1).close()
  }

  private companion object {
    const val DATABASE_NAME = "schema-version-one-test"
  }
}
