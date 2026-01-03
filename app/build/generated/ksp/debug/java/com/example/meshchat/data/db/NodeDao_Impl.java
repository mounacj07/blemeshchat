package com.example.meshchat.data.db;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class NodeDao_Impl implements NodeDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<NodeEntity> __insertionAdapterOfNodeEntity;

  private final SharedSQLiteStatement __preparedStmtOfUpdateNode;

  private final SharedSQLiteStatement __preparedStmtOfUpdateNodeName;

  private final SharedSQLiteStatement __preparedStmtOfClearAll;

  public NodeDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfNodeEntity = new EntityInsertionAdapter<NodeEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `nodes` (`nodeId`,`lastSeenTimestamp`,`hopCount`,`isDirect`,`name`) VALUES (?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final NodeEntity entity) {
        statement.bindString(1, entity.getNodeId());
        statement.bindLong(2, entity.getLastSeenTimestamp());
        statement.bindLong(3, entity.getHopCount());
        final int _tmp = entity.isDirect() ? 1 : 0;
        statement.bindLong(4, _tmp);
        if (entity.getName() == null) {
          statement.bindNull(5);
        } else {
          statement.bindString(5, entity.getName());
        }
      }
    };
    this.__preparedStmtOfUpdateNode = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE nodes SET lastSeenTimestamp = ?, hopCount = ?, isDirect = ? WHERE nodeId = ?";
        return _query;
      }
    };
    this.__preparedStmtOfUpdateNodeName = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE nodes SET name = ? WHERE nodeId = ?";
        return _query;
      }
    };
    this.__preparedStmtOfClearAll = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM nodes";
        return _query;
      }
    };
  }

  @Override
  public Object insertNode(final NodeEntity node, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfNodeEntity.insert(node);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object updateNode(final String id, final long t, final int hops, final boolean direct,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfUpdateNode.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, t);
        _argIndex = 2;
        _stmt.bindLong(_argIndex, hops);
        _argIndex = 3;
        final int _tmp = direct ? 1 : 0;
        _stmt.bindLong(_argIndex, _tmp);
        _argIndex = 4;
        _stmt.bindString(_argIndex, id);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfUpdateNode.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object updateNodeName(final String id, final String name,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfUpdateNodeName.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, name);
        _argIndex = 2;
        _stmt.bindString(_argIndex, id);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfUpdateNodeName.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object clearAll(final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfClearAll.acquire();
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfClearAll.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<NodeEntity>> getAllNodes() {
    final String _sql = "SELECT * FROM nodes ORDER BY name ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"nodes"}, new Callable<List<NodeEntity>>() {
      @Override
      @NonNull
      public List<NodeEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfNodeId = CursorUtil.getColumnIndexOrThrow(_cursor, "nodeId");
          final int _cursorIndexOfLastSeenTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "lastSeenTimestamp");
          final int _cursorIndexOfHopCount = CursorUtil.getColumnIndexOrThrow(_cursor, "hopCount");
          final int _cursorIndexOfIsDirect = CursorUtil.getColumnIndexOrThrow(_cursor, "isDirect");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final List<NodeEntity> _result = new ArrayList<NodeEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final NodeEntity _item;
            final String _tmpNodeId;
            _tmpNodeId = _cursor.getString(_cursorIndexOfNodeId);
            final long _tmpLastSeenTimestamp;
            _tmpLastSeenTimestamp = _cursor.getLong(_cursorIndexOfLastSeenTimestamp);
            final int _tmpHopCount;
            _tmpHopCount = _cursor.getInt(_cursorIndexOfHopCount);
            final boolean _tmpIsDirect;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsDirect);
            _tmpIsDirect = _tmp != 0;
            final String _tmpName;
            if (_cursor.isNull(_cursorIndexOfName)) {
              _tmpName = null;
            } else {
              _tmpName = _cursor.getString(_cursorIndexOfName);
            }
            _item = new NodeEntity(_tmpNodeId,_tmpLastSeenTimestamp,_tmpHopCount,_tmpIsDirect,_tmpName);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Object getNodeById(final String id, final Continuation<? super NodeEntity> $completion) {
    final String _sql = "SELECT * FROM nodes WHERE nodeId = ? LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, id);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<NodeEntity>() {
      @Override
      @Nullable
      public NodeEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfNodeId = CursorUtil.getColumnIndexOrThrow(_cursor, "nodeId");
          final int _cursorIndexOfLastSeenTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "lastSeenTimestamp");
          final int _cursorIndexOfHopCount = CursorUtil.getColumnIndexOrThrow(_cursor, "hopCount");
          final int _cursorIndexOfIsDirect = CursorUtil.getColumnIndexOrThrow(_cursor, "isDirect");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final NodeEntity _result;
          if (_cursor.moveToFirst()) {
            final String _tmpNodeId;
            _tmpNodeId = _cursor.getString(_cursorIndexOfNodeId);
            final long _tmpLastSeenTimestamp;
            _tmpLastSeenTimestamp = _cursor.getLong(_cursorIndexOfLastSeenTimestamp);
            final int _tmpHopCount;
            _tmpHopCount = _cursor.getInt(_cursorIndexOfHopCount);
            final boolean _tmpIsDirect;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsDirect);
            _tmpIsDirect = _tmp != 0;
            final String _tmpName;
            if (_cursor.isNull(_cursorIndexOfName)) {
              _tmpName = null;
            } else {
              _tmpName = _cursor.getString(_cursorIndexOfName);
            }
            _result = new NodeEntity(_tmpNodeId,_tmpLastSeenTimestamp,_tmpHopCount,_tmpIsDirect,_tmpName);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
