This android java library can be used to send heavy files from your Android app to a standard http server with complete deliverability.

Sample Caller Code
```java

package org.test.ojise;

import java.util.*;

import com.macgrenor.ojise.Ise;
import com.macgrenor.ojise.Ojise;
import com.macgrenor.ojise.OjiseAndroid;

import android.app.Application;

public class Incident {
	private int mRecordId = 0;
	
	private String mTitle;
	private String mDescription;
	private long mDate = System.currentTimeMillis();
	private Double mLatitude;
	private Double mLongitude;
	
	private ArrayList<Media> mMedia = new ArrayList<Media>(20
	
	private void saveToDb() {
        
        HashMap<String, Object> params = new HashMap<String, Object>(50);			
        Set<Entry<String, Object>> params1 = values.valueSet();			
        for (Entry<String, Object> e : params1) {
            params.put(e.getKey(), e.getValue());
        }
        
        HashMap<String, Object> accessDetails = new HashMap<String, Object>(5);
        accessDetails.put("agent_id", ApplicationWide.AssignedFieldAgent.getId());
        
        Ojise ojise = new OjiseAndroid(Params.OJISE_URL, accessDetails, params, 0, 300 * 1024, 50 * 1024, 1024 * 1024, 0, 0, ApplicationWide.ApplicationContext);
        
        for (int i = 0; i < getImages().size(); i++) {
            HashMap<String, Object> Params = new HashMap<String, Object>(5);
            Media md = getImages().get(i);
            Params.put("title", md.getTitle());
            Params.put("type", md.getType());
            
            ojise.addUploadItem(new Ise(String.valueOf(md.getRecordId()), 1, md.getFilePath(), Params));
        }
        
        for (int i = 0; i < getVideos().size(); i++) {
            HashMap<String, Object> Params = new HashMap<String, Object>(5);
            Media md = getVideos().get(i);
            Params.put("duration", md.getDuration());
            Params.put("title", md.getTitle());
            Params.put("type", md.getType());
            
            ojise.addUploadItem(new Ise(String.valueOf(md.getRecordId()), 2, md.getFilePath(), Params));
        }
        
        for (int i = 0; i < getAudios().size(); i++) {
            HashMap<String, Object> Params = new HashMap<String, Object>(5);
            Media md = getAudios().get(i);
            Params.put("duration", md.getDuration());
            Params.put("title", md.getTitle());
            Params.put("type", md.getType());
            
            ojise.addUploadItem(new Ise(String.valueOf(md.getRecordId()), 3, md.getFilePath(), Params));
        }
        
        ojise.start();
        int mOjiseLocalId = ojise.getLocalId();
        
        String criteria = "ID = " + mRecordId;
        values.put("Published", publish);
        values.put("OjiseLocalId", mOjiseLocalId);

        DataProvider.getDataProvider().update("incident", values, criteria, null);
        
        mPublished = publish;
    }
    
	
}


```