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
public class ChapterDao_Impl(
  __db: RoomDatabase,
) : ChapterDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfChapterEntity: EntityInsertAdapter<ChapterEntity>

  private val __updateAdapterOfChapterEntity: EntityDeleteOrUpdateAdapter<ChapterEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfChapterEntity = object : EntityInsertAdapter<ChapterEntity>() {
      protected override fun createQuery(): String =
          "INSERT OR REPLACE INTO `chapters` (`id`,`mangaId`,`chapterNumber`,`name`,`scanlator`,`releaseDate`,`read`,`bookmarked`,`lastPageRead`,`totalPages`,`fetchUrl`,`dateUpload`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: ChapterEntity) {
        statement.bindText(1, entity.id)
        statement.bindText(2, entity.mangaId)
        statement.bindDouble(3, entity.chapterNumber.toDouble())
        statement.bindText(4, entity.name)
        statement.bindText(5, entity.scanlator)
        statement.bindText(6, entity.releaseDate)
        val _tmp: Int = if (entity.read) 1 else 0
        statement.bindLong(7, _tmp.toLong())
        val _tmp_1: Int = if (entity.bookmarked) 1 else 0
        statement.bindLong(8, _tmp_1.toLong())
        statement.bindLong(9, entity.lastPageRead.toLong())
        statement.bindLong(10, entity.totalPages.toLong())
        statement.bindText(11, entity.fetchUrl)
        statement.bindLong(12, entity.dateUpload)
      }
    }
    this.__updateAdapterOfChapterEntity = object : EntityDeleteOrUpdateAdapter<ChapterEntity>() {
      protected override fun createQuery(): String =
          "UPDATE OR ABORT `chapters` SET `id` = ?,`mangaId` = ?,`chapterNumber` = ?,`name` = ?,`scanlator` = ?,`releaseDate` = ?,`read` = ?,`bookmarked` = ?,`lastPageRead` = ?,`totalPages` = ?,`fetchUrl` = ?,`dateUpload` = ? WHERE `id` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: ChapterEntity) {
        statement.bindText(1, entity.id)
        statement.bindText(2, entity.mangaId)
        statement.bindDouble(3, entity.chapterNumber.toDouble())
        statement.bindText(4, entity.name)
        statement.bindText(5, entity.scanlator)
        statement.bindText(6, entity.releaseDate)
        val _tmp: Int = if (entity.read) 1 else 0
        statement.bindLong(7, _tmp.toLong())
        val _tmp_1: Int = if (entity.bookmarked) 1 else 0
        statement.bindLong(8, _tmp_1.toLong())
        statement.bindLong(9, entity.lastPageRead.toLong())
        statement.bindLong(10, entity.totalPages.toLong())
        statement.bindText(11, entity.fetchUrl)
        statement.bindLong(12, entity.dateUpload)
        statement.bindText(13, entity.id)
      }
    }
  }

  public override suspend fun insertChapters(chapters: List<ChapterEntity>): Unit =
      performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfChapterEntity.insert(_connection, chapters)
  }

  public override suspend fun insertChapter(chapter: ChapterEntity): Unit = performSuspending(__db,
      false, true) { _connection ->
    __insertAdapterOfChapterEntity.insert(_connection, chapter)
  }

  public override suspend fun updateChapter(chapter: ChapterEntity): Unit = performSuspending(__db,
      false, true) { _connection ->
    __updateAdapterOfChapterEntity.handle(_connection, chapter)
  }

  public override fun getChaptersForManga(mangaId: String): Flow<List<ChapterEntity>> {
    val _sql: String = "SELECT * FROM chapters WHERE mangaId = ? ORDER BY chapterNumber DESC"
    return createFlow(__db, false, arrayOf("chapters")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, mangaId)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfMangaId: Int = getColumnIndexOrThrow(_stmt, "mangaId")
        val _columnIndexOfChapterNumber: Int = getColumnIndexOrThrow(_stmt, "chapterNumber")
        val _columnIndexOfName: Int = getColumnIndexOrThrow(_stmt, "name")
        val _columnIndexOfScanlator: Int = getColumnIndexOrThrow(_stmt, "scanlator")
        val _columnIndexOfReleaseDate: Int = getColumnIndexOrThrow(_stmt, "releaseDate")
        val _columnIndexOfRead: Int = getColumnIndexOrThrow(_stmt, "read")
        val _columnIndexOfBookmarked: Int = getColumnIndexOrThrow(_stmt, "bookmarked")
        val _columnIndexOfLastPageRead: Int = getColumnIndexOrThrow(_stmt, "lastPageRead")
        val _columnIndexOfTotalPages: Int = getColumnIndexOrThrow(_stmt, "totalPages")
        val _columnIndexOfFetchUrl: Int = getColumnIndexOrThrow(_stmt, "fetchUrl")
        val _columnIndexOfDateUpload: Int = getColumnIndexOrThrow(_stmt, "dateUpload")
        val _result: MutableList<ChapterEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: ChapterEntity
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpMangaId: String
          _tmpMangaId = _stmt.getText(_columnIndexOfMangaId)
          val _tmpChapterNumber: Float
          _tmpChapterNumber = _stmt.getDouble(_columnIndexOfChapterNumber).toFloat()
          val _tmpName: String
          _tmpName = _stmt.getText(_columnIndexOfName)
          val _tmpScanlator: String
          _tmpScanlator = _stmt.getText(_columnIndexOfScanlator)
          val _tmpReleaseDate: String
          _tmpReleaseDate = _stmt.getText(_columnIndexOfReleaseDate)
          val _tmpRead: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfRead).toInt()
          _tmpRead = _tmp != 0
          val _tmpBookmarked: Boolean
          val _tmp_1: Int
          _tmp_1 = _stmt.getLong(_columnIndexOfBookmarked).toInt()
          _tmpBookmarked = _tmp_1 != 0
          val _tmpLastPageRead: Int
          _tmpLastPageRead = _stmt.getLong(_columnIndexOfLastPageRead).toInt()
          val _tmpTotalPages: Int
          _tmpTotalPages = _stmt.getLong(_columnIndexOfTotalPages).toInt()
          val _tmpFetchUrl: String
          _tmpFetchUrl = _stmt.getText(_columnIndexOfFetchUrl)
          val _tmpDateUpload: Long
          _tmpDateUpload = _stmt.getLong(_columnIndexOfDateUpload)
          _item =
              ChapterEntity(_tmpId,_tmpMangaId,_tmpChapterNumber,_tmpName,_tmpScanlator,_tmpReleaseDate,_tmpRead,_tmpBookmarked,_tmpLastPageRead,_tmpTotalPages,_tmpFetchUrl,_tmpDateUpload)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getChaptersListForManga(mangaId: String): List<ChapterEntity> {
    val _sql: String = "SELECT * FROM chapters WHERE mangaId = ? ORDER BY chapterNumber DESC"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, mangaId)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfMangaId: Int = getColumnIndexOrThrow(_stmt, "mangaId")
        val _columnIndexOfChapterNumber: Int = getColumnIndexOrThrow(_stmt, "chapterNumber")
        val _columnIndexOfName: Int = getColumnIndexOrThrow(_stmt, "name")
        val _columnIndexOfScanlator: Int = getColumnIndexOrThrow(_stmt, "scanlator")
        val _columnIndexOfReleaseDate: Int = getColumnIndexOrThrow(_stmt, "releaseDate")
        val _columnIndexOfRead: Int = getColumnIndexOrThrow(_stmt, "read")
        val _columnIndexOfBookmarked: Int = getColumnIndexOrThrow(_stmt, "bookmarked")
        val _columnIndexOfLastPageRead: Int = getColumnIndexOrThrow(_stmt, "lastPageRead")
        val _columnIndexOfTotalPages: Int = getColumnIndexOrThrow(_stmt, "totalPages")
        val _columnIndexOfFetchUrl: Int = getColumnIndexOrThrow(_stmt, "fetchUrl")
        val _columnIndexOfDateUpload: Int = getColumnIndexOrThrow(_stmt, "dateUpload")
        val _result: MutableList<ChapterEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: ChapterEntity
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpMangaId: String
          _tmpMangaId = _stmt.getText(_columnIndexOfMangaId)
          val _tmpChapterNumber: Float
          _tmpChapterNumber = _stmt.getDouble(_columnIndexOfChapterNumber).toFloat()
          val _tmpName: String
          _tmpName = _stmt.getText(_columnIndexOfName)
          val _tmpScanlator: String
          _tmpScanlator = _stmt.getText(_columnIndexOfScanlator)
          val _tmpReleaseDate: String
          _tmpReleaseDate = _stmt.getText(_columnIndexOfReleaseDate)
          val _tmpRead: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfRead).toInt()
          _tmpRead = _tmp != 0
          val _tmpBookmarked: Boolean
          val _tmp_1: Int
          _tmp_1 = _stmt.getLong(_columnIndexOfBookmarked).toInt()
          _tmpBookmarked = _tmp_1 != 0
          val _tmpLastPageRead: Int
          _tmpLastPageRead = _stmt.getLong(_columnIndexOfLastPageRead).toInt()
          val _tmpTotalPages: Int
          _tmpTotalPages = _stmt.getLong(_columnIndexOfTotalPages).toInt()
          val _tmpFetchUrl: String
          _tmpFetchUrl = _stmt.getText(_columnIndexOfFetchUrl)
          val _tmpDateUpload: Long
          _tmpDateUpload = _stmt.getLong(_columnIndexOfDateUpload)
          _item =
              ChapterEntity(_tmpId,_tmpMangaId,_tmpChapterNumber,_tmpName,_tmpScanlator,_tmpReleaseDate,_tmpRead,_tmpBookmarked,_tmpLastPageRead,_tmpTotalPages,_tmpFetchUrl,_tmpDateUpload)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getChapterById(id: String): ChapterEntity? {
    val _sql: String = "SELECT * FROM chapters WHERE id = ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, id)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfMangaId: Int = getColumnIndexOrThrow(_stmt, "mangaId")
        val _columnIndexOfChapterNumber: Int = getColumnIndexOrThrow(_stmt, "chapterNumber")
        val _columnIndexOfName: Int = getColumnIndexOrThrow(_stmt, "name")
        val _columnIndexOfScanlator: Int = getColumnIndexOrThrow(_stmt, "scanlator")
        val _columnIndexOfReleaseDate: Int = getColumnIndexOrThrow(_stmt, "releaseDate")
        val _columnIndexOfRead: Int = getColumnIndexOrThrow(_stmt, "read")
        val _columnIndexOfBookmarked: Int = getColumnIndexOrThrow(_stmt, "bookmarked")
        val _columnIndexOfLastPageRead: Int = getColumnIndexOrThrow(_stmt, "lastPageRead")
        val _columnIndexOfTotalPages: Int = getColumnIndexOrThrow(_stmt, "totalPages")
        val _columnIndexOfFetchUrl: Int = getColumnIndexOrThrow(_stmt, "fetchUrl")
        val _columnIndexOfDateUpload: Int = getColumnIndexOrThrow(_stmt, "dateUpload")
        val _result: ChapterEntity?
        if (_stmt.step()) {
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpMangaId: String
          _tmpMangaId = _stmt.getText(_columnIndexOfMangaId)
          val _tmpChapterNumber: Float
          _tmpChapterNumber = _stmt.getDouble(_columnIndexOfChapterNumber).toFloat()
          val _tmpName: String
          _tmpName = _stmt.getText(_columnIndexOfName)
          val _tmpScanlator: String
          _tmpScanlator = _stmt.getText(_columnIndexOfScanlator)
          val _tmpReleaseDate: String
          _tmpReleaseDate = _stmt.getText(_columnIndexOfReleaseDate)
          val _tmpRead: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfRead).toInt()
          _tmpRead = _tmp != 0
          val _tmpBookmarked: Boolean
          val _tmp_1: Int
          _tmp_1 = _stmt.getLong(_columnIndexOfBookmarked).toInt()
          _tmpBookmarked = _tmp_1 != 0
          val _tmpLastPageRead: Int
          _tmpLastPageRead = _stmt.getLong(_columnIndexOfLastPageRead).toInt()
          val _tmpTotalPages: Int
          _tmpTotalPages = _stmt.getLong(_columnIndexOfTotalPages).toInt()
          val _tmpFetchUrl: String
          _tmpFetchUrl = _stmt.getText(_columnIndexOfFetchUrl)
          val _tmpDateUpload: Long
          _tmpDateUpload = _stmt.getLong(_columnIndexOfDateUpload)
          _result =
              ChapterEntity(_tmpId,_tmpMangaId,_tmpChapterNumber,_tmpName,_tmpScanlator,_tmpReleaseDate,_tmpRead,_tmpBookmarked,_tmpLastPageRead,_tmpTotalPages,_tmpFetchUrl,_tmpDateUpload)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun getChapterByIdFlow(id: String): Flow<ChapterEntity?> {
    val _sql: String = "SELECT * FROM chapters WHERE id = ?"
    return createFlow(__db, false, arrayOf("chapters")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, id)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfMangaId: Int = getColumnIndexOrThrow(_stmt, "mangaId")
        val _columnIndexOfChapterNumber: Int = getColumnIndexOrThrow(_stmt, "chapterNumber")
        val _columnIndexOfName: Int = getColumnIndexOrThrow(_stmt, "name")
        val _columnIndexOfScanlator: Int = getColumnIndexOrThrow(_stmt, "scanlator")
        val _columnIndexOfReleaseDate: Int = getColumnIndexOrThrow(_stmt, "releaseDate")
        val _columnIndexOfRead: Int = getColumnIndexOrThrow(_stmt, "read")
        val _columnIndexOfBookmarked: Int = getColumnIndexOrThrow(_stmt, "bookmarked")
        val _columnIndexOfLastPageRead: Int = getColumnIndexOrThrow(_stmt, "lastPageRead")
        val _columnIndexOfTotalPages: Int = getColumnIndexOrThrow(_stmt, "totalPages")
        val _columnIndexOfFetchUrl: Int = getColumnIndexOrThrow(_stmt, "fetchUrl")
        val _columnIndexOfDateUpload: Int = getColumnIndexOrThrow(_stmt, "dateUpload")
        val _result: ChapterEntity?
        if (_stmt.step()) {
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpMangaId: String
          _tmpMangaId = _stmt.getText(_columnIndexOfMangaId)
          val _tmpChapterNumber: Float
          _tmpChapterNumber = _stmt.getDouble(_columnIndexOfChapterNumber).toFloat()
          val _tmpName: String
          _tmpName = _stmt.getText(_columnIndexOfName)
          val _tmpScanlator: String
          _tmpScanlator = _stmt.getText(_columnIndexOfScanlator)
          val _tmpReleaseDate: String
          _tmpReleaseDate = _stmt.getText(_columnIndexOfReleaseDate)
          val _tmpRead: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfRead).toInt()
          _tmpRead = _tmp != 0
          val _tmpBookmarked: Boolean
          val _tmp_1: Int
          _tmp_1 = _stmt.getLong(_columnIndexOfBookmarked).toInt()
          _tmpBookmarked = _tmp_1 != 0
          val _tmpLastPageRead: Int
          _tmpLastPageRead = _stmt.getLong(_columnIndexOfLastPageRead).toInt()
          val _tmpTotalPages: Int
          _tmpTotalPages = _stmt.getLong(_columnIndexOfTotalPages).toInt()
          val _tmpFetchUrl: String
          _tmpFetchUrl = _stmt.getText(_columnIndexOfFetchUrl)
          val _tmpDateUpload: Long
          _tmpDateUpload = _stmt.getLong(_columnIndexOfDateUpload)
          _result =
              ChapterEntity(_tmpId,_tmpMangaId,_tmpChapterNumber,_tmpName,_tmpScanlator,_tmpReleaseDate,_tmpRead,_tmpBookmarked,_tmpLastPageRead,_tmpTotalPages,_tmpFetchUrl,_tmpDateUpload)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun getRecentlyReadChapters(): Flow<List<ChapterEntity>> {
    val _sql: String = "SELECT * FROM chapters WHERE read = 1 ORDER BY dateUpload DESC LIMIT 20"
    return createFlow(__db, false, arrayOf("chapters")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfMangaId: Int = getColumnIndexOrThrow(_stmt, "mangaId")
        val _columnIndexOfChapterNumber: Int = getColumnIndexOrThrow(_stmt, "chapterNumber")
        val _columnIndexOfName: Int = getColumnIndexOrThrow(_stmt, "name")
        val _columnIndexOfScanlator: Int = getColumnIndexOrThrow(_stmt, "scanlator")
        val _columnIndexOfReleaseDate: Int = getColumnIndexOrThrow(_stmt, "releaseDate")
        val _columnIndexOfRead: Int = getColumnIndexOrThrow(_stmt, "read")
        val _columnIndexOfBookmarked: Int = getColumnIndexOrThrow(_stmt, "bookmarked")
        val _columnIndexOfLastPageRead: Int = getColumnIndexOrThrow(_stmt, "lastPageRead")
        val _columnIndexOfTotalPages: Int = getColumnIndexOrThrow(_stmt, "totalPages")
        val _columnIndexOfFetchUrl: Int = getColumnIndexOrThrow(_stmt, "fetchUrl")
        val _columnIndexOfDateUpload: Int = getColumnIndexOrThrow(_stmt, "dateUpload")
        val _result: MutableList<ChapterEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: ChapterEntity
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpMangaId: String
          _tmpMangaId = _stmt.getText(_columnIndexOfMangaId)
          val _tmpChapterNumber: Float
          _tmpChapterNumber = _stmt.getDouble(_columnIndexOfChapterNumber).toFloat()
          val _tmpName: String
          _tmpName = _stmt.getText(_columnIndexOfName)
          val _tmpScanlator: String
          _tmpScanlator = _stmt.getText(_columnIndexOfScanlator)
          val _tmpReleaseDate: String
          _tmpReleaseDate = _stmt.getText(_columnIndexOfReleaseDate)
          val _tmpRead: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfRead).toInt()
          _tmpRead = _tmp != 0
          val _tmpBookmarked: Boolean
          val _tmp_1: Int
          _tmp_1 = _stmt.getLong(_columnIndexOfBookmarked).toInt()
          _tmpBookmarked = _tmp_1 != 0
          val _tmpLastPageRead: Int
          _tmpLastPageRead = _stmt.getLong(_columnIndexOfLastPageRead).toInt()
          val _tmpTotalPages: Int
          _tmpTotalPages = _stmt.getLong(_columnIndexOfTotalPages).toInt()
          val _tmpFetchUrl: String
          _tmpFetchUrl = _stmt.getText(_columnIndexOfFetchUrl)
          val _tmpDateUpload: Long
          _tmpDateUpload = _stmt.getLong(_columnIndexOfDateUpload)
          _item =
              ChapterEntity(_tmpId,_tmpMangaId,_tmpChapterNumber,_tmpName,_tmpScanlator,_tmpReleaseDate,_tmpRead,_tmpBookmarked,_tmpLastPageRead,_tmpTotalPages,_tmpFetchUrl,_tmpDateUpload)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun updateChapterReadState(
    chapterId: String,
    read: Boolean,
    lastPageRead: Int,
  ) {
    val _sql: String = "UPDATE chapters SET read = ?, lastPageRead = ? WHERE id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        val _tmp: Int = if (read) 1 else 0
        _stmt.bindLong(_argIndex, _tmp.toLong())
        _argIndex = 2
        _stmt.bindLong(_argIndex, lastPageRead.toLong())
        _argIndex = 3
        _stmt.bindText(_argIndex, chapterId)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun toggleBookmark(chapterId: String, bookmarked: Boolean) {
    val _sql: String = "UPDATE chapters SET bookmarked = ? WHERE id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        val _tmp: Int = if (bookmarked) 1 else 0
        _stmt.bindLong(_argIndex, _tmp.toLong())
        _argIndex = 2
        _stmt.bindText(_argIndex, chapterId)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun markPreviousChaptersAsRead(mangaId: String, chapterNumber: Float) {
    val _sql: String = "UPDATE chapters SET read = 1 WHERE mangaId = ? AND chapterNumber <= ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, mangaId)
        _argIndex = 2
        _stmt.bindDouble(_argIndex, chapterNumber.toDouble())
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
