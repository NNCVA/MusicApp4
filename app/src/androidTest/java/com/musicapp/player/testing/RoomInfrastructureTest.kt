package com.musicapp.player.testing

import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@Entity(tableName = "wave0_test_marker")
internal data class TestMarker(@PrimaryKey val id: Int)

@Database(entities = [TestMarker::class], version = 1, exportSchema = false)
internal abstract class InfrastructureTestDatabase : RoomDatabase()

@RunWith(AndroidJUnit4::class)
class RoomInfrastructureTest {
  @Test
  fun inMemoryDatabase_opensWithRealSqlite() {
    val database =
      Room.inMemoryDatabaseBuilder(
          ApplicationProvider.getApplicationContext(),
          InfrastructureTestDatabase::class.java,
        )
        .build()

    try {
      database.openHelper.writableDatabase
      assertTrue(database.isOpen)
    } finally {
      database.close()
    }
  }
}
