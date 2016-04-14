package com.androlua;

import android.content.*;
import android.graphics.*;
import android.net.http.*;
import android.os.*;
import android.view.*;
import android.webkit.*;
import com.androlua.LuaWebView.*;
import android.app.*;
import android.widget.*;
import android.graphics.drawable.*;
import android.net.*;
import android.util.*;

public class LuaWebView extends WebView
{

	private Main mContext;

	private ProgressBar mProgressbar;

	private DisplayMetrics dm;


	public LuaWebView(Main context)
	{
		super(context);
		context.Webs.add(this);
		mContext = context;
		getSettings().setJavaScriptEnabled(true);
		getSettings().setJavaScriptCanOpenWindowsAutomatically(true);
		addJavascriptInterface(new LuaJavaScriptinterface(context), "androlua");
		//requestFocus();
		setWebViewClient(new WebViewClient()
			{
				public boolean shouldOverrideUrlLoading(WebView view, String url)
				{
					if(!url.substring(0,3).equals("http"))
						return false;
					view.loadUrl(url);  
					return true;
				}
			}
		);

		//mContext.requestWindowFeature(Window.FEATURE_PROGRESS);
		//mContext.requestWindowFeature(1000);
		//mContext.requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		//mContext.setProgressBarVisibility(true);
		
		//mContext.requestWindowFeature(Window.FEATURE_PROGRESS); 
		//mContext.setProgressBarVisibility(true);
		
		//mContext.setSecondaryProgress(progressHorizontal.getSecondaryProgress()* 100); 
		dm=context.getResources().getDisplayMetrics();
		int top=(int) TypedValue.applyDimension(1,8,dm);
		
		mProgressbar = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
        mProgressbar.setLayoutParams(new LayoutParams(LayoutParams.FILL_PARENT, top, 0, 0));
        addView(mProgressbar);
		
		setWebChromeClient(new LuaWebChromeClient());
		setDownloadListener(new Download());
	}
	
	@Override
	protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        LayoutParams lp = (LayoutParams) mProgressbar.getLayoutParams();
        lp.x = l;
        lp.y = t;
        mProgressbar.setLayoutParams(lp);
		super.onScrollChanged(l, t, oldl, oldt);
    }
	
	@Override
	public void setDownloadListener(DownloadListener listener)
	{
		// TODO: Implement this method
		super.setDownloadListener(listener);
	}

	public interface onDownloadStartListener
	{
		public abstract void onDownloadStart(String url, String userAgent, String contentDisposition, String mimetype, long contentLength)
	}


	class Download implements DownloadListener
	{

		private String mUrl;

		private String mUserAgent;

		private String mContentDisposition;

		private String mMimetype;

		private long mContentLength;
		EditText file_input_field=new EditText(mContext);
		private String mFilename;

		@Override
		public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimetype, long contentLength)
		{
			// TODO: Implement this method
			mUrl = url;
			mUserAgent = userAgent;
			mContentDisposition = contentDisposition;
			mMimetype = mimetype;
			mContentLength = contentLength;
			Uri uri=Uri.parse(mUrl);
			mFilename = uri.getLastPathSegment();
			if (contentDisposition != null)
			{
				String p="filename=\"";
				int i=contentDisposition.indexOf(p);
				if (i != -1)
				{
					i+=p.length();
					int n=contentDisposition.indexOf('"', i);
					if (n > i)
						mFilename = contentDisposition.substring(i, n);
				}
			}
			
			file_input_field.setText(mFilename);

			new AlertDialog.Builder(mContext)
				.setTitle("确认下载")
				.setMessage("url: " + url + "\nType: " + mimetype + "\nSize: " + contentLength)
				.setView(file_input_field)
				.setPositiveButton("Download", new DialogInterface.OnClickListener(){
					@Override
					public void onClick(DialogInterface p1, int p2)
					{
						// TODO: Implement this method
						mFilename = file_input_field.getText().toString();
						download(false);
					}
				})
				.setNegativeButton("Cancel", null)
				.setNeutralButton("Only Wifi", new DialogInterface.OnClickListener(){

					@Override
					public void onClick(DialogInterface p1, int p2)
					{
						// TODO: Implement this method
						mFilename = file_input_field.getText().toString();
						download(true);
					}
				})
				.create()
				.show();
		}

		private long download(boolean isWifi)
		{
			DownloadManager downloadManager =  (DownloadManager)mContext.getSystemService(Context.DOWNLOAD_SERVICE);

			Uri uri=Uri.parse(mUrl);
			uri.getLastPathSegment();
			DownloadManager.Request request = new  DownloadManager.Request(uri);

			request.setDestinationInExternalPublicDir("Download", mFilename);

			//request.setTitle(mFilename);

			request.setDescription("By Androlua+");

			request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
			if (isWifi)
				request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI);

			//request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_HIDDEN);

			request.setMimeType(mMimetype);

			long downloadId =  downloadManager.enqueue(request);
			return downloadId;
		}
	}


	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event)
	{       
		if ((keyCode == KeyEvent.KEYCODE_BACK) && canGoBack())
		{       
            goBack();       
			return true;       
        }       
		return super.onKeyDown(keyCode, event);       
    }

	@Override
	public void setOnKeyListener(View.OnKeyListener l)
	{
		// TODO: Implement this method
		super.setOnKeyListener(l);
	}

	@Override
	public void addJSInterface(JsInterface object, String name)
	{
		// TODO: Implement this method
		super.addJavascriptInterface(new JsObject(object), name);
	}

	@Override
	public void addJsInterface(JsInterface object, String name)
	{
		// TODO: Implement this method
		super.addJavascriptInterface(new JsObject(object), name);
	}

	class JsObject
	{
		private LuaWebView.JsInterface mJs;
		public JsObject(JsInterface js)
		{
			mJs = js;
		}
		@JavascriptInterface
		public String execute(String arg)
		{
			return mJs.execute(arg);
		};
	}

	public interface JsInterface
	{
		@JavascriptInterface
		public String execute(String arg);
	}

	public void setWebViewClient(LuaWebViewClient client)
	{
		// TODO: Implement this method
		super.setWebViewClient(new SimpleLuaWebViewClient(client));
	}

	private class LuaJavaScriptinterface
	{

		private Main mMain;
		public LuaJavaScriptinterface(Main  main)
		{
			mMain = main;
		}

		@JavascriptInterface
		public Object callLuaFunnction(String name)
		{
			return mMain.runFunc(name);
		}

		@JavascriptInterface
		public Object callLuaFunnction(String name, String arg)
		{
			return mMain.runFunc(name, arg);
		}

		@JavascriptInterface
		public Object doLuaString(String name)
		{
			return mMain.doString(name);
		}
	}

	private class SimpleLuaWebViewClient extends WebViewClient
	{

		private LuaWebView.LuaWebViewClient mLuaWebViewClient;

		public SimpleLuaWebViewClient(LuaWebViewClient wvc)
		{
			mLuaWebViewClient = wvc;
		}

		public boolean shouldOverrideUrlLoading(WebView view, String url)
		{
			return mLuaWebViewClient.shouldOverrideUrlLoading(view, url);
		}

		public void onPageStarted(WebView view, String url, Bitmap favicon)
		{
			mLuaWebViewClient.onPageStarted(view, url, favicon);
		}

		public void onPageFinished(WebView view, String url)
		{
			mLuaWebViewClient.onPageFinished(view, url);
		}

		public void onLoadResource(WebView view, String url)
		{
			mLuaWebViewClient.onLoadResource(view, url);
		}

		public WebResourceResponse shouldInterceptRequest(WebView view, String url)
		{
			return mLuaWebViewClient.shouldInterceptRequest(view, url);
		}

		@Deprecated
		public void onTooManyRedirects(WebView view, Message cancelMsg,
									   Message continueMsg)
		{
			cancelMsg.sendToTarget();
		}

		public void onReceivedError(WebView view, int errorCode,
									String description, String failingUrl)
		{
			mLuaWebViewClient.onReceivedError(view, errorCode, description, failingUrl);
		}

		public void onFormResubmission(WebView view, Message dontResend,
									   Message resend)
		{
			dontResend.sendToTarget();
		}

		public void doUpdateVisitedHistory(WebView view, String url,
										   boolean isReload)
		{
			mLuaWebViewClient.doUpdateVisitedHistory(view, url, isReload);
		}

		public void onReceivedSslError(WebView view, SslErrorHandler handler,
									   SslError error)
		{
			handler.cancel();
		}

		public void onProceededAfterSslError(WebView view, SslError error)
		{
			mLuaWebViewClient.onProceededAfterSslError(view, error);
		}

		public void onReceivedClientCertRequest(WebView view,
												ClientCertRequest handler, String host_and_port)
		{
			handler.cancel();
		}

		public void onReceivedHttpAuthRequest(WebView view,
											  HttpAuthHandler handler, String host, String realm)
		{
			handler.cancel();
		}

		public boolean shouldOverrideKeyEvent(WebView view, KeyEvent event)
		{
			return mLuaWebViewClient.shouldOverrideKeyEvent(view, event);
		}

		public void onUnhandledKeyEvent(WebView view, KeyEvent event)
		{
			mLuaWebViewClient.onUnhandledKeyEvent(view, event);
		}

		public void onScaleChanged(WebView view, float oldScale, float newScale)
		{
			mLuaWebViewClient.onScaleChanged(view, oldScale, newScale);
		}

		public void onReceivedLoginRequest(WebView view, String realm,
										   String account, String args)
		{
			mLuaWebViewClient.onReceivedLoginRequest(view, realm, account, args);
		}
	}

	public interface LuaWebViewClient
	{

		public boolean shouldOverrideUrlLoading(WebView view, String url)


		public void onPageStarted(WebView view, String url, Bitmap favicon)


		public void onPageFinished(WebView view, String url)


		public void onLoadResource(WebView view, String url)


		public WebResourceResponse shouldInterceptRequest(WebView view,
														  String url)


		@Deprecated
		public void onTooManyRedirects(WebView view, Message cancelMsg,
									   Message continueMsg)


		// These ints must match up to the hidden values in EventHandler.
		/** Generic error */
		public static final int ERROR_UNKNOWN = -1;
		/** Server or proxy hostname lookup failed */
		public static final int ERROR_HOST_LOOKUP = -2;
		/** Unsupported authentication scheme (not basic or digest) */
		public static final int ERROR_UNSUPPORTED_AUTH_SCHEME = -3;
		/** User authentication failed on server */
		public static final int ERROR_AUTHENTICATION = -4;
		/** User authentication failed on proxy */
		public static final int ERROR_PROXY_AUTHENTICATION = -5;
		/** Failed to connect to the server */
		public static final int ERROR_CONNECT = -6;
		/** Failed to read or write to the server */
		public static final int ERROR_IO = -7;
		/** Connection timed out */
		public static final int ERROR_TIMEOUT = -8;
		/** Too many redirects */
		public static final int ERROR_REDIRECT_LOOP = -9;
		/** Unsupported URI scheme */
		public static final int ERROR_UNSUPPORTED_SCHEME = -10;
		/** Failed to perform SSL handshake */
		public static final int ERROR_FAILED_SSL_HANDSHAKE = -11;
		/** Malformed URL */
		public static final int ERROR_BAD_URL = -12;
		/** Generic file error */
		public static final int ERROR_FILE = -13;
		/** File not found */
		public static final int ERROR_FILE_NOT_FOUND = -14;
		/** Too many requests during this load */
		public static final int ERROR_TOO_MANY_REQUESTS = -15;

		public void onReceivedError(WebView view, int errorCode,
									String description, String failingUrl)


		public void onFormResubmission(WebView view, Message dontResend,
									   Message resend)


		public void doUpdateVisitedHistory(WebView view, String url,
										   boolean isReload)


		public void onReceivedSslError(WebView view, SslErrorHandler handler,
									   SslError error)


		public void onProceededAfterSslError(WebView view, SslError error)


		public void onReceivedClientCertRequest(WebView view,
												ClientCertRequest handler, String host_and_port)


		public void onReceivedHttpAuthRequest(WebView view,
											  HttpAuthHandler handler, String host, String realm)


		public boolean shouldOverrideKeyEvent(WebView view, KeyEvent event)


		public void onUnhandledKeyEvent(WebView view, KeyEvent event)


		public void onScaleChanged(WebView view, float oldScale, float newScale)


		public void onReceivedLoginRequest(WebView view, String realm,
										   String account, String args)

	}

	class LuaWebChromeClient extends  WebChromeClient
	{
		EditText prompt_input_field=new EditText(mContext);

		@Override
		public boolean onJsAlert(WebView view, String url, String message, final android.webkit.JsResult result)
		{
			new AlertDialog.Builder(mContext)
				.setTitle("javaScript dialog")
				.setMessage(message)
				.setPositiveButton(android.R.string.ok,
				new AlertDialog.OnClickListener()
				{
					public void onClick(DialogInterface dialog, int which)
					{
						result.confirm();
					}
				})
				.setCancelable(false)
				.create()
				.show();
			return true;
		};
		@Override
		public boolean onJsConfirm(WebView view, String url,
								   String message, final JsResult result)
		{
			AlertDialog.Builder b = new AlertDialog.Builder(mContext);
			b.setTitle("Confirm");
			b.setMessage(message);
			b.setPositiveButton(android.R.string.ok,
				new AlertDialog.OnClickListener() {
					public void onClick(DialogInterface dialog,
										int which)
					{
						result.confirm();
					}
				});
			b.setNegativeButton(android.R.string.cancel,
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog,
										int which)
					{
						result.cancel();
					}
				});
			b.setCancelable(false);
			b.create();
			b.show();
			return true;
		};

		@Override
		public boolean onJsPrompt(WebView view, String url, String message,
								  String defaultValue, final JsPromptResult result)
		{
			prompt_input_field.setText(defaultValue);
			AlertDialog.Builder b = new AlertDialog.Builder(mContext);
			b.setTitle("Prompt");
			b.setMessage(message);
			b.setView(prompt_input_field);
			b.setPositiveButton(android.R.string.ok,
				new AlertDialog.OnClickListener() {
					public void onClick(DialogInterface dialog,
										int which)
					{
						String value = prompt_input_field
							.getText().toString();
						result.confirm(value);
					}
				});
			b.setNegativeButton(android.R.string.cancel,
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog,
										int which)
					{
						result.cancel();
					}
				});
			b.setOnCancelListener(new DialogInterface.OnCancelListener() {
					public void onCancel(DialogInterface dialog)
					{
						result.cancel();
					}
				});
			b.show();
			return true;
		};
		
		@Override
		public void onProgressChanged(WebView view, int newProgress)
		{
			//mContext.setProgressBarVisibility(true);
			//mContext.setProgress(newProgress * 100);
			//mContext.setSecondaryProgress(newProgress * 100);
			if(newProgress==100)
			{
				mProgressbar.setVisibility(View.GONE);
			}
			else
			{
				mProgressbar.setVisibility(View.VISIBLE);
				mProgressbar.setProgress(newProgress);
			}
			super.onProgressChanged(view, newProgress);
		}
		
		@Override
		public void onReceivedTitle(WebView view, String title)
		{
			//mContext.setTitle(title);
			super.onReceivedTitle(view, title);
		}

		@Override
		public void onReceivedIcon(WebView view, Bitmap icon)
		{
			// TODO: Implement this method
			//mContext.setIcon(new BitmapDrawable(icon));
			super.onReceivedIcon(view, icon);
		}

	}

}
