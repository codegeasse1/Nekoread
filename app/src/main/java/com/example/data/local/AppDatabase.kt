package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        MangaEntity::class,
        ChapterEntity::class,
        ExtensionRepoEntity::class,
        ExtensionEntity::class,
        ExtensionSourceEntity::class,
        CategoryEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mangaDao(): MangaDao
    abstract fun chapterDao(): ChapterDao
    abstract fun extensionDao(): ExtensionDao
    abstract fun categoryDao(): CategoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * v2 -> v3: extensions were keyed on packageName only, so two repos shipping the same
         * package (keiyoushi's newer "The Blank"/"4KHD" + a personal older fork) REPLACED each
         * other — only one ever showed. Rebuild the table with the composite (packageName, repoId)
         * key, preserving every existing row (and the user's library) in the process.
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS extensions_new (
                        packageName TEXT NOT NULL,
                        repoId TEXT NOT NULL,
                        name TEXT NOT NULL,
                        versionName TEXT NOT NULL,
                        versionCode TEXT NOT NULL,
                        libVersion TEXT NOT NULL,
                        contentWarning TEXT NOT NULL DEFAULT '',
                        apkUrl TEXT NOT NULL,
                        iconUrl TEXT NOT NULL DEFAULT '',
                        nsfw INTEGER NOT NULL,
                        isInstalled INTEGER NOT NULL,
                        installedVersionName TEXT,
                        installError TEXT,
                        sourcesJson TEXT NOT NULL DEFAULT '',
                        PRIMARY KEY(packageName, repoId)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "INSERT INTO extensions_new (packageName, repoId, name, versionName, versionCode, libVersion, contentWarning, apkUrl, iconUrl, nsfw, isInstalled, installedVersionName, installError, sourcesJson) " +
                        "SELECT packageName, repoId, name, versionName, versionCode, libVersion, contentWarning, apkUrl, iconUrl, nsfw, isInstalled, installedVersionName, installError, sourcesJson FROM extensions"
                )
                db.execSQL("DROP TABLE extensions")
                db.execSQL("ALTER TABLE extensions_new RENAME TO extensions")
            }
        }

        /**
         * v3 -> v4: track which version of an extension is installed so the UI can offer an
         * "Update" button when a repo refresh reveals a newer build. installedVersionName already
         * existed but is a display string (e.g. "1.4.5") — the numeric versionCode is what a
         * real update check compares, so it gets its own column.
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE extensions ADD COLUMN installedVersionCode TEXT")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "nekoread.db"
                )
                .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
