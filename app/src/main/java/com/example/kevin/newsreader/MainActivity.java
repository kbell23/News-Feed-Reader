package com.example.kevin.newsreader;

import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.os.AsyncTask;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringBufferInputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    ArrayList<String> titles = new ArrayList<String>();
    ArrayList<String> content = new ArrayList<String>();

    ArrayAdapter arrayAdapter;

    SQLiteDatabase articlesDatabase;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        articlesDatabase = this.openOrCreateDatabase("Articles", MODE_PRIVATE, null);

        articlesDatabase.execSQL("CREATE TABLE IF NOT EXISTS articles(id INTEGER PRIMARY KEY, " +
                "articleID, INTEGER, title VARCHAR, content VARCHAR)");


        DownloadData task = new DownloadData();

        try{
            task.execute("https://hacker-news.firebaseio.com/v0/topstories.json?print=pretty");
        }catch (Exception e){
            e.printStackTrace();
        }

        ListView listView = (ListView)findViewById(R.id.listView);
        arrayAdapter = new ArrayAdapter(this, android.R.layout.simple_list_item_1, titles);
        listView.setAdapter(arrayAdapter);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

                Intent intent = new Intent(getApplicationContext(), ArticleActivity.class);
                intent.putExtra("content", content.get(position));

                startActivity(intent);
            }
        });

        updateListView();
    }

    // gathers information from the database and updates the listview
    public void updateListView(){
        Cursor c = articlesDatabase.rawQuery("SELECT * FROM articles", null);

        int contentIndex = c.getColumnIndex("content");
        int titleIndex = c.getColumnIndex("title");

        if(c.moveToFirst()) {
            titles.clear();
            content.clear();

            do{

                titles.add(c.getString(titleIndex));
                content.add(c.getString(contentIndex));
            }while(c.moveToNext());

            arrayAdapter.notifyDataSetChanged();
        }

    }

    /* class to establish connection to the Internet, gather the urls from the JSON data
       we've collected, and store the information into a result string.
     */
    public class DownloadData extends AsyncTask<String, Void, String>{

        @Override
        protected String doInBackground(String... urls) {
            String result = "";

            URL url;
            HttpURLConnection urlConnection = null;
            // establishes connection
            try{

                url = new URL(urls[0]);

                urlConnection = (HttpURLConnection) url.openConnection();

                InputStream inputStream = urlConnection.getInputStream();

                InputStreamReader reader = new InputStreamReader(inputStream);

                int data = reader.read();

                while(data != -1){

                    char current = (char)data;
                    result += current;
                    data  = reader.read();
                }

                // creates a new JSONArray so we can loop through the
                // keys we gather from hacker news
                JSONArray jsonArray = new JSONArray(result);

                int numofItems = 20;

                if (jsonArray.length() < 20){
                    numofItems = jsonArray.length();
                }

                articlesDatabase.execSQL("DELETE FROM articles");

                for (int i = 0; i < numofItems; i++){

                    // grabs the article we want
                    String articleID = jsonArray.getString(i);
                    url = new URL("https://hacker-news.firebaseio.com/v0/item/" + articleID +
                            ".json?print=pretty");

                    urlConnection = (HttpURLConnection) url.openConnection();

                    inputStream = urlConnection.getInputStream();

                    reader = new InputStreamReader(inputStream);

                    data = reader.read();

                    String articleInfo = "";

                    while(data != -1){

                        char current = (char)data;
                        articleInfo += current;
                        data  = reader.read();
                    }

                    JSONObject jsonObject = new JSONObject(articleInfo);

                    if (!jsonObject.isNull("title") && !jsonObject.isNull("url")) {
                        String articleTitle = jsonObject.getString("title");
                        String articleUrl = jsonObject.getString("url");

                        url = new URL(articleUrl);

                        urlConnection = (HttpURLConnection)url.openConnection();
                        inputStream = urlConnection.getInputStream();
                        reader = new InputStreamReader(inputStream);
                        data = reader.read();
                        String articleContent = "";

                        while(data != -1){
                            char current = (char)data;
                            articleContent += current;
                            data = reader.read();
                        }

                        Log.i("HTML", articleContent);

                        // now that we have everything we need, store the data
                        String sql = "INSERT INTO articles (articleID, title, content) " +
                                "VALUES(?, ?, ?)";
                        /* create a statement to protect the database in case we put something in
                        the database that trys to corrupt the data*/
                        SQLiteStatement statement = articlesDatabase.compileStatement(sql);
                        statement.bindString(1, articleID);
                        statement.bindString(2, articleTitle);
                        statement.bindString(3, articleContent);

                        statement.execute();

                    }
                }

                Log.i("URL Content:", result);
                return result;

            }catch(Exception e){
                e.printStackTrace();
            }


            return null;
        }

        @Override
        protected void onPostExecute(String s) {
            super.onPostExecute(s);
            updateListView();
        }
    }
}
