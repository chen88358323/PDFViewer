package org.ebookdroid.common.settings.books;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.ebookdroid.common.settings.types.DocumentViewMode;
import org.ebookdroid.common.settings.types.PageAlign;
import org.ebookdroid.core.PageIndex;
import org.ebookdroid.core.curl.PageAnimationType;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

public class DBAdapterV1 implements IDBAdapter {

    public static final int VERSION = 1;

    public static final String DB_BOOK_CREATE = "create table book_settings ("
    // Book file path
            + "book varchar(1024) not null , "
            // Last update time
            + "last_updated integer not null, "
            // Current document page
            + "doc_page integer not null, "
            // Current view page - dependent on view mode
            + "view_page integer not null, "
            // Page zoom
            + "zoom integer not null, "
            // Single page mode on/off
            + "single_page integer not null, "
            // Page align
            + "page_align integer not null, "
            // Page animation type
            + "page_animation integer not null, "
            // Split pages on/off
            + "split_pages integer not null" +
            // ...
            ");";

   
    public static final String DB_BOOK_GET_ALL = "SELECT book, last_updated, doc_page, view_page, zoom, single_page, page_align, page_animation, split_pages FROM book_settings where last_updated > 0 ORDER BY last_updated DESC";

    public static final String DB_BOOK_GET_ONE = "SELECT book, last_updated, doc_page, view_page, zoom, single_page, page_align, page_animation, split_pages FROM book_settings WHERE book=?";

    public static final String DB_BOOK_STORE = "INSERT OR REPLACE INTO book_settings (book, last_updated, doc_page," +
    		" view_page, zoom, single_page, page_align, page_animation, split_pages) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

    public static final String DB_BOOK_DEL = "DELETE FROM book_settings WHERE book=?";

    public static final String DB_BOOK_CLEAR_RECENT = "UPDATE book_settings set last_updated = 0";

    public static final String DB_BOOK_REMOVE_BOOK_FROM_RECENT = "UPDATE book_settings set last_updated = 0 WHERE book=?";

    public static final String DB_BOOK_DROP = "DROP TABLE IF EXISTS book_settings";

    protected final DBSettingsManager manager;

    public DBAdapterV1(final DBSettingsManager manager) {
        this.manager = manager;
    }

    @Override
    public void onCreate(final SQLiteDatabase db) {
        db.execSQL(DB_BOOK_CREATE);
    
    }

    @Override
    public void onDestroy(final SQLiteDatabase db) {
        db.execSQL(DB_BOOK_DROP);
    }

    @Override
    public Map<String, BookSettings> getAllBooks() {
        return getRecentBooks(true);
    }

    @Override
    public Map<String, BookSettings> getRecentBooks(final boolean all) {
        return getBookSettings(DB_BOOK_GET_ALL, all);
    }

    protected final Map<String, BookSettings> getBookSettings(final String query, final boolean all) {
        final Map<String, BookSettings> map = new LinkedHashMap<String, BookSettings>();

        try {
            final SQLiteDatabase db = manager.getReadableDatabase();
            try {
                final Cursor c = db.rawQuery(query, null);
                if (c != null) {
                    try {
                        for (boolean next = c.moveToFirst(); next; next = c.moveToNext()) {
                            final BookSettings bs = createBookSettings(c);
                            loadBookmarks(bs, db);
                            map.put(bs.fileName, bs);
                            if (!all) {
                                break;
                            }
                        }
                    } finally {
                        close(c);
                    }
                }
            } finally {
                manager.closeDatabase(db);
            }
        } catch (final Throwable th) {
            LCTX.e("Retrieving book settings failed: ", th);
        }

        return map;
    }

    @Override
    public BookSettings getBookSettings(final String fileName) {
        return getBookSettings(DB_BOOK_GET_ONE, fileName);
    }

    protected final BookSettings getBookSettings(final String query, final String fileName) {
        try {
            final SQLiteDatabase db = manager.getReadableDatabase();
            try {
                final Cursor c = db.rawQuery(query, new String[] { fileName });
                if (c != null) {
                    try {
                        if (c.moveToFirst()) {
                            final BookSettings bs = createBookSettings(c);
                            loadBookmarks(bs, db);
                            return bs;
                        }
                    } finally {
                        close(c);
                    }
                }
            } finally {
                manager.closeDatabase(db);
            }
        } catch (final Throwable th) {
            LCTX.e("Retrieving book settings failed: ", th);
        }

        return null;
    }

    @Override
    public final boolean storeBookSettings(final BookSettings bs) {
        try {
            final SQLiteDatabase db = manager.getWritableDatabase();
            try {
                db.beginTransaction();

                if (bs.lastChanged > 0) {
                    bs.lastUpdated = System.currentTimeMillis();
                }

                if (LCTX.isDebugEnabled()) {
                    LCTX.d("Store: " + bs.toJSON());
                }

                storeBookSettings(bs, db);

                db.setTransactionSuccessful();

                return true;
            } finally {
                endTransaction(db);
            }
        } catch (final Throwable th) {
            LCTX.e("Update book settings failed: ", th);
        }
        return false;
    }

    @Override
    public boolean storeBookSettings(final List<BookSettings> list) {
        try {
            final SQLiteDatabase db = manager.getWritableDatabase();
            try {
                db.beginTransaction();

                for (final BookSettings bs : list) {
                    if (bs.lastChanged > 0) {
                        bs.lastUpdated = System.currentTimeMillis();
                    }

                    if (LCTX.isDebugEnabled()) {
                        LCTX.d("Store: " + bs.toJSON());
                    }

                    storeBookSettings(bs, db);
                }
                db.setTransactionSuccessful();

                return true;
            } finally {
                endTransaction(db);
            }
        } catch (final Throwable th) {
            LCTX.e("Update book settings failed: ", th);
        }
        return false;
    }

    @Override
    public final boolean restoreBookSettings(final Collection<BookSettings> c) {
        try {
            final SQLiteDatabase db = manager.getWritableDatabase();
            try {
                db.beginTransaction();

                for (final BookSettings bs : c) {
                    storeBookSettings(bs, db);
                }

                db.setTransactionSuccessful();

                return true;
            } finally {
                endTransaction(db);
            }
        } catch (final Throwable th) {
            LCTX.e("Update book settings failed: ", th);
        }
        return false;
    }

    @Override
    public boolean deleteAllBookmarks() {
        return false;
    }

    @Override
    public final boolean updateBookmarks(final BookSettings book) {
        try {
            final SQLiteDatabase db = manager.getWritableDatabase();
            try {
                db.beginTransaction();

                updateBookmarks(book, db);

                db.setTransactionSuccessful();

                return true;
            } finally {
                endTransaction(db);
            }
        } catch (final Throwable th) {
            LCTX.e("Update bookmarks failed: ", th);
        }
        return false;
    }

    @Override
    public boolean deleteBookmarks(final String book, final List<Bookmark> bookmarks) {
        return false;
    }

    @Override
    public boolean clearRecent() {
        try {
            final SQLiteDatabase db = manager.getWritableDatabase();
            try {
                db.beginTransaction();

                db.execSQL(DB_BOOK_CLEAR_RECENT, new Object[] {});

                db.setTransactionSuccessful();

                return true;
            } finally {
                endTransaction(db);
            }
        } catch (final Throwable th) {
            LCTX.e("Update book settings failed: ", th);
        }
        return false;
    }

    @Override
    public boolean removeBookFromRecents(final BookSettings bs) {
        try {
            final SQLiteDatabase db = manager.getWritableDatabase();
            try {
                db.beginTransaction();

                db.execSQL(DB_BOOK_REMOVE_BOOK_FROM_RECENT, new Object[] { bs.fileName });

                db.setTransactionSuccessful();

                return true;
            } finally {
                endTransaction(db);
            }
        } catch (final Throwable th) {
            LCTX.e("Removing book from recents failed: ", th);
        }
        return false;
    }

    @Override
    public void delete(final BookSettings current) {
        try {
            final SQLiteDatabase db = manager.getWritableDatabase();
            try {
                db.beginTransaction();

                db.execSQL(DB_BOOK_DEL, new Object[] { current.fileName });

                db.setTransactionSuccessful();
            } finally {
                endTransaction(db);
            }
        } catch (final Throwable th) {
            LCTX.e("Delete book settings failed: ", th);
        }
    }

    @Override
    public boolean deleteAll() {
        try {
            final SQLiteDatabase db = manager.getWritableDatabase();
            try {
                db.beginTransaction();

                db.execSQL(DB_BOOK_DROP, new Object[] {});

                onCreate(db);

                db.setTransactionSuccessful();

                return true;
            } finally {
                endTransaction(db);
            }
        } catch (final Throwable th) {
            LCTX.e("Update book settings failed: ", th);
        }
        return false;
    }

    protected void storeBookSettings(final BookSettings bs, final SQLiteDatabase db) {
        final Object[] args = new Object[] {
                // File name
                bs.fileName,
                // Last update
                bs.lastUpdated,
                // Current document page
                bs.currentPage.docIndex,
                // Current view page
                bs.currentPage.viewIndex,
                // Current page zoom
                bs.zoom,
                // Single page on/off
                bs.viewMode == DocumentViewMode.SINGLE_PAGE ? 1 : 0,
                // Page align
                bs.pageAlign.ordinal(),
                // Page animation type
                bs.animationType.ordinal(),
                // Split pages on/off
                bs.splitPages ? 1 : 0 };

        db.execSQL(DB_BOOK_STORE, args);

        updateBookmarks(bs, db);
    }

    protected BookSettings createBookSettings(final Cursor c) {
        int index = 0;

        final BookSettings bs = new BookSettings(c.getString(index++));
        bs.lastUpdated = c.getLong(index++);
        bs.currentPage = new PageIndex(c.getInt(index++), c.getInt(index++));
        bs.zoom = c.getInt(index++);
        bs.viewMode = c.getInt(index++) != 0 ? DocumentViewMode.SINGLE_PAGE : DocumentViewMode.VERTICALL_SCROLL;
        bs.pageAlign = PageAlign.values()[c.getInt(index++)];
        bs.animationType = PageAnimationType.values()[c.getInt(index++)];
        bs.splitPages = c.getInt(index++) != 0;

        return bs;
    }
    protected Version createVersion(final Cursor c) {
        int index = 0;
        final Version bs = new Version();
        bs.setId(c.getLong(index++));
        bs.setVnum(c.getLong(index++));
        bs.setMd5(c.getString(index++));
        bs.setMarksname(c.getString(index++));
        bs.setBookname(c.getString(index++));
        bs.setMethod(c.getString(index++));
        bs.setCreatetime(c.getLong(index++));
        bs.setModifytime(c.getLong(index++));
        bs.setSyntag(c.getInt(index++));
        return bs;
    }

    void updateBookmarks(final BookSettings bs, final SQLiteDatabase db) {
    }

    void loadBookmarks(final BookSettings book, final SQLiteDatabase db) {
        book.bookmarks.clear();
    }

    void endTransaction(final SQLiteDatabase db) {
        try {
            db.endTransaction();
        } catch (final Exception ex) {
        }
        manager.closeDatabase(db);
    }

    final void close(final Cursor c) {
        try {
            c.close();
        } catch (final Exception ex) {
        }
    }
    public static final String getVersionByNameMD5Val="select  * from versions where md5=?  and synctag=";
	/**获取该书的记录列表**/
    @Override
	public  List<Version> getVersionByBookNameMd5Val(Version v,int syncTag) {
		 return getVersionByBookNameMd5Val(v.getMd5(),v.getBookname(),syncTag);
	}
    
    /**获取该书的记录列表**/
    @Override
	public  List<Version> getVersionByBookNameMd5Val(String bn,String md5,int syncTag) {
		 List<Version> vl=new ArrayList<Version>();
		 	String order=" order by vnum desc";
	        try {
	            final SQLiteDatabase db = manager.getReadableDatabase();
	            try {
	                final Cursor c = db.rawQuery(getVersionByNameMD5Val+syncTag+order,  new String[]{md5});
	                if (c != null) {
	                    try {
	                        for (boolean next = c.moveToFirst(); next; next = c.moveToNext()) {
	                            final Version bs = createVersion(c);

	                            vl.add(bs);
	                        }
	                    } finally {
	                        close(c);
	                    }
	                }
	            } finally {
	                manager.closeDatabase(db);
	            }
	        } catch (final Throwable th) {
	            LCTX.e("Retrieving book settings failed: ", th);
	        }

	        return vl;
	}
    //todo bookname or md5?
    public static final String getMaxVnumByMD5ValSyncTag="select  max(vnum) from versions where md5=? and synctag=";
	public  long  getMaxVnumByBookNameMd5Val(String bn,int syncTag) {
    	long res=0;
	        try {
	            final SQLiteDatabase db = manager.getReadableDatabase();
	            try {
	                final Cursor c = db.rawQuery(getMaxVnumByMD5ValSyncTag+syncTag, new String[]{bn} );
	                if (c != null) {
	                    try {
	                    	c.moveToFirst();
	                    	res=c.getLong(0);
	                    } finally {
	                        close(c);
	                    }
	                }
	            } finally {
	                manager.closeDatabase(db);
	            }
	        } catch (final Throwable th) {
	            LCTX.e("Retrieving book settings failed: ", th);
	        }

	        return res;
	}
    public static final String getMaxVnumByNameMD5Val="select  max(vnum) from versions where md5=? and bookname=? and synctag=";
    @Override
	public  long  getMaxVnumByBookNameMd5Val(Version v,int syncTag) {
    	long res=0;
	        try {
	            final SQLiteDatabase db = manager.getReadableDatabase();
	            try {
	                final Cursor c = db.rawQuery(getMaxVnumByNameMD5Val+syncTag,  new String[]{v.getMd5(),v.getBookname()});
	                if (c != null) {
	                    try {
	                    	c.moveToFirst();
	                    	res=c.getLong(0);
	                    } finally {
	                        close(c);
	                    }
	                }
	            } finally {
	                manager.closeDatabase(db);
	            }
	        } catch (final Throwable th) {
	            LCTX.e("Retrieving book settings failed: ", th);
	        }

	        return res;
	}
    public static final String DB_VERSION_STORE = "INSERT OR REPLACE INTO versions (vnum, md5, method, bookname,marksname, createtime, modifytime,synctag) VALUES (?, ?, ?, ?, ?, ?, ?,?)";

	@Override
	public boolean storeVersion(Version v) {
		  try {
	            final SQLiteDatabase db = manager.getWritableDatabase();
	            try {
	                db.beginTransaction();
	                final Object[] args = new Object[] {
	    	                // File name
	    	               v.getVnum(),
	    	                // Last update
	    	                v.getMd5(),
	    	                // Current view page
	    	               v.getMethod(),
	    	                // Current page zoom
	    	                v.getBookname(),
	    	                v.getMarksname(),
	    	                // Page align
	    	              v.getCreatetime(),
	    	              v.getModifytime() ,
	    	              v.getSyntag()
	    	              };
	    	        db.execSQL(DB_VERSION_STORE, args);

	                db.setTransactionSuccessful();

	                return true;
	            } finally {
	                endTransaction(db);
	            }
	        } catch (final Throwable th) {
	            LCTX.e("Update book settings failed: ", th);
	        }
	        return false;
	}
	public static final String DB_VERSION_UPDATE = "UPDATE versions  SET  synctag=?  WHERE  id=?";

		
	public  boolean  updateVersion(long vid,int synctag){
		 try {
	            final SQLiteDatabase db = manager.getWritableDatabase();
	            try {
	                db.beginTransaction();
	                final Object[] args = new Object[] {
	                		synctag,
	                		vid
	    	              };
	    	        db.execSQL(DB_VERSION_UPDATE, args);
	                db.setTransactionSuccessful();

	                return true;
	            } finally {
	                endTransaction(db);
	            }
	        } catch (final Throwable th) {
	            LCTX.e("Update version settings failed: ", th);
	        }
	        return false;
	}
	
}
