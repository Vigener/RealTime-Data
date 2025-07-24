package io.github.vgnri.util;

import java.io.IOException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

/**
 * LocalTime用のGson TypeAdapter
 * LocalTimeオブジェクトをJSON文字列に変換し、その逆も行う
 */
public class LocalTimeTypeAdapter extends TypeAdapter<LocalTime> {
    
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss.SSSSSS");
    private static final DateTimeFormatter FALLBACK_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
    
    @Override
    public void write(JsonWriter out, LocalTime value) throws IOException {
        if (value == null) {
            out.nullValue();
        } else {
            out.value(value.format(FORMATTER));
        }
    }
    
    @Override
    public LocalTime read(JsonReader in) throws IOException {
        if (in.peek() == com.google.gson.stream.JsonToken.NULL) {
            in.nextNull();
            return null;
        }
        
        String timeString = in.nextString();
        try {
            // メインフォーマットを試行
            return LocalTime.parse(timeString, FORMATTER);
        } catch (Exception e) {
            try {
                // フォールバック: 秒のみのフォーマット
                return LocalTime.parse(timeString, FALLBACK_FORMATTER);
            } catch (Exception e2) {
                try {
                    // 最終フォールバック: ISO標準フォーマット
                    return LocalTime.parse(timeString);
                } catch (Exception e3) {
                    System.err.println("LocalTime解析エラー: " + timeString + " - " + e3.getMessage());
                    return LocalTime.now(); // 最終フォールバック値
                }
            }
        }
    }
}
