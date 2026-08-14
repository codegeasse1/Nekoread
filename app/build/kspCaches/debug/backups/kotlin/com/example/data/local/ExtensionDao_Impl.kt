package com.example.`data`.local

import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import javax.`annotation`.processing.Generated
import kotlin.Boolean
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
public class ExtensionDao_Impl(
  __db: RoomDatabase,
) : ExtensionDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfExtensionRepoEntity: EntityInsertAdapter<ExtensionRepoEntity>

  private val __insertAdapterOfExtensionSourceEntity: EntityInsertAdapter<ExtensionSourceEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfExtensionRepoEntity = object : EntityInsertAdapter<ExtensionRepoEntity>()
        {
      protected override fun createQuery(): String =
          "INSERT OR REPLACE INTO `extension_repos` (`id`,`name`,`url`,`extensionCount`,`isOfficial`,`addedDate`) VALUES (?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: ExtensionRepoEntity) {
        statement.bindText(1, entity.id)
        statement.bindText(2, entity.name)
        statement.bindText(3, entity.url)
        statement.bindLong(4, entity.extensionCount.toLong())
        val _tmp: Int = if (entity.isOfficial) 1 else 0
        statement.bindLong(5, _tmp.toLong())
        statement.bindLong(6, entity.addedDate)
      }
    }
    this.__insertAdapterOfExtensionSourceEntity = object :
        EntityInsertAdapter<ExtensionSourceEntity>() {
      protected override fun createQuery(): String =
          "INSERT OR REPLACE INTO `extension_sources` (`id`,`name`,`version`,`lang`,`iconUrl`,`repoId`,`isInstalled`,`isNsfw`,`baseUrl`,`sourceType`) VALUES (?,?,?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: ExtensionSourceEntity) {
        statement.bindText(1, entity.id)
        statement.bindText(2, entity.name)
        statement.bindText(3, entity.version)
        statement.bindText(4, entity.lang)
        statement.bindText(5, entity.iconUrl)
        statement.bindText(6, entity.repoId)
        val _tmp: Int = if (entity.isInstalled) 1 else 0
        statement.bindLong(7, _tmp.toLong())
        val _tmp_1: Int = if (entity.isNsfw) 1 else 0
        statement.bindLong(8, _tmp_1.toLong())
        statement.bindText(9, entity.baseUrl)
        statement.bindText(10, entity.sourceType)
      }
    }
  }

  public override suspend fun insertRepo(repo: ExtensionRepoEntity): Unit = performSuspending(__db,
      false, true) { _connection ->
    __insertAdapterOfExtensionRepoEntity.insert(_connection, repo)
  }

  public override suspend fun insertRepos(repos: List<ExtensionRepoEntity>): Unit =
      performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfExtensionRepoEntity.insert(_connection, repos)
  }

  public override suspend fun insertSources(sources: List<ExtensionSourceEntity>): Unit =
      performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfExtensionSourceEntity.insert(_connection, sources)
  }

  public override fun getAllRepos(): Flow<List<ExtensionRepoEntity>> {
    val _sql: String = "SELECT * FROM extension_repos ORDER BY isOfficial DESC, name ASC"
    return createFlow(__db, false, arrayOf("extension_repos")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfName: Int = getColumnIndexOrThrow(_stmt, "name")
        val _columnIndexOfUrl: Int = getColumnIndexOrThrow(_stmt, "url")
        val _columnIndexOfExtensionCount: Int = getColumnIndexOrThrow(_stmt, "extensionCount")
        val _columnIndexOfIsOfficial: Int = getColumnIndexOrThrow(_stmt, "isOfficial")
        val _columnIndexOfAddedDate: Int = getColumnIndexOrThrow(_stmt, "addedDate")
        val _result: MutableList<ExtensionRepoEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: ExtensionRepoEntity
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpName: String
          _tmpName = _stmt.getText(_columnIndexOfName)
          val _tmpUrl: String
          _tmpUrl = _stmt.getText(_columnIndexOfUrl)
          val _tmpExtensionCount: Int
          _tmpExtensionCount = _stmt.getLong(_columnIndexOfExtensionCount).toInt()
          val _tmpIsOfficial: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfIsOfficial).toInt()
          _tmpIsOfficial = _tmp != 0
          val _tmpAddedDate: Long
          _tmpAddedDate = _stmt.getLong(_columnIndexOfAddedDate)
          _item =
              ExtensionRepoEntity(_tmpId,_tmpName,_tmpUrl,_tmpExtensionCount,_tmpIsOfficial,_tmpAddedDate)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun getAllSources(): Flow<List<ExtensionSourceEntity>> {
    val _sql: String = "SELECT * FROM extension_sources ORDER BY isInstalled DESC, name ASC"
    return createFlow(__db, false, arrayOf("extension_sources")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfName: Int = getColumnIndexOrThrow(_stmt, "name")
        val _columnIndexOfVersion: Int = getColumnIndexOrThrow(_stmt, "version")
        val _columnIndexOfLang: Int = getColumnIndexOrThrow(_stmt, "lang")
        val _columnIndexOfIconUrl: Int = getColumnIndexOrThrow(_stmt, "iconUrl")
        val _columnIndexOfRepoId: Int = getColumnIndexOrThrow(_stmt, "repoId")
        val _columnIndexOfIsInstalled: Int = getColumnIndexOrThrow(_stmt, "isInstalled")
        val _columnIndexOfIsNsfw: Int = getColumnIndexOrThrow(_stmt, "isNsfw")
        val _columnIndexOfBaseUrl: Int = getColumnIndexOrThrow(_stmt, "baseUrl")
        val _columnIndexOfSourceType: Int = getColumnIndexOrThrow(_stmt, "sourceType")
        val _result: MutableList<ExtensionSourceEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: ExtensionSourceEntity
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpName: String
          _tmpName = _stmt.getText(_columnIndexOfName)
          val _tmpVersion: String
          _tmpVersion = _stmt.getText(_columnIndexOfVersion)
          val _tmpLang: String
          _tmpLang = _stmt.getText(_columnIndexOfLang)
          val _tmpIconUrl: String
          _tmpIconUrl = _stmt.getText(_columnIndexOfIconUrl)
          val _tmpRepoId: String
          _tmpRepoId = _stmt.getText(_columnIndexOfRepoId)
          val _tmpIsInstalled: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfIsInstalled).toInt()
          _tmpIsInstalled = _tmp != 0
          val _tmpIsNsfw: Boolean
          val _tmp_1: Int
          _tmp_1 = _stmt.getLong(_columnIndexOfIsNsfw).toInt()
          _tmpIsNsfw = _tmp_1 != 0
          val _tmpBaseUrl: String
          _tmpBaseUrl = _stmt.getText(_columnIndexOfBaseUrl)
          val _tmpSourceType: String
          _tmpSourceType = _stmt.getText(_columnIndexOfSourceType)
          _item =
              ExtensionSourceEntity(_tmpId,_tmpName,_tmpVersion,_tmpLang,_tmpIconUrl,_tmpRepoId,_tmpIsInstalled,_tmpIsNsfw,_tmpBaseUrl,_tmpSourceType)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteRepo(id: String) {
    val _sql: String = "DELETE FROM extension_repos WHERE id = ?"
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

  public override suspend fun updateSourceInstalledStatus(id: String, installed: Boolean) {
    val _sql: String = "UPDATE extension_sources SET isInstalled = ? WHERE id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        val _tmp: Int = if (installed) 1 else 0
        _stmt.bindLong(_argIndex, _tmp.toLong())
        _argIndex = 2
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
