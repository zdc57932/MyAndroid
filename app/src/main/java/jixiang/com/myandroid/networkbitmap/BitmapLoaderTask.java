package jixiang.com.myandroid.networkbitmap;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.ref.WeakReference;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.widget.ImageView;

import jixiang.com.myandroid.http.Logg;


/**
 * 从SD卡缓存目录中加载图片
 * 通过URL进行文件查找匹配
 */
public class BitmapLoaderTask extends AsyncTask<String, Void, Bitmap> {
	private static final String TAG = BitmapLoaderTask.class.getCanonicalName();

	private WeakReference<ImageView> imageViewReference;
	private Context mContext;
	private BitmapLoadListener mListener;
	public String mUrl;
	private boolean mError;
	private String picPath = null;
	private int mWidth;
	private int mHeight;
	private static int defaultWidth = 480;
	private static int defaultHeight = 800;
	
	/**
	 * 图片加载器回调接口
	 *
	 */
	public interface BitmapLoadListener {
		public void notFound();

		public void loadBitmap(Bitmap b);

		public void onLoadError();

		public void onLoadCancelled();
	}

	public BitmapLoaderTask(ImageView imageView, BitmapLoadListener listener) {
		imageViewReference = new WeakReference<ImageView>(imageView);
		mContext = imageView.getContext().getApplicationContext();
		mListener = listener;
	}
	
	public BitmapLoaderTask(ImageView imageView, String path, BitmapLoadListener listener) {
		imageViewReference = new WeakReference<ImageView>(imageView);
		mContext = imageView.getContext().getApplicationContext();
		mListener = listener;
		picPath = path;
	}

	/**
	 * Conservatively estimates inSampleSize. Given a required width and height,
	 * this method calculates an inSampleSize that will result in a bitmap that is
	 * approximately the size requested, but guaranteed to not be smaller than
	 * what is requested.
	 * 
	 * @param options
	 *          the {@link BitmapFactory.Options} obtained by decoding the image
	 *          with inJustDecodeBounds = true
	 * @param reqWidth
	 *          the required width
	 * @param reqHeight
	 *          the required height
	 * 
	 * @return the calculated inSampleSize
	 */
	private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
		// Raw height and width of image
		final int height = options.outHeight;
		final int width = options.outWidth;
		int inSampleSize = 1;

		if (height > reqHeight || width > reqWidth) {
			if (width > height) {
				inSampleSize = Math.round((float) height / (float) reqHeight);
			} else {
				inSampleSize = Math.round((float) width / (float) reqWidth);
			}
		}
		return inSampleSize;
	}
	
	/**
	 * 从路径中加载图片
	 */
	@Override
	protected Bitmap doInBackground(String... params) {
		ImageView imageView = imageViewReference.get();
		Bitmap bitmap = null;
		if(imageView != null) {
			mWidth = imageView.getMeasuredWidth();
			mHeight = imageView.getMeasuredHeight();
			if(mWidth == 0 || mHeight == 0) {
				mWidth = defaultWidth;
				mHeight = defaultHeight;
			}
			Logg.out("width=" + mWidth);
			Logg.out("height=" + mHeight);
			mUrl = params[0];
			if (mUrl == null) {
				return null;
			}
			String filename = MD5.md5(mUrl);
			if (isCancelled()) {
				return null;
			}
			if (filename != null) {
				try {
					System.gc();
					FileInputStream local = null;
					if(picPath == null) {
						local = mContext.openFileInput(filename);
					} else {
						File file = new File(picPath, filename);
						local = new FileInputStream(file);
					}
					final BitmapFactory.Options options = new BitmapFactory.Options();
					options.inJustDecodeBounds = true;
					BitmapFactory.decodeFileDescriptor(local.getFD(), null, options);

					options.inSampleSize = calculateInSampleSize(options, mWidth, mHeight);
					options.inJustDecodeBounds = false;
					try{
						bitmap = BitmapFactory.decodeFileDescriptor(local.getFD(), null, options);
					} catch(OutOfMemoryError error) {
						System.gc();
						try{
							bitmap = BitmapFactory.decodeFileDescriptor(local.getFD(), null, options);
						} catch(OutOfMemoryError error2) {
							Logg.e(TAG, "OUT of Memory");
						}
					}
					if (bitmap == null) {
						Logg.w(TAG, "The file specified is corrupt.");
						mContext.deleteFile(filename);
						mError = true;
						throw new FileNotFoundException("The file specified is corrupt.");
					}
					if(local != null) {
						local.close();
						local = null;
					}
					System.gc();
				} catch (FileNotFoundException e) {
					Logg.w(TAG, "Bitmap is not cached on disk. File Not Found." + e.getMessage());
				} catch (IOException e) {
					Logg.w(TAG, "Bitmap is not cached on disk. Redownloading." +e.getMessage());
				}
			}
			return bitmap;
		} else {
			return null;
		}
	}

	@Override
	protected void onPostExecute(Bitmap bitmap) {
		if (bitmap == null && !mError && !isCancelled()) {
			mListener.notFound();
		} else {
			ImageView imageView = imageViewReference.get();
			if(imageView != null) {
				if(bitmap != null) {
					if(!isCancelled()) {
						mListener.loadBitmap(bitmap);
					} else {
						mListener.onLoadCancelled();
						if(!bitmap.isRecycled()) {
							bitmap.recycle();
							bitmap = null;
						}
					}
				} else {
					mListener.onLoadError();
				}
			} else { //图片控件不再使用，无论加载与否，都不再需要这张图片以节省空间
				mListener.onLoadCancelled(); 
				if(bitmap != null && !bitmap.isRecycled()) {
					bitmap.recycle();
					bitmap = null;
				}
			}
			
			//旧版本的代码，可能会照成内存泄露
			/*if (isCancelled() && !bitmap.isRecycled()) {
				bitmap.recycle();
				bitmap = null;
			}
			ImageView imageView = imageViewReference.get();
			if (imageView != null && !mError) {
				if (bitmap != null) {
					mListener.loadBitmap(bitmap);
				} else if (!isCancelled()) {
					mListener.onLoadError();
				} else if (isCancelled()) {
					mListener.onLoadCancelled();
				}
			} else {
				mListener.onLoadError();
			}*/
		}
	}
}
