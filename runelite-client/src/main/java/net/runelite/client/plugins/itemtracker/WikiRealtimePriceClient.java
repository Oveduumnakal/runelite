package net.runelite.client.plugins.itemtracker;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import javax.inject.Inject;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class WikiRealtimePriceClient
{
    private static final HttpUrl LATEST_URL = HttpUrl.parse(
            "https://prices.runescape.wiki/api/v1/osrs/latest");

    @Value
    public static class ItemPrices
    {
        long high;
        long low;

        public long avg()
        {
            if (high > 0 && low > 0)
            {
                return (high + low) / 2;
            }
            return Math.max(high, low);
        }
    }

    private final OkHttpClient httpClient;
    private final Gson gson;

    @Inject
    public WikiRealtimePriceClient(OkHttpClient httpClient, Gson gson)
    {
        this.httpClient = httpClient;
        this.gson = gson;
    }

    public Map<Integer, ItemPrices> fetchAll()
    {
        Request request = new Request.Builder()
                .url(LATEST_URL)
                .header("User-Agent", "RuneLite ItemTracker Plugin")
                .build();

        try (Response response = httpClient.newCall(request).execute())
        {
            if (!response.isSuccessful() || response.body() == null)
            {
                log.warn("Wiki price fetch failed: {}", response.code());
                return Collections.emptyMap();
            }

            JsonObject root = gson.fromJson(response.body().charStream(), JsonObject.class);
            JsonObject data = root.getAsJsonObject("data");
            if (data == null)
            {
                return Collections.emptyMap();
            }

            Map<Integer, ItemPrices> result = new HashMap<>(data.size());
            for (Map.Entry<String, JsonElement> entry : data.entrySet())
            {
                try
                {
                    int id = Integer.parseInt(entry.getKey());
                    JsonObject obj = entry.getValue().getAsJsonObject();
                    long high = obj.has("high") && !obj.get("high").isJsonNull()
                            ? obj.get("high").getAsLong() : 0L;
                    long low = obj.has("low") && !obj.get("low").isJsonNull()
                            ? obj.get("low").getAsLong() : 0L;
                    result.put(id, new ItemPrices(high, low));
                }
                catch (NumberFormatException | IllegalStateException e)
                {
                    // skip malformed entries
                }
            }
            return result;
        }
        catch (IOException | JsonParseException e)
        {
            log.warn("Error fetching wiki prices", e);
            return Collections.emptyMap();
        }
    }
}
