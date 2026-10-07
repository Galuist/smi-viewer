package com.example.subtitlepad;

import java.util.*;
import java.util.regex.*;

public final class SubtitleParser {
    public static List<Subtitle> parse(String raw, String extension) {
        if ("smi".equalsIgnoreCase(extension)) return parseSmi(raw);
        return parseSrt(raw);
    }

    private static List<Subtitle> parseSrt(String raw) {
        List<Subtitle> out = new ArrayList<>();
        String normalized = raw.replace("\r\n", "\n").replace('\r', '\n');
        String[] blocks = normalized.split("\\n\\s*\\n");
        Pattern time = Pattern.compile(
            "(?m)(\\d{1,2}:\\d{2}:\\d{2}[,.]\\d{1,3})\\s*-->\\s*(\\d{1,2}:\\d{2}:\\d{2}[,.]\\d{1,3})"
        );
        for (String block : blocks) {
            Matcher m = time.matcher(block);
            if (!m.find()) continue;
            long start = parseTime(m.group(1));
            long end = parseTime(m.group(2));
            String text = block.substring(m.end())
                    .replaceAll("(?i)<br\\s*/?>", "\n")
                    .replaceAll("<[^>]+>", "")
                    .trim();
            if (!text.isEmpty()) out.add(new Subtitle(start, end, text));
        }
        return normalize(out);
    }

    private static List<Subtitle> parseSmi(String raw) {
        List<Subtitle> out = new ArrayList<>();
        Pattern p = Pattern.compile(
            "(?is)<sync\\s+start\\s*=\\s*['\"]?(\\d+)['\"]?[^>]*>(.*?)(?=<sync\\s+start\\s*=|$)"
        );
        Matcher m = p.matcher(raw);
        List<Long> starts = new ArrayList<>();
        List<String> texts = new ArrayList<>();

        while (m.find()) {
            long start = Long.parseLong(m.group(1));
            String body = m.group(2)
                    .replaceAll("(?i)<br\\s*/?>", "\n")
                    .replaceAll("(?i)</?p[^>]*>", "")
                    .replaceAll("(?i)<[^>]+>", "")
                    // SMI uses these as an explicit blank/clear cue.
                    .replace("&nbsp;", " ")
                    .replace("&nbsp", " ")
                    .replace("&#160;", " ")
                    .replace("&#xA0;", " ")
                    .replace("&#xa0;", " ")
                    .replace('\u00A0', ' ')
                    .replace("&amp;", "&")
                    .replace("&lt;", "<")
                    .replace("&gt;", ">")
                    .replace("&quot;", "\"")
                    .replace("&#39;", "'")
                    .trim();

            // IMPORTANT: do NOT discard an empty body here.
            // In SMI, <SYNC Start=...>&nbsp; is an actual event that clears
            // the subtitle currently displayed. Keeping it lets MainActivity
            // replace the previous subtitle with an empty string at this time.
            starts.add(start);
            texts.add(body);
        }

        for (int i = 0; i < starts.size(); i++) {
            long end = (i + 1 < starts.size()) ? starts.get(i + 1) : starts.get(i) + 5000;
            out.add(new Subtitle(starts.get(i), end, texts.get(i)));
        }
        return normalize(out);
    }

    private static List<Subtitle> normalize(List<Subtitle> list) {
        list.sort(Comparator.comparingLong(s -> s.startMs));
        List<Subtitle> out = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            Subtitle s = list.get(i);
            long end = s.endMs;
            if (end <= s.startMs) {
                end = (i + 1 < list.size()) ? list.get(i + 1).startMs : s.startMs + 5000;
            }
            out.add(new Subtitle(s.startMs, end, s.text));
        }
        return out;
    }

    private static long parseTime(String s) {
        String[] a = s.replace(',', '.').split(":");
        double sec = Double.parseDouble(a[2]);
        long h = Long.parseLong(a[0]);
        long min = Long.parseLong(a[1]);
        return Math.round((h * 3600 + min * 60 + sec) * 1000.0);
    }
}
