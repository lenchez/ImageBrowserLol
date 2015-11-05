package eshque.com.imagebrowserlol;

import android.os.AsyncTask;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;

public class SearchResultsProvider {

    static final int PAGE_SIZE = 25;

    static final String FMT_TAG = "&$format=json";
    static final String SEARCH_BASE_URL = "https://api.datamarket.azure.com/Bing/Search/v1/Image?$top="+
            PAGE_SIZE+FMT_TAG+"&Query=";
    static final String NEXT_KEY = "__next";

    static final String BING_ACCOUNT_KEY = "RLSuItT7w3jKr10GrSyT6P63TK5accxPbANCJtwdonA=";
    static String encodedCredentials = null;
    String getCredentials() {
        if(encodedCredentials == null) {
            encodedCredentials = BING_ACCOUNT_KEY+":"+BING_ACCOUNT_KEY;
            encodedCredentials = "Basic "+ Base64.encodeToString(encodedCredentials.getBytes(), Base64.URL_SAFE);
            encodedCredentials = encodedCredentials.replace("\n", "");
        }

        return encodedCredentials;
    }

    private static String input2Url(String userInput) {
        String query = "'" + userInput + "'";
        try {
            query = URLEncoder.encode(query, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            MainActivity.logException(e);
        }

        return SEARCH_BASE_URL+query;
    }

    public enum SearchStatus {
        OK,
        ERROR,
        NO_MORE_RESULTS
    }

    public interface SearchDelegate {
        void onStart(boolean isNewSearch);
        void onDone(SearchStatus status);
    }

    private SearchDelegate delegate;
    private ArrayList<PictureInfoItem> results = new ArrayList<>();
    private HashMap<String, Integer> id2index = new HashMap<>();
    private FetchJsonTask runningTask;
    private String nextUrl;

    public SearchResultsProvider(SearchDelegate delegate) {
        this.delegate = delegate;
    }

    public void search(String userInput) {
        if(runningTask != null)
            runningTask.cancel(true);
        results.clear();
        id2index.clear();
        runningTask = new SearchQueryTask(userInput);
        runningTask.execute();
    }

    public void fetchNextPage() {
        if(!hasNextPage()) {
            delegate.onDone(SearchStatus.NO_MORE_RESULTS);
            return;
        }

        runningTask = new FetchJsonTask(nextUrl, false /*not a new search*/);
        runningTask.execute();
    }

    public int getResultsCount() {
        return results.size();
    }

    public boolean hasNextPage() {
        return nextUrl != null;
    }

    public boolean isLoading() {
        return runningTask != null;
    }

    public PictureInfoItem getResultAt(int index) {
        return results.get(index);
    }

    private void addItem(JSONObject data) {
        PictureInfoItem item = new PictureInfoItem(data);
        String id = item.getPicUrl();
        if(id2index.containsKey(id))
            return;

        id2index.put(id, results.size());
        results.add(item);
    }

    class SearchQueryTask extends FetchJsonTask {

        public SearchQueryTask(String userInput) {
            super(input2Url(userInput), true);
        }
    }

    class FetchJsonTask extends AsyncTask<String, Void, JSONObject> {
        protected String urlStr;
        protected boolean isNew;

        public FetchJsonTask(String url, boolean newSearch) {
            urlStr = url;
            isNew = newSearch;
        }

        protected void onPreExecute() {
            delegate.onStart(isNew);
        }

        protected JSONObject doInBackground(String... urls) {

            try {
                URL obj = new URL(urlStr);
                HttpURLConnection con = (HttpURLConnection) obj.openConnection();

                con.setRequestMethod("GET");
                con.setRequestProperty("Authorization", getCredentials());

                MainActivity.logDebug("Requesting " + obj);
                con.setDoOutput(true);

                int responseCode = con.getResponseCode();
                MainActivity.logDebug("Response Code : " + responseCode);
                if (responseCode != 200) {
                    return null;
                }

                BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
                String inputLine;
                StringBuilder resultBuilder = new StringBuilder();
                while ((inputLine = in.readLine()) != null) {
                    if(isCancelled())
                        return null;
                    resultBuilder.append(inputLine);
                }
                in.close();
                return new JSONObject(resultBuilder.toString());
            } catch (Exception e) {
                MainActivity.logDebug("Error during HTTP request!");
                MainActivity.logException(e);
            }

            return null;
        }

        protected void onPostExecute(JSONObject response) {
            runningTask = null;
            nextUrl = null;

            if(response == null) {
                delegate.onDone(SearchStatus.ERROR);
                return;
            }

            try {
                JSONObject data = response.getJSONObject("d");
                if(data.has(NEXT_KEY))
                    nextUrl = data.getString(NEXT_KEY)+FMT_TAG;

                JSONArray array = data.getJSONArray("results");
                for(int i=0; i<array.length(); ++i) {
                    //results.add(new PictureInfoItem(array.getJSONObject(i)));
                    addItem(array.getJSONObject(i));
                }

                MainActivity.logDebug("Loaded new page. Total results: "+results.size());
            } catch (JSONException e) {
                delegate.onDone(SearchStatus.ERROR);
                return;
            }

            delegate.onDone(SearchStatus.OK);
        }
    }
}
