package com.macgrenor.ojise;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import org.macgrenor.json.JSONException;
import org.macgrenor.json.JSONObject;

final class Omose extends Thread {
	//id, thread_number, start_pos, current_size, size, local_id, priority
	private long mId;
	private int mThreadNumber;
	private long mStartPos;
	private long mCurrentSize;
	private long mSize;
	private int mLocalId;
	private int mPriority;
	private Ojise mOjise;
	private int mUploadBitsSize;
		
	protected Omose(long Id, int ThreadNumber, long StartPos, long CurrentSize,
			long Size, int LocalId, int Priority, int UploadBitsSize, Ojise ojise) {
		super();
		
		
		this.mId = Id;
		this.mThreadNumber = ThreadNumber;
		this.mStartPos = StartPos;
		this.mSize = Size;
		this.mLocalId = LocalId;
		this.mPriority = Priority;
		this.mOjise = ojise;
		this.mUploadBitsSize = UploadBitsSize;
		setCurrentSize(CurrentSize);
	}
	
	public void run() { //TODO This Thread must not die except intentionally.
		boolean safeEnding = false;
		while (!safeEnding) {
			try {
				if (mUploadBitsSize == 0) mUploadBitsSize = mOjise.getUploadBitsSize();
				
				while (true) {	
					if (isCompleted()) {
						mOjise.notifyThreadCompleted(this);
						break;
					}
					
					if (mOjise.getIse(mLocalId).isCompleted() || mOjise.isPaused()) return;
					if (this.isInterrupted()) return;
					
					//TODO, there is wahala here.
					int mSendSize = ((mCurrentSize + mUploadBitsSize) > mSize) ? (int)(mSize - mCurrentSize) : mUploadBitsSize;		
					byte[] data = null;
					
					while (true) {
						try {
							data = mOjise.getIse(mLocalId).read(mStartPos + mCurrentSize, mSendSize);
							break;
						} catch (IOException e) {
							e.printStackTrace();
							try {
								Thread.sleep(10 * 1000);
							} catch (InterruptedException e1) {
								return;
							}
						}
					}
					
					//send the data			
					//read response
					if (this.isInterrupted()) return;
					
					JSONObject item_meta = new JSONObject();
					item_meta.put("thread_id", mId);
					item_meta.put("chunk_position", mCurrentSize);
					item_meta.put("chunk_size", mSendSize);
					
					JSONObject ojiseData = new JSONObject();
					ojiseData.put("ojise_key", mOjise.getOjiseKey());
					ojiseData.put("batch_id", mOjise.getBatchId());
					ojiseData.put("item_meta", item_meta);
	
					//ByteArrayInputStream bais = new ByteArrayInputStream(data);
					
					/* File item_chunk = File.createTempFile("ojise.", ".tmp", new File("C:/tmp/ojise/"));
					FileOutputStream fos = new FileOutputStream(item_chunk);
					fos.write(data);
					fos.close(); */
					
					if (this.isInterrupted()) return;
					
					long timeStore = System.currentTimeMillis();
					
					JSONObject ret = mOjise.getOjiseServerProxy()._uploadPart(ojiseData, data);
					//item_chunk.delete();
					
					/*try {
						sleep((long)((2 + (Math.random() * mOjise.getTimeOut())) * 1000));
					} catch (InterruptedException e) {
						e.printStackTrace();
					}*/
					
					long timeDiff = System.currentTimeMillis() - timeStore;
					
					
					if (this.isInterrupted()) return;
					
					if (ret != null) {
						mOjise.setMerged(ret.getInt("batch_merged") == 1);
						mOjise.setCompleted(ret.getInt("batch_completed") == 1);
						
						mOjise.getIse(mLocalId).setMerged(ret.getInt("item_merged") == 1);
						mOjise.getIse(mLocalId).setCompleted(ret.getInt("item_completed") == 1);
						
						setCurrentSize(ret.getLong("current_size"));
						
						//if (mOjise.isCompleted() || mOjise.getIse(mLocalId).isCompleted()) return;	
						
						if (mSendSize == mUploadBitsSize && timeDiff <= (mOjise.getTimeOut() * 1000 * 4 / 5)) { //with 4/5 of the time left.
							mUploadBitsSize += (int)Math.ceil((double)(mUploadBitsSize) / 5d);
							if (mUploadBitsSize > mOjise.getUploadBitsMaxSize()) mUploadBitsSize = mOjise.getUploadBitsMaxSize(); 
						}
					}
					else {
						mUploadBitsSize = (int)Math.ceil((double)(mSendSize * 2) / 3d);
					}				
				}
				
				safeEnding = true;
			} catch (JSONException e) {
				e.printStackTrace();
			} catch (Exception e) {
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

	public long getId() {
		return mId;
	}

	public int getThreadNumber() {
		return mThreadNumber;
	}

	public long getStartPos() {
		return mStartPos;
	}

	public long getCurrentSize() {
		return mCurrentSize;
	}

	protected void setCurrentSize(long cSize) {
		mOjise.getItems().get(mLocalId).addUploadSize(cSize - mCurrentSize);
		mCurrentSize = cSize;
	}

	public long getSize() {
		return mSize;
	}

	public int getLocalId() {
		return mLocalId;
	}
	
	public int getOmosePriority() {
		return mPriority;
	}
	
	public boolean isCompleted() {
		return this.mSize == this.mCurrentSize;
	}

	public int getUploadBitsSize() {
		return mUploadBitsSize;
	}

	public void setUploadBitsSize(int UploadBitsSize) {
		mUploadBitsSize = UploadBitsSize;
	}
}
