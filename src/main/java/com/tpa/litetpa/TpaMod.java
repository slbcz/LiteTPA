package com.tpa.tpamod;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

@Mod(TpaMod.MODID)
public class TpaMod {
    public static final String MODID = "tpamod";

    public TpaMod() {
        // 注册服务器配置文件（让超时时间可调）
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, ConfigHolder.SPEC);
    }

    // ---------- 配置文件部分 ----------
    public static class ConfigHolder {
        public static final ForgeConfigSpec SPEC;
        public static final Config CONFIG;

        static {
            Pair<Config, ForgeConfigSpec> specPair = new ForgeConfigSpec.Builder().configure(Config::new);
            CONFIG = specPair.getLeft();
            SPEC = specPair.getRight();
        }

        public static class Config {
            public final ForgeConfigSpec.IntValue requestTimeoutSeconds;

            Config(ForgeConfigSpec.Builder builder) {
                builder.comment("TpaMod Settings");
                this.requestTimeoutSeconds = builder
                        .comment("传送请求有效时间（秒），超过此时间未处理的请求会自动取消")
                        .defineInRange("requestTimeoutSeconds", 60, 5, 3600);
            }
        }
    }
}