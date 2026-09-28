package com.example.subtitlepad;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;

public class MainActivity extends Activity {

    private static final int PICK_SUBTITLE = 1001;

    private TextView subtitleView, timeView, fileView;
    private SeekBar seekBar;
    private Button playButton;
    private ArrayList<Subtitle> subtitles = new ArrayList<>();
    private long positionMs = 0;
    private long syncMs = 0;
    private boolean playing = false;
    private float textSizeSp = 34f;
    private long lastTick;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            long now = SystemClock.elapsedRealtime();
            if (playing) {
                positionMs += Math.max(0, now - lastTick);
                updateUi();
            }
            lastTick = now;
            handler.postDelayed(this, 50);
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        buildUi();
        lastTick = SystemClock.elapsedRealtime();
        handler.post(ticker);
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        root.setPadding(18, 12, 18, 12);

        fileView = label("자막 파일을 선택하세요", 16);
        root.addView(fileView, new LinearLayout.LayoutParams(-1, 42));

        FrameLayout subtitleBox = new FrameLayout(this);
        subtitleBox.setBackgroundColor(Color.BLACK);

        subtitleView = new TextView(this);
        subtitleView.setTextColor(Color.WHITE);
        subtitleView.setTextSize(textSizeSp);
        subtitleView.setGravity(Gravity.CENTER);
        subtitleView.setShadowLayer(5, 2, 2, Color.BLACK);
        subtitleView.setPadding(30, 10, 30, 10);
        subtitleBox.addView(subtitleView, new FrameLayout.LayoutParams(-1, -1));
        root.addView(subtitleBox, new LinearLayout.LayoutParams(-1, 0, 1));

        timeView = label("00:00:00 / 00:00:00", 14);
        timeView.setGravity(Gravity.CENTER);
        root.addView(timeView, new LinearLayout.LayoutParams(-1, 34));

        seekBar = new SeekBar(this);
        seekBar.setMax(100000);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                if (fromUser && !subtitles.isEmpty()) {
                    long duration = subtitles.get(subtitles.size()-1).endMs;
                    positionMs = duration * p / 100000L;
                    updateUi();
                }
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });
        root.addView(seekBar, new LinearLayout.LayoutParams(-1, 42));

        LinearLayout row1 = new LinearLayout(this);
        row1.setGravity(Gravity.CENTER);
        addButton(row1, "자막 열기", v -> pickSubtitle());
        addButton(row1, "−10초", v -> jump(-10000));
        addButton(row1, "−1초", v -> jump(-1000));
        playButton = addButton(row1, "재생", v -> togglePlay());
        addButton(row1, "+1초", v -> jump(1000));
        addButton(row1, "+10초", v -> jump(10000));
        root.addView(row1, new LinearLayout.LayoutParams(-1, 58));

        LinearLayout row2 = new LinearLayout(this);
        row2.setGravity(Gravity.CENTER);
        addButton(row2, "싱크 −0.1", v -> adjustSync(-100));
        addButton(row2, "싱크 +0.1", v -> adjustSync(100));
        addButton(row2, "글자 −", v -> changeTextSize(-2));
        addButton(row2, "글자 +", v -> changeTextSize(2));
        addButton(row2, "처음", v -> { positionMs = 0; updateUi(); });
        root.addView(row2, new LinearLayout.LayoutParams(-1, 58));

        TextView hint = label("싱크: 0.00초  ·  화면을 길게 누르면 컨트롤 표시", 12);
        hint.setGravity(Gravity.CENTER);
        root.addView(hint, new LinearLayout.LayoutParams(-1, 34));

        subtitleBox.setOnLongClickListener(v -> {
            Toast.makeText(this, "싱크 " + String.format(Locale.US, "%+.2f초", syncMs / 1000.0), Toast.LENGTH_SHORT).show();
            return true;
        });

        setContentView(root);
    }

    private TextView label(String text, float size) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(Color.LTGRAY);
        t.setTextSize(size);
        t.setGravity(Gravity.CENTER_VERTICAL);
        return t;
    }

    private Button addButton(LinearLayout row, String text, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(12);
        b.setOnClickListener(l);
        row.addView(b, new LinearLayout.LayoutParams(0, -1, 1));
        return b;
    }

    private void pickSubtitle() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"text/plain", "application/octet-stream"});
        startActivityForResult(i, PICK_SUBTITLE);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_SUBTITLE || resultCode != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null) return;
        try {
            byte[] bytes;
            try (InputStream in = getContentResolver().openInputStream(uri);
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
                bytes = out.toByteArray();
            }
            String raw = Encoding.decode(bytes);
            String name = getFileName(uri);
            String ext = "";
            int dot = name.lastIndexOf('.');
            if (dot >= 0) ext = name.substring(dot + 1);
            subtitles.clear();
            subtitles.addAll(SubtitleParser.parse(raw, ext));
            positionMs = 0;
            syncMs = 0;
            playing = false;
            playButton.setText("재생");
            fileView.setText(name + "  ·  " + subtitles.size() + "개 자막");
            updateUi();
        } catch (Exception e) {
            Toast.makeText(this, "자막을 읽을 수 없습니다: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private String getFileName(Uri uri) {
        String result = "subtitle";
        try (android.database.Cursor c = getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) result = c.getString(idx);
            }
        } catch (Exception ignored) {}
        return result;
    }

    private void togglePlay() {
        if (subtitles.isEmpty()) {
            Toast.makeText(this, "먼저 SMI 또는 SRT 파일을 열어주세요.", Toast.LENGTH_SHORT).show();
            return;
        }
        playing = !playing;
        lastTick = SystemClock.elapsedRealtime();
        playButton.setText(playing ? "일시정지" : "재생");
    }

    private void jump(long delta) {
        positionMs = Math.max(0, positionMs + delta);
        updateUi();
    }

    private void adjustSync(long delta) {
        syncMs += delta;
        updateUi();
        Toast.makeText(this, String.format(Locale.US, "싱크 %+.1f초", syncMs / 1000.0), Toast.LENGTH_SHORT).show();
    }

    private void changeTextSize(float delta) {
        textSizeSp = Math.max(18, Math.min(70, textSizeSp + delta));
        subtitleView.setTextSize(textSizeSp);
    }

    private void updateUi() {
        long effective = positionMs + syncMs;
        Subtitle current = null;
        for (Subtitle s : subtitles) {
            if (effective >= s.startMs && effective < s.endMs) {
                current = s;
                break;
            }
            if (s.startMs > effective) break;
        }

        subtitleView.setText(current == null ? "" : current.text);

        long duration = subtitles.isEmpty() ? 0 : subtitles.get(subtitles.size()-1).endMs;
        timeView.setText(format(positionMs) + " / " + format(duration)
                + String.format(Locale.US, "    싱크 %+.2fs", syncMs / 1000.0));

        if (duration > 0) {
            seekBar.setProgress((int)Math.min(100000L, positionMs * 100000L / duration));
        }
    }

    private String format(long ms) {
        long sec = Math.max(0, ms) / 1000;
        long h = sec / 3600;
        long m = (sec % 3600) / 60;
        long s = sec % 60;
        return String.format(Locale.US, "%02d:%02d:%02d", h, m, s);
    }

    @Override protected void onDestroy() {
        handler.removeCallbacks(ticker);
        super.onDestroy();
    }
}
