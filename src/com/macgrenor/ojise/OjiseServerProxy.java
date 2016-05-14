package com.macgrenor.ojise;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.macgrenor.json.JSONException;
import org.macgrenor.json.JSONObject;

public class OjiseServerProxy {
	private String mUrl;
	private int timeout;
	public OjiseServerProxy(Ojise ojise) {
		mUrl = ojise.getURL();
		timeout = ojise.getTimeOut();
	}
	
	final public JSONObject doSomething(String method, JSONObject ojiseData, byte[] data) {
		String s = null;
		
		ClientHttpRequest conn = null;
		try {
			conn = new ClientHttpRequest(mUrl, timeout * 1000);
			conn.setParameter("method", method);
			conn.setParameter("ojiseData", ojiseData.toString());
			if (data != null) {				
				conn.setParameter("chunk", "chunk.ise", new ByteArrayInputStream(data));
			}
			
			s = conn.postAndRetrieve();
		} catch (Exception e1) {
			s = null;
			e1.printStackTrace();
		}
		finally {
			if (conn != null) conn.closeAll();
		}
		
		//System.out.println("serveroutput: " + s);
		
		try {
			return s == null || s.equals("") ? null : new JSONObject(s);
		} catch (JSONException e) {
			System.out.println("serveroutput: " + s);
			//System.exit(0);
			e.printStackTrace();
		}
		return null;
	}
	final public JSONObject _registerOjiseBatch(JSONObject ojiseData) {
		return doSomething("_registerOjiseBatch", ojiseData, null);
		
	}
	
	final public JSONObject _getUploadPlanAndStatus(JSONObject ojiseData) {		 
		return doSomething("_getUploadPlanAndStatus", ojiseData, null);
	}
	
	final public JSONObject _uploadPart(JSONObject ojiseData, byte[] data) {
		return doSomething("_uploadPart", ojiseData, data);
	}
	
	final public JSONObject _mergeUploadItem(JSONObject ojiseData) {
		return doSomething("_mergeUploadItem", ojiseData, null);
	}
	
	final public JSONObject _mergeBatch(JSONObject ojiseData) {
		return doSomething("_mergeBatch", ojiseData, null);
	}
	
}
