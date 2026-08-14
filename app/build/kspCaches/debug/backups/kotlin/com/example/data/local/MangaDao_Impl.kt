package com.example.`data`.local

import androidx.room.EntityDeleteOrUpdateAdapter
import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import javax.`annotation`.processing.Generated
import kotlin.Boolean
import kotlin.Float
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.Suppress
import kotlin.Unit
import kotlin.collections.List
import kotlin.collections.MutableList
import kotlin.collections.mutableListOf
import kotlin.reflect.KClass
import kotlinx.coroutines.flow.Flow

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class MangaDao_Impl(
  __db: RoomDatabase,
) : MangaDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfMangaEntity: EntityInsertAdapter<MangaEntity>

  private val __updateAdapterOfMangaEntity: EntityDeleteOrUpdateAdapter<MangaEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfMangaEntity = object : EntityInsertAdapter<MangaEntity>() {
      protected override fun createQuery(): String =
          "INSERT OR REPLACE INTO `manga` (`id`,`title`,`coverUrl`,`author`,`artist`,`description`,`sourceId`,`sourceName`,`status`,`type`,`inLibrary`,`category`,`lastReadChapterId`,`lastReadChapterName`,`lastReadPage`,`lastReadTimestamp`,`unreadCount`,`bookmarkCount`,`rating`,`genres`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: MangaEntity) {
        statement.bindText(1, entity.id)
        statement.bindText(2, entity.title)
        statement.bindText(3, entity.coverUrl)
        statement.bindText(4, entity.author)
        statement.bindText(5, entity.artist)
        statement.bindText(6, entity.description)
        statement.bindText(7, entity.sourceId)
        statement.bindText(8, entity.sourceName)
        statement.bindText(9, entity.status)
        statement.bindText(10, entity.type)
        val _tmp: Int = if (entity.inLibrary) 1 else 0
        statement.bindLong(11, _tmp.toLong())
        statement.bindText(12, entity.category)
        val _tmpLastReadChapterId: String? = entity.lastReadChapterId
        if (_tmpLastReadChapterId == null) {
          statement.bindNull(13)
        } else {
          statement.bindText(13, _tmpLastReadChapterId)
        }
        val _tmpLastReadChapterName: String? = entity.lastReadChapterName
        if (_tmpLastReadChapterName == null) {
          statement.bindNull(14)
        } else {
          statement.bindText(14, _tmpLastReadChapterName)
        }
        statement.bindLong(15, entity.lastReadPage.toLong())
        statement.bindLong(16, entity.lastReadTimestamp)
        statement.bindLong(17, entity.unreadCount.toLong())
        statement.bindLong(18, entity.bookmarkCount.toLong())
        statement.bindDouble(19, entity.rating.toDouble())
        statement.bindText(20, entity.genres)
      }
    }
    this.__updateAdapterOfMangaEntity = object : EntityDeleteOrUpdateAdapter<MangaEntity>() {
      protected override fun createQuery(): String =
          "UPDATE OR ABORT `manga` SET `id` = ?,`title` = ?,`coverUrl` = ?,`author` = ?,`artist` = ?,`description` = ?,`sourceId` = ?,`sourceName` = ?,`status` = ?,`type` = ?,`inLibrary` = ?,`category` = ?,`lastReadChapterId` = ?,`lastReadChapterName` = ?,`lastReadPage` = ?,`lastReadTimestamp` = ?,`unreadCount` = ?,`bookmarkCount` = ?,`rating` = ?,`genres` = ? WHERE `id` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: MangaEntity) {
        statement.bindText(1, entity.id)
        statement.bindText(2, entity.title)
        statement.bindText(3, entity.coverUrl)
        statement.bindText(4, entity.author)
        statement.bindText(5, entity.artist)
        statement.bindText(6, entity.description)
        statement.bindText(7, entity.sourceId)
        statement.bindText(8, entity.sourceName)
        statement.bindText(9, entity.status)
        statement.bindText(10, entity.type)
        val _tmp: Int = if (entity.inLibrary) 1 else 0
        statement.bindLong(11, _tmp.toLong())
        statement.bindText(12, entity.category)
        val _tmpLastReadChapterId: String? = entity.lastReadChapterId
        if (_tmpLastReadChapterId == null) {
          statement.bindNull(13)
        } else {
          statement.bindText(13, _tmpLastReadChapterId)
        }
        val _tmpLastReadChapterName: String? = entity.lastReadChapterName
        if (_tmpLastReadChapterName == null) {
          statement.bindNull(14)
        } else {
          statement.bindText(14, _tmpLastReadChapterName)
        }
        statement.bindLong(15, entity.lastReadPage.toLong())
        statement.bindLong(16, entity.lastReadTimestamp)
        statement.bindLong(17, entity.unreadCount.toLong())
        statement.bindLong(18, entity.bookmarkCount.toLong())
        statement.bindDouble(19, entity.rating.toDouble())
        statement.bindText(20, entity.genres)
        statement.bindText(21, entity.id)
      }
    }
  }

  public override suspend fun insertManga(manga: MangaEntity): Unit = performSuspending(__db, false,
      true) { _connection ->
    __insertAdapterOfMangaEntity.insert(_connection, manga)
  }

  public override suspend fun insertMangaList(mangaList: List<MangaEntity>): Unit =
      performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfMangaEntity.insert(_connection, mangaList)
  }

  public override suspend fun updateManga(manga: MangaEntity): Unit = performSuspending(__db, false,
      true) { _connection ->
    __updateAdapterOfMangaEntity.handle(_connection, manga)
  }

  public override fun getLibraryManga(): Flow<List<MangaEntity>> {
    val _sql: String =
        "SELECT * FROM manga WHERE inLibrary = 1 ORDER BY lastReadTimestamp DESC, title ASC"
    return createFlow(__db, false, arrayOf("manga")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfCoverUrl: Int = getColumnIndexOrThrow(_stmt, "coverUrl")
        val _columnIndexOfAuthor: Int = getColumnIndexOrThrow(_stmt, "author")
        val _columnIndexOfArtist: Int = getColumnIndexOrThrow(_stmt, "artist")
        val _columnIndexOfDescription: Int = getColumnIndexOrThrow(_stmt, "description")
        val _columnIndexOfSourceId: Int = getColumnIndexOrThrow(_stmt, "sourceId")
        val _columnIndexOfSourceName: Int = getColumnIndexOrThrow(_stmt, "sourceName")
        val _columnIndexOfStatus: Int = getColumnIndexOrThrow(_stmt, "status")
        val _columnIndexOfType: Int = getColumnIndexOrThrow(_stmt, "type")
        val _columnIndexOfInLibrary: Int = getColumnIndexOrThrow(_stmt, "inLibrary")
        val _columnIndexOfCategory: Int = getColumnIndexOrThrow(_stmt, "category")
        val _columnIndexOfLastReadChapterId: Int = getColumnIndexOrThrow(_stmt, "lastReadChapterId")
        val _columnIndexOfLastReadChapterName: Int = getColumnIndexOrThrow(_stmt,
            "lastReadChapterName")
        val _columnIndexOfLastReadPage: Int = getColumnIndexOrThrow(_stmt, "lastReadPage")
        val _columnIndexOfLastReadTimestamp: Int = getColumnIndexOrThrow(_stmt, "lastReadTimestamp")
        val _columnIndexOfUnreadCount: Int = getColumnIndexOrThrow(_stmt, "unreadCount")
        val _columnIndexOfBookmarkCount: Int = getColumnIndexOrThrow(_stmt, "bookmarkCount")
        val _columnIndexOfRating: Int = getColumnIndexOrThrow(_stmt, "rating")
        val _columnIndexOfGenres: Int = getColumnIndexOrThrow(_stmt, "genres")
        val _result: MutableList<MangaEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: MangaEntity
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpTitle: String
          _tmpTitle = _stmt.getText(_columnIndexOfTitle)
          val _tmpCoverUrl: String
          _tmpCoverUrl = _stmt.getText(_columnIndexOfCoverUrl)
          val _tmpAuthor: String
          _tmpAuthor = _stmt.getText(_columnIndexOfAuthor)
          val _tmpArtist: String
          _tmpArtist = _stmt.getText(_columnIndexOfArtist)
          val _tmpDescription: String
          _tmpDescription = _stmt.getText(_columnIndexOfDescription)
          val _tmpSourceId: String
          _tmpSourceId = _stmt.getText(_columnIndexOfSourceId)
          val _tmpSourceName: String
          _tmpSourceName = _stmt.getText(_columnIndexOfSourceName)
          val _tmpStatus: String
          _tmpStatus = _stmt.getText(_columnIndexOfStatus)
          val _tmpType: String
          _tmpType = _stmt.getText(_columnIndexOfType)
          val _tmpInLibrary: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfInLibrary).toInt()
          _tmpInLibrary = _tmp != 0
          val _tmpCategory: String
          _tmpCategory = _stmt.getText(_columnIndexOfCategory)
          val _tmpLastReadChapterId: String?
          if (_stmt.isNull(_columnIndexOfLastReadChapterId)) {
            _tmpLastReadChapterId = null
          } else {
            _tmpLastReadChapterId = _stmt.getText(_columnIndexOfLastReadChapterId)
          }
          val _tmpLastReadChapterName: String?
          if (_stmt.isNull(_columnIndexOfLastReadChapterName)) {
            _tmpLastReadChapterName = null
          } else {
            _tmpLastReadChapterName = _stmt.getText(_columnIndexOfLastReadChapterName)
          }
          val _tmpLastReadPage: Int
          _tmpLastReadPage = _stmt.getLong(_columnIndexOfLastReadPage).toInt()
          val _tmpLastReadTimestamp: Long
          _tmpLastReadTimestamp = _stmt.getLong(_columnIndexOfLastReadTimestamp)
          val _tmpUnreadCount: Int
          _tmpUnreadCount = _stmt.getLong(_columnIndexOfUnreadCount).toInt()
          val _tmpBookmarkCount: Int
          _tmpBookmarkCount = _stmt.getLong(_columnIndexOfBookmarkCount).toInt()
          val _tmpRating: Float
          _tmpRating = _stmt.getDouble(_columnIndexOfRating).toFloat()
          val _tmpGenres: String
          _tmpGenres = _stmt.getText(_columnIndexOfGenres)
          _item =
              MangaEntity(_tmpId,_tmpTitle,_tmpCoverUrl,_tmpAuthor,_tmpArtist,_tmpDescription,_tmpSourceId,_tmpSourceName,_tmpStatus,_tmpType,_tmpInLibrary,_tmpCategory,_tmpLastReadChapterId,_tmpLastReadChapterName,_tmpLastReadPage,_tmpLastReadTimestamp,_tmpUnreadCount,_tmpBookmarkCount,_tmpRating,_tmpGenres)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun getLibraryMangaByCategory(category: String): Flow<List<MangaEntity>> {
    val _sql: String = "SELECT * FROM manga WHERE inLibrary = 1 AND category = ? ORDER BY title ASC"
    return createFlow(__db, false, arrayOf("manga")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, category)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfCoverUrl: Int = getColumnIndexOrThrow(_stmt, "coverUrl")
        val _columnIndexOfAuthor: Int = getColumnIndexOrThrow(_stmt, "author")
        val _columnIndexOfArtist: Int = getColumnIndexOrThrow(_stmt, "artist")
        val _columnIndexOfDescription: Int = getColumnIndexOrThrow(_stmt, "description")
        val _columnIndexOfSourceId: Int = getColumnIndexOrThrow(_stmt, "sourceId")
        val _columnIndexOfSourceName: Int = getColumnIndexOrThrow(_stmt, "sourceName")
        val _columnIndexOfStatus: Int = getColumnIndexOrThrow(_stmt, "status")
        val _columnIndexOfType: Int = getColumnIndexOrThrow(_stmt, "type")
        val _columnIndexOfInLibrary: Int = getColumnIndexOrThrow(_stmt, "inLibrary")
        val _columnIndexOfCategory: Int = getColumnIndexOrThrow(_stmt, "category")
        val _columnIndexOfLastReadChapterId: Int = getColumnIndexOrThrow(_stmt, "lastReadChapterId")
        val _columnIndexOfLastReadChapterName: Int = getColumnIndexOrThrow(_stmt,
            "lastReadChapterName")
        val _columnIndexOfLastReadPage: Int = getColumnIndexOrThrow(_stmt, "lastReadPage")
        val _columnIndexOfLastReadTimestamp: Int = getColumnIndexOrThrow(_stmt, "lastReadTimestamp")
        val _columnIndexOfUnreadCount: Int = getColumnIndexOrThrow(_stmt, "unreadCount")
        val _columnIndexOfBookmarkCount: Int = getColumnIndexOrThrow(_stmt, "bookmarkCount")
        val _columnIndexOfRating: Int = getColumnIndexOrThrow(_stmt, "rating")
        val _columnIndexOfGenres: Int = getColumnIndexOrThrow(_stmt, "genres")
        val _result: MutableList<MangaEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: MangaEntity
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpTitle: String
          _tmpTitle = _stmt.getText(_columnIndexOfTitle)
          val _tmpCoverUrl: String
          _tmpCoverUrl = _stmt.getText(_columnIndexOfCoverUrl)
          val _tmpAuthor: String
          _tmpAuthor = _stmt.getText(_columnIndexOfAuthor)
          val _tmpArtist: String
          _tmpArtist = _stmt.getText(_columnIndexOfArtist)
          val _tmpDescription: String
          _tmpDescription = _stmt.getText(_columnIndexOfDescription)
          val _tmpSourceId: String
          _tmpSourceId = _stmt.getText(_columnIndexOfSourceId)
          val _tmpSourceName: String
          _tmpSourceName = _stmt.getText(_columnIndexOfSourceName)
          val _tmpStatus: String
          _tmpStatus = _stmt.getText(_columnIndexOfStatus)
          val _tmpType: String
          _tmpType = _stmt.getText(_columnIndexOfType)
          val _tmpInLibrary: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfInLibrary).toInt()
          _tmpInLibrary = _tmp != 0
          val _tmpCategory: String
          _tmpCategory = _stmt.getText(_columnIndexOfCategory)
          val _tmpLastReadChapterId: String?
          if (_stmt.isNull(_columnIndexOfLastReadChapterId)) {
            _tmpLastReadChapterId = null
          } else {
            _tmpLastReadChapterId = _stmt.getText(_columnIndexOfLastReadChapterId)
          }
          val _tmpLastReadChapterName: String?
          if (_stmt.isNull(_columnIndexOfLastReadChapterName)) {
            _tmpLastReadChapterName = null
          } else {
            _tmpLastReadChapterName = _stmt.getText(_columnIndexOfLastReadChapterName)
          }
          val _tmpLastReadPage: Int
          _tmpLastReadPage = _stmt.getLong(_columnIndexOfLastReadPage).toInt()
          val _tmpLastReadTimestamp: Long
          _tmpLastReadTimestamp = _stmt.getLong(_columnIndexOfLastReadTimestamp)
          val _tmpUnreadCount: Int
          _tmpUnreadCount = _stmt.getLong(_columnIndexOfUnreadCount).toInt()
          val _tmpBookmarkCount: Int
          _tmpBookmarkCount = _stmt.getLong(_columnIndexOfBookmarkCount).toInt()
          val _tmpRating: Float
          _tmpRating = _stmt.getDouble(_columnIndexOfRating).toFloat()
          val _tmpGenres: String
          _tmpGenres = _stmt.getText(_columnIndexOfGenres)
          _item =
              MangaEntity(_tmpId,_tmpTitle,_tmpCoverUrl,_tmpAuthor,_tmpArtist,_tmpDescription,_tmpSourceId,_tmpSourceName,_tmpStatus,_tmpType,_tmpInLibrary,_tmpCategory,_tmpLastReadChapterId,_tmpLastReadChapterName,_tmpLastReadPage,_tmpLastReadTimestamp,_tmpUnreadCount,_tmpBookmarkCount,_tmpRating,_tmpGenres)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun getMangaByIdFlow(id: String): Flow<MangaEntity?> {
    val _sql: String = "SELECT * FROM manga WHERE id = ?"
    return createFlow(__db, false, arrayOf("manga")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, id)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfCoverUrl: Int = getColumnIndexOrThrow(_stmt, "coverUrl")
        val _columnIndexOfAuthor: Int = getColumnIndexOrThrow(_stmt, "author")
        val _columnIndexOfArtist: Int = getColumnIndexOrThrow(_stmt, "artist")
        val _columnIndexOfDescription: Int = getColumnIndexOrThrow(_stmt, "description")
        val _columnIndexOfSourceId: Int = getColumnIndexOrThrow(_stmt, "sourceId")
        val _columnIndexOfSourceName: Int = getColumnIndexOrThrow(_stmt, "sourceName")
        val _columnIndexOfStatus: Int = getColumnIndexOrThrow(_stmt, "status")
        val _columnIndexOfType: Int = getColumnIndexOrThrow(_stmt, "type")
        val _columnIndexOfInLibrary: Int = getColumnIndexOrThrow(_stmt, "inLibrary")
        val _columnIndexOfCategory: Int = getColumnIndexOrThrow(_stmt, "category")
        val _columnIndexOfLastReadChapterId: Int = getColumnIndexOrThrow(_stmt, "lastReadChapterId")
        val _columnIndexOfLastReadChapterName: Int = getColumnIndexOrThrow(_stmt,
            "lastReadChapterName")
        val _columnIndexOfLastReadPage: Int = getColumnIndexOrThrow(_stmt, "lastReadPage")
        val _columnIndexOfLastReadTimestamp: Int = getColumnIndexOrThrow(_stmt, "lastReadTimestamp")
        val _columnIndexOfUnreadCount: Int = getColumnIndexOrThrow(_stmt, "unreadCount")
        val _columnIndexOfBookmarkCount: Int = getColumnIndexOrThrow(_stmt, "bookmarkCount")
        val _columnIndexOfRating: Int = getColumnIndexOrThrow(_stmt, "rating")
        val _columnIndexOfGenres: Int = getColumnIndexOrThrow(_stmt, "genres")
        val _result: MangaEntity?
        if (_stmt.step()) {
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpTitle: String
          _tmpTitle = _stmt.getText(_columnIndexOfTitle)
          val _tmpCoverUrl: String
          _tmpCoverUrl = _stmt.getText(_columnIndexOfCoverUrl)
          val _tmpAuthor: String
          _tmpAuthor = _stmt.getText(_columnIndexOfAuthor)
          val _tmpArtist: String
          _tmpArtist = _stmt.getText(_columnIndexOfArtist)
          val _tmpDescription: String
          _tmpDescription = _stmt.getText(_columnIndexOfDescription)
          val _tmpSourceId: String
          _tmpSourceId = _stmt.getText(_columnIndexOfSourceId)
          val _tmpSourceName: String
          _tmpSourceName = _stmt.getText(_columnIndexOfSourceName)
          val _tmpStatus: String
          _tmpStatus = _stmt.getText(_columnIndexOfStatus)
          val _tmpType: String
          _tmpType = _stmt.getText(_columnIndexOfType)
          val _tmpInLibrary: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfInLibrary).toInt()
          _tmpInLibrary = _tmp != 0
          val _tmpCategory: String
          _tmpCategory = _stmt.getText(_columnIndexOfCategory)
          val _tmpLastReadChapterId: String?
          if (_stmt.isNull(_columnIndexOfLastReadChapterId)) {
            _tmpLastReadChapterId = null
          } else {
            _tmpLastReadChapterId = _stmt.getText(_columnIndexOfLastReadChapterId)
          }
          val _tmpLastReadChapterName: String?
          if (_stmt.isNull(_columnIndexOfLastReadChapterName)) {
            _tmpLastReadChapterName = null
          } else {
            _tmpLastReadChapterName = _stmt.getText(_columnIndexOfLastReadChapterName)
          }
          val _tmpLastReadPage: Int
          _tmpLastReadPage = _stmt.getLong(_columnIndexOfLastReadPage).toInt()
          val _tmpLastReadTimestamp: Long
          _tmpLastReadTimestamp = _stmt.getLong(_columnIndexOfLastReadTimestamp)
          val _tmpUnreadCount: Int
          _tmpUnreadCount = _stmt.getLong(_columnIndexOfUnreadCount).toInt()
          val _tmpBookmarkCount: Int
          _tmpBookmarkCount = _stmt.getLong(_columnIndexOfBookmarkCount).toInt()
          val _tmpRating: Float
          _tmpRating = _stmt.getDouble(_columnIndexOfRating).toFloat()
          val _tmpGenres: String
          _tmpGenres = _stmt.getText(_columnIndexOfGenres)
          _result =
              MangaEntity(_tmpId,_tmpTitle,_tmpCoverUrl,_tmpAuthor,_tmpArtist,_tmpDescription,_tmpSourceId,_tmpSourceName,_tmpStatus,_tmpType,_tmpInLibrary,_tmpCategory,_tmpLastReadChapterId,_tmpLastReadChapterName,_tmpLastReadPage,_tmpLastReadTimestamp,_tmpUnreadCount,_tmpBookmarkCount,_tmpRating,_tmpGenres)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getMangaById(id: String): MangaEntity? {
    val _sql: String = "SELECT * FROM manga WHERE id = ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, id)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfCoverUrl: Int = getColumnIndexOrThrow(_stmt, "coverUrl")
        val _columnIndexOfAuthor: Int = getColumnIndexOrThrow(_stmt, "author")
        val _columnIndexOfArtist: Int = getColumnIndexOrThrow(_stmt, "artist")
        val _columnIndexOfDescription: Int = getColumnIndexOrThrow(_stmt, "description")
        val _columnIndexOfSourceId: Int = getColumnIndexOrThrow(_stmt, "sourceId")
        val _columnIndexOfSourceName: Int = getColumnIndexOrThrow(_stmt, "sourceName")
        val _columnIndexOfStatus: Int = getColumnIndexOrThrow(_stmt, "status")
        val _columnIndexOfType: Int = getColumnIndexOrThrow(_stmt, "type")
        val _columnIndexOfInLibrary: Int = getColumnIndexOrThrow(_stmt, "inLibrary")
        val _columnIndexOfCategory: Int = getColumnIndexOrThrow(_stmt, "category")
        val _columnIndexOfLastReadChapterId: Int = getColumnIndexOrThrow(_stmt, "lastReadChapterId")
        val _columnIndexOfLastReadChapterName: Int = getColumnIndexOrThrow(_stmt,
            "lastReadChapterName")
        val _columnIndexOfLastReadPage: Int = getColumnIndexOrThrow(_stmt, "lastReadPage")
        val _columnIndexOfLastReadTimestamp: Int = getColumnIndexOrThrow(_stmt, "lastReadTimestamp")
        val _columnIndexOfUnreadCount: Int = getColumnIndexOrThrow(_stmt, "unreadCount")
        val _columnIndexOfBookmarkCount: Int = getColumnIndexOrThrow(_stmt, "bookmarkCount")
        val _columnIndexOfRating: Int = getColumnIndexOrThrow(_stmt, "rating")
        val _columnIndexOfGenres: Int = getColumnIndexOrThrow(_stmt, "genres")
        val _result: MangaEntity?
        if (_stmt.step()) {
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpTitle: String
          _tmpTitle = _stmt.getText(_columnIndexOfTitle)
          val _tmpCoverUrl: String
          _tmpCoverUrl = _stmt.getText(_columnIndexOfCoverUrl)
          val _tmpAuthor: String
          _tmpAuthor = _stmt.getText(_columnIndexOfAuthor)
          val _tmpArtist: String
          _tmpArtist = _stmt.getText(_columnIndexOfArtist)
          val _tmpDescription: String
          _tmpDescription = _stmt.getText(_columnIndexOfDescription)
          val _tmpSourceId: String
          _tmpSourceId = _stmt.getText(_columnIndexOfSourceId)
          val _tmpSourceName: String
          _tmpSourceName = _stmt.getText(_columnIndexOfSourceName)
          val _tmpStatus: String
          _tmpStatus = _stmt.getText(_columnIndexOfStatus)
          val _tmpType: String
          _tmpType = _stmt.getText(_columnIndexOfType)
          val _tmpInLibrary: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfInLibrary).toInt()
          _tmpInLibrary = _tmp != 0
          val _tmpCategory: String
          _tmpCategory = _stmt.getText(_columnIndexOfCategory)
          val _tmpLastReadChapterId: String?
          if (_stmt.isNull(_columnIndexOfLastReadChapterId)) {
            _tmpLastReadChapterId = null
          } else {
            _tmpLastReadChapterId = _stmt.getText(_columnIndexOfLastReadChapterId)
          }
          val _tmpLastReadChapterName: String?
          if (_stmt.isNull(_columnIndexOfLastReadChapterName)) {
            _tmpLastReadChapterName = null
          } else {
            _tmpLastReadChapterName = _stmt.getText(_columnIndexOfLastReadChapterName)
          }
          val _tmpLastReadPage: Int
          _tmpLastReadPage = _stmt.getLong(_columnIndexOfLastReadPage).toInt()
          val _tmpLastReadTimestamp: Long
          _tmpLastReadTimestamp = _stmt.getLong(_columnIndexOfLastReadTimestamp)
          val _tmpUnreadCount: Int
          _tmpUnreadCount = _stmt.getLong(_columnIndexOfUnreadCount).toInt()
          val _tmpBookmarkCount: Int
          _tmpBookmarkCount = _stmt.getLong(_columnIndexOfBookmarkCount).toInt()
          val _tmpRating: Float
          _tmpRating = _stmt.getDouble(_columnIndexOfRating).toFloat()
          val _tmpGenres: String
          _tmpGenres = _stmt.getText(_columnIndexOfGenres)
          _result =
              MangaEntity(_tmpId,_tmpTitle,_tmpCoverUrl,_tmpAuthor,_tmpArtist,_tmpDescription,_tmpSourceId,_tmpSourceName,_tmpStatus,_tmpType,_tmpInLibrary,_tmpCategory,_tmpLastReadChapterId,_tmpLastReadChapterName,_tmpLastReadPage,_tmpLastReadTimestamp,_tmpUnreadCount,_tmpBookmarkCount,_tmpRating,_tmpGenres)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun getReadingHistory(): Flow<List<MangaEntity>> {
    val _sql: String =
        "SELECT * FROM manga WHERE lastReadTimestamp > 0 ORDER BY lastReadTimestamp DESC LIMIT 30"
    return createFlow(__db, false, arrayOf("manga")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfCoverUrl: Int = getColumnIndexOrThrow(_stmt, "coverUrl")
        val _columnIndexOfAuthor: Int = getColumnIndexOrThrow(_stmt, "author")
        val _columnIndexOfArtist: Int = getColumnIndexOrThrow(_stmt, "artist")
        val _columnIndexOfDescription: Int = getColumnIndexOrThrow(_stmt, "description")
        val _columnIndexOfSourceId: Int = getColumnIndexOrThrow(_stmt, "sourceId")
        val _columnIndexOfSourceName: Int = getColumnIndexOrThrow(_stmt, "sourceName")
        val _columnIndexOfStatus: Int = getColumnIndexOrThrow(_stmt, "status")
        val _columnIndexOfType: Int = getColumnIndexOrThrow(_stmt, "type")
        val _columnIndexOfInLibrary: Int = getColumnIndexOrThrow(_stmt, "inLibrary")
        val _columnIndexOfCategory: Int = getColumnIndexOrThrow(_stmt, "category")
        val _columnIndexOfLastReadChapterId: Int = getColumnIndexOrThrow(_stmt, "lastReadChapterId")
        val _columnIndexOfLastReadChapterName: Int = getColumnIndexOrThrow(_stmt,
            "lastReadChapterName")
        val _columnIndexOfLastReadPage: Int = getColumnIndexOrThrow(_stmt, "lastReadPage")
        val _columnIndexOfLastReadTimestamp: Int = getColumnIndexOrThrow(_stmt, "lastReadTimestamp")
        val _columnIndexOfUnreadCount: Int = getColumnIndexOrThrow(_stmt, "unreadCount")
        val _columnIndexOfBookmarkCount: Int = getColumnIndexOrThrow(_stmt, "bookmarkCount")
        val _columnIndexOfRating: Int = getColumnIndexOrThrow(_stmt, "rating")
        val _columnIndexOfGenres: Int = getColumnIndexOrThrow(_stmt, "genres")
        val _result: MutableList<MangaEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: MangaEntity
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpTitle: String
          _tmpTitle = _stmt.getText(_columnIndexOfTitle)
          val _tmpCoverUrl: String
          _tmpCoverUrl = _stmt.getText(_columnIndexOfCoverUrl)
          val _tmpAuthor: String
          _tmpAuthor = _stmt.getText(_columnIndexOfAuthor)
          val _tmpArtist: String
          _tmpArtist = _stmt.getText(_columnIndexOfArtist)
          val _tmpDescription: String
          _tmpDescription = _stmt.getText(_columnIndexOfDescription)
          val _tmpSourceId: String
          _tmpSourceId = _stmt.getText(_columnIndexOfSourceId)
          val _tmpSourceName: String
          _tmpSourceName = _stmt.getText(_columnIndexOfSourceName)
          val _tmpStatus: String
          _tmpStatus = _stmt.getText(_columnIndexOfStatus)
          val _tmpType: String
          _tmpType = _stmt.getText(_columnIndexOfType)
          val _tmpInLibrary: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfInLibrary).toInt()
          _tmpInLibrary = _tmp != 0
          val _tmpCategory: String
          _tmpCategory = _stmt.getText(_columnIndexOfCategory)
          val _tmpLastReadChapterId: String?
          if (_stmt.isNull(_columnIndexOfLastReadChapterId)) {
            _tmpLastReadChapterId = null
          } else {
            _tmpLastReadChapterId = _stmt.getText(_columnIndexOfLastReadChapterId)
          }
          val _tmpLastReadChapterName: String?
          if (_stmt.isNull(_columnIndexOfLastReadChapterName)) {
            _tmpLastReadChapterName = null
          } else {
            _tmpLastReadChapterName = _stmt.getText(_columnIndexOfLastReadChapterName)
          }
          val _tmpLastReadPage: Int
          _tmpLastReadPage = _stmt.getLong(_columnIndexOfLastReadPage).toInt()
          val _tmpLastReadTimestamp: Long
          _tmpLastReadTimestamp = _stmt.getLong(_columnIndexOfLastReadTimestamp)
          val _tmpUnreadCount: Int
          _tmpUnreadCount = _stmt.getLong(_columnIndexOfUnreadCount).toInt()
          val _tmpBookmarkCount: Int
          _tmpBookmarkCount = _stmt.getLong(_columnIndexOfBookmarkCount).toInt()
          val _tmpRating: Float
          _tmpRating = _stmt.getDouble(_columnIndexOfRating).toFloat()
          val _tmpGenres: String
          _tmpGenres = _stmt.getText(_columnIndexOfGenres)
          _item =
              MangaEntity(_tmpId,_tmpTitle,_tmpCoverUrl,_tmpAuthor,_tmpArtist,_tmpDescription,_tmpSourceId,_tmpSourceName,_tmpStatus,_tmpType,_tmpInLibrary,_tmpCategory,_tmpLastReadChapterId,_tmpLastReadChapterName,_tmpLastReadPage,_tmpLastReadTimestamp,_tmpUnreadCount,_tmpBookmarkCount,_tmpRating,_tmpGenres)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun updateLibraryStatus(
    id: String,
    inLibrary: Boolean,
    category: String,
  ) {
    val _sql: String = "UPDATE manga SET inLibrary = ?, category = ? WHERE id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        val _tmp: Int = if (inLibrary) 1 else 0
        _stmt.bindLong(_argIndex, _tmp.toLong())
        _argIndex = 2
        _stmt.bindText(_argIndex, category)
        _argIndex = 3
        _stmt.bindText(_argIndex, id)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun updateReadProgress(
    mangaId: String,
    chapterId: String,
    chapterName: String,
    page: Int,
    timestamp: Long,
  ) {
    val _sql: String =
        "UPDATE manga SET lastReadChapterId = ?, lastReadChapterName = ?, lastReadPage = ?, lastReadTimestamp = ? WHERE id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, chapterId)
        _argIndex = 2
        _stmt.bindText(_argIndex, chapterName)
        _argIndex = 3
        _stmt.bindLong(_argIndex, page.toLong())
        _argIndex = 4
        _stmt.bindLong(_argIndex, timestamp)
        _argIndex = 5
        _stmt.bindText(_argIndex, mangaId)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteManga(id: String) {
    val _sql: String = "DELETE FROM manga WHERE id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, id)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public companion object {
    public fun getRequiredConverters(): List<KClass<*>> = emptyList()
  }
}
