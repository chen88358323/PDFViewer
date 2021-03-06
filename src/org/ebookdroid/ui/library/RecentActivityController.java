package org.ebookdroid.ui.library;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.ebookdroid.CodecType;
import org.ebookdroid.common.cache.CacheManager;
import org.ebookdroid.common.cache.CacheManager.ICacheListener;
import org.ebookdroid.common.cache.ThumbnailFile;
import org.ebookdroid.common.settings.AppSettings;
import org.ebookdroid.common.settings.BackupSettings;
import org.ebookdroid.common.settings.LibSettings;
import org.ebookdroid.common.settings.SettingsManager;
import org.ebookdroid.common.settings.books.BookSettings;
import org.ebookdroid.common.settings.books.Bookmark;
import org.ebookdroid.common.settings.books.Version;
import org.ebookdroid.common.settings.listeners.ILibSettingsChangeListener;
import org.ebookdroid.common.settings.listeners.IRecentBooksChangedListener;
import org.ebookdroid.ui.library.adapters.BookNode;
import org.ebookdroid.ui.library.adapters.BookShelfAdapter;
import org.ebookdroid.ui.library.adapters.BooksAdapter;
import org.ebookdroid.ui.library.adapters.LibraryAdapter;
import org.ebookdroid.ui.library.adapters.RecentAdapter;
import org.ebookdroid.ui.library.dialogs.BackupDlg;
import org.ebookdroid.ui.library.tasks.CopyBookTask;
import org.ebookdroid.ui.library.tasks.MoveBookTask;
import org.ebookdroid.ui.library.tasks.RenameBookTask;
import org.ebookdroid.ui.settings.SettingsUI;
import org.emdev.common.backup.BackupManager;
import org.emdev.common.filesystem.FileExtensionFilter;
import org.emdev.common.filesystem.MediaManager;
import org.emdev.common.filesystem.MediaState;
import org.emdev.common.log.LogContext;
import org.emdev.common.log.LogManager;
import org.emdev.ui.AbstractActivityController;
import org.emdev.ui.actions.ActionDialogBuilder;
import org.emdev.ui.actions.ActionEx;
import org.emdev.ui.actions.ActionMenuHelper;
import org.emdev.ui.actions.ActionMethod;
import org.emdev.ui.actions.IActionController;
import org.emdev.ui.actions.params.Constant;
import org.emdev.ui.actions.params.EditableValue;
import org.emdev.ui.uimanager.IUIManager;
import org.emdev.utils.CompareUtils;
import org.emdev.utils.FileUtils;
import org.emdev.utils.LengthUtils;
import org.emdev.utils.http.HttpUtils;
import org.emdev.utils.md5.Md5Creater;

import the.pdfviewerx.BrowserActivity;
import the.pdfviewerx.EBookDroidApp;
import the.pdfviewerx.FolderDlg;
import the.pdfviewerx.R;
import the.pdfviewerx.RecentActivity;
import the.pdfviewerx.ViewerActivity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.text.Editable;
import android.view.View;
import android.view.Window;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;

import com.alibaba.fastjson.JSON;

public class RecentActivityController extends AbstractActivityController<RecentActivity> implements IBrowserActivity,
        ILibSettingsChangeListener, IRecentBooksChangedListener, ICacheListener, MediaManager.Listener {

    public static final AtomicBoolean working = new AtomicBoolean();

    private static final int CLEAR_RECENT_LIST = 0;
    private static final int DELETE_BOOKMARKS = 1;
    private static final int DELETE_BOOK_SETTINGS = 2;
    private static final int ERASE_DISK_CACHE = 3;

    private RecentAdapter recentAdapter;
    private LibraryAdapter libraryAdapter;
    private BooksAdapter bookshelfAdapter;

    private final ThumbnailFile def = CacheManager.getThumbnailFile(".");

    private boolean recentLoaded = false;

    public RecentActivityController(final RecentActivity activity) {
        super(activity, BEFORE_CREATE, AFTER_CREATE, ON_RESUME, ON_DESTROY);
        working.set(true);
    }

    /**
     * {@inheritDoc}
     *
     * @see org.emdev.ui.AbstractActivityController#beforeCreate(android.app.Activity)
     */
    @Override
    public void beforeCreate(final RecentActivity activity) {
        super.beforeCreate(activity);

        recentAdapter = new RecentAdapter(this);
        bookshelfAdapter = new BooksAdapter(this, recentAdapter);
        libraryAdapter = new LibraryAdapter(bookshelfAdapter);

        CacheManager.listeners.addListener(this);
        MediaManager.listeners.addListener(this);
        SettingsManager.addListener(this);
    }
    /****
     * 同步线程
     * */
    class SyncThread  extends Thread {
    	private String bn;
    	SyncThread(String bookname){
    		bn=bookname;
    	}
    	public void run(){
    		String temp[] = bn.split("/"); /**split里面必须是正则表达式，"\\"的作用是对字符串转义*/  
        	bn = temp[temp.length-1];  
        	String md5=Md5Creater.getMd5(bn);
        	vlog.i("******书籍:"+bn+" is start sync md5 is:"+md5);
        	long local=SettingsManager.getMaxVnumByBookName(md5,1);
        	long remote=getRemoteMaxVnumByBookName(md5);
//        	long remote=0;
        	//待同步
        	long needSyn= SettingsManager.getMaxVnumByBookName(md5,0);
            boolean update=false;
        	if(local<remote){//远程有更新
        		update=true;
        		//获取增量数据
        		List<Version> vl=getVersionListByremote(md5,local);
        		if(vl!=null&&vl.size()>0){
        			vlog.i("*******获取服务器增量数据 getlist size:"+vl.size());
        			//写入数据库
        			SettingsManager.storeVersionsList(vl, bn,1);
        		}
        	}
        	vlog.i("local:"+local+ "  remote:"+remote+"   needSyn:"+needSyn);
        	if(local<needSyn){//有待上传版本
        		
        		//查看本地是否有更新,查询增量数据
        		List<Version> list=SettingsManager.getVersionsList(md5, 0);
        		if(list!=null&&list.size()>0){
        			if(update){// 
        				vlog.i("服务器=====>客户端完毕，开始客户端=====>服务器端同步数据");
        				vlog.i("查询增量数据条数:"+list.size()+" | "+JSON.toJSONString(list));
            			long tar=remote-local;
            			for (int i = 0; i < list.size(); i++) {
    						list.get(i).setVnum(list.get(i).getVnum()+tar);
    					}
            		}
        			vlog.i("查询增量数据条数:"+list.size()+" | "+JSON.toJSONString(list));
        			//提交增量数据
        			List<Integer> boollist= uploadVersionList(list);
        			
        			//提交成功，修改本地状态
        			for (int i = 0; i < list.size(); i++) {
    					if(boollist.get(i)==1){
    						SettingsManager.updateVersion(list.get(i).getId(),1);
    					}
    				}
        		}
    		}
    	}
    	LogContext vlog = LogManager.root().lctx("booksync", false);
        //同步最新图书信息
//        private void syncRencetBooks( String bn){
//        }
        public static final String host="http://192.168.0.25:8080/rest/version";
        
        /***
         * 根据书名获取最大数
         * bn 为书名md5后的值
         * */
        private long getRemoteMaxVnumByBookName(String bn){
//        	String url =host+"/queryMaxNumByBN?bn=b36dc17387e06a00842e1ef5bd225739";
        	String url =host+"/queryMaxNumByBN?bn="+bn;
//        	String json="{\"msg\":\"9\",\"res\":\"success\"}";
        	String json=HttpUtils.post(url);
        	vlog.i("根据书名"+bn+"获取当前服务器最大版本号url:"+url);
        	vlog.i("根据书名"+bn+"获取当前服务器最大版本号返回数据为:"+json);
        	if(json!=null&&!"".equals(json)){
        		Map<String,String> map=(Map<String,String>)JSON.parse(json);
        		String res=map.get("res");
        		if(res!=null&&!"".equals(res)&&"success".equalsIgnoreCase(res)){
        			Long r=Long.parseLong(map.get("msg"));
        			return r;
        		}else{
        			vlog.e("getRemoteMaxVnumByBookName:"+bn+" error"+map.get("msg"));
        		}
        	}
        	return 0;
        }
        
        //批量上传版本信息
        private List<Integer> uploadVersionList(List<Version> vl){
        	String data=JSON.toJSONString(vl);
        	String url=host+"/addlist";
//        	String url=host+"/addlist?json="+data;
        	//提交数据获取结果
        	vlog.i("批量上传版本信息url :"+url);
//        	String json="{\"msg\":\"[\\\"1\\\",\\\"1\\\"]\",\"res\":\"success\"}";
        	String json=HttpUtils.postJson(url,data);
        	vlog.i("批量上传版本信息 :"+data);
        	vlog.i("批量上传版本信息返回数据为:"+json);
        	
        	if(json!=null&&!"".equals(json)){
        		Map<String,String> map=(Map<String,String>)JSON.parse(json);
        		String res=map.get("res");
        		if(res!=null&&!"".equals(res)&&"success".equalsIgnoreCase(res)){
        			//解析结果
        			List<Integer> r=(List<Integer>)(JSON.parseArray(map.get("msg"), Integer.class));
        			return r;
        		}else{
        			vlog.e("uploadVersionList: error"+map.get("msg"));
        		}
        	}
        	return null;
        }
        
        /***
         * 根据图书md5值及版本号获取待更新数据
         * **/
        private List<Version> getVersionListByremote(String md5,long vnum){
//        	String url =host+"/querybookbyVnum?bn=b36dc17387e06a00842e1ef5bd225739&st=0";
//        	String json="[{\"bid\":1,\"createtime\":1463133755000,\"id\":104,\"marksname\":\"test2\",\"md5\":\"b36dc17387e06a00842e1ef5bd225739\",\"method\":\"ADD\",\"vnum\":2},{\"bid\":1,\"createtime\":1463133755000,\"id\":105,\"marksname\":\"test3\",\"md5\":\"b36dc17387e06a00842e1ef5bd225739\",\"method\":\"ADD\",\"vnum\":3},{\"bid\":1,\"createtime\":1463133755000,\"id\":106,\"marksname\":\"test4\",\"md5\":\"b36dc17387e06a00842e1ef5bd225739\",\"method\":\"ADD\",\"vnum\":4},{\"bid\":1,\"createtime\":1463133755000,\"id\":107,\"marksname\":\"test5\",\"md5\":\"b36dc17387e06a00842e1ef5bd225739\",\"method\":\"ADD\",\"vnum\":5},{\"bid\":1,\"createtime\":1463133755000,\"id\":108,\"marksname\":\"test6\",\"md5\":\"b36dc17387e06a00842e1ef5bd225739\",\"method\":\"ADD\",\"vnum\":6},{\"bid\":1,\"createtime\":1463133755000,\"id\":109,\"marksname\":\"test7\",\"md5\":\"b36dc17387e06a00842e1ef5bd225739\",\"method\":\"ADD\",\"vnum\":7},{\"bid\":1,\"createtime\":1463133755000,\"id\":110,\"marksname\":\"test8\",\"md5\":\"b36dc17387e06a00842e1ef5bd225739\",\"method\":\"ADD\",\"vnum\":8},{\"bid\":1,\"createtime\":1463133755000,\"id\":111,\"marksname\":\"test9\",\"md5\":\"b36dc17387e06a00842e1ef5bd225739\",\"method\":\"ADD\",\"vnum\":9}]";
        	String url =host+"/querybookbyVnum?bn="+md5+"&st="+vnum;
        	vlog.i("获取待更新数据url :"+url);
        	String  json=HttpUtils.post(url);
        	vlog.i("获取待更新数据返回结果:"+json);
        	
        	if(json!=null&&!"".equals(json)){
        		Map<String,String> map=(Map<String,String>)JSON.parse(json);
        		String res=map.get("res");
        		if(res!=null&&!"".equals(res)&&"success".equalsIgnoreCase(res)){
        			//解析结果
        			List<Version>  vl =paserJson2VersionList(map.get("msg"));
            		return vl;
        		}else{
        			vlog.e("uploadVersionList: error"+map.get("msg"));
        		}
        	}
        	return null;
        		
    	}
        
        /***
         * 解析json为list对象
         * **/
        private List<Version> paserJson2VersionList(String json){
        	List<Version>  vl=JSON.parseArray(json, Version.class);
        	return vl;
        }
        
    }
    /**
     * {@inheritDoc}
     *
     * @see org.emdev.ui.AbstractActivityController#afterCreate(android.app.Activity, boolean)
     */
    @Override
    public void afterCreate(final RecentActivity activity, final boolean recreated) {

        final LibSettings libSettings = LibSettings.current();
        LibSettings.applySettingsChanges(null, libSettings);
//获取最近图书列表
        final BookSettings recent = SettingsManager.getRecentBook();
        //同步信息
        if(recent!=null&&!"".equals(recent.fileName)){
//        	syncRencetBooks(recent.fileName);
        	SyncThread st=new SyncThread(recent.fileName);
        	st.start();
        }
        	
        if (!recreated) {
            init();
            recentLoaded = checkAutoLoad(libSettings, recent);
            if (recentLoaded) {
                return;
            }
            EBookDroidApp.checkInstalledFonts(getManagedComponent());
        }

        changeLibraryView(recent != null ? RecentActivity.VIEW_RECENT : RecentActivity.VIEW_LIBRARY);
    }

    protected boolean checkAutoLoad(final LibSettings libSettings, final BookSettings recent) {
        final boolean shouldLoad = AppSettings.current().loadRecent;
        final File file = (recent != null && recent.fileName != null) ? new File(recent.fileName) : null;
        final boolean found = file != null ? file.exists() && libSettings.allowedFileTypes.accept(file) : false;

        if (LCTX.isDebugEnabled()) {
            LCTX.d("Last book: " + (file != null ? file.getAbsolutePath() : "") + ", found: " + found
                    + ", should load: " + shouldLoad);
        }

        if (shouldLoad && found) {
            changeLibraryView(RecentActivity.VIEW_RECENT);
            showDocument(Uri.fromFile(file), null);
            return true;
        }
        return false;
    }

    protected void init() {
        final LibSettings libSettings = LibSettings.current();
        recentAdapter.setBooks(SettingsManager.getRecentBooks().values(), libSettings.allowedFileTypes);
        bookshelfAdapter.startScan();
    }

    /**
     * {@inheritDoc}
     *
     * @see org.emdev.ui.AbstractActivityController#onDestroy(boolean)
     */
    @Override
    public void onDestroy(final boolean finishing) {
        if (finishing) {
            if (BackupSettings.current().backupOnExit) {
                BackupManager.backup();
            }
            working.set(false);
            bookshelfAdapter.onDestroy();
            CacheManager.listeners.removeListener(this);
            SettingsManager.removeListener(this);
            MediaManager.listeners.removeListener(this);

            EBookDroidApp.onActivityClose(finishing);
        }
    }

    @ActionMethod(ids = R.id.recentmenu_cleanrecent)
    public void showClearRecentDialog(final ActionEx action) {
        final ActionDialogBuilder builder = new ActionDialogBuilder(getContext(), this);

        builder.setTitle(R.string.clear_recent_title);
        builder.setMultiChoiceItems(R.array.list_clear_recent_mode, R.id.actions_clearRecent);
        builder.setPositiveButton(R.id.actions_clearRecent);
        builder.setNegativeButton();
        builder.show();
    }

    @ActionMethod(ids = R.id.actions_clearRecent)
    public void doClearRecent(final ActionEx action) {
        if (action.isDialogItemSelected(ERASE_DISK_CACHE)) {
            CacheManager.clear();
            recentAdapter.notifyDataSetInvalidated();
            libraryAdapter.notifyDataSetInvalidated();
        }

        if (action.isDialogItemSelected(DELETE_BOOK_SETTINGS)) {
            SettingsManager.deleteAllBookSettings();
            recentAdapter.clearBooks();
            libraryAdapter.notifyDataSetInvalidated();
        } else {
            if (action.isDialogItemSelected(CLEAR_RECENT_LIST)) {
                SettingsManager.clearAllRecentBookSettings();
                recentAdapter.clearBooks();
                libraryAdapter.notifyDataSetInvalidated();
            }
            if (action.isDialogItemSelected(DELETE_BOOKMARKS)) {
                SettingsManager.deleteAllBookmarks();
            }
        }
    }

    @ActionMethod(ids = R.id.bookmenu_removefromrecent)
    public void removeBookFromRecents(final ActionEx action) {
        final BookNode book = action.getParameter(ActionMenuHelper.MENU_ITEM_SOURCE);
        if (book != null) {
            SettingsManager.removeBookFromRecents(book.path);
            recentAdapter.removeBook(book);
            libraryAdapter.notifyDataSetInvalidated();
        }
    }

    @ActionMethod(ids = R.id.bookmenu_cleardata)
    public void removeCachedBookFiles(final ActionEx action) {
        final BookNode book = action.getParameter(ActionMenuHelper.MENU_ITEM_SOURCE);
        if (book != null) {
            CacheManager.clear(book.path);
            recentAdapter.notifyDataSetInvalidated();
            libraryAdapter.notifyDataSetInvalidated();
        }
    }

    @ActionMethod(ids = R.id.bookmenu_deletesettings)
    public void removeBookSettings(final ActionEx action) {
        final BookNode book = action.getParameter(ActionMenuHelper.MENU_ITEM_SOURCE);
        if (book != null) {
            final BookSettings bs = SettingsManager.getBookSettings(book.path);
            if (bs != null) {
                SettingsManager.deleteBookSettings(bs);
                recentAdapter.removeBook(book);
                libraryAdapter.notifyDataSetInvalidated();
            }
        }
    }

    @ActionMethod(ids = R.id.recentmenu_searchBook)
    public void showSearchDlg(final ActionEx action) {
        final ActionDialogBuilder builder = new ActionDialogBuilder(getContext(), this);

        final EditText input = new EditText(getManagedComponent());
        input.setSingleLine();
        input.setText(LengthUtils.safeString(bookshelfAdapter.getSearchQuery()));
        input.selectAll();

        builder.setTitle(R.string.search_book_dlg_title);
        builder.setView(input);
        builder.setPositiveButton(android.R.string.search_go, R.id.actions_searchBook,
                new EditableValue("input", input));
        builder.setNegativeButton();
        builder.show();
    }

    @ActionMethod(ids = R.id.actions_searchBook)
    public void searchBook(final ActionEx action) {
        final Editable value = action.getParameter("input");
        final String searchQuery = value.toString();
        if (bookshelfAdapter.startSearch(searchQuery)) {
            if (LibSettings.current().useBookcase) {
                getManagedComponent().showBookshelf(BooksAdapter.SEARCH_INDEX);
            }
        }
    }

    @ActionMethod(ids = R.id.mainmenu_settings)
    public void showSettings(final ActionEx action) {
        SettingsUI.showAppSettings(getManagedComponent(), null);
    }

    @ActionMethod(ids = { R.id.bookmenu_copy, R.id.bookmenu_move })
    public void copyBook(final ActionEx action) {
        final BookNode book = action.getParameter("source");
        if (book == null) {
            return;
        }
        final boolean isCopy = action.id == R.id.bookmenu_copy;
        final int titleId = isCopy ? R.string.copy_book_to_dlg_title : R.string.move_book_to_dlg_title;
        final int id = isCopy ? R.id.actions_doCopyBook : R.id.actions_doMoveBook;
        getOrCreateAction(id).putValue("source", book);

        final FolderDlg dlg = new FolderDlg(this);
        dlg.show(new File(book.path), titleId, id);
    }

    @ActionMethod(ids = R.id.actions_doCopyBook)
    public void doCopyBook(final ActionEx action) {
        final File targetFolder = action.getParameter(FolderDlg.SELECTED_FOLDER);
        final BookNode book = action.getParameter("source");
        new CopyBookTask(this.getContext(), recentAdapter, targetFolder).execute(book);
    }

    @ActionMethod(ids = R.id.actions_doMoveBook)
    public void doMoveBook(final ActionEx action) {
        final File targetFolder = action.getParameter(FolderDlg.SELECTED_FOLDER);
        final BookNode book = action.getParameter("source");
        new MoveBookTask(this.getContext(), recentAdapter, targetFolder).execute(book);
    }

    @ActionMethod(ids = R.id.bookmenu_rename)
    public void renameBook(final ActionEx action) {
        final BookNode book = action.getParameter("source");
        if (book == null) {
            return;
        }

        final FileUtils.FilePath file = FileUtils.parseFilePath(book.path, CodecType.getAllExtensions());
        final EditText input = new EditText(getManagedComponent());
        input.setSingleLine();
        input.setText(file.name);
        input.selectAll();

        final ActionDialogBuilder builder = new ActionDialogBuilder(getContext(), this);
        builder.setTitle(R.string.book_rename_title);
        builder.setMessage(R.string.book_rename_msg);
        builder.setView(input);
        builder.setPositiveButton(R.id.actions_doRenameBook, new Constant("source", book), new Constant("file", file),
                new EditableValue("input", input));
        builder.setNegativeButton().show();
    }

    @ActionMethod(ids = R.id.actions_doRenameBook)
    public void doRenameBook(final ActionEx action) {
        final BookNode book = action.getParameter("source");
        final FileUtils.FilePath path = action.getParameter("file");
        final Editable value = action.getParameter("input");
        final String newName = value.toString();
        if (!CompareUtils.equals(path.name, newName)) {
            path.name = newName;
            new RenameBookTask(this.getContext(), recentAdapter, path).execute(book);
        }
    }

    @ActionMethod(ids = R.id.bookmenu_delete)
    public void deleteBook(final ActionEx action) {
        final BookNode book = action.getParameter("source");
        if (book == null) {
            return;
        }

        final ActionDialogBuilder builder = new ActionDialogBuilder(getContext(), this);
        builder.setTitle(R.string.book_delete_title);
        builder.setMessage(R.string.book_delete_msg);
        builder.setPositiveButton(R.id.actions_doDeleteBook, new Constant("source", book));
        builder.setNegativeButton().show();
    }

    @ActionMethod(ids = R.id.actions_doDeleteBook)
    public void doDeleteBook(final ActionEx action) {
        final BookNode book = action.getParameter("source");
        if (book == null) {
            return;
        }

        final File f = new File(book.path);
        if (f.delete()) {
            CacheManager.clear(book.path);
            final LibSettings libSettings = LibSettings.current();
            recentAdapter.setBooks(SettingsManager.getRecentBooks().values(), libSettings.allowedFileTypes);
        }
    }

    /**
     * {@inheritDoc}
     *
     * @see org.ebookdroid.ui.library.IBrowserActivity#setCurrentDir(java.io.File)
     */
    @Override
    public void setCurrentDir(final File newDir) {
    }

    @ActionMethod(ids = { R.id.actions_goToBookmark })
    public void openBook(final ActionEx action) {
        final BookNode book = action.getParameter(ActionMenuHelper.MENU_ITEM_SOURCE);
        final File file = new File(book.path);
        if (!file.isDirectory()) {
            final Bookmark b = action.getParameter("bookmark");
            showDocument(Uri.fromFile(file), b);
        }
    }

    @ActionMethod(ids = R.id.bookmenu_settings)
    public void openBookSettings(final ActionEx action) {
        final BookNode book = action.getParameter(ActionMenuHelper.MENU_ITEM_SOURCE);
        SettingsManager.create(0, book.path, false, null);
        SettingsUI.showBookSettings(getManagedComponent(), book.path);
    }

    @ActionMethod(ids = R.id.bookmenu_openbookshelf)
    public void openBookShelf(final ActionEx action) {
        final BookNode book = action.getParameter(ActionMenuHelper.MENU_ITEM_SOURCE);
        final BookShelfAdapter bookShelf = getBookShelf(book);
        if (bookShelf != null) {
            final int pos = bookshelfAdapter.getShelfPosition(bookShelf);
            getManagedComponent().showBookshelf(pos);
        }
    }

    @ActionMethod(ids = R.id.bookmenu_openbookfolder)
    public void openBookFolder(final ActionEx action) {
        final BookNode book = action.getParameter(ActionMenuHelper.MENU_ITEM_SOURCE);
        final Intent myIntent = new Intent(getManagedComponent(), BrowserActivity.class);
        myIntent.setData(Uri.fromFile(new File(book.path).getParentFile().getAbsoluteFile()));
        getManagedComponent().startActivity(myIntent);
    }

    /**
     * {@inheritDoc}
     *
     * @see org.ebookdroid.ui.library.IBrowserActivity#showDocument(android.net.Uri,
     *      org.ebookdroid.common.settings.books.Bookmark)
     */
    @Override
    public void showDocument(final Uri uri, final Bookmark b) {
        final Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        intent.setClass(getManagedComponent(), ViewerActivity.class);
        if (b != null) {
            intent.putExtra("pageIndex", "" + b.page.viewIndex);
            intent.putExtra("offsetX", "" + b.offsetX);
            intent.putExtra("offsetY", "" + b.offsetY);
        }
        getManagedComponent().startActivity(intent);
    }

    @ActionMethod(ids = R.id.ShelfCaption)
    public void showSelectShelfDlg(final ActionEx action) {
        final List<String> names = bookshelfAdapter.getListNames();

        if (LengthUtils.isNotEmpty(names)) {
            final ActionDialogBuilder builder = new ActionDialogBuilder(getContext(), this);
            builder.setTitle(R.string.bookcase_shelves);
            builder.setItems(names.toArray(new String[names.size()]), this.getOrCreateAction(R.id.actions_selectShelf));
            builder.show();
        }
    }

    @ActionMethod(ids = R.id.actions_selectShelf)
    public void selectShelf(final ActionEx action) {
        final Integer item = action.getParameter(IActionController.DIALOG_ITEM_PROPERTY);
        getManagedComponent().showBookshelf(item);
    }

    @ActionMethod(ids = R.id.ShelfLeftButton)
    public void selectPrevShelf(final ActionEx action) {
        getManagedComponent().showPrevBookshelf();
    }

    @ActionMethod(ids = R.id.ShelfRightButton)
    public void selectNextShelf(final ActionEx action) {
        getManagedComponent().showNextBookshelf();
    }

    @ActionMethod(ids = { R.id.recent_showlibrary, R.id.recent_showrecent })
    public void goLibrary(final ActionEx action) {
        if (!LibSettings.current().useBookcase) {
            final int viewMode = getManagedComponent().getViewMode();
            if (viewMode == RecentActivity.VIEW_RECENT) {
                changeLibraryView(RecentActivity.VIEW_LIBRARY);
            } else if (viewMode == RecentActivity.VIEW_LIBRARY) {
                changeLibraryView(RecentActivity.VIEW_RECENT);
            }
        }
    }

    @ActionMethod(ids = { R.id.recent_showbrowser, R.id.recent_storage_all, R.id.recent_storage_external,
            R.id.actions_storage })
    public void goFileBrowser(final ActionEx action) {
        final Intent myIntent = new Intent(getManagedComponent(), BrowserActivity.class);
        final String path = action.getParameter("path");
        if (path != null) {
            myIntent.setData(Uri.fromFile(new File(path)));
        }
        getManagedComponent().startActivity(myIntent);
    }

//    @ActionMethod(ids = R.id.mainmenu_opds)
//    public void goOPDSBrowser(final ActionEx action) {
//        final Intent myIntent = new Intent(getManagedComponent(), OPDSActivity.class);
//        getManagedComponent().startActivity(myIntent);
//    }

    @ActionMethod(ids = R.id.recentmenu_backupsettings)
    public void backupSettings(final ActionEx action) {
        new BackupDlg(m_managedComponent).show();
    }

    @ActionMethod(ids = R.id.mainmenu_close)
    public void close(final ActionEx action) {
        getManagedComponent().finish();
    }

    @Override
    public void showProgress(final boolean show) {
        final RecentActivity activity = getManagedComponent();
        final ProgressBar progress = (ProgressBar) activity.findViewById(R.id.recentprogress);
        if (progress != null) {
            if (show) {
                progress.setVisibility(View.VISIBLE);
            } else {
                progress.setVisibility(View.GONE);
            }
        } else {
            activity.runOnUiThread(new Runnable() {

                @Override
                public void run() {
                    try {
                        activity.setProgressBarIndeterminateVisibility(show);
                        activity.getWindow().setFeatureInt(Window.FEATURE_INDETERMINATE_PROGRESS, !show ? 10000 : 1);
                    } catch (final Throwable e) {
                    }
                }
            });
        }
    }

    /**
     * {@inheritDoc}
     *
     * @see org.ebookdroid.common.cache.CacheManager.ICacheListener#onThumbnailChanged(org.ebookdroid.common.cache.ThumbnailFile)
     */
    @Override
    public void onThumbnailChanged(final ThumbnailFile tf) {
        getManagedComponent().runOnUiThread(new Runnable() {

            @Override
            public void run() {
                final BookNode node = recentAdapter.getNode(tf.book);
                if (node != null) {
                    recentAdapter.notifyDataSetInvalidated();
                    final BookShelfAdapter bookShelf = getBookShelf(node);
                    if (bookShelf != null) {
                        bookShelf.notifyDataSetInvalidated();
                    }
                }
            }
        });
    }

    /**
     * {@inheritDoc}
     *
     * @see org.ebookdroid.ui.library.IBrowserActivity#loadThumbnail(java.lang.String, android.widget.ImageView, int)
     */
    @Override
    public void loadThumbnail(final String path, final ImageView imageView, final int defaultResID) {
        final ThumbnailFile newTF = CacheManager.getThumbnailFile(path);
        imageView.setTag(newTF);

        final Bitmap defImage = def.getImage();
        newTF.loadImageAsync(defImage, new ThumbnailFile.ImageLoadingListener() {

            @Override
            public void onImageLoaded(final Bitmap image) {
                if (image != null && imageView.getTag() == newTF) {
                    imageView.setImageBitmap(image);
                    imageView.postInvalidate();
                }
            }
        });
    }

    /**
     * {@inheritDoc}
     *
     * @see org.ebookdroid.common.settings.listeners.ILibSettingsChangeListener#onLibSettingsChanged(org.ebookdroid.common.settings.LibSettings,
     *      org.ebookdroid.common.settings.LibSettings, org.ebookdroid.common.settings.LibSettings.Diff)
     */
    @Override
    public void onLibSettingsChanged(final LibSettings oldSettings, final LibSettings newSettings,
            final LibSettings.Diff diff) {
        try {//load books msg
            final FileExtensionFilter filter = newSettings.allowedFileTypes;
            if (diff.isUseBookcaseChanged()) {
                recentAdapter.setBooks(SettingsManager.getRecentBooks().values(), filter);
                if (newSettings.useBookcase) {
                    getManagedComponent().showBookcase(bookshelfAdapter, recentAdapter);
                } else {
                    getManagedComponent().showLibrary(libraryAdapter, recentAdapter);
                }
                return;
            }

            if (diff.isAutoScanDirsChanged()) {
                bookshelfAdapter.startScan();
                return;
            }
            if (diff.isAllowedFileTypesChanged()) {
                recentAdapter.setBooks(SettingsManager.getRecentBooks().values(), filter);
                bookshelfAdapter.startScan();
            }
            if (diff.isAutoScanRemovableMediaChanged()) {
                final Collection<String> media = MediaManager.getReadableMedia();
                if (LengthUtils.isNotEmpty(media)) {
                    if (newSettings.autoScanRemovableMedia) {
                        bookshelfAdapter.startScan(media);
                    } else {
                        bookshelfAdapter.removeAll(media);
                    }
                }
            }
        } finally {
            IUIManager.instance.invalidateOptionsMenu(getManagedComponent());
        }
    }

    /**
     * {@inheritDoc}
     *
     * @see org.ebookdroid.common.settings.listeners.IRecentBooksChangedListener#onRecentBooksChanged()
     */
    @Override
    public void onRecentBooksChanged() {
        getManagedComponent().runOnUiThread(new Runnable() {

            @Override
            public void run() {
                final FileExtensionFilter filter = LibSettings.current().allowedFileTypes;
                Collection<BookSettings> rencetbooks=SettingsManager.getRecentBooks().values();
                recentAdapter.setBooks(rencetbooks, filter);
             
            }
        });
    }
   
    
    public void changeLibraryView(final int view) {
        if (!LibSettings.current().useBookcase) {
            getManagedComponent().changeLibraryView(view);
        }
    }

    public BookShelfAdapter getBookShelf(final BookNode node) {
        final String parent = new File(node.path).getParentFile().getAbsolutePath();
        return bookshelfAdapter.getShelf(parent);
    }

    public BookShelfAdapter getBookShelf(final int index) {
        return bookshelfAdapter.getList(index);
    }

    /**
     * {@inheritDoc}
     *
     * @see org.emdev.common.filesystem.MediaManager.Listener#onMediaStateChanged(java.lang.String,
     *      org.emdev.common.filesystem.MediaState, org.emdev.common.filesystem.MediaState)
     */
    @Override
    public void onMediaStateChanged(final String path, final MediaState oldState, final MediaState newState) {
        if (newState.readable) {
            if (oldState == null || !oldState.readable) {
                if (LibSettings.current().autoScanRemovableMedia) {
                    bookshelfAdapter.startScan(path);
                }
                IUIManager.instance.invalidateOptionsMenu(getManagedComponent());
            }
            return;
        }

        if (oldState != null && oldState.readable) {
            bookshelfAdapter.removeAll(path);
            IUIManager.instance.invalidateOptionsMenu(getManagedComponent());
        }
    }
}
