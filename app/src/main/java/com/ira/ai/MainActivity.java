package com.ira.ai;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final String SERVER = "http://127.0.0.1:5000/chat";
    private TextView log;
    private ScrollView scroll;
    private EditText input;
    private TextToSpeech tts;
    private SpeechRecognizer recognizer;
    private final Handler ui = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        buildUi();
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(new Locale("hi", "IN"));
            }
        });
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 1);
        }
        add("IRA", "Namaste! Main IRA hoon. Mic dabakar bolo ya likho.");
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 90, 24, 24);

        scroll = new ScrollView(this);
        log = new TextView(this);
        log.setTextSize(17);
        scroll.addView(log);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        input = new EditText(this);
        input.setHint("Yahan likho...");
        root.addView(input, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout row = new LinearLayout(this);
        Button send = new Button(this);
        send.setText("Bhejo");
        Button mic = new Button(this);
        mic.setText("Mic");
        row.addView(send, new LinearLayout.LayoutParams(0, -2, 1f));
        row.addView(mic, new LinearLayout.LayoutParams(0, -2, 1f));
        root.addView(row, new LinearLayout.LayoutParams(-1, -2));

        send.setOnClickListener(v -> {
            String t = input.getText().toString().trim();
            if (!t.isEmpty()) {
                input.setText("");
                ask(t);
            }
        });
        mic.setOnClickListener(v -> listen());
        setContentView(root);
    }

    private void add(String who, String text) {
        log.append(who + ": " + text + "\n\n");
        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }

    private void ask(final String msg) {
        add("Tum", msg);
        new Thread(() -> {
            String reply;
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(SERVER).openConnection();
                c.setRequestMethod("POST");
                c.setRequestProperty("Content-Type", "application/json");
                c.setConnectTimeout(10000);
                c.setReadTimeout(60000);
                c.setDoOutput(true);
                JSONObject body = new JSONObject();
                body.put("message", msg);
                try (OutputStream os = c.getOutputStream()) {
                    os.write(body.toString().getBytes("UTF-8"));
                }
                int code = c.getResponseCode();
                InputStream is = code < 400 ? c.getInputStream() : c.getErrorStream();
                BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                JSONObject j = new JSONObject(sb.toString());
                reply = j.has("reply") ? j.getString("reply") : "Error: " + sb;
            } catch (Exception e) {
                reply = "Server se connect nahi hua. Termux mein server chalu hai? ("
                        + e.getMessage() + ")";
            }
            final String r = reply;
            ui.post(() -> {
                add("IRA", r);
                tts.speak(r, TextToSpeech.QUEUE_FLUSH, null, "ira");
            });
        }).start();
    }

    private void listen() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            add("IRA", "Is phone par speech recognition available nahi hai.");
            return;
        }
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this);
            recognizer.setRecognitionListener(new RecognitionListener() {
                public void onResults(Bundle r) {
                    ArrayList<String> list =
                            r.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (list != null && !list.isEmpty()) ask(list.get(0));
                }
                public void onError(int e) {
                    add("IRA", "Sunai nahi diya, dobara try karo.");
                }
                public void onReadyForSpeech(Bundle p) { }
                public void onBeginningOfSpeech() { }
                public void onRmsChanged(float v) { }
                public void onBufferReceived(byte[] b) { }
                public void onEndOfSpeech() { }
                public void onPartialResults(Bundle p) { }
                public void onEvent(int t, Bundle p) { }
            });
        }
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN");
        recognizer.startListening(i);
    }

    @Override
    protected void onDestroy() {
        if (tts != null) tts.shutdown();
        if (recognizer != null) recognizer.destroy();
        super.onDestroy();
    }
                }
