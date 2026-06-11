/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
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
