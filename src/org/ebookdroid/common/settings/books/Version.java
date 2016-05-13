package org.ebookdroid.common.settings.books;
/**
 * Created by cc on 15-7-20.
 */
public class Version {

	    private Long id;//

	    private Long vnum;//版本号，用来比对

	    private String md5;//目前是 书名+filesize md5下

	    private String method;//可以改成枚举 目前是ADD DEL方法

	    private String marksname;//标签名
	    
	    private String bookname;//书名
	    
	    private int syntag;//同步标记

        //暂时使用Long 代替
	    private long  createtime;
	    private long modifytime;
	    
	    public String getMarksname() {
			return marksname;
		}

		public void setMarksname(String marksname) {
			this.marksname = marksname;
		}

		public long getCreatetime() {
			return createtime;
		}

		public void setCreatetime(long createtime) {
			this.createtime = createtime;
		}

		public long getModifytime() {
			return modifytime;
		}

		public int getSyntag() {
			return syntag;
		}

		public void setSyntag(int syntag) {
			this.syntag = syntag;
		}

		public void setModifytime(long modifytime) {
			this.modifytime = modifytime;
		}

		public String getBookname() {
	        return bookname;
	    }

	    public void setBookname(String bookname) {
	        this.bookname = bookname;
	    }

	    public String getMd5() {
	        return md5;
	    }

	    public void setMd5(String md5) {
	        this.md5 = md5;
	    }

	    public Long getId() {
	        return id;
	    }

	    public void setId(Long id) {
	        this.id = id;
	    }


	    public Long getVnum() {
	        return vnum;
	    }

	    public void setVnum(Long vnum) {
	        this.vnum = vnum;
	    }

	    public String getMethod() {
	        return method;
	    }

	    public void setMethod(String method) {
	        this.method = method;
	    }

	    public Version() {
	    }

		public Version(Long id, Long vnum, String md5, String method,
				String bookname, long createtime, long modifytime ,int tag) {
			super();
			this.id = id;
			this.vnum = vnum;
			this.md5 = md5;
			this.method = method;
			this.bookname = bookname;
			this.createtime = createtime;
			this.modifytime = modifytime;
			this.syntag=tag;
		}
		public Version( String md5, String method,
				String bookname, long createtime ) {
			super();
			this.md5 = md5;
			this.method = method;
			this.bookname = bookname;
			this.createtime = createtime;
		}
	}

