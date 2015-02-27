package map.kll.org.yatayatdemo;

import org.mapsforge.core.graphics.Style;
import org.mapsforge.core.model.MapPosition;
import org.mapsforge.map.android.graphics.AndroidGraphicFactory;
import org.mapsforge.map.android.view.MapView;
import org.mapsforge.map.layer.cache.TileCache;
import org.mapsforge.map.android.util.AndroidUtil;
import org.mapsforge.map.layer.renderer.TileRendererLayer;
import org.mapsforge.core.model.Point;
import org.mapsforge.core.model.LatLong;
import org.mapsforge.map.rendertheme.InternalRenderTheme;
import org.mapsforge.map.layer.Layers;
import org.mapsforge.map.layer.overlay.Marker;
import org.mapsforge.core.graphics.Bitmap;
import org.mapsforge.core.graphics.Paint;
import org.mapsforge.map.layer.overlay.Polyline;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.util.PointList;
import com.graphhopper.util.StopWatch;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;

import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Environment;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import java.io.File;
import java.util.List;


public class MainActivity extends Activity implements  AsyncTaskCompleteListener {

    private MapView mapView;
    private TileCache tileCache;
    private File mapsFolder;
    private String downloadUrl = "https://dl.dropboxusercontent.com/u/95497883/kathmandu-gh.zip";
    private GraphHopper hopper;
    private LatLong start;
    private LatLong end;


    private volatile boolean shortestPathRunning = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AndroidGraphicFactory.createInstance(getApplication());
        mapView = new MapView(this);
        mapView.setClickable(true);
        mapView.setBuiltInZoomControls(true);
        tileCache = AndroidUtil.createTileCache(this,getClass().getSimpleName(),mapView.getModel().displayModel.getTileSize(),1f,
                mapView.getModel().frameBufferModel.getOverdrawFactor());
        boolean greaterOrEqKitkat = Build.VERSION.SDK_INT>=19;
        if (greaterOrEqKitkat){
            if(!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)){
                logUser("No External Storage Detected");
                return;
            }
            mapsFolder= new File(Environment.getExternalStorageDirectory(),
                    "/yatayat/maps/kathmandu-gh" ) ;
        }else
            mapsFolder = new File(Environment.getExternalStorageDirectory(),"/yatayat/maps/kathmandu-gh");
        if (!mapsFolder.exists())
            mapsFolder.mkdirs();
           loadMap(mapsFolder);
        }

    private void downloadData() {

            Log.i("Downloading", "yes");
            if (!isInternetAvailable()) {
                Toast.makeText(
                        getBaseContext(),
                        "Internet not available, Make sure you are connected to internet",
                        Toast.LENGTH_LONG).show();

            }

            if (!isWifiOn()) {
                if (isMobileDataOn()) {
                    buildAlertMessageUsingDataNetwork();

                }
            }

            final DownloadData downloadTask = new DownloadData(
                    MainActivity.this);
            downloadTask
                    .execute(downloadUrl);


    }

    private void loadMap(File mapsFolder) {
        logUser("Loading Map");
        File mapFile =new File(mapsFolder,"kathmandu.map");
        if(!mapFile.exists()){
            downloadData();
        }
            mapView.getLayerManager().getLayers().clear();
            TileRendererLayer tileRendererLayer = new TileRendererLayer(tileCache, mapView.getModel().mapViewPosition,
                    true, true, AndroidGraphicFactory.INSTANCE) {
                @Override
                public boolean onLongPress(LatLong tapLatLong, Point layerXY, Point tapXY) {
                    return onMapTap(tapLatLong, layerXY, tapXY);
                }

            };
            tileRendererLayer.setMapFile(mapFile);
            tileRendererLayer.setTextScale(1.5f);
            tileRendererLayer.setXmlRenderTheme(InternalRenderTheme.OSMARENDER);
            mapView.getModel().mapViewPosition.setMapPosition(new MapPosition(tileRendererLayer.getMapDatabase().getMapFileInfo().boundingBox.getCenterPoint(), (byte) 15));
            mapView.getLayerManager().getLayers().add(tileRendererLayer);
            setContentView(mapView);
            loadGraphStorage();


    }

    private boolean onMapTap(LatLong tapLatLong, Point layerXY, Point tapXY) {
        if (!isReady())
            return false;
        if(shortestPathRunning)
        {
            logUser("Calculation still in progress");
            return false;
        }
        Layers layers = mapView.getLayerManager().getLayers();
        if (start !=null && end == null)
        {
            end = tapLatLong;
            shortestPathRunning = true;
            Marker marker = createMarker(tapLatLong,R.drawable.flag_red);
            if(marker !=null)
            {
                layers.add(marker);
            }
            calcPath(start.latitude,start.longitude,end.latitude,end.longitude);
        }else {
            start = tapLatLong;
            end = null;
            while(layers.size()>1)
            {
                layers.remove(1);
            }
            Marker marker = createMarker(start,R.drawable.flag_green);
            if (marker != null)
            {
                layers.add(marker);
            }
        }
        return true;
    }

    void loadGraphStorage(){
        logUser("Loading Graph....");
        GraphHopper tmpHopp = new GraphHopper().forMobile();
        tmpHopp.load(Environment.getExternalStorageDirectory()
                + "/yatayat/maps/kathmandu-gh/");
        hopper = tmpHopp;
     
    }
    boolean isReady()
    {
        // only return true if already loaded
        if (hopper != null)
            return true;


       return false;
    }
    private Marker createMarker( LatLong p, int resource )
    {
        Drawable drawable = getResources().getDrawable(resource);
        Bitmap bitmap = AndroidGraphicFactory.convertToBitmap(drawable);
        return new Marker(p, bitmap, 0, -bitmap.getHeight() / 2);
    }
    private Polyline createPolyline( GHResponse response )
    {
        Paint paintStroke = AndroidGraphicFactory.INSTANCE.createPaint();
        paintStroke.setStyle(Style.STROKE);
        paintStroke.setColor(Color.argb(200,0,0xCC,0x33));
        paintStroke.setDashPathEffect(new float[]
                {
                        25,15
                });
        paintStroke.setStrokeWidth(8);

        Polyline line = new Polyline((org.mapsforge.core.graphics.Paint) paintStroke,AndroidGraphicFactory.INSTANCE);
        List<LatLong> geoPoints = line.getLatLongs();
        PointList tmp = response.getPoints();
        for (int i = 0;i< response.getPoints().getSize();i++){
            geoPoints.add(new LatLong(tmp.getLatitude(i),tmp.getLongitude(i)));
        }
        return line;
    }
    public void calcPath( final double fromLat, final double fromLon,
                          final double toLat, final double toLon ) {
        float time;
        StopWatch sw = new StopWatch().start();
        GHRequest req = new GHRequest(fromLat, fromLon, toLat, toLon).
                setAlgorithm("dijkstrabi");
        req.getHint("instructions","false");
        GHResponse resp = hopper.route(req);
        time = sw.stop().getSeconds();
        if (!resp.hasErrors())
        {

            logUser("the route is " + (int) (resp.getDistance() / 100) / 10f
                    + "km long");

            mapView.getLayerManager().getLayers().add(createPolyline(resp));
            //mapView.redraw();
        } else
        {
            logUser("Error:" + resp.getErrors());
        }
        shortestPathRunning = false;
    }
        public boolean isInternetAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        if (netInfo != null && netInfo.isConnected()) {
            Log.i("Internet","True");

            return true;
        }
        return false;
    }

    public boolean isWifiOn() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        if (netInfo != null && netInfo.isConnected()) {
            Log.i("Wifi","On");
            return true;
        }
        return false;
    }

    public boolean isMobileDataOn() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm
                .getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
        if (netInfo != null && netInfo.isConnected()) {
            return true;
        }
        return false;
    }
    private void buildAlertMessageUsingDataNetwork() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(
                "You are using data network, Are you sure you want to continue download")
                .setCancelable(false)
                .setPositiveButton("Yes",
                        new DialogInterface.OnClickListener() {
                            public void onClick(final DialogInterface dialog,
                                                final int id) {
                                final DownloadData downloadTask = new DownloadData(
                                        MainActivity.this);
                                downloadTask
                                        .execute(downloadUrl);

                            }
                        })
                .setNegativeButton("No", new DialogInterface.OnClickListener() {
                    public void onClick(final DialogInterface dialog,
                                        final int id) {
                        dialog.cancel();
                    }
                });
        final AlertDialog alert = builder.create();
        alert.show();
    }


    @Override
    public void onTaskComplete(String result) {
        Toast.makeText(this, result, Toast.LENGTH_LONG).show();
        loadMap(mapsFolder);
    }




    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
    private void logUser( String str )
    {
        Toast.makeText(this, str, Toast.LENGTH_LONG).show();
    }
}
