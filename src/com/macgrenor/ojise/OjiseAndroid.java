package com.macgrenor.ojise;

import java.util.HashMap;

import org.macgrenor.json.JSONException;
import org.macgrenor.json.JSONObject;

import android.content.Context;
import android.content.SharedPreferences;

public class OjiseAndroid extends Ojise {
	private Context context;
	
	protected OjiseAndroid(JSONObject data, Context ctx) {		
		super(data);
		this.context = ctx;
	}
	
	public OjiseAndroid(String URL, HashMap<String, Object> AccessDetails,
			HashMap<String, Object> Params, int ChunksCount,
			long ChunksMinSize, int UploadBitsSize, int UploadBitsMaxSize,
			int TimeOut, int Expiry, Context ctx) {
		super(URL, AccessDetails, Params, ChunksCount, ChunksMinSize, UploadBitsSize,
				UploadBitsMaxSize, TimeOut, Expiry);
		
		this.context = ctx;
	}
	
	@Override
	protected synchronized int getNextOjiseId() {
		SharedPreferences prefs = context.getSharedPreferences("OJISE_SERIAL", Context.MODE_PRIVATE);
		
		int serial = prefs.getInt("SERIAL", 0) + 1;
		SharedPreferences.Editor editor = prefs.edit();
		
		editor.putInt("SERIAL", serial);
		editor.commit();
		
		return serial;
	} 

	protected void _saveState() {
		if (context == null) return;
		SharedPreferences prefs = context.getSharedPreferences("OJISE_DATA_" + this.getLocalId(), Context.MODE_PRIVATE);
		SharedPreferences.Editor editor = prefs.edit();
		editor.putString("DATA", _jsonObject.toString());
		editor.commit();
	}
	
	private static boolean ojiseStarted = false;
	private static HashMap<Integer, Ojise> ojiseList = new HashMap<Integer, Ojise>(10);
	public synchronized static void startOjise(Context ctx, OjiseListener listener) {
		if (ojiseStarted) return;
		ojiseStarted = true;
		
		SharedPreferences prefs = ctx.getSharedPreferences("OJISE_SERIAL", Context.MODE_PRIVATE);
		int serial_max = prefs.getInt("SERIAL", 0);
		
		for (int i = 1; i <= serial_max; i++) {
			SharedPreferences prefs2 = ctx.getSharedPreferences("OJISE_DATA_" + i, Context.MODE_PRIVATE);
			JSONObject data;
			try {
				data = new JSONObject(prefs2.getString("DATA", ""));
				Ojise ojise = new OjiseAndroid(data, ctx);
				ojise.setListener(listener);
				ojiseList.put(i, ojise);
			} catch (JSONException e) {
				e.printStackTrace();
			}
		}
	}
	public synchronized static void stopAllOjise() {
		if (!ojiseStarted) return;
		ojiseStarted = false;
		
		try {
			for (int i = 0; i < ojiseList.size(); i++) {
				ojiseList.get(i).stop();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		
	}
	public static Ojise getRunningOjise(int i) {
		return ojiseList.get(i);
	}
	protected void addRunningOjise(Ojise ojise) {
		OjiseAndroid.ojiseList.put(ojise.getLocalId(), ojise);
	}
}
