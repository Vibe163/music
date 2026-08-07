package com.localmusic.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.localmusic.app.data.model.ImportLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ImportLogDao {

    @Insert
    suspend fun insert(log: ImportLogEntity)

    @Query("SELECT * FROM import_logs ORDER BY timestamp DESC, id DESC")
    fun observeAll(): Flow<List<ImportLogEntity>>
}
