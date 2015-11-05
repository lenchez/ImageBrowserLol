package eshque.com.imagebrowserlol;

import org.json.JSONException;
import org.json.JSONObject;

public class PictureInfoItem {
    private int width;
    private int height;
    private float aspectRatio;
    private String picUrl;
    private String sourceUrl;
    private String description;

    public PictureInfoItem(JSONObject json) {
        try {
            height = json.getInt("Height");
            width = json.getInt("Width");
            aspectRatio = (float)width/height;
            picUrl = json.getString("MediaUrl");
            sourceUrl = json.getString("SourceUrl");
            description = json.getString("Title");
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public float getAspectRatio() {
        return aspectRatio;
    }

    public String getPicUrl() {
        return picUrl;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public String getDescription() {
        return description;
    }
}
