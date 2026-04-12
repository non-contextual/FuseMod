package net.milazy.fusemod.data;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.milazy.fusemod.FuseModMain;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 加载 fuse_materials.json，提供材料加成查询。
 * 纯 Java 实现，不涉及 MC 版本差异。
 */
public class FuseMaterialRegistry {

    private static final Map<String, FuseMaterialBonus> BONUSES = new HashMap<>();
    private static final Gson GSON = new Gson();

    public static void load() {
        String resourcePath = "/data/fusemod/fuse_materials.json";
        try (InputStream stream = FuseMaterialRegistry.class.getResourceAsStream(resourcePath)) {
            if (stream == null) {
                FuseModMain.LOGGER.error("[FuseMod] 找不到 fuse_materials.json");
                return;
            }
            JsonObject root = GSON.fromJson(
                new InputStreamReader(stream, StandardCharsets.UTF_8),
                JsonObject.class
            );
            int count = 0;
            for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                String key = entry.getKey();
                if (key.startsWith("_")) continue;
                JsonObject data = entry.getValue().getAsJsonObject();
                double attackBonus = data.has("attack_bonus") ? data.get("attack_bonus").getAsDouble() : 0;
                int fireTicks     = data.has("fire_ticks")    ? data.get("fire_ticks").getAsInt()    : 0;
                int knockback     = data.has("knockback")     ? data.get("knockback").getAsInt()     : 0;
                BONUSES.put(key, new FuseMaterialBonus(attackBonus, fireTicks, knockback));
                count++;
            }
            FuseModMain.LOGGER.info("[FuseMod] 加载了 {} 种可融合材料", count);
        } catch (Exception e) {
            FuseModMain.LOGGER.error("[FuseMod] 加载 fuse_materials.json 失败", e);
        }
    }

    public static FuseMaterialBonus getBonus(String materialId) {
        return BONUSES.get(materialId);
    }

    public static boolean isFuseable(String materialId) {
        return BONUSES.containsKey(materialId);
    }
}
