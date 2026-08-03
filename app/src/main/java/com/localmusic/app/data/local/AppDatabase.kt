package com.localmusic.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.localmusic.app.data.model.Playlist
import com.localmusic.app.data.model.PlaylistSong
import com.localmusic.app.data.model.SongEntity

@Database(
    entities = [SongEntity::class, Playlist::class, PlaylistSong::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun songDao(): SongDao
    abstract fun playlistDao(): PlaylistDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        /** v2 → v3：新增 playCount（播放次数）和 favorite（喜欢）字段。 */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE songs ADD COLUMN playCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE songs ADD COLUMN favorite INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** v3 → v4：新增 md5 字段（用于迁移时基于内容精确匹配）。 */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE songs ADD COLUMN md5 TEXT")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "local_music.db"
                )
                    // v1 的 playlist_songs 引用的是 MediaStore _ID，与新 songs 表 id 不兼容；
                    // v1.0 新应用无存量数据，直接破坏性迁移。
                    .fallbackToDestructiveMigration()
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
