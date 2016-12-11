package com.example.ben.easytranslate;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.AsyncTaskLoader;
import android.support.v4.content.Loader;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity implements
        LoaderManager.LoaderCallbacks<String[]>,
        SharedPreferences.OnSharedPreferenceChangeListener {

    EditText phraseEditText;
    Button translateBtn;
    Spinner fromLangDropdown;
    Spinner toLangDropdown;
    ArrayAdapter<String> langsAdapter;
    String originalLangStr;
    String translatedLangStr;

    private static final String API_KEY =
            "";

    private static final String SAVED_LANGS_KEY = "langs";
    private static final String GET_LANGS_BUNDLE_KEY = "langs_url";
    private static final int GET_LANGS_LOADER = 1;
    private static final int TRANSLATE_LOADER = 2;


    //TODO: Add additional info on translation page
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        langsAdapter = new ArrayAdapter<>(this,
                R.layout.dropdown_item,
                R.id.dropdown_item_text);

        if (savedInstanceState != null) {
            if (savedInstanceState.containsKey(SAVED_LANGS_KEY)) {
                ArrayList<String> lifecycleCallbacks =
                        savedInstanceState.getStringArrayList(SAVED_LANGS_KEY);
                langsAdapter.addAll(lifecycleCallbacks);
            }
        } else {
            final String getAvailableLangsUrlString = "https://translate.yandex.net/api/v1.5/tr.json/getLangs?key="
                    + API_KEY;
            makeHttpRequestFromString(getAvailableLangsUrlString);
        }

        setContentView(R.layout.activity_main);

        phraseEditText = (EditText) findViewById(R.id.phrase_edit_text);
        fromLangDropdown = (Spinner) findViewById(R.id.translate_from);
        toLangDropdown = (Spinner) findViewById(R.id.translate_to);

        setupSharedPreferences();
        setupDropdown(toLangDropdown);
        translateBtn = (Button) findViewById(R.id.translate_btn);
        translateBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String phrase = phraseEditText.getText().toString();
                String translateUrlString = "https://translate.yandex.net/api/v1.5/tr.json/translate?key="
                        + API_KEY + "&text=" + phrase + "&lang=" + originalLangStr + translatedLangStr;

                makeHttpRequestFromString(translateUrlString);
            }
        });

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        PreferenceManager.getDefaultSharedPreferences(this).
                unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        ArrayList<String> savedLangs = new ArrayList<>();
        for (int i = 0; i < langsAdapter.getCount(); i++) {
            savedLangs.add(langsAdapter.getItem(i));
        }
        outState.putStringArrayList(SAVED_LANGS_KEY, savedLangs);

    }

    @Override
    public Loader<String[]> onCreateLoader(int id, final Bundle args) {
        return new AsyncTaskLoader<String[]>(this) {
            @Override
            protected void onStartLoading() {
                super.onStartLoading();
                if (args == null) {
                    return;
                }
            }

            @Override
            public String[] loadInBackground() {
                String getLangsUrlString = args.getString(GET_LANGS_BUNDLE_KEY);
                Log.d("###", getLangsUrlString);
                if (getLangsUrlString == null || TextUtils.isEmpty(getLangsUrlString)) {
                    return null;
                }
                try {
                    StringBuffer buffer = getRawJsonBuffer(new URL(getLangsUrlString));
                    if (buffer != null) {
                        String translationJsonString = buffer.toString();
                        return getAvailableLangsFromJson(translationJsonString);
                    }
                    return null;

                } catch (Exception e) {
                    return null;
                }
            }
        };
    }

    @Override
    public void onLoadFinished(Loader<String[]> loader, String[] data) {
        if (data != null) {
            if (data.length > 0) {
                langsAdapter.clear();
                langsAdapter.addAll(data);
            }
        }
    }

    @Override
    public void onLoaderReset(Loader<String[]> loader) {

    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(getString(R.string.lang_input_key))) {
            setupInputLangPref(sharedPreferences.getString(key,
                    getString(R.string.manual_input_key)));
        }
    }

    private void setupSharedPreferences() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        String initialInputLangPref = sharedPreferences.getString(getString(R.string.lang_input_key),
                getString(R.string.manual_input_key));
        setupInputLangPref(initialInputLangPref);
        sharedPreferences.registerOnSharedPreferenceChangeListener(this);


    }

    private void setupInputLangPref(String key) {
        switch (key) {
            case "manual":
                setupDropdown(fromLangDropdown);
                break;
            case "auto":
                ArrayAdapter<String> autoLangAdapter =
                        new ArrayAdapter<>(this,
                                R.layout.dropdown_item,
                                R.id.dropdown_item_text);
                autoLangAdapter.add(getString(R.string.auto_input_label));
                fromLangDropdown.setAdapter(autoLangAdapter);
                originalLangStr = "";

                break;
            case "location":
                String lang = Locale.getDefault().getDisplayLanguage();
                ArrayAdapter<String> locAdapter =
                        new ArrayAdapter<>(this,
                                R.layout.dropdown_item,
                                R.id.dropdown_item_text);
                locAdapter.add(lang);
                fromLangDropdown.setAdapter(locAdapter);
                originalLangStr = Locale.getDefault().getISO3Language() + "-";
                break;
        }
    }

    private void setupDropdown(final Spinner dropdown) {
        dropdown.setAdapter(langsAdapter);
        dropdown.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                String selectedLang = adapterView.getItemAtPosition(i).toString();
                String langCode = null;

                for (Locale locale : Locale.getAvailableLocales()) {
                    if (selectedLang.equals(locale.getDisplayLanguage())) {
                        langCode = locale.getISO3Language();
                        break;
                    }
                }
                if (dropdown == fromLangDropdown && langCode != null) {
                    originalLangStr = langCode + "-";
                } else if (dropdown == toLangDropdown && langCode != null) {
                    translatedLangStr = langCode;
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
                // Do nothing
            }
        });

    }

    private void   makeHttpRequestFromString(String urlStr) {
        Pattern apiCallPattern = Pattern.compile("tr.json/(.*)\\?key=");
        Matcher matcher = apiCallPattern.matcher(urlStr);
        try {
            if (matcher.find()) {
                switch (matcher.group(1)) {
                    case "getLangs":
                        LoaderManager loaderManager = getSupportLoaderManager();
                        Loader<String[]> getLangsLoader = loaderManager.getLoader(GET_LANGS_LOADER);

                        Bundle queryBundle = new Bundle();
                        queryBundle.putString(GET_LANGS_BUNDLE_KEY, urlStr);

                        if (getLangsLoader == null) {
                            loaderManager.initLoader(GET_LANGS_LOADER, queryBundle, this).forceLoad();
                        } else {
                            loaderManager.restartLoader(GET_LANGS_LOADER, queryBundle, this).forceLoad();
                        }
                        break;
                    case "translate":
                        new TranslationTask().execute(new URL(urlStr)).get();
                        break;
                    default:
                        throw new MalformedURLException(matcher.group(1));
                }
            }

        } catch (Exception e) {
            showException(e);
        }
    }

    //TODO: Move network tasks from AsyncTask


    public class TranslationTask extends AsyncTask<URL, Void, String> {

        @Override
        protected String doInBackground(URL... params){
            URL url = params[0];
            try {
                StringBuffer buffer = getRawJsonBuffer(url);
                if (buffer != null) {
                    String translationJsonString = buffer.toString();
                    return getTranslationFromJson(translationJsonString);
                }
                return null;

            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        protected void onPostExecute(String result) {
            if(result != null) {
                Intent translateIntent = new Intent(MainActivity.this, TranslationResult.class);
                translateIntent.putExtra(Intent.EXTRA_TEXT, result);
                startActivity(translateIntent);
            }
        }
    }

    private StringBuffer getRawJsonBuffer(URL url) throws IOException{
        HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
        urlConnection.setRequestMethod("GET");
        urlConnection.connect();

        BufferedReader reader = null;
        InputStream inputStream = urlConnection.getInputStream();
        StringBuffer buffer = new StringBuffer();
        if (inputStream == null) {
            // Nothing to do.
            return null;
        }
        reader = new BufferedReader(new InputStreamReader(inputStream));

        String line;
        while ((line = reader.readLine()) != null) {
            // Since it's JSON, adding a newline isn't necessary (it won't affect parsing)
            // But it does make debugging a *lot* easier if you print out the completed
            // buffer for debugging.
            buffer.append(line + "\n");
        }

        if (buffer.length() == 0) {
            // Stream was empty.  No point in parsing.
            return null;
        }

        return buffer;
    }

    //TODO: Investigate adding support for same languages from different countries
    private String[] getAvailableLangsFromJson(String jsonStr) throws JSONException {
        JSONObject json = new JSONObject(jsonStr);
        JSONArray langsArray = json.getJSONArray("dirs");
        ArrayList<String> resultList = new ArrayList<>();
        for (int i = 0; i < langsArray.length(); i++) {
            String[] countryLang = langsArray.getString(i).split("-");
            Locale locale = new Locale(countryLang[1], countryLang[0]);
            String currentLang = locale.getDisplayLanguage();
            if (!resultList.contains(currentLang)) {
                resultList.add(currentLang);
            }
        }
        Collections.sort(resultList);
        return resultList.toArray(new String[resultList.size()]);

    }

    //TODO: Possibly adding support for multiple words for the query
    private String getTranslationFromJson(String jsonStr) throws JSONException{
        JSONObject json = new JSONObject(jsonStr);
        JSONArray translationsArray = json.getJSONArray("text");
        return translationsArray.getString(0);
    }

    //TODO: Handle errors more gracefully
    private void showException(Exception e) {
        Context context = getApplicationContext();
        String text = getString(R.string.error_msg);
        int duration = Toast.LENGTH_LONG;
        Toast.makeText(context, text, duration).show();
        Log.e("Exception", e.toString());
        //resultText.setText(e.toString());
        //resultText.setTextColor(getResources().getColor(R.color.colorAccent));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int optionSelected = item.getItemId();
        if (optionSelected == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
