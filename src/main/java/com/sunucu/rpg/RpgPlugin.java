package com.sunucu.rpg;

import com.sunucu.rpg.core.Module;
import com.sunucu.rpg.core.db.Database;
import com.sunucu.rpg.core.gui.MenuListener;
import com.sunucu.rpg.social.SocialModule;
import java.sql.SQLException;
import java.util.List;
import java.util.logging.Level;
import org.bukkit.plugin.java.JavaPlugin;

public final class RpgPlugin extends JavaPlugin {

    private Database database;
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
        // Ortak menü altyapısı (core.gui.Menu): tüm modüllerin pencerelerini korur.
        getServer().getPluginManager().registerEvents(new MenuListener(this), this);
        database = openDatabase();
        social = new SocialModule(this, database);
        // Her sistem buraya tek satır ekler, gerisi kendi paketinde durur.
        modules = List.of(social);
        modules.forEach(Module::enable);
    }

    @Override
    public void onDisable() {
        modules.forEach(Module::disable);
        // Modüllerden sonra: bekleyen yazımlar bitince kapanır.
        if (database != null) {
            database.close();
        }
    }

    public SocialModule social() {
        return social;
    }

    /** Ortak veritabanı; açılamadıysa null (modüller bellekte çalışır). */
    public Database database() {
        return database;
    }

    // Veritabanı açılamazsa plugin kapanmaz: sohbet filtresiz kalmasın. Ayarlar o oturumda kalıcı olmaz.
    private Database openDatabase() {
        try {
            return Database.open(getDataFolder().toPath().resolve("data.db"), getLogger());
        } catch (SQLException e) {
            getLogger().log(Level.SEVERE, "Veritabanı açılamadı! Oyuncu ayarları bu oturumda kaydedilmeyecek", e);
            return null;
        }
    }
}
