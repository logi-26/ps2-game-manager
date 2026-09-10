package ps2gm.game.manager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public record AppReleaseInfo(String version, String channel, String notes, String publishedAt, List<AppPlatformAsset> platforms) {

    public static AppReleaseInfo fromJson(Map<String, Object> json) {
        List<AppPlatformAsset> platforms = new ArrayList<>();
        for (Object item : MiniJson.list(json, "platforms")) {
            Map<String, Object> p = MiniJson.asObject(item);
            platforms.add(new AppPlatformAsset(
                    MiniJson.str(p, "platform"),
                    MiniJson.str(p, "asset_name"),
                    MiniJson.longVal(p, "bytes", 0L),
                    MiniJson.str(p, "checksum_asset_name")));
        }
        return new AppReleaseInfo(
                MiniJson.str(json, "version"),
                MiniJson.str(json, "channel"),
                MiniJson.str(json, "notes"),
                MiniJson.str(json, "published_at"),
                platforms);
    }

    public AppPlatformAsset assetFor(DistributionType type) {
        String key = AppPlatformAsset.platformKeyFor(type);
        if (key == null) {
            return null;
        }
        return platforms.stream().filter(p -> key.equals(p.platform())).findFirst().orElse(null);
    }
}
