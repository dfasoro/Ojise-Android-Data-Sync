package com.macgrenor.ojise;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;

import org.macgrenor.json.JSONArray;
import org.macgrenor.json.JSONException;
import org.macgrenor.json.JSONObject;

public abstract class Ojise {
	private long mBatchId;
	private int mLocalId;
	private String mOjiseKey;
	private int mExpiry;
	private int mChunksCount;
	private long mChunksMinSize;
	private int mUploadBitsSize;
	private int mUploadBitsMaxSize;
	private boolean mPaused = true;
	private boolean mCompleted;
	private boolean mMerged;
	private int mTimeOut;
	private String mURL;

	private JSONObject mParams;
	private JSONObject mAccessDetails;

	private HashMap<Integer, Ise> mItems = new HashMap<Integer, Ise>(20);
	private HashMap<Integer, HashMap<Integer, HashMap<Long, Omose>>> mOmose;

	protected JSONObject _jsonObject;
	private OjiseThread mOjiseThread;
	private OjiseListener mListener;

	private boolean _mSaveRequested = false;
	private JSONObject mSaveResult;
	private OjiseServerProxy mOjiseServerProxy;

	public static final String TYPE_FILE = "file";
	public static final String TYPE_DATA = "data";

	private Ojise() {
		
	}

	protected Ojise(String URL, HashMap<String, Object> AccessDetails, HashMap<String, Object> Params, 
			int ChunksCount, long ChunksMinSize, int UploadBitsSize, int UploadBitsMaxSize, 			
			int TimeOut, int Expiry) {
		this();

		this.mExpiry = Expiry;
		this.mChunksCount = ChunksCount;
		this.mChunksMinSize = ChunksMinSize;
		this.mParams = Ojise._toJSON(Params);
		this.mAccessDetails = Ojise._toJSON(AccessDetails);
		this.mTimeOut = TimeOut;
		this.mUploadBitsSize = UploadBitsSize;
		this.mUploadBitsMaxSize = UploadBitsMaxSize;
		this.mURL = URL;

		toJSON(false);
	}
	
	abstract protected int getNextOjiseId();
	abstract protected void addRunningOjise(Ojise ojise);
	
	public void start() {
		if (mLocalId == 0) {
			mLocalId = getNextOjiseId();			
			toJSON(true);
			addRunningOjise(this);
		}
		setPaused(false);
	}
	public void stop() {
		setPaused(true);
	}

	public void addUploadItem(Ise item) {
		if (mBatchId > 0) return; //throw error, no more addition after batch has been created

		int i = this.mItems.size() + 1; //this ordering is mandated for the code in the run method
		item.setLocalId(i); //Add AutoIncrement number
		item.setOjise(this);
		this.mItems.put(item.getLocalId(), item);	

		try {
			_jsonObject.getJSONArray("Items").put(item.toJSON(false));
		} catch (JSONException e) {
			e.printStackTrace();
		}
	}

	protected Ojise(JSONObject data) {
		this();

		try {
			this.mBatchId = data.getLong("BatchId");
			this.mLocalId = data.getInt("LocalId");
			this.mOjiseKey = data.getString("OjiseKey");
			this.mCompleted = data.getBoolean("Completed");
			this.mMerged = data.getBoolean("Merged");
			this.mExpiry = data.getInt("Expiry");
			this.mChunksCount = data.getInt("ChunksCount");
			this.mChunksMinSize = data.getLong("ChunksMinSize");
			this.mParams = data.getJSONObject("Params");
			this.mAccessDetails = data.getJSONObject("AccessDetails");
			this.mTimeOut = data.getInt("TimeOut");
			this.mUploadBitsSize = data.getInt("UploadBitsSize");
			this.mUploadBitsMaxSize = data.getInt("UploadBitsMaxSize");
			this.mURL = data.getString("URL");

			JSONArray items = data.getJSONArray("Items");

			for (int i = 0; i < items.length(); i++) {
				Ise item = new Ise(items.getJSONObject(i));
				item.setOjise(this);				
				this.mItems.put(item.getLocalId(), item);
			}

			toJSON(false);

			setPaused(data.getBoolean("Paused"));

		} catch (JSONException e) {
			e.printStackTrace();
		}	
	}

	private synchronized JSONObject toJSON(boolean refresh) {
		try {
			if (refresh || _jsonObject == null) {
				_jsonObject = new JSONObject();

				_jsonObject.put("BatchId", this.mBatchId);
				_jsonObject.put("LocalId", this.mLocalId);
				_jsonObject.put("OjiseKey", this.mOjiseKey);
				_jsonObject.put("Paused", this.mPaused);
				_jsonObject.put("Merged", this.mMerged);
				_jsonObject.put("Completed", this.mCompleted);
				_jsonObject.put("Expiry", this.mExpiry);
				_jsonObject.put("ChunksCount", this.mChunksCount);
				_jsonObject.put("ChunksMinSize", this.mChunksMinSize);
				_jsonObject.put("Params", this.mParams);
				_jsonObject.put("AccessDetails", this.mAccessDetails);
				_jsonObject.put("TimeOut", this.mTimeOut);
				_jsonObject.put("UploadBitsSize",this.mUploadBitsSize);
				_jsonObject.put("UploadBitsMaxSize",this.mUploadBitsMaxSize);
				_jsonObject.put("URL", this.mURL);

				JSONArray items = new JSONArray();
				Set<Integer> keys = this.mItems.keySet();
				for (Integer i : keys) {
					items.put(this.mItems.get(i).toJSON(refresh));
				}

				_jsonObject.put("Items", items);
			}
		} catch (JSONException e) {

			e.printStackTrace();
		}
		return _jsonObject;
	}

	protected void clearOnSend() {
		this.mAccessDetails = null;
		this.mParams = null;
	}

	protected void requestSaveState() {		
		_mSaveRequested = true;
	}

	abstract protected void _saveState();
	private void saveState() {
		if (mLocalId == 0) return;
		_mSaveRequested = false;
		
		_saveState();
	}
	
	

	protected void notifyThreadCompleted(final Omose o) {
		if (mListener != null) {
			(new Thread(new Runnable() {
				@Override
				public void run() {
					mListener.onThreadCompleted(o);
				}
			})).start();
		}
		this.executeNextInThreadFamily(o.getThreadNumber(), o);
	}
	
	protected void notifyItemMerged(final Ise item) {
		if (mListener != null) {
			(new Thread(new Runnable() {
				@Override
				public void run() {
					mListener.onItemCompleted(item);
				}
			})).start();
		}
		tryMergeAll();
	}

	protected void notifyBatchComplete() {
		if (mListener != null) {
			(new Thread(new Runnable() {
				@Override
				public void run() {
					mListener.onBatchComplete(Ojise.this);
				}
			})).start();
		}
		this.stop();
	}

	private synchronized void tryMergeAll() {
		//close the batch
		if (this.isMerged()) return;

		boolean mergedAll = false;
		if (this.isCompleted()) mergedAll = true; 
		else {
			Set<Integer> keys_item = this.mItems.keySet();
			mergedAll = true; 

			for (Integer i : keys_item) {
				if (!mItems.get(i).isMerged()) {
					mergedAll = false;
					break;
				}			
			}	
		}

		if (mergedAll) {
			try {
				JSONObject ojiseData = new JSONObject();
				ojiseData.put("ojise_key", mOjiseKey);
				ojiseData.put("batch_id", mBatchId);

				JSONObject ret = Ojise.this.getOjiseServerProxy()._mergeBatch(ojiseData);
				if (ret != null) {
					setMerged(ret.getInt("merged") == 1);
					if (isMerged()) {
						setCompleted(true);
						mSaveResult = ret.getJSONObject("save_result");
						notifyBatchComplete();
					}
				}
			} catch (JSONException e) {
				e.printStackTrace();
			}
		}
	}

	private synchronized void tryCompleteAll() {
		if (this.isCompleted() || this.isMerged()) return;

		Set<Integer> keys_item = this.mItems.keySet();
		for (Integer i : keys_item) {
			boolean completed = false;
			if (mItems.get(i).isCompleted() && !mItems.get(i).isMerged()) completed = true;
			else if (!mItems.get(i).isCompleted()) {
				HashMap<Integer, HashMap<Long, Omose>> priorityGroup = mOmose.get(mItems.get(i).getIsePriority());
				Iterator<Integer> keys2 = priorityGroup.keySet().iterator();
				completed = true;

				while (keys2.hasNext()) {
					int thread_number = keys2.next();
					HashMap<Long, Omose> threadFamily = priorityGroup.get(thread_number);				
					Iterator<Long> keys3 = threadFamily.keySet().iterator();

					while (keys3.hasNext()) {
						long thread_id = keys3.next();
						Omose o = threadFamily.get(thread_id);

						if (o.getLocalId() == mItems.get(i).getLocalId() && !o.isCompleted()) {
							completed = false;
							break;
						}
					}

					if (completed == false) break;
				}
			}
			if (completed) mItems.get(i).tryMerge();
		}	
	}

	protected void setOmose(JSONArray Omose) {
		this.mOmose = new HashMap<Integer, HashMap<Integer, HashMap<Long, Omose>>>(5);

		Set<Integer> keys = this.mItems.keySet();
		for (Integer i : keys) {
			this.mItems.get(i).resetUploadedSize();
		}

		try {
			//id, thread_number, start_pos, current_size, size, local_id, priority
			for (int i = 0; i < Omose.length(); i++) {
				JSONObject om = Omose.getJSONObject(i);
				Omose o = new Omose(om.getLong("id"), om.getInt("thread_number"), 
						om.getLong("start_pos"), om.getLong("current_size"), om.getLong("size"), 
						om.getInt("local_id"), om.getInt("priority"), 0, this);

				if (!this.mOmose.containsKey(o.getOmosePriority())) {
					this.mOmose.put(o.getOmosePriority(), new HashMap<Integer, HashMap<Long, Omose>>(5));
				}
				if (!this.mOmose.get(o.getOmosePriority()).containsKey(o.getThreadNumber())) {
					this.mOmose.get(o.getOmosePriority()).put(o.getThreadNumber(), new HashMap<Long, Omose>(3));
				}

				this.mOmose.get(o.getOmosePriority()).get(o.getThreadNumber()).put(o.getId(), o);
			}
		} catch (JSONException e) {

			e.printStackTrace();
		}

	}

	protected synchronized void executeIse() {
		mActivePriority = getActivePriorityList();
		if (mActivePriority == -1) return;

		HashMap<Integer, HashMap<Long, Omose>> priorityGroup = mOmose.get(mActivePriority);
		Iterator<Integer> keys2 = priorityGroup.keySet().iterator();

		while (keys2.hasNext()) {
			int thread_number = keys2.next();
			executeNextInThreadFamily(thread_number, null);
		}		
	}

	private synchronized void executeNextInThreadFamily(int thread_number, Omose fin) {
		if (mActivePriority == -1) return;
		if (isPaused() || (mOjiseThread != null && mOjiseThread.isInterrupted())) return;

		HashMap<Long, Omose> threadFamily = mOmose.get(mActivePriority).get(thread_number);

		if (threadFamily == null) return;

		Iterator<Long> keys3 = threadFamily.keySet().iterator();		
		while (keys3.hasNext()) {
			long thread_id = keys3.next();
			Omose o = threadFamily.get(thread_id);
			if (!o.isCompleted()) {
				if (o.isAlive()) return; //A thread is running in the family.
				if (!o.isAlive()) {
					try {
						if (fin != null) o.setUploadBitsSize(fin.getUploadBitsSize());
						o.start();
					} catch (Exception e) {
						e.printStackTrace();
					}
					break;
				}
			}
		}
	}

	private int mActivePriority;
	private synchronized int getActivePriorityList() {
		if (mOmose != null) {
			Iterator<Integer> keys = mOmose.keySet().iterator();
	
			while (keys.hasNext()) {
				int priority = keys.next();
	
				Set<Integer> keys_item = this.mItems.keySet();
				for (Integer i : keys_item) {
					if (this.mItems.get(i).getIsePriority() == priority && !this.mItems.get(i).isCompleted()) {
						return priority;
					}
				}			
			}
		}
		return -1;
	}
	private synchronized void stopAllOmose() {
		if (mOmose == null) return;
		Iterator<Integer> keys = mOmose.keySet().iterator();

		while (keys.hasNext()) {
			int priority = keys.next();

			HashMap<Integer, HashMap<Long, Omose>> priorityGroup = mOmose.get(priority);
			Iterator<Integer> keys2 = priorityGroup.keySet().iterator();

			while (keys2.hasNext()) {
				int thread_number = keys2.next();
				HashMap<Long, Omose> threadFamily = priorityGroup.get(thread_number);				
				Iterator<Long> keys3 = threadFamily.keySet().iterator();

				while (keys3.hasNext()) {
					long thread_id = keys3.next();
					Omose o = threadFamily.get(thread_id);

					if (o.isAlive()) o.interrupt();
				}
			}
		}
	}

	public long getBatchId() {
		return mBatchId;
	}
	public int getExpiry() {
		return mExpiry;
	}
	public int getChunksCount() {
		return mChunksCount;
	}
	public long getChunksMinSize() {
		return mChunksMinSize;
	}

	public int getLocalId() {
		return mLocalId;
	}

	public boolean isPaused() {
		return mPaused;
	}

	private synchronized void setPaused(boolean Paused) {
		if (this.mPaused == Paused) return;

		this.mPaused = Paused;
		try {
			_jsonObject.put("Paused", this.mPaused);
		} catch (JSONException e) {

			e.printStackTrace();
		}
		saveState();

		if (mPaused) {
			//run through all omose and set their paused state and even delete them sef.
			stopAllOmose();
			for (int i = 0; i < mItems.size(); i++) mItems.get(i).closeFile();
			this.mOmose = null;
			mOjiseThread.interrupt();

			System.gc();
		}
		else {
			if (this.isMerged()) return;
			mOjiseServerProxy = new OjiseServerProxy(this);
			mOjiseThread = new OjiseThread();
			mOjiseThread.start();
		}
	}

	public JSONObject getParams() {
		return mParams;
	}
	public String getOjiseKey() {
		return mOjiseKey;
	}
	protected HashMap<Integer, Ise> getItems() {
		return mItems;
	}

	public int getTimeOut() {
		return mTimeOut;
	}

	public void setTimeOut(int TimeOut) {
		this.mTimeOut = TimeOut;
	}

	public String getURL() {
		return mURL;
	}

	public int getUploadBitsSize() {
		return mUploadBitsSize;
	}

	public void setUploadBitsSize(int UploadBitsSize) {
		this.mUploadBitsSize = UploadBitsSize;
	}
	public int getUploadBitsMaxSize() {
		return mUploadBitsMaxSize;
	}

	public void setUploadBitsMaxSize(int UploadBitsMaxSize) {
		this.mUploadBitsMaxSize = UploadBitsMaxSize;
	}

	public Ise getIse(int LocalId) {
		return mItems.get(LocalId);
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

	public OjiseServerProxy getOjiseServerProxy() {
		return mOjiseServerProxy;
	}

	public JSONObject getSaveResult() {
		return mSaveResult;
	}

	public void setListener(OjiseListener Listener) {
		this.mListener = Listener;
	}

	public OjiseListener getListener() {
		return mListener;
	}

	@SuppressWarnings("unchecked")
	protected static JSONObject _toJSON(HashMap<String, Object> obj) {

		if (obj == null) return null;

		JSONObject ret = new JSONObject();
		Set<String> keys = obj.keySet();
		try {
			for (String key : keys) {
				Object item = obj.get(key);
				if (item instanceof ArrayList) ret.put(key, _toJSON((ArrayList)item));
				else if (item instanceof HashMap) ret.put(key, _toJSON((HashMap<String, Object>)item));
				else ret.put(key, item);
			}
		} catch (JSONException e) {
			e.printStackTrace();
			return null;
		}
		return ret;
	}

	@SuppressWarnings("unchecked")
	protected static JSONArray _toJSON(ArrayList obj) {

		if (obj == null) return null;		

		JSONArray ret = new JSONArray();
		for (int i = 0; i < obj.size(); i++) {
			Object item = obj.get(i);
			if (item instanceof ArrayList) ret.put(_toJSON((ArrayList)item));
			else if (item instanceof HashMap) ret.put(_toJSON((HashMap<String, Object>)item));
			else ret.put(item);
		}
		return ret;
	}

	private class OjiseThread extends Thread {
		public void run() {
			boolean safeEnding = false;
			while (!safeEnding) {
				if (Ojise.this.isPaused() || Ojise.this.isMerged() || this.isInterrupted()) break;
				try {
					JSONArray omose = null;

					if (Ojise.this.mBatchId == 0) {
						//Connect to internet and send JSON object.
						JSONArray file_data = new JSONArray();

						for (int i = 1; i <= Ojise.this.mItems.size(); i++) {
							Ise item = Ojise.this.mItems.get(i);
							JSONObject json_item = new JSONObject();

							json_item.put("local_id", item.getLocalId());
							json_item.put("type", item.getType());
							json_item.put("params", item.getParams());
							json_item.put("priority", item.getIsePriority());
							json_item.put("filename", item.getFileName());
							json_item.put("size", item.getSize());
							file_data.put(json_item);
						}


						String[] keys = {"Expiry", "ChunksCount", "ChunksMinSize", "Params", "AccessDetails"};
						JSONObject ojiseData = new JSONObject(Ojise.this._jsonObject, keys);
						ojiseData.put("file_data", file_data);

						JSONObject ret = null;
						do {
							ret = Ojise.this.getOjiseServerProxy()._registerOjiseBatch(ojiseData);
							if (ret != null) break;
							try {
								Thread.sleep(60 * 1000);
							} catch (InterruptedException e) {
								break;
							}
						} while (true);
						
						if (ret == null) {
							//problem ...
							break;
						}


						Ojise.this.mBatchId = ret.getLong("batch_id");
						Ojise.this.mOjiseKey = ret.getString("ojise_key");

						if (Ojise.this.mChunksMinSize == 0) Ojise.this.mChunksMinSize = ret.getLong("ChunkSize");
						if (Ojise.this.mChunksCount == 0) Ojise.this.mChunksCount = ret.getInt("ChunkCount");
						if (Ojise.this.mTimeOut == 0) Ojise.this.mTimeOut = ret.getInt("TimeOut");
						if (Ojise.this.mUploadBitsSize == 0) Ojise.this.mUploadBitsSize = ret.getInt("UploadBitsSize");
						if (Ojise.this.mUploadBitsMaxSize == 0) Ojise.this.mUploadBitsMaxSize = ret.getInt("UploadBitsMaxSize");
						if (Ojise.this.mExpiry == 0) Ojise.this.mExpiry = ret.getInt("Expiry");

						omose = ret.getJSONArray("upload_threads");


						Ojise.this.clearOnSend();
						for (int i = 1; i <= Ojise.this.mItems.size(); i++) Ojise.this.mItems.get(i).clearOnSend();

						Ojise.this.toJSON(true);
						Ojise.this.saveState();

					}
					else {
						JSONObject ojiseData = new JSONObject();
						ojiseData.put("ojise_key", Ojise.this.mOjiseKey);
						ojiseData.put("batch_id", Ojise.this.mBatchId);

						JSONObject ret = null;
						do {
							ret = Ojise.this.getOjiseServerProxy()._getUploadPlanAndStatus(ojiseData);
							if (ret != null) break;
							try {
								Thread.sleep(60 * 1000);
							} catch (InterruptedException e) {
								break;
							}
						} while (true);
						
						if (ret == null) {
							//problem ...
							break;
						}

						omose = ret.getJSONArray("upload_threads");
						JSONArray item_status = ret.getJSONArray("upload_items_status");

						for (int i = 0; i < item_status.length(); i++) {
							JSONObject item_status_item = item_status.getJSONObject(i);
							Ise item = Ojise.this.getIse(item_status_item.getInt("local_id"));
							item.updateStatus(item_status.getJSONObject(i));
						}
					}
					
					if (Ojise.this.isPaused() || Ojise.this.isMerged() || this.isInterrupted()) break;

					Ojise.this.setOmose(omose);
					
					safeEnding = true;
				} catch (JSONException e) {
					e.printStackTrace();
				}
				catch (Exception e) {
					e.printStackTrace();
				}
				
				if (safeEnding == false && !this.isInterrupted()) {
					try {
						Thread.sleep(10 * 1000);
					} catch (InterruptedException e) {
						
					}
				}
			}
			
			safeEnding = false;
			int iseCounter = 0;
			
			while (!safeEnding) {
				if (Ojise.this.isPaused() || Ojise.this.isMerged() || this.isInterrupted()) break;
				
				try {
					while (true) {

						if (iseCounter == 0) Ojise.this.executeIse();
						if (++iseCounter >= 5) iseCounter = 0;
						
						Ojise.this.tryCompleteAll();
						Ojise.this.tryMergeAll();
						Ojise.this.saveState();

						try {
							Thread.sleep(60 * 1000);
						} catch (InterruptedException e) {
							
						}

						if (Ojise.this.isPaused() || Ojise.this.isMerged() || this.isInterrupted()) break;
					}

					safeEnding = true;
				}
				catch (Exception e) {
					e.printStackTrace();			
				}
				
				if (safeEnding == false && !this.isInterrupted()) {
					try {
						Thread.sleep(10 * 1000);
					} catch (InterruptedException e) {
						
					}
				}
			}
		}
	}
}
