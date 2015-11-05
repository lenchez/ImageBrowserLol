package eshque.com.imagebrowserlol;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ImageView;

public class AspectRatioImageView extends ImageView{

    private float aspectRatio = 1.0f;
    private int lastWidth;

    public AspectRatioImageView(Context context) {
        super(context);
    }

    public AspectRatioImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public AspectRatioImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int width = getMeasuredWidth();
        int height = Math.round(width / getAspectRatio());
        setMeasuredDimension(width, height);
        if(measureCallback != null)
            measureCallback.onMeasure(width, height);
        lastWidth = width;
    }

    public void setMeasureCallback(MeasureCallback measureCallback) {
        this.measureCallback = measureCallback;
    }

    public float getAspectRatio() {
        return aspectRatio;
    }

    public void setAspectRatio(float aspectRatio) {
        this.aspectRatio = aspectRatio;
        requestLayout();
    }

    public interface MeasureCallback {
        void onMeasure(int width, int height);
    }

    private MeasureCallback measureCallback;

}
