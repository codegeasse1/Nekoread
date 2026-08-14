package com.example.`data`.local

import androidx.room.InvalidationTracker
import androidx.room.RoomOpenDelegate
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.room.util.TableInfo
import androidx.room.util.TableInfo.Companion.read
import androidx.room.util.dropFtsSyncTriggers
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import javax.`annotation`.processing.Generated
import kotlin.Lazy
import kotlin.String
import kotlin.Suppress
import kotlin.collections.List
import kotlin.collections.Map
import kotlin.collections.MutableList
import kotlin.collections.MutableMap
import kotlin.collections.MutableSet
import kotlin.collections.Set
import kotlin.collections.mutableListOf
import kotlin.collections.mutableMapOf
import kotlin.collections.mutableSetOf
import kotlin.reflect.KClass

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class AppDatabase_Impl : AppDatabase() {
  private val _mangaDao: Lazy<MangaDao> = lazy {
    MangaDao_Impl(this)
  }

  private val _chapterDao: Lazy<ChapterDao> = lazy {
    ChapterDao_Impl(this)
  }

  private val _extensionDao: Lazy<ExtensionDao> = lazy {
    ExtensionDao_Impl(this)
  }

  private val _categoryDao: Lazy<CategoryDao> = lazy {
    CategoryDao_Impl(this)
  }

  protected override fun createOpenDelegate(): RoomOpenDelegate {
    val _openDelegate: RoomOpenDelegate = object : RoomOpenDelegate(1,
        "48a5084e451cae3bf3291307904d6a9e", "942d7464374965ed756438fcce6221cc") {
      public override fun createAllTables(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS `manga` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `coverUrl` TEXT NOT NULL, `author` TEXT NOT NULL, `artist` TEXT NOT NULL, `description` TEXT NOT NULL, `sourceId` TEXT NOT NULL, `sourceName` TEXT NOT NULL, `status` TEXT NOT NULL, `type` TEXT NOT NULL, `inLibrary` INTEGER NOT NULL, `category` TEXT NOT NULL, `lastReadChapterId` TEXT, `lastReadChapterName` TEXT, `lastReadPage` INTEGER NOT NULL, `lastReadTimestamp` INTEGER NOT NULL, `unreadCount` INTEGER NOT NULL, `bookmarkCount` INTEGER NOT NULL, `rating` REAL NOT NULL, `genres` TEXT NOT NULL, PRIMARY KEY(`id`))")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `chapters` (`id` TEXT NOT NULL, `mangaId` TEXT NOT NULL, `chapterNumber` REAL NOT NULL, `name` TEXT NOT NULL, `scanlator` TEXT NOT NULL, `releaseDate` TEXT NOT NULL, `read` INTEGER NOT NULL, `bookmarked` INTEGER NOT NULL, `lastPageRead` INTEGER NOT NULL, `totalPages` INTEGER NOT NULL, `fetchUrl` TEXT NOT NULL, `dateUpload` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `extension_repos` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `url` TEXT NOT NULL, `extensionCount` INTEGER NOT NULL, `isOfficial` INTEGER NOT NULL, `addedDate` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `extension_sources` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `version` TEXT NOT NULL, `lang` TEXT NOT NULL, `iconUrl` TEXT NOT NULL, `repoId` TEXT NOT NULL, `isInstalled` INTEGER NOT NULL, `isNsfw` INTEGER NOT NULL, `baseUrl` TEXT NOT NULL, `sourceType` TEXT NOT NULL, PRIMARY KEY(`id`))")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `categories` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        connection.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
        connection.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '48a5084e451cae3bf3291307904d6a9e')")
      }

      public override fun dropAllTables(connection: SQLiteConnection) {
        connection.execSQL("DROP TABLE IF EXISTS `manga`")
        connection.execSQL("DROP TABLE IF EXISTS `chapters`")
        connection.execSQL("DROP TABLE IF EXISTS `extension_repos`")
        connection.execSQL("DROP TABLE IF EXISTS `extension_sources`")
        connection.execSQL("DROP TABLE IF EXISTS `categories`")
      }

      public override fun onCreate(connection: SQLiteConnection) {
      }

      public override fun onOpen(connection: SQLiteConnection) {
        internalInitInvalidationTracker(connection)
      }

      public override fun onPreMigrate(connection: SQLiteConnection) {
        dropFtsSyncTriggers(connection)
      }

      public override fun onPostMigrate(connection: SQLiteConnection) {
      }

      public override fun onValidateSchema(connection: SQLiteConnection):
          RoomOpenDelegate.ValidationResult {
        val _columnsManga: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsManga.put("id", TableInfo.Column("id", "TEXT", true, 1, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsManga.put("title", TableInfo.Column("title", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsManga.put("coverUrl", TableInfo.Column("coverUrl", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsManga.put("author", TableInfo.Column("author", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsManga.put("artist", TableInfo.Column("artist", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsManga.put("description", TableInfo.Column("description", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsManga.put("sourceId", TableInfo.Column("sourceId", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsManga.put("sourceName", TableInfo.Column("sourceName", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsManga.put("status", TableInfo.Column("status", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsManga.put("type", TableInfo.Column("type", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsManga.put("inLibrary", TableInfo.Column("inLibrary", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsManga.put("category", TableInfo.Column("category", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsManga.put("lastReadChapterId", TableInfo.Column("lastReadChapterId", "TEXT", false,
            0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsManga.put("lastReadChapterName", TableInfo.Column("lastReadChapterName", "TEXT",
            false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsManga.put("lastReadPage", TableInfo.Column("lastReadPage", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsManga.put("lastReadTimestamp", TableInfo.Column("lastReadTimestamp", "INTEGER",
            true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsManga.put("unreadCount", TableInfo.Column("unreadCount", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsManga.put("bookmarkCount", TableInfo.Column("bookmarkCount", "INTEGER", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsManga.put("rating", TableInfo.Column("rating", "REAL", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsManga.put("genres", TableInfo.Column("genres", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysManga: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesManga: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoManga: TableInfo = TableInfo("manga", _columnsManga, _foreignKeysManga,
            _indicesManga)
        val _existingManga: TableInfo = read(connection, "manga")
        if (!_infoManga.equals(_existingManga)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |manga(com.example.data.local.MangaEntity).
              | Expected:
              |""".trimMargin() + _infoManga + """
              |
              | Found:
              |""".trimMargin() + _existingManga)
        }
        val _columnsChapters: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsChapters.put("id", TableInfo.Column("id", "TEXT", true, 1, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsChapters.put("mangaId", TableInfo.Column("mangaId", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsChapters.put("chapterNumber", TableInfo.Column("chapterNumber", "REAL", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsChapters.put("name", TableInfo.Column("name", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsChapters.put("scanlator", TableInfo.Column("scanlator", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsChapters.put("releaseDate", TableInfo.Column("releaseDate", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsChapters.put("read", TableInfo.Column("read", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsChapters.put("bookmarked", TableInfo.Column("bookmarked", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsChapters.put("lastPageRead", TableInfo.Column("lastPageRead", "INTEGER", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsChapters.put("totalPages", TableInfo.Column("totalPages", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsChapters.put("fetchUrl", TableInfo.Column("fetchUrl", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsChapters.put("dateUpload", TableInfo.Column("dateUpload", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysChapters: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesChapters: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoChapters: TableInfo = TableInfo("chapters", _columnsChapters, _foreignKeysChapters,
            _indicesChapters)
        val _existingChapters: TableInfo = read(connection, "chapters")
        if (!_infoChapters.equals(_existingChapters)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |chapters(com.example.data.local.ChapterEntity).
              | Expected:
              |""".trimMargin() + _infoChapters + """
              |
              | Found:
              |""".trimMargin() + _existingChapters)
        }
        val _columnsExtensionRepos: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsExtensionRepos.put("id", TableInfo.Column("id", "TEXT", true, 1, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsExtensionRepos.put("name", TableInfo.Column("name", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsExtensionRepos.put("url", TableInfo.Column("url", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsExtensionRepos.put("extensionCount", TableInfo.Column("extensionCount", "INTEGER",
            true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsExtensionRepos.put("isOfficial", TableInfo.Column("isOfficial", "INTEGER", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsExtensionRepos.put("addedDate", TableInfo.Column("addedDate", "INTEGER", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysExtensionRepos: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesExtensionRepos: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoExtensionRepos: TableInfo = TableInfo("extension_repos", _columnsExtensionRepos,
            _foreignKeysExtensionRepos, _indicesExtensionRepos)
        val _existingExtensionRepos: TableInfo = read(connection, "extension_repos")
        if (!_infoExtensionRepos.equals(_existingExtensionRepos)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |extension_repos(com.example.data.local.ExtensionRepoEntity).
              | Expected:
              |""".trimMargin() + _infoExtensionRepos + """
              |
              | Found:
              |""".trimMargin() + _existingExtensionRepos)
        }
        val _columnsExtensionSources: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsExtensionSources.put("id", TableInfo.Column("id", "TEXT", true, 1, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsExtensionSources.put("name", TableInfo.Column("name", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsExtensionSources.put("version", TableInfo.Column("version", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsExtensionSources.put("lang", TableInfo.Column("lang", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsExtensionSources.put("iconUrl", TableInfo.Column("iconUrl", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsExtensionSources.put("repoId", TableInfo.Column("repoId", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsExtensionSources.put("isInstalled", TableInfo.Column("isInstalled", "INTEGER", true,
            0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsExtensionSources.put("isNsfw", TableInfo.Column("isNsfw", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsExtensionSources.put("baseUrl", TableInfo.Column("baseUrl", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsExtensionSources.put("sourceType", TableInfo.Column("sourceType", "TEXT", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysExtensionSources: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesExtensionSources: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoExtensionSources: TableInfo = TableInfo("extension_sources",
            _columnsExtensionSources, _foreignKeysExtensionSources, _indicesExtensionSources)
        val _existingExtensionSources: TableInfo = read(connection, "extension_sources")
        if (!_infoExtensionSources.equals(_existingExtensionSources)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |extension_sources(com.example.data.local.ExtensionSourceEntity).
              | Expected:
              |""".trimMargin() + _infoExtensionSources + """
              |
              | Found:
              |""".trimMargin() + _existingExtensionSources)
        }
        val _columnsCategories: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsCategories.put("id", TableInfo.Column("id", "TEXT", true, 1, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsCategories.put("name", TableInfo.Column("name", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsCategories.put("sortOrder", TableInfo.Column("sortOrder", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysCategories: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesCategories: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoCategories: TableInfo = TableInfo("categories", _columnsCategories,
            _foreignKeysCategories, _indicesCategories)
        val _existingCategories: TableInfo = read(connection, "categories")
        if (!_infoCategories.equals(_existingCategories)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |categories(com.example.data.local.CategoryEntity).
              | Expected:
              |""".trimMargin() + _infoCategories + """
              |
              | Found:
              |""".trimMargin() + _existingCategories)
        }
        return RoomOpenDelegate.ValidationResult(true, null)
      }
    }
    return _openDelegate
  }

  protected override fun createInvalidationTracker(): InvalidationTracker {
    val _shadowTablesMap: MutableMap<String, String> = mutableMapOf()
    val _viewTables: MutableMap<String, Set<String>> = mutableMapOf()
    return InvalidationTracker(this, _shadowTablesMap, _viewTables, "manga", "chapters",
        "extension_repos", "extension_sources", "categories")
  }

  public override fun clearAllTables() {
    super.performClear(false, "manga", "chapters", "extension_repos", "extension_sources",
        "categories")
  }

  protected override fun getRequiredTypeConverterClasses(): Map<KClass<*>, List<KClass<*>>> {
    val _typeConvertersMap: MutableMap<KClass<*>, List<KClass<*>>> = mutableMapOf()
    _typeConvertersMap.put(MangaDao::class, MangaDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(ChapterDao::class, ChapterDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(ExtensionDao::class, ExtensionDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(CategoryDao::class, CategoryDao_Impl.getRequiredConverters())
    return _typeConvertersMap
  }

  public override fun getRequiredAutoMigrationSpecClasses(): Set<KClass<out AutoMigrationSpec>> {
    val _autoMigrationSpecsSet: MutableSet<KClass<out AutoMigrationSpec>> = mutableSetOf()
    return _autoMigrationSpecsSet
  }

  public override
      fun createAutoMigrations(autoMigrationSpecs: Map<KClass<out AutoMigrationSpec>, AutoMigrationSpec>):
      List<Migration> {
    val _autoMigrations: MutableList<Migration> = mutableListOf()
    return _autoMigrations
  }

  public override fun mangaDao(): MangaDao = _mangaDao.value

  public override fun chapterDao(): ChapterDao = _chapterDao.value

  public override fun extensionDao(): ExtensionDao = _extensionDao.value

  public override fun categoryDao(): CategoryDao = _categoryDao.value
}
