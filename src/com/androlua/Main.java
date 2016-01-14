package com.androlua;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.content.res.*;
import android.graphics.*;
import android.net.*;
import android.os.*;
import android.util.*;
import android.view.*;
import android.view.ViewGroup.*;
import android.widget.*;
import com.luajava.*;
import dalvik.system.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;
import java.util.zip.*;

public class Main extends Activity implements LuaBroadcastReceiver.OnReceiveListerer
{

	private LuaState L;
	private String luaPath;
	public String luaDir;

	private StringBuilder toastbuilder = new StringBuilder();
	private Boolean isCreate = false;

	public Handler handler;

	private Toast toast;
	public TextView status;
	private LinearLayout layout;

	private boolean isSetViewed;

	private long lastShow;

	private Menu optionsMenu;

	private LuaObject mOnKeyDown;

	private LuaObject mOnKeyUp;

	private LuaObject mOnKeyLongPress;

	private LuaObject mOnTouchEvent;

	private ArrayList<LuaThread> threadList=new ArrayList<LuaThread>();

	private ExecutorService mThreadPool;

	public String luaCpath;
	private boolean mInAsset=false;

	private String extDir;

	private String odexDir;

	private String libDir;

	private String luaExtDir;

	private LuaBroadcastReceiver mReceiver;

	public String getLuaExtDir()
	{
		return luaExtDir;
	}
	
	public String getLuaExtDir(String name)
	{
		File dir=new File(luaExtDir + "/" + name);
		if(!dir.exists())
			if(!dir.mkdirs())
				return null;
		return dir.getAbsolutePath();
	}
	
	public String getLuaDir()
	{
		return luaDir;
	}

	public String getLuaDir(String name)
	{
		File dir=new File(luaDir + "/" + name);
		if(!dir.exists())
			if(!dir.mkdirs())
				return null;
		return dir.getAbsolutePath();
	}
	
	@Override
	public void onCreate(Bundle savedInstanceState)
	{//startForeground(1, new Notification()); 
		setTheme(android.R.style.Theme_Holo_Light_NoActionBar);
		//s=android.R.style.Theme_Holo_Wallpaper_NoTitleBar;
		//设置主题
//		Intent intent=getIntent();
//		int theme=intent.getIntExtra("theme", android.R.style.Theme_Holo_Light_NoActionBar);
//		setTheme(theme);


		//设置print界面
		super.onCreate(savedInstanceState);
		layout = new LinearLayout(this);
		layout.setBackgroundColor(Color.WHITE);
		ScrollView scroll=new ScrollView(this);
		scroll.setFillViewport(true);
		status = new TextView(this);
		status.setTextColor(Color.BLACK);
		scroll.addView(status, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
		layout.addView(scroll, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
		status.setText("");
		status.setTextIsSelectable(true);
		//初始化AndroLua工作目录
		if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED))
		{
			String sdDir = Environment.getExternalStorageDirectory().getAbsolutePath();
			luaExtDir = sdDir + "/AndroLua";
		}
		else
		{
			File[] fs= new File("/storage").listFiles();
			for (File f:fs)
			{
				String[] ls=f.list();
				if (ls == null)
					continue;
				if (ls.length > 5)
					luaExtDir = f.getAbsolutePath() + "/AndroLua";
			}
			if (luaExtDir == null)
				luaExtDir = getDir("AndroLua", Context.MODE_PRIVATE).getAbsolutePath();
		}
		FragmentManager fm=getFragmentManager();
		FragmentTransaction ft=fm.beginTransaction();
		ft.add(1,new Fragment());
		File destDir = new File(luaExtDir);
		if (!destDir.exists())
			destDir.mkdirs();

		//定义文件夹
		extDir = getFilesDir().getAbsolutePath();
		odexDir = getDir("odex", Context.MODE_PRIVATE).getAbsolutePath();
		libDir = getDir("lib", Context.MODE_PRIVATE).getAbsolutePath();
		luaCpath = getApplicationInfo().nativeLibraryDir + "/lib?.so" + ";" + libDir + "/lib?.so";


		handler = new MainHandler();

		try
		{
			status.setText("");
			Intent intent=getIntent();
			Uri uri=intent.getData();
			Object[] arg=(Object[]) intent.getSerializableExtra("arg");
			if (arg == null)
				arg = new Object[0];
			String path = null;
			if (uri != null)
			{
				path = uri.getPath();
				if (path.indexOf("/android_asset") == 0)
				{
					initLua();
					path = path.substring("/android_asset/".length(), path.length());
					mInAsset = true;
				}
				else
				{
					luaPath = path;
					luaDir = luaPath.substring(0, luaPath.lastIndexOf("/"));
				}
			}
			else
			{
				path = extDir + "/main.lua";
				luaPath = path;
				luaDir = extDir;
			}
			initLua();
			checkInfo();

			if (mInAsset && path != null)
			{
				doAsset(path, arg);
			}
			else if (path != null)
			{
				doFile(path, arg);
			}
			else
			{
				mInAsset = true;
				doAsset("main", arg);
			}


			isCreate = true;
			runFunc("onCreate", savedInstanceState);
			if (!isSetViewed)
				setContentView(layout);
		} 
		catch (Exception e)
		{
			sendMsg(e.getMessage());
			setContentView(layout);
			return;
		}

		mOnKeyDown = L.getLuaObject("onKeyDown");
		if (mOnKeyDown.isNil())
			mOnKeyDown = null;
		mOnKeyUp = L.getLuaObject("onKeyUp");
		if (mOnKeyUp.isNil())
			mOnKeyUp = null;
		mOnKeyLongPress = L.getLuaObject("onKeyLongPress");
		if (mOnKeyLongPress.isNil())
			mOnKeyLongPress = null;
		mOnTouchEvent = L.getLuaObject("onTouchEvent");
		if (mOnTouchEvent.isNil())
			mOnTouchEvent = null;

	}

	public void checkInfo()
	{
		try
		{
			PackageInfo packageInfo=getPackageManager().getPackageInfo(this.getPackageName(), 0);
			long lastTime=packageInfo.lastUpdateTime;
			String versionName=packageInfo.versionName;
			SharedPreferences info=getSharedPreferences("appInfo", 0);
			long oldLastTime=info.getLong("lastUpdateTime", 0);
			if (oldLastTime != lastTime)
			{
				SharedPreferences.Editor edit=info.edit();
				edit.putLong("lastUpdateTime", lastTime);
				edit.commit();
				onUpdata(lastTime, oldLastTime);
			}
			String oldVersionName=info.getString("versionName", "");
			if (!versionName.equals(oldVersionName))
			{
				SharedPreferences.Editor edit=info.edit();
				edit.putString("versionName", versionName);
				edit.commit();
				onVersionChanged(versionName, oldVersionName);
			}
		}
		catch (PackageManager.NameNotFoundException e)
		{

		}

	}


	private void onVersionChanged(String versionName, String oldVersionName)
	{
		// TODO: Implement this method
		runFunc("onVersionChanged", versionName, oldVersionName);
	}

	private void onUpdata(long lastTime, long oldLastTime)
	{
		try
		{
			unApk("assets");
		}
		catch (IOException e)
		{
			sendMsg(e.getMessage());
		}
	}

	private void unApk(String dir) throws IOException
	{
		int i=dir.length() + 1;
		ZipFile zip=new ZipFile(getApplicationInfo().publicSourceDir);
		Enumeration<? extends ZipEntry> entries=zip.entries();
		while (entries.hasMoreElements())
		{
			ZipEntry entry=entries.nextElement();
			String name=entry.getName();
			if (name.indexOf(dir) != 0)
				continue;
			String path=name.substring(i);
			if (entry.isDirectory())
			{
				File f=new File(extDir + File.separator + path);
				if (!f.exists())
					f.mkdirs();
			}
			else
			{
				File temp=new File(extDir + File.separator + path).getParentFile();
                if (!temp.exists())
				{
                    if (!temp.mkdirs())
					{
                        throw new RuntimeException("create file " + temp.getName() + " fail");
                    }
                }
				
				FileOutputStream out=new FileOutputStream(extDir + File.separator + path);
				InputStream in=zip.getInputStream(entry);
				byte[] buf=new byte[4096];
				int count=0;
				while ((count = in.read(buf)) != -1)
				{
					out.write(buf, 0, count);
				}
				out.close();
				in.close();
			}
		}
		zip.close();
	}


	private boolean unAssets(String dir) throws IOException
	{
		// TODO: Implement this method
		AssetManager am=getAssets();

		String[] list=am.list(dir);
		if (list.length == 0)
			return false;

		File d=new File(extDir + "/" + dir);
		if (!d.exists())
			d.mkdirs();

		for (String name:list)
		{
			String path;
			if (dir.length() == 0)
			{
				path = name;
				if (path.equals("images") || path.equals("sounds") || path.equals("webkit"))
					continue;
			}
			else
				path = dir + "/" + name;
			//images、sounds、webkit
			if (unAssets(path))
				continue;
			if (name.lastIndexOf(".so") > 1)
			{
				assetsToSD(path, libDir + "/" + path);
			}
			else
			{
				assetsToSD(path, extDir + "/" + path);
			}

		}
		return true;
	}


	/** 
	 * 解压Assets中的文件 
	 * @param context上下文对象 
	 * @param assetName压缩包文件名 
	 * @param outputDirectory输出目录 
	 * @throws IOException 
	 */
	public void unZipAssets(String assetName, String outputDirectory) throws IOException
	{  
		//创建解压目标目录  
		File file = new File(outputDirectory);  
		//如果目标目录不存在，则创建  
		if (!file.exists())
		{  
			file.mkdirs();  
		}  
		InputStream inputStream = null;  
		//打开压缩文件  
		inputStream = this.getAssets().open(assetName);  
		ZipInputStream zipInputStream = new ZipInputStream(inputStream);  
		//读取一个进入点  
		ZipEntry zipEntry = zipInputStream.getNextEntry();  
		//使用1Mbuffer  
		byte[] buffer = new byte[1024 * 32];  
		//解压时字节计数  
		int count = 0;  
		//如果进入点为空说明已经遍历完所有压缩包中文件和目录  
		while (zipEntry != null)
		{  
			//如果是一个目录  
			if (zipEntry.isDirectory())
			{  
				//String name = zipEntry.getName();  
				//name = name.substring(0, name.length() - 1);  
				file = new File(outputDirectory + File.separator + zipEntry.getName());  
				file.mkdir();  
			}
			else
			{  
				//如果是文件  
				file = new File(outputDirectory + File.separator  
								+ zipEntry.getName());  
				//创建该文件  
				file.createNewFile();  
				FileOutputStream fileOutputStream = new FileOutputStream(file);  
				while ((count = zipInputStream.read(buffer)) > 0)
				{  
					fileOutputStream.write(buffer, 0, count);  
				}  
				fileOutputStream.close();  
			}  
			//定位到下一个文件入口  
			zipEntry = zipInputStream.getNextEntry();  
		}  
		zipInputStream.close();  
	}  


	public boolean isInAsset()
	{
		return mInAsset;
	}

	public LuaState getLuaState()
	{
		return L;
	}

	public DexClassLoader loadDex(String path)
	{
		if(path.charAt(0)!='/')
		if (isInAsset())
			path = extDir + "/" + path;
		else
			path = luaDir + "/" + path;
		return new DexClassLoader(path, odexDir, getApplicationInfo().nativeLibraryDir, getClassLoader());
	}

	public Object loadLib(String name) throws LuaException
	{
		int i=name.indexOf(".");
		String fn = name;
		if (i > 0)
			fn = name.substring(0, i);
		File f=new File(libDir + "/lib" + fn + ".so");
		if (!f.exists())
		{
			f=new File(luaDir + "/lib" + fn+".so");
			if (!f.exists())
				throw new LuaException("can not find lib "+name);
			copyFile(luaDir + "/lib" + fn+".so",libDir + "/lib" + fn+".so");
		}
		LuaObject require=L.getLuaObject("require");
		return require.call(name);
	}

	private void copyFile(String oldPath, String newPath) { 
		try { 
			int bytesum = 0; 
			int byteread = 0; 
			File oldfile = new File(oldPath); 
			if (oldfile.exists()) { //文件存在时 
				InputStream inStream = new FileInputStream(oldPath); //读入原文件 
				FileOutputStream fs = new FileOutputStream(newPath); 
				byte[] buffer = new byte[4096]; 
				int length; 
				while ( (byteread = inStream.read(buffer)) != -1) { 
					bytesum += byteread; //字节数 文件大小 
					System.out.println(bytesum); 
					fs.write(buffer, 0, byteread); 
				} 
				inStream.close(); 
			} 
		} 
		catch (Exception e) { 
			System.out.println("复制文件操作出错"); 
			e.printStackTrace(); 

		} 

	} 
	
	public Intent registerReceiver(LuaBroadcastReceiver receiver, IntentFilter filter)
	{
		// TODO: Implement this method
		return super.registerReceiver(receiver, filter);
	}

	public Intent registerReceiver(LuaBroadcastReceiver.OnReceiveListerer ltr, IntentFilter filter)
	{
		// TODO: Implement this method
		LuaBroadcastReceiver receiver=new LuaBroadcastReceiver(ltr);
		return super.registerReceiver(receiver, filter);
	}
	
	public Intent registerReceiver(IntentFilter filter)
	{
		// TODO: Implement this method
		if(mReceiver!=null)
			unregisterReceiver(mReceiver);
		mReceiver=new LuaBroadcastReceiver(this);
		return super.registerReceiver(mReceiver, filter);
	}
	
	@Override
	public void onReceive(Context context, Intent intent)
	{
		// TODO: Implement this method
		runFunc("onReceive",context,intent);
	}
	
	
	@Override
	public void onContentChanged()
	{
		// TODO: Implement this method
		super.onContentChanged();
		isSetViewed = true;
	}

	@Override
	protected void onStart()
	{
		super.onStart();
		runFunc("onStart");
	}

	@Override
	protected void onResume()
	{
		super.onResume();
		runFunc("onResume");
	}

	@Override
	protected void onPause()
	{
		super.onPause();
		runFunc("onPause");
	}

	@Override
	protected void onStop()
	{
		super.onStop();
		runFunc("onStop");
	}

	@Override
	protected void onDestroy()
	{
		for (LuaThread t:threadList)
		{
			sendMsg(t.toString() + t.isRun);
			if (t.isRun)
				t.quit();
		}
		runFunc("onDestroy");
		super.onDestroy();
		System.gc();
		L.gc(LuaState.LUA_GCCOLLECT, 1);
		//L.close();
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data)
	{
		// TODO: Implement this method
		runFunc("onActivityResult", requestCode, resultCode, data);
		super.onActivityResult(requestCode, resultCode, data);
	}


	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event)
	{
		if (mOnKeyDown != null)
		{
			try
			{
				Object ret=mOnKeyDown.call(keyCode, event);
				if (ret != null && ret.getClass() == Boolean.class && (boolean)ret)
					return true;
			}
			catch (LuaException e)
			{
				sendMsg("onKeyDown " + e.getMessage());
			}
		}
		return super.onKeyDown(keyCode, event);
	}

	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event)
	{
		if (mOnKeyUp != null)
		{
			try
			{
				Object ret=mOnKeyUp.call(keyCode, event);
				if (ret != null && ret.getClass() == Boolean.class && (boolean)ret)
					return true;
			}
			catch (LuaException e)
			{
				sendMsg("onKeyUp " + e.getMessage());
			}
		}
		return super.onKeyUp(keyCode, event);
	}

	@Override
	public boolean onKeyLongPress(int keyCode, KeyEvent event)
	{
		if (mOnKeyLongPress != null)
		{
			try
			{
				Object ret=mOnKeyLongPress.call(keyCode, event);
				if (ret != null && ret.getClass() == Boolean.class && (boolean)ret)
					return true;
			}
			catch (LuaException e)
			{
				sendMsg("onKeyLongPress " + e.getMessage());
			}
		}
		return super.onKeyLongPress(keyCode, event);
	}

	@Override
	public boolean onTouchEvent(MotionEvent event)
	{
		if (mOnTouchEvent != null)
		{
			try
			{
				Object ret=mOnTouchEvent.call(event);
				if (ret != null && ret.getClass() == Boolean.class && (boolean)ret)
					return true;
			}
			catch (LuaException e)
			{
				sendMsg("onTouchEvent " + e.getMessage());
			}
		}
		return super.onTouchEvent(event);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		// TODO: Implement this method
		optionsMenu = menu;
		runFunc("onCreateOptionsMenu", menu);
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		// TODO: Implement this method
		Object ret = null;
		if (!item.hasSubMenu())
			ret = runFunc("onOptionsItemSelected", item);
		if (ret != null && ret.getClass() == Boolean.class && (boolean)ret)
			return true;
		return super.onOptionsItemSelected(item);
	}

	public Menu getOptionsMenu()
	{
		return optionsMenu;
	}

	@Override
	public boolean onMenuItemSelected(int featureId, MenuItem item)
	{
		// TODO: Implement this method
		if (!item.hasSubMenu())
			runFunc("onMenuItemSelected", featureId, item);
		return super.onMenuItemSelected(featureId, item);
	}


	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo)
	{
		// TODO: Implement this method
		runFunc("onCreateContextMenu", menu, v, menuInfo);
		super.onCreateContextMenu(menu, v, menuInfo);
	}

	@Override
	public boolean onContextItemSelected(MenuItem item)
	{
		// TODO: Implement this method
		runFunc("onContextItemSelected", item);
		return super.onContextItemSelected(item);
	}



	public int getWidth()
	{
		return getWindowManager().getDefaultDisplay().getWidth();
	}
	public int getHeight()
	{
		return getWindowManager().getDefaultDisplay().getHeight();
	}

	public void newActivity(String path)
	{
		newActivity(1,path,null);
	}
	
	public void newActivity(String path, Object[] arg)
	{
		newActivity(1,path,arg);
	}
	
	public void newActivity(int req, String path)
	{
		newActivity(req,path,null);
	}

	public void newActivity(int req, String path, Object[] arg)
	{
		Intent intent = new Intent(this, Main.class);
		if (path.charAt(0)!='/')
			if (isInAsset())
				intent.setData(Uri.parse("file:/android_asset/" + path + ".lua"));
			else
				intent.setData(Uri.parse("file://" + luaDir + "/" + path + ".lua"));
		else
			intent.setData(Uri.parse("file://" + path));

		if(arg!=null)
			intent.putExtra("arg", arg);
		startActivityForResult(intent, req);
		overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right);
	}

	public LuaAsyncTask newTask(LuaObject func) throws LuaException
	{
		return newTask(func, null, null);
	}

	public LuaAsyncTask newTask(LuaObject func, LuaObject callback) throws LuaException
	{
		return newTask(func, null, callback);
	}

	public LuaAsyncTask newTask(LuaObject func, LuaObject update, LuaObject callback) throws LuaException
	{
		return new LuaAsyncTask(this, func, update, callback);
	}

	public LuaThread newThread(LuaObject func) throws LuaException
	{
		return newThread(func, null);
	}

	public LuaThread newThread(LuaObject func, Object[] arg) throws LuaException
	{
		LuaThread thread= new LuaThread(this, func, true, arg);
		threadList.add(thread);
		return thread;
	}

	public LuaTimer newTimer(LuaObject func) throws LuaException
	{
		return newTimer(func, null);
	}

	public LuaTimer newTimer(LuaObject func, Object[] arg) throws LuaException
	{
		return new LuaTimer(this, func, arg);
	}

	
	public Bitmap loadBitmap(String path) throws IOException
	{
		return LuaBitmap.getBitmap(this,path);
	}
	
	public void setContentView(LuaObject layout) throws LuaException
	{
		setContentView(layout,null);
	}
	
	public void setContentView(LuaObject layout, LuaObject env) throws LuaException
	{
		// TODO: Implement this method
		LuaObject loadlayout=L.getLuaObject("loadlayout");
		View view=(View) loadlayout.call(layout,env);
		super.setContentView(view);
	}
	
	
//初始化lua使用的Java函数
	private void initLua() throws Exception
	{
		L = LuaStateFactory.newLuaState();
		L.openLibs();
		L.pushJavaObject(this);
		L.setGlobal("activity");
		L.getGlobal("activity");
		L.setGlobal("this");

		L.getGlobal("luajava"); 
		L.pushString(luaExtDir);
		L.setField(-2, "luaextdir");
		L.pushString(luaDir);
		L.setField(-2, "luadir"); 
		L.pushString(luaPath);
		L.setField(-2, "luapath"); 
		L.pop(1);

		JavaFunction print = new LuaPrint(this, L);
		print.register("print");

		JavaFunction assetLoader = new LuaAssetLoader(this, L); 

		L.getGlobal("package"); 
		L.getField(-1, "loaders"); 
		int nLoaders = L.objLen(-1);
		int idx=3;
		if (isInAsset())
			idx = 2;
		for (int i=nLoaders;i >= idx;i--)
		{
			L.rawGetI(-1, i);
			L.rawSetI(-2, i + 1);
		}
		L.pushJavaFunction(assetLoader); 
		L.rawSetI(-2, idx);
		L.pop(1);          

		L.pushString(luaDir + "/?.lua;" + luaDir + "/lua/?.lua;" + luaDir + "/?/init.lua;");
		L.setField(-2, "path");
		L.pushString(luaCpath);
		L.setField(-2, "cpath");
		L.pop(1);          
		/*
		 JavaFunction task = new newLuaAsyncTask(L);
		 task.register("task");


		 JavaFunction thread = new newLuaThread(L);
		 thread.register("thread");
		 */
		JavaFunction set = new JavaFunction(L) {
			@Override
			public int execute() throws LuaException
			{
				LuaThread thread = (LuaThread) L.toJavaObject(2);

				thread.set(L.toString(3), L.toJavaObject(4));
				return 0;
			}
		};
		set.register("set");

		JavaFunction call = new JavaFunction(L) {
			@Override
			public int execute() throws LuaException
			{
				LuaThread thread = (LuaThread) L.toJavaObject(2);

				int top=L.getTop();
				if (top > 3)
				{
					Object[] args = new Object[top - 3];
					for (int i=4;i <= top;i++)
					{
						args[i - 4] = L.toJavaObject(i);
					}
					thread.call(L.toString(3), args);
				}
				else if (top == 3)
				{
					thread.call(L.toString(3));
				}

				return 0;
			};
		};
		call.register("call");

	}

//运行lua脚本
	public Object doFile(String filePath) 
	{
		return doFile(filePath,new Object[0]);
	}
	
	public Object doFile(String filePath, Object[] args) 
	{
		int ok = 0;
		try
		{
			if (filePath.charAt(0)!='/')
				if (isInAsset())
					return doAsset(filePath);
				else
					filePath=luaDir + "/" + filePath;
			
			L.setTop(0);
			ok = L.LloadFile(filePath);

			if (ok == 0)
			{
				L.getGlobal("debug");
				L.getField(-1, "traceback");
				L.remove(-2);
				L.insert(-2);
				int l=args.length;
				for (int i=0;i < l;i++)
				{
					L.pushObjectValue(args[i]);
				}
				ok = L.pcall(l, 1, -2 - l);
				if (ok == 0)
				{				
					return L.toJavaObject(-1);
				}
			}
			Intent res= new Intent();
			res.putExtra("data", L.toString(-1));
			setResult(ok, res);
			throw new LuaException(errorReason(ok) + ": " + L.toString(-1));
		} 
		catch (LuaException e)
		{			
			setTitle(errorReason(ok));
			setContentView(layout);
			sendMsg(e.getMessage());
		}

		return null;
	}

	public Object doAsset(String name, Object...args) 
	{
		int ok = 0;
		try
		{
			byte[] bytes = readAsset(name);
			L.setTop(0);
			ok = L.LloadBuffer(bytes, name);

			if (ok == 0)
			{
				L.getGlobal("debug");
				L.getField(-1, "traceback");
				L.remove(-2);
				L.insert(-2);
				int l=args.length;
				for (int i=0;i < l;i++)
				{
					L.pushObjectValue(args[i]);
				}
				ok = L.pcall(l, 0, -2 - l);
				if (ok == 0)
				{				
					return L.toJavaObject(-1);
				}
			}
			throw new LuaException(errorReason(ok) + ": " + L.toString(-1));
		} 
		catch (Exception e)
		{			
			setTitle(errorReason(ok));
			setContentView(layout);
			sendMsg(e.getMessage());
		}

		return null;
	}

//运行lua函数
	public Object runFunc(String funcName, Object...args)
	{
		if (L != null)
		{
			try
			{
				L.setTop(0);
				L.getGlobal(funcName);
				if (L.isFunction(-1))
				{
					L.getGlobal("debug");
					L.getField(-1, "traceback");
					L.remove(-2);
					L.insert(-2);

					int l=args.length;
					for (int i=0;i < l;i++)
					{
						L.pushObjectValue(args[i]);
					}

					int ok = L.pcall(l, 1, -2 - l);
					if (ok == 0)
					{				
						return L.toJavaObject(-1);
					}
					throw new LuaException(errorReason(ok) + ": " + L.toString(-1));
				}
			}
			catch (LuaException e)
			{
				sendMsg(funcName + " " + e.getMessage());
			}
		}	
		return null;
	}



//运行lua代码
	public Object doString(String funcSrc, Object... args)
	{
		try
		{
			L.setTop(0);
			int ok = L.LloadString(funcSrc);

			if (ok == 0)
			{
				L.getGlobal("debug");
				L.getField(-1, "traceback");
				L.remove(-2);
				L.insert(-2);

				int l=args.length;
				for (int i=0;i < l;i++)
				{
					L.pushObjectValue(args[i]);
				}

				ok = L.pcall(l, 1, -2 - l);
				if (ok == 0)
				{				
					return L.toJavaObject(-1);
				}
			}
			throw new LuaException(errorReason(ok) + ": " + L.toString(-1)) ;
		}
		catch (LuaException e)
		{
			sendMsg(e.getMessage());
		}
		return null;
	}


//生成错误信息
	private String errorReason(int error)
	{
		switch (error)
		{
			case 6:
				return "error error";
			case 5:
				return "GC error";
			case 4:
				return "Out of memory";
			case 3:
				return "Syntax error";
			case 2:
				return "Runtime error";
			case 1:
				return "Yield error";
		}
		return "Unknown error " + error;
	}

//读取asset文件

	public byte[] readAsset(String name) throws IOException 
	{
		AssetManager am = getAssets();
		InputStream is = am.open(name);
		byte[] ret= readAll(is);
		is.close();
		//am.close();
		return ret;
	}

	private static byte[] readAll(InputStream input) throws IOException 
	{
		ByteArrayOutputStream output = new ByteArrayOutputStream(4096);
		byte[] buffer = new byte[4096];
		int n = 0;
		while (-1 != (n = input.read(buffer)))
		{
			output.write(buffer, 0, n);
		}
		byte[] ret= output.toByteArray();
		output.close();
		return ret;
	}

//复制asset文件到sd卡
	public void assetsToSD(String InFileName, String OutFileName) throws IOException 
	{  
		InputStream myInput;  
		OutputStream myOutput = new FileOutputStream(OutFileName);  
		myInput = this.getAssets().open(InFileName);  
		byte[] buffer = new byte[8192];  
		int length = myInput.read(buffer);
        while (length > 0)
        {
			myOutput.write(buffer, 0, length); 
			length = myInput.read(buffer);
		}

        myOutput.flush();  
		myInput.close();  
		myOutput.close();        
	}  

//显示信息
	public void sendMsg(String msg)
	{
		Message message = new Message();
		Bundle bundle = new Bundle();  
		bundle.putString("data", msg);  
		message.setData(bundle);  
		message.what = 0;
		handler.sendMessage(message);
		Log.d("lua", msg);
	}


//显示toast
	public void showToast(String text)
	{   
		long now=System.currentTimeMillis();
        if (toast == null || now - lastShow > 1000)
		{ 
			toastbuilder.setLength(0);
            toast = Toast.makeText(this, text, 1000);    
			toastbuilder.append(text);
		}
		else
		{    
			toastbuilder.append("\n");
			toastbuilder.append(text);
			toast.setText(toastbuilder.toString());      
            toast.setDuration(1000);           
		}    
		lastShow = now;
		toast.show();
    } 

	private void setField(String key, Object value)
	{
		try
		{
			L.pushObjectValue(value);
			L.setGlobal(key);
		}
		catch (LuaException e)
		{
			sendMsg(e.getMessage());
		}
	}

	public void call(String func)
	{
		push(2, func);

	}

	public void call(String func, Object[] args)
	{
		if (args.length == 0)
			push(2, func);
		else
			push(3, func, args);
	}

	public void set(String key, Object value)
	{
		push(1, key, new Object[]{ value});
	}

	public Object get(String key) throws LuaException
	{
		L.getGlobal(key);
		return L.toJavaObject(-1);
	}

	public void push(int what, String s)
	{
		Message message = new Message();
		Bundle bundle = new Bundle();
		bundle.putString("data", s);
		message.setData(bundle);  
		message.what = what;

		handler.sendMessage(message);

	}

	public void push(int what, String s, Object[] args)
	{
		Message message = new Message();
		Bundle bundle = new Bundle();
		bundle.putString("data", s);
		bundle.putSerializable("args", args);
		message.setData(bundle);  
		message.what = what;

		handler.sendMessage(message);

	}





	public class MainHandler extends Handler
	{
		@Override 
		public void handleMessage(Message msg)
		{ 
			super.handleMessage(msg); 
			switch (msg.what)
			{
				case 0:
					{

						String data = msg.getData().getString("data");
//							Toast.makeText(Main.this, data , Toast.LENGTH_SHORT).show();
						showToast(data);
//							msgbuilder.append(data);
//							msgbuilder.append("\n");
//							status.setText(msgbuilder.toString());
						status.append(data + "\n");
					}
					break;
				case 1:
					{
						Bundle data=msg.getData();
						setField(data.getString("data"), ((Object[])data.getSerializable("args"))[0]);
					}
					break;
				case 2:
					{
						String src=msg.getData().getString("data");
						runFunc(src);
					}
					break;
				case 3:
					{
						String src=msg.getData().getString("data");
						Serializable args=msg.getData().getSerializable("args");
						runFunc(src, (Object[])args);
					}
			}
		}
	}
}
