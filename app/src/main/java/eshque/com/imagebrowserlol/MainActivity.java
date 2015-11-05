package eshque.com.imagebrowserlol;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ProgressBar;

public class MainActivity extends AppCompatActivity {

    static final boolean DEBUG = true;
    static final String TAG = "imageslol";

    static final int COLS_PORTRAIT = 2;
    static final int COLS_LANDSCAPE = 3;

    public static void logDebug(String msg) {
        if (DEBUG)
            Log.d(TAG, msg);
    }

    public static void logException(Exception e) {
        if (DEBUG)
            e.printStackTrace();
        else
            Log.e(TAG, e.getMessage());
    }

    private EditText queryTextView;
    private RecyclerView resultsView;
    private ProgressBar loadingIndicator;

    private SearchResultsProvider searchResultsProvider;
    private final StaggeredGridLayoutManager managerPortrait =
            new StaggeredGridLayoutManager(COLS_PORTRAIT, StaggeredGridLayoutManager.VERTICAL);
    private final StaggeredGridLayoutManager managerLandscape =
            new StaggeredGridLayoutManager(COLS_LANDSCAPE, StaggeredGridLayoutManager.VERTICAL);

    private PicturesAdapter adapter;
    private EndlessScrollListener scrollListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ImageCache.init();

        queryTextView = (EditText)findViewById(R.id.editText);
        queryTextView.setOnKeyListener(keyListener);

        resultsView = (RecyclerView)findViewById(R.id.recyclerView);
        resultsView.setVisibility(View.GONE);
        loadingIndicator = (ProgressBar)findViewById(R.id.progressBar);
        loadingIndicator.setVisibility(View.GONE);

        searchResultsProvider = new SearchResultsProvider(searchDelegate);
        adapter = new PicturesAdapter(this, searchResultsProvider);
        scrollListener = new EndlessScrollListener(searchResultsProvider);
        resultsView.setAdapter(adapter);
        resultsView.addOnScrollListener(scrollListener);
        updateResultsViewOrientation();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateResultsViewOrientation();
    }

    @Override
    public void onPause() {
        super.onPause();
        if(adapter != null)
            adapter.purge();
    }

    @Override
    public void onResume() {
        super.onResume();
        if(adapter != null)
            adapter.notifyDataSetChanged(); //that will restart interrupted downloads (if any)
    }


    void updateResultsViewOrientation() {
        if(getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE)
            resultsView.setLayoutManager(managerLandscape);
        else
            resultsView.setLayoutManager(managerPortrait);

        if(adapter.getItemCount() > 0)
            adapter.notifyDataSetChanged();
        resultsView.scrollToPosition(scrollListener.getFirstVisiblePosition());
    }

    public void onSearchBtnClick(View v) {
        performSearch();
    }

    public void performSearch() {
        String text = queryTextView.getText().toString();
        if(text == null || text.length() == 0) {
            showAlertDialog(R.string.search_query_empty);
            return;
        }

        resultsView.getLayoutManager().scrollToPosition(0);
        adapter.purge();

        View view = this.getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
        searchResultsProvider.search(text);
    }

    SearchResultsProvider.SearchDelegate searchDelegate = new SearchResultsProvider.SearchDelegate() {
        @Override
        public void onStart(boolean isNewSearch) {
            if(isNewSearch)
                resultsView.setVisibility(View.GONE);
            loadingIndicator.setVisibility(View.VISIBLE);
        }

        @Override
        public void onDone(SearchResultsProvider.SearchStatus status) {
            resultsView.setVisibility(View.VISIBLE);
            loadingIndicator.setVisibility(View.GONE);
            if(status == SearchResultsProvider.SearchStatus.ERROR)
                showAlertDialog(R.string.search_error);
            else if(searchResultsProvider.getResultsCount() == 0)
                showAlertDialog(R.string.search_nothing_found);
            if(status == SearchResultsProvider.SearchStatus.OK)
                resultsView.getAdapter().notifyDataSetChanged();
        }
    };

    private View.OnKeyListener keyListener = new View.OnKeyListener() {
        @Override
        public boolean onKey(View view, int keyCode, KeyEvent event) {
            if (keyCode == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN) {
                performSearch();
                return true;
            }
            return false;
        }
    };

    private class EndlessScrollListener extends RecyclerView.OnScrollListener {

        private int[] visiblePosBuf = new int[] { -1, -1, -1 };
        private void clearBuf() {
            for(int i=0; i<visiblePosBuf.length; ++i)
                visiblePosBuf[i] = -1;
        }

        private SearchResultsProvider provider;
        private int firstVisiblePosition = 0;

        public EndlessScrollListener(SearchResultsProvider resultsProvider) {
            provider = resultsProvider;
        }

        private int getLast(StaggeredGridLayoutManager mgr) {
            clearBuf();
            mgr.findLastVisibleItemPositions(visiblePosBuf);
            int max = visiblePosBuf[0];
            for(int i=1; i<visiblePosBuf.length; ++i) {
                if(visiblePosBuf[i] > max)
                    max = visiblePosBuf[i];
            }
            return max;
        }

        private int getFirst(StaggeredGridLayoutManager mgr) {
            clearBuf();
            mgr.findFirstVisibleItemPositions(visiblePosBuf);
            int min = visiblePosBuf[0];
            for(int i=1; i<visiblePosBuf.length; ++i) {
                if(visiblePosBuf[i] < min && visiblePosBuf[i] >= 0)
                    min = visiblePosBuf[i];
            }
            return min;
        }

        @Override
        public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
            super.onScrolled(recyclerView, dx, dy);

            StaggeredGridLayoutManager mgr = (StaggeredGridLayoutManager)recyclerView.getLayoutManager();
            int visibleThreshold = mgr.getSpanCount()*2;
            int lastVisible = getLast(mgr);
            int firstVisible = getFirst(mgr);

            if (!provider.isLoading() && provider.hasNextPage() &&
                    lastVisible >= provider.getResultsCount() - visibleThreshold) {
                logDebug("threshold: "+visibleThreshold);
                logDebug("last visible: "+lastVisible);
                logDebug("total items: "+provider.getResultsCount());
                logDebug("Loading next page...");
                provider.fetchNextPage();
            }

            firstVisiblePosition = firstVisible;
        }

        public int getFirstVisiblePosition() {
            return firstVisiblePosition;
        }
    };

    void showAlertDialog(int messageId) {
        showAlertDialog(getString(messageId));
    }

    void showAlertDialog(String message) {
        new AlertDialog.Builder(this)
                .setMessage(message)
                .setCancelable(true)
                .setNeutralButton(android.R.string.ok, null)
                .show();
    }
}
