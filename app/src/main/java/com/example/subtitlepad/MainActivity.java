package com.example.subtitlepad;

import android.app.*;
import android.os.*;
import android.content.*;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;

public class MainActivity extends Activity {
    private static final int PICK_SUBTITLE = 1001;
    private static final long AUTO_HIDE_DELAY_MS = 3500L;

    private TextView subtitleView, timeView, fileView;
    private SeekBar seekBar;
    private TextView playButton;
    private LinearLayout controlPanel;

    private ArrayList<Subtitle> subtitles = new ArrayList<>();
    private long positionMs = 0;
    private long syncMs = 0;
    private boolean playing = false;
    private float textSizeSp = 34f;
    private float lineSpacing = 1.0f;
    private String loadedUri = null;
    private String loadedExtension = null;

    private long lastTick;
    private float touchDownY;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean systemBarsHidden = false;
    private int baseLeft = 14, baseTop = 8, baseRight = 14, baseBottom = 6;

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

    private final Runnable hideControlsRunnable = new Runnable() {
        @Override public void run() {
            if (playing) {
                if (controlPanel != null) controlPanel.setVisibility(View.GONE);
                if (fileView != null) fileView.setVisibility(View.GONE);
                setSystemBarsHidden(true);
            }
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (savedInstanceState != null) {
            positionMs = savedInstanceState.getLong("positionMs", 0);
            syncMs = savedInstanceState.getLong("syncMs", 0);
            playing = savedInstanceState.getBoolean("playing", false);
            textSizeSp = savedInstanceState.getFloat("textSizeSp", 34f);
            lineSpacing = savedInstanceState.getFloat("lineSpacing", 1.0f);
            loadedUri = savedInstanceState.getString("loadedUri");
            loadedExtension = savedInstanceState.getString("loadedExtension");
        }

        buildUi();
        setupSystemBarInsets();
        showControls();
        lastTick = SystemClock.elapsedRealtime();
        handler.post(ticker);

        if (loadedUri != null) {
            try {
                loadSubtitle(Uri.parse(loadedUri), false);
            } catch (Exception ignored) {
                loadedUri = null;
                loadedExtension = null;
                playing = false;
            }
        }
    }

    @Override public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Keep the same Activity instance so subtitle position, sync and settings survive rotation.
        subtitleView.setTextSize(textSizeSp);
        subtitleView.setLineSpacing(0f, lineSpacing);
        setupSystemBarInsets();
        updateUi();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        root.setPadding(14, 8, 14, 6);

        fileView = label("자막 파일을 선택하세요", 15);
        fileView.setSingleLine(true);
        fileView.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        root.addView(fileView, new LinearLayout.LayoutParams(-1, 34));

        FrameLayout subtitleBox = new FrameLayout(this);
        subtitleBox.setBackgroundColor(Color.BLACK);

        subtitleView = new TextView(this);
        subtitleView.setTextColor(Color.WHITE);
        subtitleView.setTextSize(textSizeSp);
        subtitleView.setGravity(Gravity.CENTER);
        subtitleView.setShadowLayer(5, 2, 2, Color.BLACK);
        subtitleView.setPadding(24, 8, 24, 8);
        subtitleView.setLineSpacing(0f, lineSpacing);
        subtitleView.setIncludeFontPadding(true);
        subtitleBox.addView(subtitleView, new FrameLayout.LayoutParams(-1, -1));
        root.addView(subtitleBox, new LinearLayout.LayoutParams(-1, 0, 1));

        controlPanel = new LinearLayout(this);
        controlPanel.setOrientation(LinearLayout.VERTICAL);
        controlPanel.setPadding(0, 2, 0, 0);

        timeView = label("00:00:00 / 00:00:00", 13);
        timeView.setGravity(Gravity.CENTER);
        controlPanel.addView(timeView, new LinearLayout.LayoutParams(-1, 28));

        seekBar = new SeekBar(this);
        seekBar.setMax(100000);
        seekBar.setPadding(0, 0, 0, 0);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                if (fromUser && !subtitles.isEmpty()) {
                    long duration = subtitles.get(subtitles.size() - 1).endMs;
                    positionMs = duration * p / 100000L;
                    updateUi();
                    scheduleHideControls();
                }
            }
            public void onStartTrackingTouch(SeekBar s) { showControls(); }
            public void onStopTrackingTouch(SeekBar s) { scheduleHideControls(); }
        });
        controlPanel.addView(seekBar, new LinearLayout.LayoutParams(-1, 32));

        LinearLayout row1 = makeRow();
        addButton(row1, "자막 열기", v -> { showControls(); pickSubtitle(); });
        addButton(row1, "−10초", v -> { jump(-10000); scheduleHideControls(); });
        addButton(row1, "−1초", v -> { jump(-1000); scheduleHideControls(); });
        playButton = addButton(row1, "재생", v -> togglePlay());
        addButton(row1, "+1초", v -> { jump(1000); scheduleHideControls(); });
        addButton(row1, "+10초", v -> { jump(10000); scheduleHideControls(); });
        controlPanel.addView(row1, new LinearLayout.LayoutParams(-1, 46));

        LinearLayout row2 = makeRow();
        addButton(row2, "싱크 −0.1", v -> { adjustSync(-100); scheduleHideControls(); });
        addButton(row2, "싱크 +0.1", v -> { adjustSync(100); scheduleHideControls(); });
        addButton(row2, "글자 −", v -> { changeTextSize(-4); scheduleHideControls(); });
        addButton(row2, "글자 +", v -> { changeTextSize(4); scheduleHideControls(); });
        addButton(row2, "줄 −", v -> { changeLineSpacing(-0.1f); scheduleHideControls(); });
        addButton(row2, "줄 +", v -> { changeLineSpacing(0.1f); scheduleHideControls(); });
        addButton(row2, "처음", v -> { positionMs = 0; updateUi(); scheduleHideControls(); });
        controlPanel.addView(row2, new LinearLayout.LayoutParams(-1, 46));

        TextView hint = label("싱크: 0.00초 · 위로 스와이프하면 컨트롤 표시", 11);
        hint.setGravity(Gravity.CENTER);
        controlPanel.addView(hint, new LinearLayout.LayoutParams(-1, 24));

        root.addView(controlPanel, new LinearLayout.LayoutParams(-1, 178));

        subtitleBox.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                touchDownY = event.getY();
                return true;
            }
            if (event.getAction() == MotionEvent.ACTION_UP) {
                float dy = event.getY() - touchDownY;
                if (dy < -70f) {
                    showControls();
                    scheduleHideControls();
                    return true;
                }
                return true;
            }
            return true;
        });

        setContentView(root);
    }

    private void setupSystemBarInsets() {
        final View content = findViewById(android.R.id.content);
        if (content == null) return;
        content.setOnApplyWindowInsetsListener((v, insets) -> {
            int top = 0, bottom = 0;
            if (!systemBarsHidden && Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(android.view.WindowInsets.Type.systemBars());
                top = bars.top;
                bottom = bars.bottom;
            }
            v.setPadding(baseLeft, baseTop + top, baseRight, baseBottom + bottom);
            return insets;
        });
        content.requestApplyInsets();
    }

    private void setSystemBarsHidden(boolean hide) {
        systemBarsHidden = hide;
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                if (hide) controller.hide(android.view.WindowInsets.Type.systemBars());
                else controller.show(android.view.WindowInsets.Type.systemBars());
            }
        } else {
            int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
            if (hide) {
                flags |= View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
            }
            getWindow().getDecorView().setSystemUiVisibility(flags);
        }
        setupSystemBarInsets();
    }

    private LinearLayout makeRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        return row;
    }

    private TextView label(String text, float size) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(Color.LTGRAY);
        t.setTextSize(size);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setIncludeFontPadding(true);
        return t;
    }

    private TextView addButton(LinearLayout row, String text, View.OnClickListener l) {
        TextView b = new TextView(this);
        b.setText(text);
        b.setTextColor(Color.WHITE);
        b.setTextSize(12);
        b.setGravity(Gravity.CENTER);
        b.setIncludeFontPadding(true);
        b.setPadding(2, 0, 2, 0);
        b.setMinHeight(0);
        b.setMinWidth(0);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(92, 92, 92));
        bg.setCornerRadius(5f);
        b.setBackground(bg);
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1f);
        lp.setMargins(2, 2, 2, 2);
        row.addView(b, lp);
        return b;
    }

    private void pickSubtitle() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i, PICK_SUBTITLE);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_SUBTITLE || resultCode != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null) return;

        try {
            int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            if (flags != 0) {
                try { getContentResolver().takePersistableUriPermission(uri, flags); }
                catch (Exception ignored) {}
            }
            loadSubtitle(uri, true);
        } catch (Exception e) {
            Toast.makeText(this, "자막을 읽을 수 없습니다: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void loadSubtitle(Uri uri, boolean reset) throws Exception {
        byte[] bytes;
        try (InputStream in = getContentResolver().openInputStream(uri);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (in == null) throw new IOException("파일을 열 수 없습니다.");
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            bytes = out.toByteArray();
        }

        String raw = Encoding.decode(bytes);
        String name = getFileName(uri);
        String ext = "";
        int dot = name.lastIndexOf('.');
        if (dot >= 0) ext = name.substring(dot + 1).toLowerCase(Locale.US);

        if (!ext.equals("smi") && !ext.equals("srt")) {
            Toast.makeText(this, "SMI 또는 SRT 파일을 선택하세요.\n선택된 파일: " + name, Toast.LENGTH_LONG).show();
            return;
        }

        List<Subtitle> parsed = SubtitleParser.parse(raw, ext);
        if (parsed.isEmpty()) {
            Toast.makeText(this, "자막을 찾지 못했습니다. 파일 형식이나 인코딩을 확인하세요.\n파일: " + name, Toast.LENGTH_LONG).show();
            fileView.setText(name + " · 자막 0개");
            subtitleView.setText("");
            return;
        }

        subtitles.clear();
        subtitles.addAll(parsed);
        loadedUri = uri.toString();
        loadedExtension = ext;

        if (reset) {
            positionMs = 0;
            syncMs = 0;
            playing = false;
        }

        playButton.setText(playing ? "일시정지" : "재생");
        fileView.setText(name + " · " + subtitles.size() + "개 자막");
        updateUi();

        if (playing) {
            lastTick = SystemClock.elapsedRealtime();
            scheduleHideControls();
        } else {
            showControls();
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
        if (playing) scheduleHideControls();
        else showControls();
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
        textSizeSp = Math.max(18, Math.min(120, textSizeSp + delta));
        subtitleView.setTextSize(textSizeSp);
    }

    private void changeLineSpacing(float delta) {
        lineSpacing = Math.max(0.8f, Math.min(1.8f, lineSpacing + delta));
        subtitleView.setLineSpacing(0f, lineSpacing);
        Toast.makeText(this, String.format(Locale.US, "줄 간격 %.1f", lineSpacing), Toast.LENGTH_SHORT).show();
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
        long duration = subtitles.isEmpty() ? 0 : subtitles.get(subtitles.size() - 1).endMs;
        timeView.setText(format(positionMs) + " / " + format(duration)
                + String.format(Locale.US, "    싱크 %+.2fs", syncMs / 1000.0));

        if (duration > 0) {
            seekBar.setProgress((int)Math.min(100000L, positionMs * 100000L / duration));
        }
    }

    private void showControls() {
        handler.removeCallbacks(hideControlsRunnable);
        if (fileView != null) fileView.setVisibility(View.VISIBLE);
        if (controlPanel != null) controlPanel.setVisibility(View.VISIBLE);
        setSystemBarsHidden(false);
    }

    private void scheduleHideControls() {
        handler.removeCallbacks(hideControlsRunnable);
        if (playing) handler.postDelayed(hideControlsRunnable, AUTO_HIDE_DELAY_MS);
    }

    private String format(long ms) {
        long sec = Math.max(0, ms) / 1000;
        long h = sec / 3600;
        long m = (sec % 3600) / 60;
        long s = sec % 60;
        return String.format(Locale.US, "%02d:%02d:%02d", h, m, s);
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        out.putLong("positionMs", positionMs);
        out.putLong("syncMs", syncMs);
        out.putBoolean("playing", playing);
        out.putFloat("textSizeSp", textSizeSp);
        out.putFloat("lineSpacing", lineSpacing);
        out.putString("loadedUri", loadedUri);
        out.putString("loadedExtension", loadedExtension);
        super.onSaveInstanceState(out);
    }

    @Override protected void onPause() {
        super.onPause();
        // Keep the playback position while the Activity is temporarily covered.
        lastTick = SystemClock.elapsedRealtime();
    }

    @Override protected void onDestroy() {
        handler.removeCallbacks(ticker);
        handler.removeCallbacks(hideControlsRunnable);
        super.onDestroy();
    }
}
