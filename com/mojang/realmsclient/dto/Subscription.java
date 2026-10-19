package com.mojang.realmsclient.dto;

import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.mojang.realmsclient.util.JsonUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.LenientJsonParser;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class Subscription extends ValueObject {
	private static final Logger LOGGER = LogUtils.getLogger();
	public long startDate;
	public int daysLeft;
	public Subscription.SubscriptionType type = Subscription.SubscriptionType.NORMAL;

	public static Subscription parse(String string) {
		Subscription subscription = new Subscription();

		try {
			JsonObject jsonObject = LenientJsonParser.parse(string).getAsJsonObject();
			subscription.startDate = JsonUtils.getLongOr("startDate", jsonObject, 0L);
			subscription.daysLeft = JsonUtils.getIntOr("daysLeft", jsonObject, 0);
			subscription.type = typeFrom(JsonUtils.getStringOr("subscriptionType", jsonObject, Subscription.SubscriptionType.NORMAL.name()));
		} catch (Exception exception) {
			LOGGER.error("Could not parse Subscription: {}", exception.getMessage());
		}

		return subscription;
	}

	private static Subscription.SubscriptionType typeFrom(String string) {
		try {
			return Subscription.SubscriptionType.valueOf(string);
		} catch (Exception exception) {
			return Subscription.SubscriptionType.NORMAL;
		}
	}

	@Environment(EnvType.CLIENT)
	public enum SubscriptionType {
		NORMAL,
		RECURRING;
	}
}
