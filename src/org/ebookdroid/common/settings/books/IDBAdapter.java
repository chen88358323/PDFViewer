package org.ebookdroid.common.settings.books;


import android.database.sqlite.SQLiteDatabase;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.emdev.common.log.LogContext;
import org.emdev.common.log.LogManager;

interface IDBAdapter {

    LogContext LCTX = LogManager.root().lctx("DBAdapter", false);

    void onCreate(final SQLiteDatabase db);

    void onDestroy(final SQLiteDatabase db);

    Map<String, BookSettings> getAllBooks();

    Map<String, BookSettings> getRecentBooks(boolean all);

    BookSettings getBookSettings(String fileName);

    boolean storeBookSettings(BookSettings bs);

    boolean storeBookSettings(List<BookSettings> list);

    boolean restoreBookSettings(Collection<BookSettings> c);

    boolean clearRecent();

    void delete(BookSettings current);

    boolean deleteAll();

    boolean updateBookmarks(BookSettings book);

    boolean deleteBookmarks(String book, List<Bookmark> bookmarks);

    boolean deleteAllBookmarks();

    boolean removeBookFromRecents(BookSettings bs);
    
    //根据bookname ,md5  查询记录
    List<Version> getVersionByBookNameMd5Val(Version v,int syntag);
    //根据bookname ,md5  查询记录
    List<Version> getVersionByBookNameMd5Val(String bn,String md5,int syntag);
    //根据bookname ,md5  获得最大记录号
    long  getMaxVnumByBookNameMd5Val(Version v,int syntag) ;
    //根据bookname ,md5  获得最大记录号
    long  getMaxVnumByBookNameMd5Val(String v,int syntag) ;
    //根据bookname md5值和同步状态  获得最大记录号
    //  long  getMaxVnumByBookNameMd5Val(String v,int syncTag) ;
  
    boolean  storeVersion(Version v);
    
    boolean  updateVersion(long vid,int synctag);
}
