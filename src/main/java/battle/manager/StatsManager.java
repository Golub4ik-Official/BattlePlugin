package battle.manager;

import battle.BattlePlugin;
import battle.model.BattleStats;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Хранение истории битв в файле battles.yml.
 */
public class StatsManager {

    private final BattlePlugin plugin;
    private final File file;
    private final List<BattleStats> history = new ArrayList<>();
    private int nextId = 1;

    public StatsManager(BattlePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "battles.yml");
    }

    public void load() {
        history.clear();
        if (!file.exists()) {
            nextId = 1;
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        nextId = config.getInt("next-id", 1);
        var battlesSection = config.getConfigurationSection("battles");
        if (battlesSection != null) {
            for (String key : battlesSection.getKeys(false)) {
                history.add(BattleStats.load(battlesSection.getConfigurationSection(key)));
            }
        }
        history.sort(Comparator.comparingInt(s -> s.id));
    }

    public void add(BattleStats stats) {
        history.add(stats);
        if (stats.id >= nextId) {
            nextId = stats.id + 1;
        }
    }

    public int allocateId() {
        return nextId++;
    }

    public List<BattleStats> history() {
        return new ArrayList<>(history);
    }

    public BattleStats get(int id) {
        for (BattleStats s : history) {
            if (s.id == id) {
                return s;
            }
        }
        return null;
    }

    public void save() {
        plugin.getDataFolder().mkdirs();
        YamlConfiguration config = new YamlConfiguration();
        config.set("next-id", nextId);
        var battlesSection = config.createSection("battles");
        for (BattleStats stats : history) {
            stats.save(battlesSection.createSection(String.valueOf(stats.id)));
        }
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Не удалось сохранить историю битв: " + e.getMessage());
        }
    }
}
