package map.kll.org.yatayatdemo;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.PowerManager;
import android.util.Log;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;


public class DownloadData extends AsyncTask<String,Integer,String>{
    private Activity activity;
    private ProgressDialog mProgressDialog;
    private AsyncTaskCompleteListener callback;
    private Context context;

    public DownloadData(Context c) {
        this.context = c;
        Activity a = (Activity) c;
        this.activity = a;
        this.callback = (AsyncTaskCompleteListener) a;
    }
    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        mProgressDialog = new ProgressDialog(activity);
        mProgressDialog.setMessage("Downloading Data....");
        mProgressDialog.setIndeterminate(true);
        mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        mProgressDialog.setCancelable(false);
        mProgressDialog.show();
    }

    @Override
    protected String doInBackground(String... sUrl){
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,getClass().getName());
        wl.acquire();

        try{
            InputStream input = null;
            OutputStream output = null;
            HttpURLConnection connection = null;
            try{
                URL url = new URL(sUrl[0]);
               connection = (HttpURLConnection) url.openConnection();
               connection.connect();
                if (connection.getResponseCode() !=HttpURLConnection.HTTP_OK)
                    return "Server returned HTTP"
                    + connection.getResponseCode()+" "
                    + connection.getResponseMessage();
                int fileLength = connection.getContentLength();
                input = connection.getInputStream();
                output = new FileOutputStream(Environment.getExternalStorageDirectory().getPath()+"/yatayat/maps"+"/kathmandu-gh.zip");
                byte data[] = new byte[4096];
                long total = 0;
                int count;
                while ((count = input.read(data)) != -1) {
                    if (isCancelled())
                        return null;
                    total+=count;
                    if (fileLength> 0)
                        publishProgress((int)(total*100/fileLength));
                    output.write(data,0,count);
                }

                }catch (Exception e){
                return e.toString();
            }finally {
                try{
                    if(output!=null)
                        output.close();
                    if(input!=null)
                        input.close();
                }catch (IOException ignored){

                }
                if(connection!= null)
                    connection.disconnect();

            }
        }finally {
            wl.release();
        }
        return null;
    }
    @Override
    protected void onProgressUpdate(Integer... progress){
        super.onProgressUpdate(progress);
        mProgressDialog.setIndeterminate(false);
        mProgressDialog.setMax(100);
        mProgressDialog.setProgress(progress[0]);

    }
    @Override
    protected void onPostExecute(String result){
        mProgressDialog.dismiss();
        if(result !=null){
           callback.onTaskComplete("Download error:"+result);
        }
        else {

            Toast.makeText(context,"File downloaded",Toast.LENGTH_SHORT).show();
            callback.onTaskComplete("File downloaded");
            unpackZip(Environment.getExternalStorageDirectory().getPath()+"/yatayat/maps/","kathmandu-gh.zip");

        }
    }

    private boolean unpackZip(String path,String zipname){
        InputStream is;
        ZipInputStream zis;
        try{
            Log.i("Unpacking",zipname);
            String filename;
            is = new FileInputStream(path+zipname);
            zis = new ZipInputStream(new BufferedInputStream(is));
            ZipEntry ze;
            byte[] buffer = new byte[1024];
            int count;

            while((ze = zis.getNextEntry()) != null){
                filename = ze.getName();
                if(ze.isDirectory()){
                    File fmd = new File(path + filename);
                    fmd.mkdirs();
                    continue;
                }
                FileOutputStream fout = new FileOutputStream(path + filename);
                while ((count = zis.read(buffer)) != -1){
                    fout.write(buffer,0,count);
                }
                fout.close();
                zis.closeEntry();
            }
            zis.close();
        }catch (IOException e){
            e.printStackTrace();
            return false;

        }
        return true;

    }
}
