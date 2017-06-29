package com.zeykit.dev.cryptomarketcap;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.MenuItemCompat;
import android.support.v4.view.ViewCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.Toolbar;
import android.text.Html;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.LinearLayout;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements SearchView.OnQueryTextListener {

    private final String _TAG = "CryptoMarketCap";

    private interface PERMISSIONS {
        int REQUEST_NETWORK_STATE = 0x1;
        int REQUEST_INTERNET = 0x2;
    }

    private boolean isRunning = false;
    private boolean canRefresh = false;
    private boolean isFirstLaunch = true;

    private LinearLayout activityMain;
    private Toolbar mToolbar;
    RecyclerView recyclerView;
    private List<CryptoAdapter> cryptoAdapterList;
    private CryptoRvAdapter adapter;
    SwipeRefreshLayout swipeRefreshLayout;

    private interface TAG {
        String NAME = "name";
        String SYMBOL = "symbol";
        String PRICE_USD = "price_usd";
        String PRICE_EUR = "price_eur";
        String PRICE_GBP = "price_gbp";
        String PERCENT_CHANGE_24H = "percent_change_24h";
    }

    private SharedPreferences sharedPreferences = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        init();
        setupToolbar();
        setupRecyclerView();

        swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                cryptoAdapterList.clear();
                new JSONParse().execute();
            }
        });
    }

    /**
     * Elements initialization
     */
    private void init() {
        activityMain = (LinearLayout) findViewById(R.id.activity_main);
        mToolbar = (Toolbar) findViewById(R.id.toolbar);
        swipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.swipeRefreshLayout);
        recyclerView = (RecyclerView) findViewById(R.id.recyclerView);
    }

    /**
     * Settings up the toolBar
     */
    private void setupToolbar() {
        ViewCompat.setElevation(mToolbar, 10);
        setSupportActionBar(mToolbar);
    }

    /**
     * Settings up the recyclerView
     */
    private void setupRecyclerView() {
        recyclerView.setLayoutManager(new WrapContentLinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));

        cryptoAdapterList = new ArrayList<>();

        adapter = new CryptoRvAdapter(cryptoAdapterList);
        recyclerView.setAdapter(adapter);
    }

    @Override
    public void onStart() {
        super.onStart();

        isRunning = true;
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

        // Check for permissions
        if (ContextCompat.checkSelfPermission(MainActivity.this,
                Manifest.permission.ACCESS_NETWORK_STATE) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.ACCESS_NETWORK_STATE)) {
                showNetworkStateDialog();
                Log.d(_TAG, "User have already denied the network state permission");
            } else {
                ActivityCompat.requestPermissions(this,
                        new String[] { Manifest.permission.ACCESS_NETWORK_STATE },
                        PERMISSIONS.REQUEST_NETWORK_STATE);
                Log.d(_TAG, "Requesting for network state permission");
            }
        } else {
            if (ContextCompat.checkSelfPermission(MainActivity.this,
                    Manifest.permission.INTERNET) != PackageManager.PERMISSION_GRANTED) {
                if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                        Manifest.permission.INTERNET)) {
                    showInternetDialog();
                    Log.d(_TAG, "User have already denied the Internet permission");
                } else {
                    ActivityCompat.requestPermissions(this,
                            new String[] { Manifest.permission.INTERNET },
                            PERMISSIONS.REQUEST_INTERNET);
                    Log.d(_TAG, "Requesting for Internet permission");
                }
            } else {
                if (haveNetworkConnection()) {
                    if (isFirstLaunch) {
                        new JSONParse().execute();
                        isFirstLaunch = false;
                    }
                } else {
                    showConnectionDialog();
                }
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case PERMISSIONS.REQUEST_NETWORK_STATE: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(_TAG, "Network state permission granted");
                } else {
                    showNetworkStateDialog();
                    Log.d(_TAG, "Network state permission denied");
                }
            }
            case PERMISSIONS.REQUEST_INTERNET: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(_TAG, "Internet permission granted");
                } else {
                    showInternetDialog();
                    Log.d(_TAG, "Internet permission denied");
                }
            }

        }
    }

    @Override
    public void onResume() {
        super.onResume();

        Log.d(_TAG, autoRefreshEnabled() + " > " + autoRefreshDelay() + "s");

        // Check for user preferences
        if (autoRefreshEnabled()) {
            handler.removeCallbacks(autoRefresh);
            handler.post(autoRefresh);
        } else {
            handler.removeCallbacks(autoRefresh);
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        isRunning = false;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu, menu);

        final MenuItem item = menu.findItem(R.id.action_search);
        final SearchView searchView = (SearchView) MenuItemCompat.getActionView(item);

        // Setting up the searchView style
        searchView.setQueryHint(Html.fromHtml("<font color = #373839>" + getResources().getString(R.string.search_hint) + "</font>"));
        SearchView.SearchAutoComplete textArea = (SearchView.SearchAutoComplete) searchView.findViewById(android.support.v7.appcompat.R.id.search_src_text);
        textArea.setTextColor(Color.WHITE);
        searchView.setOnQueryTextListener(this);

        MenuItemCompat.setOnActionExpandListener(item,
                new MenuItemCompat.OnActionExpandListener() {
                    @Override
                    public boolean onMenuItemActionExpand(MenuItem menuItem) {
                        return true;
                    }

                    @Override
                    public boolean onMenuItemActionCollapse(MenuItem menuItem) {
                        adapter.setFilter(cryptoAdapterList);
                        return true;
                    }
                });

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_about:
                AboutDialog aboutDialog = new AboutDialog();
                aboutDialog.show(getSupportFragmentManager(), null);
                break;
            case R.id.action_settings:
                Intent intent = new Intent(getApplicationContext(), SettingsActivity.class);
                startActivity(intent);
                break;
            default:
                return super.onOptionsItemSelected(item);
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Auto refresh method
     */
    Handler handler = new Handler();
    Runnable autoRefresh = new Runnable() {
        @Override
        public void run() {
            String autoRefreshDelay = autoRefreshDelay() + "000";
            int autoRefreshDelayToInt = Integer.parseInt(autoRefreshDelay);
            handler.postDelayed(autoRefresh, autoRefreshDelayToInt);

            if (autoRefreshEnabled() && canRefresh) {
                Log.d(_TAG, "Refreshing...");
                new JSONParse().execute();
            } else {
                canRefresh = true;
            }
        }
    };

    @Override
    public boolean onQueryTextChange(String newText) {
        final List<CryptoAdapter> filteredCryptoList = filter(cryptoAdapterList, newText);
        adapter.setFilter(filteredCryptoList);
        return true;
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        return false;
    }

    /**
     * Filtering recyclerView data
     * with the user's search
     * @param cryptoAdapterList list to filter
     * @param query user's search
     * @return filtered list
     */
    private List<CryptoAdapter> filter(List<CryptoAdapter> cryptoAdapterList, String query) {
        query = query.toLowerCase();
        final List<CryptoAdapter> filteredCryptoList = new ArrayList<>();
        for (CryptoAdapter crypto : cryptoAdapterList) {
            final String text = crypto.getName().toLowerCase();
            if (text.contains(query)) {
                filteredCryptoList.add(crypto);
            }
        }
        return filteredCryptoList;
    }

    private class JSONParse extends AsyncTask<String, String, String> {

        boolean connectionEnabled = haveNetworkConnection();

        private final String urlAddress = "https://api.coinmarketcap.com/v1/ticker/?limit=70";

        private ProgressDialog progressDialog;

        private CryptoAdapter cryptoAdapter = new CryptoAdapter();
        private JSONObject jObj;

        int _rank;
        private String rank;
        private String name;
        private String symbol;
        private String price;
        private String percentChange;

        JSONArray jArray;
        StringBuffer buffer;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            if (connectionEnabled) {
                progressDialog = new ProgressDialog(activityMain.getContext(), R.style.ProgressDialogTheme);
                progressDialog.setCancelable(false);
                progressDialog.setIndeterminate(true);
                progressDialog.setMessage(getString(R.string.receiving_data));

                if (isRunning) {
                    progressDialog.show();
                }

                cryptoAdapterList.clear();
                adapter.notifyDataSetChanged();
            } else {
                showConnectionDialog();
            }
        }

        /**
         * Enable connection to CoinMarketCap.com API
         * and retrieve data
         * @param params params
         * @return null
         */
        @Override
        protected String doInBackground(String... params) {

            HttpURLConnection connection;
            BufferedReader reader;

            if (connectionEnabled) {
                try {
                    URL url = new URL(urlAddress);
                    connection = (HttpURLConnection) url.openConnection();
                    connection.connect();

                    InputStream stream = connection.getInputStream();

                    reader = new BufferedReader(new InputStreamReader(stream));

                    buffer = new StringBuffer();
                    String line = "";

                    while ((line = reader.readLine()) != null) {
                        String append = line + "\n";
                        buffer.append(append);
                    }

                    connection.disconnect();
                    reader.close();
                    stream.close();

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            return null;
        }

        /**
         * Handling retrieved data
         * and setup the recycler view
         * @param result result
         */
        @Override
        protected void onPostExecute(String result) {
            if (connectionEnabled) {
                try {
                    jArray = new JSONArray(buffer.toString());

                    for (int i = 0; i < jArray.length(); i++) {
                        jObj = jArray.getJSONObject(i);

                        _rank = (i + 1);
                        rank = String.valueOf(_rank);
                        name = jObj.getString(TAG.NAME);
                        symbol = "(" + jObj.getString(TAG.SYMBOL) + ")";
                        price = "$" + jObj.getString(TAG.PRICE_USD);

                        percentChange = jObj.getString(TAG.PERCENT_CHANGE_24H) + "%";

                        cryptoAdapter = new CryptoAdapter(rank, null, name + "\n" + symbol, price, percentChange);

                        cryptoAdapterList.add(i, cryptoAdapter);

                        adapter.notifyItemInserted(i);
                        adapter.notifyDataSetChanged();
                    }
                } catch (JSONException | NullPointerException e) {
                    e.printStackTrace();
                }
            }

            if (progressDialog != null) {
                progressDialog.dismiss();
            }

            if (swipeRefreshLayout != null) {
                swipeRefreshLayout.setRefreshing(false);
            }
        }
    }

    private int getSdkVer() {
        return Build.VERSION.SDK_INT;
    }

    /**
     * @return if user have network connection
     */
    private boolean haveNetworkConnection() {
        boolean wifiConnected = false;
        boolean dataConnected = false;

        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo[] networkInfo = connectivityManager.getAllNetworkInfo();
        for (NetworkInfo ni : networkInfo) {
            if (ni.getTypeName().equalsIgnoreCase("WIFI")) {
                if (ni.isConnected()) {
                    wifiConnected = true;
                }
            } else if (ni.getTypeName().equalsIgnoreCase("MOBILE")) {
                if (ni.isConnected()) {
                    dataConnected = true;
                }
            }
        }
        return wifiConnected || dataConnected;
    }

    /**
     * Show the 'network state dialog' for requesting
     * to user to enable the Network State permission
     */
    private void showNetworkStateDialog() {
        NetworkStateDialog networkStateDialog = new NetworkStateDialog();
        networkStateDialog.show(getSupportFragmentManager(), null);
    }

    /**
     * Show the 'internet dialog' for requesting to
     * user to enable the Internet permission
     */
    private void showInternetDialog() {
        InternetDialog internetDialog = new InternetDialog();
        internetDialog.show(getSupportFragmentManager(), null);
    }

    /**
     * Show the 'connection dialog' for requesting to
     * user to enable Wi-FI or data mobile connection
     */
    private void showConnectionDialog() {
        RequestConnectionDialog requestConnectionDialog = new RequestConnectionDialog();
        requestConnectionDialog.show(getSupportFragmentManager(), null);
    }

    /**
     * @return if user want to enable the auto refresh function
     */
    private boolean autoRefreshEnabled() {
        return sharedPreferences.getBoolean("auto_refresh_switch", true);
    }

    /**
     * @return refresh delay set by user
     */
    private String autoRefreshDelay() {
        return sharedPreferences.getString("auto_refresh_delay", "60");
    }
}