package eshque.com.imagebrowserlol;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.ConnectException;
import java.net.ProtocolException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;

public class PictureViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {

    private TextView description;
    private AspectRatioImageView picture;
    private View root;
    private ProgressBar progressBar;
    private ImageView failIcon;
    private Context context;

    private PictureInfoItem info;
    private DownloadImageTask currentTask;

    public PictureViewHolder(Context context, View itemView) {
        super(itemView);
        this.context = context;
        root = itemView;

        root.setOnClickListener(this);
        description = (TextView) root.findViewById(R.id.infoText);
        picture = (AspectRatioImageView) root.findViewById(R.id.picture);
        failIcon = (ImageView)root.findViewById(R.id.failIcon);
        progressBar = (ProgressBar) root.findViewById(R.id.picProgressBar);
    }

    public void setInfoItem(PictureInfoItem infoItem) {
        boolean shouldUpdate = info == null ||
            !infoItem.getPicUrl().equals(info.getPicUrl());
        info = infoItem;
        if(shouldUpdate)
            updateImage();

        description.setText(info.getWidth() + "x" + info.getHeight() + " " + info.getDescription());
        picture.setAspectRatio(info.getAspectRatio());
    }

    public void cancelDownload() {
        if(isDownloading()) {
            currentTask.cancelDecoding();
            currentTask.cancel(true);
        }
        currentTask = null;
    }

    public boolean isDownloading() {
        return currentTask != null;
    }

    public void refreshImageIfNeeded() {
        if(isDownloading())
            return;

        if(picture.getVisibility() == View.VISIBLE &&
                picture.getDrawable() instanceof BitmapDrawable)
            return;

        updateImage();
    }

    public void updateImage() {
        failIcon.setVisibility(View.INVISIBLE);
        String url = info.getPicUrl();
        Bitmap cached = ImageCache.getBitmap(url);

        cancelDownload();

        if(cached != null) {
            setImage(cached);
            //MainActivity.logDebug("Cache HIT for url "+info.getPicUrl());
            return;
        } else if (ImageCache.isInvalid(url)){
            setImage(null);
            //MainActivity.logDebug("Null bitmap exists in cache for url "+info.getPicUrl()+
            //    "\n(i.e. provided URL is most likely bad)");
            return;
        }

        //MainActivity.logDebug("Cache MISS for url " + info.getPicUrl());
        if(picture.getWidth() > 0)
            startDownload();
        else
            startDownloadAfterMeasuring();
    }

    void startDownloadAfterMeasuring(){
        currentTask = null;
        //MainActivity.logDebug("Delayed download until measure for url " + info.getPicUrl());
        picture.setMeasureCallback(new AspectRatioImageView.MeasureCallback() {
            @Override
            public void onMeasure(int width, int height) {
                startDownload();
                picture.setMeasureCallback(null);
            }
        });
    }

    void startDownload() {
        currentTask = new DownloadImageTask();
        currentTask.execute(info);
    }

    void setImage(Bitmap b) {
        if(b == null) {
            failIcon.setVisibility(View.VISIBLE);
            picture.setImageResource(android.R.color.transparent);
        } else {
            failIcon.setVisibility(View.INVISIBLE);
            picture.setImageBitmap(b);
        }

        picture.setVisibility(View.VISIBLE);
        progressBar.setVisibility(View.GONE);
    }

    private String shorten(String str) {
        final int MAX = 64;
        if(str.length() <= MAX)
            return str;
        return str.substring(0, MAX-1)+"...";
    }

    @Override
    public void onClick(View view) {
        String sizeSuffix = "";
        if(picture.getVisibility() == View.VISIBLE &&
                picture.getDrawable() instanceof BitmapDrawable) {
            Bitmap bmp = ((BitmapDrawable)picture.getDrawable()).getBitmap();
            if(bmp != null && bmp.getWidth() != info.getWidth()) {
                sizeSuffix = String.format(
                        context.getString(R.string.resampled_to),
                        bmp.getWidth()+"x"+bmp.getHeight()
                );
            }
        }

        String message = String.format(
                context.getString(R.string.image_dialog_text),
                getLayoutPosition()+1,
                isDownloading() ? "(downloading...)" : "",
                shorten(info.getDescription()),
                shorten(info.getPicUrl()),
                shorten(info.getSourceUrl()),
                info.getWidth()+"x"+info.getHeight()+sizeSuffix
        );

        new AlertDialog.Builder(context)
                .setMessage(message)
                .setCancelable(true)
                .setPositiveButton(R.string.dialog_action_open_image, new DialogURL(context, info.getPicUrl()))
                .setNeutralButton(R.string.dialog_action_open_page, new DialogURL(context, info.getSourceUrl()))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static class DialogURL implements DialogInterface.OnClickListener {

        private String url;
        private Context context;

        public DialogURL(Context context, String url) {
            this.context = context;
            this.url = url;
        }

        @Override
        public void onClick(DialogInterface dialogInterface, int i) {
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            context.startActivity(browserIntent);
        }
    }

    private static class ImageDownloadResult {
        public enum Code {
            OK,
            ERR_INTERRUPTED,
            ERR_BAD_URL,
            ERR_CANT_DECODE,
            ERR_UNKNOWN
        }

        public Code code;
        public Bitmap bitmap;
        public ImageDownloadResult() {
            code = Code.ERR_UNKNOWN;
        }
    }

    private class DownloadImageTask extends AsyncTask<PictureInfoItem, Void, ImageDownloadResult> {

        private int requiredWidth = 0;
        private int requiredHeight = 0;
        private int picWidth = 0;
        private int picHeight = 0;
        private BitmapFactory.Options options = new BitmapFactory.Options();

        protected void onPreExecute() {
            requiredWidth = picture.getMeasuredWidth();
            requiredHeight = (int) (requiredWidth / picture.getAspectRatio());
            picWidth = info.getWidth();
            picHeight = info.getHeight();
            MainActivity.logDebug("Dimens for url " + info.getPicUrl());
            MainActivity.logDebug("Required dimens: " + requiredWidth + "x" + requiredHeight);
            MainActivity.logDebug("Image dimens: " + picWidth + "x" + picHeight);

            progressBar.setVisibility(View.VISIBLE);
            picture.setVisibility(View.INVISIBLE);
        }

        protected ImageDownloadResult doInBackground(PictureInfoItem... items) {
            PictureInfoItem info = items[0];
            ImageDownloadResult result = new ImageDownloadResult();
            String urlStr = info.getPicUrl();
            try {
                URL url = new URL(urlStr);
                MainActivity.logDebug("Fetching bitmap for " + urlStr);
                URLConnection conn = url.openConnection();
                conn.setConnectTimeout(10000);
                conn.connect();
                InputStream in = conn.getInputStream();

                //NOTE: Some implementations of URL.equals() resolve host names over the network
                //So we'll just compare strings instead of URLs itself
                if(!urlStr.equals(conn.getURL().toString())) {
                    in.close();
                    URL redirUrl = conn.getURL();
                    in = redirUrl.openStream();
                    MainActivity.logDebug("Redirected url " + redirUrl);
                }

                options.inSampleSize = calculateInSampleSize(picWidth, picHeight, requiredWidth, requiredHeight);
                if (options.inSampleSize > 1)
                    MainActivity.logDebug("Downsample x" + options.inSampleSize + " for " + urlStr);

                if(isCancelled())
                    throw new InterruptedIOException("Task cancelled before decodeStream started");

                result.bitmap = BitmapFactory.decodeStream(in, null, options);
                if (result.bitmap != null)
                    result.code = ImageDownloadResult.Code.OK;
                else if (options.mCancel){
                    MainActivity.logDebug("decodeStream was cancelled during execution " + urlStr);
                    throw new InterruptedIOException("Task cancelled during decodeStream");
                } else{
                    MainActivity.logDebug("It's strange but decoded bitmap is null for " + urlStr);
                    result.code = ImageDownloadResult.Code.ERR_CANT_DECODE;
                }

                in.close();
            } catch (FileNotFoundException e) {
                MainActivity.logDebug("Url not available (file was moved or deleted) " + urlStr);
                result.code = ImageDownloadResult.Code.ERR_BAD_URL;
            } catch (UnknownHostException e) {
                MainActivity.logDebug("Host not available " + urlStr);
                result.code = ImageDownloadResult.Code.ERR_BAD_URL;
            } catch (SocketTimeoutException e) {
                MainActivity.logDebug("Timed out while connecting to " + urlStr);
                result.code = ImageDownloadResult.Code.ERR_BAD_URL;
            } catch (ConnectException e) {
                MainActivity.logDebug("Failed to connect to " + urlStr);
                result.code = ImageDownloadResult.Code.ERR_BAD_URL;
            } catch (ProtocolException e) {
                MainActivity.logDebug("Protocol exception " + e.getMessage() + " for " + urlStr);
                result.code = ImageDownloadResult.Code.ERR_BAD_URL;
            } catch (InterruptedIOException e) {
                MainActivity.logDebug("Download interrupted for " + urlStr);
                result.code = ImageDownloadResult.Code.ERR_INTERRUPTED;
            } catch (Exception e) {
                MainActivity.logDebug("Download failed for " + urlStr);
                MainActivity.logException(e);
                result.code = ImageDownloadResult.Code.ERR_UNKNOWN;
            } catch (OutOfMemoryError e) {
                MainActivity.logDebug("Download failed for " + urlStr);
                result.code = ImageDownloadResult.Code.ERR_CANT_DECODE;
                System.gc();
            }

            return result;
        }

        public void cancelDecoding() {
            MainActivity.logDebug("Request download cancel at position "+getLayoutPosition());
            options.requestCancelDecode();
        }

        protected void onPostExecute(ImageDownloadResult result) {
            if (result.code == ImageDownloadResult.Code.OK && result.bitmap != null)
                ImageCache.addBitmap(info.getPicUrl(), result.bitmap);
            else if(result.code == ImageDownloadResult.Code.ERR_BAD_URL ||
                    result.code == ImageDownloadResult.Code.ERR_CANT_DECODE)
                ImageCache.markInvalid(info.getPicUrl());

            setImage(ImageCache.getBitmap(info.getPicUrl())); //get from cache to update access timestamp
            currentTask = null;
        }
    }

    private static int calculateInSampleSize(int width, int height, int reqWidth, int reqHeight) {
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            if (width > height) {
                if (reqHeight != 0)
                    inSampleSize = Math.round((float) height / (float) reqHeight);
            } else {
                if (reqWidth != 0)
                    inSampleSize = Math.round((float) width / (float) reqWidth);
            }
        }

        return inSampleSize;
    }
}