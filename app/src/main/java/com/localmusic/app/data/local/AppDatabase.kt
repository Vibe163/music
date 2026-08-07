package com.localmusic.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.localmusic.app.data.model.FingerprintCacheEntity
import com.localmusic.app.data.model.ImportLogEntity
import com.localmusic.app.data.model.Playlist
import com.localmusic.app.data.model.PlaylistSong
import com.localmusic.app.data.model.SongEntity

@Database(
    entities = [
        SongEntity::class,
        Playlist::class,
        PlaylistSong::class,
        ImportLogEntity::class,
        FingerprintCacheEntity::class
    ],
    version = 7,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun songDao(): SongDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun importLogDao(): ImportLogDao
    abstract fun fingerprintCacheDao(): FingerprintCacheDao

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

        /** v4 → v5：新增 lastPlayedAt 字段（最近播放列表按此倒序）。 */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE songs ADD COLUMN lastPlayedAt INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** v5 → v6：新增 import_logs 表（导入日志：重复/失败歌名）。 */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `import_logs` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`timestamp` INTEGER NOT NULL, " +
                        "`addedCount` INTEGER NOT NULL, " +
                        "`duplicateNames` TEXT NOT NULL, " +
                        "`failedNames` TEXT NOT NULL)"
                )
            }
        }

        /** v6 → v7：新增 fingerprint_cache 表（音频指纹缓存，用于精确重复检测）。 */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `fingerprint_cache` (" +
                        "`songId` INTEGER NOT NULL PRIMARY KEY, " +
                        "`uri` TEXT NOT NULL, " +
                        "`lastModified` INTEGER NOT NULL, " +
                        "`size` INTEGER NOT NULL, " +
                        "`fingerprint` BLOB NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL)"
                )
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
                    .addMigrations(
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7
                    )
                    .build()
                    .also { instance = it }
            }
        }
    }
}
