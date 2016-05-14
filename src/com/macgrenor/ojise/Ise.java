package com.macgrenor.ojise;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.sql.Savepoint;
import java.util.HashMap;

import org.macgrenor.json.JSONException;
import org.macgrenor.json.JSONObject;

final public class Ise {
	//batch_id, local_id, `type`, params, priority, filename,	`data`, size, completed
	private int mLocalId;
	private String mOriginalId;
	private String mType;
	private int mPriority;
	private String mFilePath;
	private long mSize;
	private long mUploadedSize;
	
	private JSONObject mParams;
	private boolean mCompleted;
	private boolean mMerged;
	
	private JSONObject _jsonObject;
	private RandomAccessFile mFileObj;
	
	private Ojise mOjise;	
	private JSONObject mSaveResult;
	
	
	public Ise(String OriginalId, int Priority,
			String FilePath, HashMap<String, Object> Params) {
		super();
		this.mOriginalId = OriginalId;
		this.mType = Ojise.TYPE_FILE;
		this.mPriority = Priority;
		this.mFilePath = FilePath;
		this.mSize = (new File(FilePath)).length();
		this.mParams = Ojise._toJSON(Params);		
	}
	
	protected Ise(JSONObject data) {
		super();
		try {
			this.mLocalId = data.getInt("LocalId");
			this.mOriginalId = data.getString("OriginalId");
			this.mType = data.getString("Type");
			this.mPriority = data.getInt("Priority");
			this.mFilePath = data.getString("FilePath");
			this.mSize = data.getLong("Size");
			this.mUploadedSize = data.getLong("UploadedSize");
			this.mParams = data.getJSONObject("Params");
			
			this.mCompleted = data.getBoolean("Completed");
			this.mMerged = data.getBoolean("Merged");
		} catch (JSONException e) {
			e.printStackTrace();
		}
	}
	
	protected void setOjise(Ojise ojise) {
		this.mOjise = ojise;
	}
	
	protected synchronized byte[] read(long offset, int length) throws IOException {
		byte[] data = new byte[length];
		if (mFileObj == null) mFileObj = new RandomAccessFile(mFilePath, "r");
		
		try {
			mFileObj.seek(offset);
		} catch (IOException e) {
			
			e.printStackTrace();
			mFileObj = new RandomAccessFile(mFilePath, "r");
			mFileObj.seek(offset);
		}
		
		mFileObj.readFully(data);
		
		return data;
	}
	
	protected void closeFile() {
		try {
			if (mFileObj != null) {
				mFileObj.close();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	protected synchronized JSONObject toJSON(boolean refresh) {
		try {
			if (refresh || _jsonObject == null) {
				_jsonObject = new JSONObject();
				_jsonObject.put("LocalId", this.mLocalId);
				_jsonObject.put("OriginalId", this.mOriginalId);
				_jsonObject.put("Type", this.mType);
				_jsonObject.put("Priority", this.mPriority);
				_jsonObject.put("FilePath", this.mFilePath);
				_jsonObject.put("Size", this.mSize);
				_jsonObject.put("UploadedSize", this.mUploadedSize);
				_jsonObject.put("Params", this.mParams);
				_jsonObject.put("Completed", this.mCompleted);
				_jsonObject.put("Merged", this.mMerged);
			}
		} catch (JSONException e) {
			
			e.printStackTrace();
		}
		return _jsonObject;
	}
	
	protected void clearOnSend() {
		this.mParams = null;
	}
	
	protected void updateStatus(JSONObject data) {
		try {
			setMerged(data.getInt("merged") == 1);
			setCompleted(data.getInt("completed") == 1);
		} catch (JSONException e) {
			
			e.printStackTrace();
		}
	}
	
	public int getLocalId() {
		return mLocalId;
	}
	protected void setLocalId(int LocalId) {
		this.mLocalId = LocalId;
	}
	public String getOriginalId() {
		return mOriginalId;
	}
	public String getType() {
		return mType;
	}
	public int getIsePriority() {
		return mPriority;
	}
	public String getFilePath() {
		return mFilePath;
	}
	public String getFileName() {
		return (new File(mFilePath)).getName();
	}
	public long getSize() {
		return mSize;
	}
	public long getUploadedSize() {
		return mCompleted ? mSize : mUploadedSize;
	}
	protected void resetUploadedSize() {
		mUploadedSize = 0;
	}
	protected synchronized void addUploadSize(long size) {
		mUploadedSize += size;
		try {
			_jsonObject.put("UploadedSize", this.mUploadedSize);
		} catch (JSONException e) {
			
			e.printStackTrace();
		}
		mOjise.requestSaveState();
	}
	
	public JSONObject getParams() {
		return mParams;
	}
	public boolean isCompleted() {
		return mCompleted;
	}
	protected synchronized void setCompleted(boolean Completed) {
		if (this.mCompleted && !Completed) return;
		this.mCompleted = Completed;
		try {
			_jsonObject.put("Completed", this.mCompleted);
		} catch (JSONException e) {
			e.printStackTrace();
		}
		mOjise.requestSaveState();
		System.gc();
		
		if (Completed && mFileObj != null) {
			try {
				mFileObj.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		
		if (!mOjise.isMerged()) {		
			tryMerge();
		}
		
		mOjise.executeIse();
	}
		
	protected synchronized void tryMerge() {
		if (isMerged()) return;
		
		try {
			JSONObject ojiseData = new JSONObject();
			ojiseData.put("batch_id", mOjise.getBatchId());
			ojiseData.put("local_id", mLocalId);
			
			JSONObject ret = mOjise.getOjiseServerProxy()._mergeUploadItem(ojiseData);
			
			if (ret != null) {
				setMerged(ret.getInt("merged") == 1);
				if (isMerged()) {
					setCompleted(true);
					mSaveResult = ret.getJSONObject("save_result");
					mOjise.notifyItemMerged(this);
				}
			}
			
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
	public boolean isMerged() {
		return mMerged;
	}
	protected synchronized void setMerged(boolean Merged) {
		if (this.mMerged && !Merged) return;
		this.mMerged = Merged;
		try {
			_jsonObject.put("Merged", this.mMerged);
		} catch (JSONException e) {
			e.printStackTrace();
		}
	}
	
	public short getStatus() {
		return (short)Math.floor(100 * this.getUploadedSize() / this.getSize());
	}

	public JSONObject getSaveResult() {
		return mSaveResult;
	}
}
