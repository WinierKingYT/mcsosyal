package com.sunucu.rpg;

import com.sunucu.rpg.core.Module;
import com.sunucu.rpg.social.SocialModule;
import java.util.List;
import org.bukkit.plugin.java.JavaPlugin;

public final class RpgPlugin extends JavaPlugin {

    private SocialModule social;
    private List<Module> modules = List.of();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        // Güncellemeden sonra yeni ayarlar mevcut dosyaya eklenir, sahibin değiştirdiği değerler korunur.
        // Dosya bozuksa (YAML hatası) üstüne yazılmaz, yoksa sahibin ayarları varsayılanlarla silinirdi.
        if (getConfig().isConfigurationSection("chat")) {
            getConfig().options().copyDefaults(true);
            saveConfig();
        }
        social = new SocialModule(this);
        // Her sistem buraya tek satır ekler, gerisi kendi paketinde durur.
        modules = List.of(social);
        modules.forEach(Module::enable);
    }

    @Override
    public void onDisable() {
        modules.forEach(Module::disable);
    }

    public SocialModule social() {
        return social;
    }
}
