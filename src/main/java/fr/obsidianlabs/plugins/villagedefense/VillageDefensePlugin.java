package fr.obsidianlabs.plugins.villagedefense;

import fr.obsidianlabs.plugins.villagedefense.achievement.AchievementManager;
import fr.obsidianlabs.plugins.villagedefense.api.VillageDefenseApi;
import fr.obsidianlabs.plugins.villagedefense.api.VillageDefenseApiService;
import fr.obsidianlabs.plugins.villagedefense.api.VillageDefenseProgressType;
import fr.obsidianlabs.plugins.villagedefense.arena.ArenaAttemptManager;
import fr.obsidianlabs.plugins.villagedefense.arena.ArenaManager;
import fr.obsidianlabs.plugins.villagedefense.arena.Arena;
import fr.obsidianlabs.plugins.villagedefense.command.VDAdminCommand;
import fr.obsidianlabs.plugins.villagedefense.command.VDCommand;
import fr.obsidianlabs.plugins.villagedefense.config.Messages;
import fr.obsidianlabs.plugins.villagedefense.database.Database;
import fr.obsidianlabs.plugins.villagedefense.database.MySQLDatabase;
import fr.obsidianlabs.plugins.villagedefense.database.SQLiteDatabase;
import fr.obsidianlabs.plugins.villagedefense.game.GameManager;
import fr.obsidianlabs.plugins.villagedefense.gui.AchievementGUI;
import fr.obsidianlabs.plugins.villagedefense.gui.AchievementAdminGUI;
import fr.obsidianlabs.plugins.villagedefense.gui.AchievementEditGUI;
import fr.obsidianlabs.plugins.villagedefense.gui.EnchantGUI;
import fr.obsidianlabs.plugins.villagedefense.gui.EnchantPricesGUI;
import fr.obsidianlabs.plugins.villagedefense.gui.TowerConfigGUI;
import fr.obsidianlabs.plugins.villagedefense.game.WaypointGuideManager;
import fr.obsidianlabs.plugins.villagedefense.gui.ArenaSetupGUI;
import fr.obsidianlabs.plugins.villagedefense.gui.ArenaPositionsGUI;
import fr.obsidianlabs.plugins.villagedefense.gui.ArenaGameplayLocationsGUI;
import fr.obsidianlabs.plugins.villagedefense.gui.ArenaOptionsGUI;
import fr.obsidianlabs.plugins.villagedefense.gui.ArenaPathsGUI;
import fr.obsidianlabs.plugins.villagedefense.gui.ArenaPathEditGUI;
import fr.obsidianlabs.plugins.villagedefense.gui.ArenaPathSpawnersGUI;
import fr.obsidianlabs.plugins.villagedefense.gui.ArenaRewardsGUI;
import fr.obsidianlabs.plugins.villagedefense.gui.BungeeConfigGUI;
import fr.obsidianlabs.plugins.villagedefense.gui.NetworkConfigGUI;
import fr.obsidianlabs.plugins.villagedefense.gui.SettingsGUI;
import fr.obsidianlabs.plugins.villagedefense.gui.ChatInputManager;
import fr.obsidianlabs.plugins.villagedefense.gui.KitConfigGUI;
import fr.obsidianlabs.plugins.villagedefense.gui.KitGUI;
import fr.obsidianlabs.plugins.villagedefense.gui.PerksGUI;
import fr.obsidianlabs.plugins.villagedefense.gui.ShopGUI;
import fr.obsidianlabs.plugins.villagedefense.gui.ShopPricesGUI;
import fr.obsidianlabs.plugins.villagedefense.gui.LanguageGUI;
import fr.obsidianlabs.plugins.villagedefense.gui.SchematicCategoryGUI;
import fr.obsidianlabs.plugins.villagedefense.gui.SchematicGUI;
import fr.obsidianlabs.plugins.villagedefense.gui.IconPickerGUI;
import fr.obsidianlabs.plugins.villagedefense.gui.VDAdminGUI;
import fr.obsidianlabs.plugins.villagedefense.gui.DatabaseGUI;
import fr.obsidianlabs.plugins.villagedefense.gui.LeaderboardGUI;
import fr.obsidianlabs.plugins.villagedefense.gui.SpecializationGUI;
import fr.obsidianlabs.plugins.villagedefense.gui.TowerGUI;
import fr.obsidianlabs.plugins.villagedefense.gui.TrapGUI;
import fr.obsidianlabs.plugins.villagedefense.hook.HookManager;
import fr.obsidianlabs.plugins.villagedefense.kit.KitAbilityManager;
import fr.obsidianlabs.plugins.villagedefense.kit.KitManager;
import fr.obsidianlabs.plugins.villagedefense.leaderboard.LeaderboardDisplayManager;
import fr.obsidianlabs.plugins.villagedefense.network.NetworkSyncManager;
import fr.obsidianlabs.plugins.villagedefense.trap.TrapManager;
import fr.obsidianlabs.plugins.villagedefense.listener.ArenaListener;
import fr.obsidianlabs.plugins.villagedefense.listener.InventoryCloseListener;
import fr.obsidianlabs.plugins.villagedefense.listener.PlayerListener;
import fr.obsidianlabs.plugins.villagedefense.listener.SignListener;
import fr.obsidianlabs.plugins.villagedefense.perk.PerkManager;
import fr.obsidianlabs.plugins.villagedefense.player.PlayerManager;
import fr.obsidianlabs.plugins.villagedefense.powerup.PowerUpManager;
import fr.obsidianlabs.plugins.villagedefense.shop.ShopManager;
import fr.obsidianlabs.plugins.villagedefense.tower.TowerManager;
import fr.obsidianlabs.plugins.villagedefense.wave.WaveManager;
import fr.obsidianlabs.plugins.villagedefense.gameplay.service.ArenaGameplayService;
import fr.obsidianlabs.plugins.villagedefense.gameplay.service.MobRegistryService;
import fr.obsidianlabs.plugins.villagedefense.gameplay.service.MobSpawnService;
import fr.obsidianlabs.plugins.villagedefense.gameplay.service.WaveCategoryResolver;
import fr.obsidianlabs.plugins.villagedefense.gameplay.service.WaveComposer;
import fr.obsidianlabs.plugins.villagedefense.gameplay.service.WaveDifficultyCalculator;
import fr.obsidianlabs.plugins.villagedefense.gameplay.provider.MythicMobsProvider;
import fr.obsidianlabs.plugins.villagedefense.gameplay.provider.VanillaMobProvider;
import fr.obsidianlabs.plugins.villagedefense.gui.GameplayMainGUI;
import fr.obsidianlabs.plugins.villagedefense.gui.DifficultyGUI;
import fr.obsidianlabs.plugins.villagedefense.gui.WaveScalingGUI;
import fr.obsidianlabs.plugins.villagedefense.gui.WaveCategoriesGUI;
import fr.obsidianlabs.plugins.villagedefense.gui.WaveCategoryEditorGUI;
import fr.obsidianlabs.plugins.villagedefense.gui.MobPoolsGUI;
import fr.obsidianlabs.plugins.villagedefense.gui.MobPoolEditorGUI;
import fr.obsidianlabs.plugins.villagedefense.gui.MobEntryEditorGUI;
import fr.obsidianlabs.plugins.villagedefense.gui.MobSelectionListGUI;
import fr.obsidianlabs.plugins.villagedefense.gui.WaveOverridesGUI;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public class VillageDefensePlugin extends JavaPlugin {

    private static VillageDefensePlugin instance;

    // Achievements
    private AchievementManager achievementManager;

    // Config
    private Messages messages;

    // Base de données
    private Database database;

    // Hooks
    private HookManager hookManager;
    private VillageDefenseApiService publicApi;

    // Managers
    private ArenaAttemptManager attemptManager;
    private ArenaManager arenaManager;
    private PlayerManager playerManager;
    private KitManager kitManager;
    private KitAbilityManager kitAbilityManager;
    private WaveManager waveManager;
    private TowerManager towerManager;
    private TrapManager trapManager;
    private ShopManager shopManager;
    private GameManager gameManager;
    private PerkManager perkManager;
    private PowerUpManager powerUpManager;
    private WaypointGuideManager waypointGuideManager;
    private LeaderboardDisplayManager leaderboardDisplayManager;
    private NetworkSyncManager networkSyncManager;

    // GUIs
    private ArenaPathsGUI arenaPathsGui;
    private ArenaPathEditGUI arenaPathEditGui;
    private ArenaPathSpawnersGUI arenaPathSpawnersGui;
    private ArenaRewardsGUI arenaRewardsGui;
    private KitGUI kitGui;
    private ShopGUI shopGui;
    private ShopPricesGUI shopPricesGui;
    private TowerGUI towerGui;
    private KitConfigGUI kitConfigGui;
    private BungeeConfigGUI bungeeConfigGui;
    private NetworkConfigGUI networkConfigGui;
    private SettingsGUI settingsGui;
    private ArenaSetupGUI arenaSetupGui;
    private ArenaPositionsGUI arenaPositionsGui;
    private ArenaGameplayLocationsGUI arenaGameplayLocationsGui;
    private ArenaOptionsGUI arenaOptionsGui;
    private PerksGUI perksGui;
    private AchievementGUI achievementGui;
    private AchievementAdminGUI achievementAdminGui;
    private AchievementEditGUI achievementEditGui;
    private SchematicGUI schematicGui;
    private SchematicCategoryGUI schematicCategoryGui;
    private VDAdminGUI vdAdminGui;
    private LanguageGUI languageGui;
    private IconPickerGUI iconPickerGui;
    private SpecializationGUI specializationGui;
    private TrapGUI trapGui;
    private LeaderboardGUI leaderboardGui;
    private EnchantGUI enchantGui;
    private EnchantPricesGUI enchantPricesGui;
    private TowerConfigGUI towerConfigGui;
    private DatabaseGUI databaseGui;

    // Gameplay system
    private ArenaGameplayService arenaGameplayService;
    private MobRegistryService mobRegistryService;
    private MobSpawnService mobSpawnService;
    private WaveCategoryResolver waveCategoryResolver;
    private WaveDifficultyCalculator waveDifficultyCalculator;
    private WaveComposer waveComposer;

    // Gameplay GUIs
    private GameplayMainGUI gameplayMainGui;
    private DifficultyGUI difficultyGui;
    private WaveScalingGUI waveScalingGui;
    private WaveCategoriesGUI waveCategoriesGui;
    private WaveCategoryEditorGUI waveCategoryEditorGui;
    private MobPoolsGUI mobPoolsGui;
    private MobPoolEditorGUI mobPoolEditorGui;
    private MobEntryEditorGUI mobEntryEditorGui;
    private MobSelectionListGUI mobSelectionListGui;
    private WaveOverridesGUI waveOverridesGui;

    // Utilitaires
    private ChatInputManager chatInputManager;

    // Colored console prefix injected into every log record
    private static final String LOG_PREFIX = "\u001B[32m\u001B[1m[VillageDefensePro]\u001B[0m ";

    private void installLogPrefix() {
        getLogger().setFilter(record -> {
            String msg = record.getMessage();
            if (msg != null && !msg.startsWith(LOG_PREFIX)) {
                record.setMessage(LOG_PREFIX + msg);
                record.setLoggerName(""); // supprime le [VillageDefense] automatique de Paper
            }
            return true;
        });
    }

    @Override
    public void onEnable() {
        instance = this;
        installLogPrefix();
        long start = System.currentTimeMillis();

        saveDefaultConfig();

        messages = new Messages(this);
        messages.reload();
        hookManager = new HookManager(this);
        hookManager.enable();
        syncRuntimeMetadata();

        initDatabase();

        achievementManager = new AchievementManager(this);
        achievementManager.load();

        playerManager = new PlayerManager(this);
        attemptManager = new ArenaAttemptManager(this);
        getDatabase().initAttemptTable();
        kitManager = new KitManager(this);
        kitManager.init();
        kitAbilityManager = new KitAbilityManager(this);
        kitAbilityManager.init();

        waveManager = new WaveManager(this);

        // Gameplay system
        mobRegistryService = new MobRegistryService();
        mobRegistryService.registerProvider(new VanillaMobProvider(this));
        if (getServer().getPluginManager().getPlugin("MythicMobs") != null) {
            mobRegistryService.registerProvider(new MythicMobsProvider(this));
            getLogger().info("[Gameplay] MythicMobs détecté — provider enregistré.");
        }
        waveCategoryResolver    = new WaveCategoryResolver();
        waveDifficultyCalculator = new WaveDifficultyCalculator();
        waveComposer            = new WaveComposer(getLogger());
        arenaGameplayService    = new ArenaGameplayService(this);
        mobSpawnService         = new MobSpawnService(this, mobRegistryService);

        towerManager = new TowerManager(this);
        towerManager.init();
        trapManager = new TrapManager(this);
        trapManager.init();

        shopManager = new ShopManager(this);
        shopManager.init();

        gameManager = new GameManager(this);
        perkManager = new PerkManager(this);
        powerUpManager = new PowerUpManager(this);
        waypointGuideManager = new WaypointGuideManager(this);
        leaderboardDisplayManager = new LeaderboardDisplayManager(this);
        publicApi = new VillageDefenseApiService(this);
        publicApi.register();

        arenaManager = new ArenaManager(this);
        arenaManager.init();
        leaderboardDisplayManager.init();

        networkSyncManager = new NetworkSyncManager(this);
        networkSyncManager.start();

        // GUIs
        chatInputManager = new ChatInputManager(this);
        kitGui = new KitGUI(this);
        kitConfigGui = new KitConfigGUI(this);
        bungeeConfigGui = new BungeeConfigGUI(this);
        networkConfigGui = new NetworkConfigGUI(this);
        settingsGui = new SettingsGUI(this);
        shopGui = new ShopGUI(this);
        shopPricesGui = new ShopPricesGUI(this);
        perksGui = new PerksGUI(this);
        towerGui = new TowerGUI(this);
        arenaSetupGui = new ArenaSetupGUI(this);
        arenaPositionsGui = new ArenaPositionsGUI(this);
        arenaGameplayLocationsGui = new ArenaGameplayLocationsGUI(this);
        arenaOptionsGui = new ArenaOptionsGUI(this);
        achievementGui = new AchievementGUI(this);
        achievementAdminGui = new AchievementAdminGUI(this);
        achievementEditGui = new AchievementEditGUI(this);
        schematicGui = new SchematicGUI(this);
        schematicCategoryGui = new SchematicCategoryGUI(this);
        vdAdminGui = new VDAdminGUI(this);
        languageGui = new LanguageGUI(this);
        iconPickerGui = new IconPickerGUI(this);
        specializationGui = new SpecializationGUI(this);
        trapGui = new TrapGUI(this);
        leaderboardGui = new LeaderboardGUI(this);
        databaseGui = new DatabaseGUI(this);
        enchantGui = new EnchantGUI(this);
        enchantPricesGui = new EnchantPricesGUI(this);
        towerConfigGui = new TowerConfigGUI(this);
        arenaPathsGui = new ArenaPathsGUI(this);
        arenaPathEditGui = new ArenaPathEditGUI(this);
        arenaPathSpawnersGui = new ArenaPathSpawnersGUI(this);
        arenaRewardsGui = new ArenaRewardsGUI(this);

        // Gameplay GUIs
        gameplayMainGui        = new GameplayMainGUI(this);
        difficultyGui          = new DifficultyGUI(this);
        waveScalingGui         = new WaveScalingGUI(this);
        waveCategoriesGui      = new WaveCategoriesGUI(this);
        waveCategoryEditorGui  = new WaveCategoryEditorGUI(this);
        mobPoolsGui            = new MobPoolsGUI(this);
        mobPoolEditorGui       = new MobPoolEditorGUI(this);
        mobEntryEditorGui      = new MobEntryEditorGUI(this);
        mobSelectionListGui    = new MobSelectionListGUI(this);
        waveOverridesGui       = new WaveOverridesGUI(this);

        registerListeners();
        registerCommands();

        long elapsed = System.currentTimeMillis() - start;
        printBanner(elapsed);
    }

    @Override
    public void onDisable() {
        if (publicApi != null)
            publicApi.unregister();
        if (networkSyncManager != null)
            networkSyncManager.stop();
        if (hookManager != null)
            hookManager.disable();
        if (gameManager != null)
            gameManager.shutdown();
        if (kitAbilityManager != null)
            kitAbilityManager.shutdown();
        if (leaderboardDisplayManager != null)
            leaderboardDisplayManager.shutdown();
        if (towerManager != null)
            towerManager.shutdown();
        if (trapManager != null)
            trapManager.shutdown();
        if (arenaManager != null)
            arenaManager.shutdown();
        if (playerManager != null) {
            var futures = getServer().getOnlinePlayers().stream()
                    .map(playerManager::savePlayerAsync)
                    .toList();
            futures.forEach(f -> {
                try {
                    f.join();
                } catch (Exception ignored) {
                }
            });
        }
        if (database != null)
            database.close();
        getLogger().info("VillageDefensePro désactivé. Au revoir !");
    }

    // ---- Banner ----

    private void printBanner(long elapsed) {
        String G = "\u001B[32m"; // vert
        String Y = "\u001B[33m"; // jaune
        String C = "\u001B[36m"; // cyan
        String W = "\u001B[37m"; // blanc
        String B = "\u001B[34m"; // bleu
        String RG = "\u001B[31m"; // rouge
        String R = "\u001B[0m"; // reset
        String BOLD = "\u001B[1m";
        java.util.logging.Logger log = getLogger();
        int bannerWidth = 66;
        String topBorder = G + BOLD + "╔" + "═".repeat(bannerWidth) + "╗" + R;
        String midBorder = G + BOLD + "╠" + "═".repeat(bannerWidth) + "╣" + R;
        String bottomBorder = G + BOLD + "╚" + "═".repeat(bannerWidth) + "╝" + R;
        java.util.function.Consumer<String> line = content -> {
            String plain = content.replaceAll("\\u001B\\[[;\\d]*m", "");
            int pad = Math.max(0, bannerWidth - plain.length());
            log.info(G + BOLD + "║" + R + content + " ".repeat(pad) + G + BOLD + "║" + R);
        };
        boolean vault = hookManager != null && hookManager.isVaultEnabled();
        boolean papi = hookManager != null && hookManager.isPlaceholderApiEnabled();
        boolean protocolLib = hookManager != null && hookManager.isProtocolLibEnabled();
        boolean ocm = isHookEnabled("OldCombatMechanics");
        boolean bungeeCord = hookManager != null && hookManager.isBungeeCordEnabled();
        String ok = G + "✓" + R + W;
        String ko = RG + "✗" + R + W;

        log.info(topBorder);
        line.accept(Y + BOLD + "  ██╗   ██╗██╗██╗     ██╗      █████╗  ██████╗ ███████╗" + R);
        line.accept(Y + BOLD + "  ██║   ██║██║██║     ██║     ██╔══██╗██╔════╝ ██╔════╝" + R);
        line.accept(Y + BOLD + "  ██║   ██║██║██║     ██║     ███████║██║  ███╗█████╗  " + R);
        line.accept(Y + BOLD + "  ╚██╗ ██╔╝██║██║     ██║     ██╔══██║██║   ██║██╔══╝  " + R);
        line.accept(Y + BOLD + "   ╚████╔╝ ██║███████╗███████╗██║  ██║╚██████╔╝███████╗" + R);
        line.accept(Y + BOLD + "    ╚═══╝  ╚═╝╚══════╝╚══════╝╚═╝  ╚═╝ ╚═════╝ ╚══════╝" + R);
        line.accept(C + BOLD + "  ██████╗ ███████╗███████╗███████╗███╗   ██╗███████╗███████╗" + R);
        line.accept(C + BOLD + "  ██╔══██╗██╔════╝██╔════╝██╔════╝████╗  ██║██╔════╝██╔════╝" + R);
        line.accept(C + BOLD + "  ██║  ██║█████╗  █████╗  █████╗  ██╔██╗ ██║███████╗█████╗  " + R);
        line.accept(C + BOLD + "  ██║  ██║██╔══╝  ██╔══╝  ██╔══╝  ██║╚██╗██║╚════██║██╔══╝  " + R);
        line.accept(C + BOLD + "  ██████╔╝███████╗██║     ███████╗██║ ╚████║███████║███████╗" + R);
        line.accept(C + BOLD + "  ╚═════╝ ╚══════╝╚═╝     ╚══════╝╚═╝  ╚═══╝╚══════╝╚══════╝" + R);
        line.accept(G + BOLD + "                     ██████╗ ██████╗  ██████╗" + R);
        line.accept(G + BOLD + "                     ██╔══██╗██╔══██╗██╔═══██╗" + R);
        line.accept(G + BOLD + "                     ██████╔╝██████╔╝██║   ██║" + R);
        line.accept(G + BOLD + "                     ██╔═══╝ ██╔══██╗██║   ██║" + R);
        line.accept(G + BOLD + "                     ██║     ██║  ██║╚██████╔╝" + R);
        line.accept(G + BOLD + "                     ╚═╝     ╚═╝  ╚═╝ ╚═════╝" + R);
        log.info(midBorder);
        line.accept(W + "  by " + B + BOLD + "Obsidian Labs" + R);
        line.accept(W + "  Version " + Y + getDescription().getVersion() + R + W
                + " | Arenes: " + Y + arenaManager.getAllArenas().size() + R + W
                + " | Charge en " + Y + elapsed + "ms" + R);
        line.accept(W + "  Hooks: " + (vault ? ok : ko) + " Vault | "
                + (papi ? ok : ko) + " PAPI | "
                + (protocolLib ? ok : ko) + " ProtocolLib");
        line.accept(W + "  Proxy: " + (bungeeCord ? ok : ko) + " BungeeCord | "
                + (ocm ? ok : ko) + " OldCombatMechanics");
        log.info(bottomBorder);
    }

    private boolean isHookEnabled(String pluginName) {
        return getServer().getPluginManager().isPluginEnabled(pluginName);
    }

    // ---- Initialisation ----

    private void initDatabase() {
        String type = getConfig().getString("database.type", "SQLITE").toUpperCase();
        database = type.equals("MYSQL") ? new MySQLDatabase(this) : new SQLiteDatabase(this);
        try {
            database.init();
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "[Database] Impossible d'initialiser la base de données !", e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    /**
     * Ferme la connexion actuelle et réinitialise la base de données (après
     * changement de config).
     */
    public void reloadDatabase() {
        if (database != null) {
            try {
                database.close();
            } catch (Exception ignored) {
            }
        }
        super.reloadConfig();
        initDatabase();
        getLogger().info("[Database] Connexion rechargée.");
    }

    private void registerListeners() {
        var pm = getServer().getPluginManager();
        pm.registerEvents(new PlayerListener(this), this);
        pm.registerEvents(new ArenaListener(this), this);
        pm.registerEvents(new InventoryCloseListener(this), this);
        pm.registerEvents(new SignListener(this), this);
    }

    private void registerCommands() {
        var vdCmd = getCommand("vd");
        var vdAdminCmd = getCommand("vdadmin");
        if (vdCmd != null) {
            VDCommand cmd = new VDCommand(this);
            vdCmd.setExecutor(cmd);
            vdCmd.setTabCompleter(cmd);
        }
        if (vdAdminCmd != null) {
            VDAdminCommand cmd = new VDAdminCommand(this);
            vdAdminCmd.setExecutor(cmd);
            vdAdminCmd.setTabCompleter(cmd);
        }
    }

    private void syncRuntimeMetadata() {
        syncCommandMetadata();
        syncPermissionMetadata();
    }

    private void syncCommandMetadata() {
        var vd = getCommand("vd");
        if (vd != null) {
            vd.setDescription(messages.raw("meta.command.vd.description"));
            vd.setUsage(messages.raw("meta.command.vd.usage"));
        }
        var vdAdmin = getCommand("vdadmin");
        if (vdAdmin != null) {
            vdAdmin.setDescription(messages.raw("meta.command.vdadmin.description"));
            vdAdmin.setUsage(messages.raw("meta.command.vdadmin.usage"));
        }
    }

    private void syncPermissionMetadata() {
        syncPermissionDescription("villagedefense.admin", "meta.permission.villagedefense-admin");
        syncPermissionDescription("villagedefense.admin.arena", "meta.permission.villagedefense-admin-arena");
        syncPermissionDescription("villagedefense.admin.setup", "meta.permission.villagedefense-admin-setup");
        syncPermissionDescription("villagedefense.admin.schematic", "meta.permission.villagedefense-admin-schematic");
        syncPermissionDescription("villagedefense.admin.lang", "meta.permission.villagedefense-admin-lang");
        syncPermissionDescription("villagedefense.admin.database", "meta.permission.villagedefense-admin-database");
        syncPermissionDescription("villagedefense.admin.prices", "meta.permission.villagedefense-admin-prices");
        syncPermissionDescription("villagedefense.admin.reload", "meta.permission.villagedefense-admin-reload");
        syncPermissionDescription("villagedefense.admin.game", "meta.permission.villagedefense-admin-game");
        syncPermissionDescription("villagedefense.admin.player", "meta.permission.villagedefense-admin-player");
        syncPermissionDescription("villagedefense.play", "meta.permission.villagedefense-play");
        syncPermissionDescription("villagedefense.test", "meta.permission.villagedefense-test");
        syncPermissionDescription("villagedefense.bypass", "meta.permission.villagedefense-bypass");
    }

    private void syncPermissionDescription(String node, String key) {
        Permission permission = getServer().getPluginManager().getPermission(node);
        if (permission != null) {
            permission.setDescription(messages.raw(key));
        }
    }

    // ---- Utilitaires ----

    public NamespacedKey getKey(String name) {
        return new NamespacedKey(this, name);
    }

    @Override
    public void reloadConfig() {
        super.reloadConfig();
        if (messages != null)
            messages.reload();
        if (hookManager != null)
            hookManager.enable();
        if (messages != null)
            syncRuntimeMetadata();
        if (leaderboardDisplayManager != null)
            leaderboardDisplayManager.refreshNow();
    }

    // ---- Getters ----

    public static VillageDefensePlugin getInstance() {
        return instance;
    }

    public boolean hasPermission(CommandSender sender, String node) {
        return hookManager != null ? hookManager.hasPermission(sender, node) : sender.hasPermission(node);
    }

    public void sendActionBar(Player player, String legacyMessage) {
        if (hookManager != null)
            hookManager.sendActionBar(player, legacyMessage);
        else
            fr.obsidianlabs.plugins.villagedefense.util.ChatUtil.sendActionBar(player, legacyMessage);
    }

    public Messages getMessages() {
        return messages;
    }

    public VillageDefenseApi getPublicApi() {
        return publicApi;
    }

    public Database getDatabase() {
        return database;
    }

    public HookManager getHookManager() {
        return hookManager;
    }

    public ArenaAttemptManager getAttemptManager() {
        return attemptManager;
    }

    public ArenaManager getArenaManager() {
        return arenaManager;
    }

    public PlayerManager getPlayerManager() {
        return playerManager;
    }

    public KitManager getKitManager() {
        return kitManager;
    }

    public KitAbilityManager getKitAbilityManager() {
        return kitAbilityManager;
    }

    public WaveManager getWaveManager() {
        return waveManager;
    }

    public TowerManager getTowerManager() {
        return towerManager;
    }

    public ShopManager getShopManager() {
        return shopManager;
    }

    public GameManager getGameManager() {
        return gameManager;
    }

    public PerkManager getPerkManager() {
        return perkManager;
    }

    public PowerUpManager getPowerUpManager() {
        return powerUpManager;
    }

    public WaypointGuideManager getWaypointGuideManager() {
        return waypointGuideManager;
    }

    public LeaderboardDisplayManager getLeaderboardDisplayManager() {
        return leaderboardDisplayManager;
    }

    public NetworkSyncManager getNetworkSyncManager() {
        return networkSyncManager;
    }

    public KitGUI getKitGui() {
        return kitGui;
    }

    public KitConfigGUI getKitConfigGui() {
        return kitConfigGui;
    }

    public BungeeConfigGUI getBungeeConfigGui() {
        return bungeeConfigGui;
    }

    public NetworkConfigGUI getNetworkConfigGui() {
        return networkConfigGui;
    }

    public SettingsGUI getSettingsGui() {
        return settingsGui;
    }

    public ShopGUI getShopGui() {
        return shopGui;
    }

    public ShopPricesGUI getShopPricesGui() {
        return shopPricesGui;
    }

    public PerksGUI getPerksGui() {
        return perksGui;
    }

    public TowerGUI getTowerGui() {
        return towerGui;
    }

    public ArenaSetupGUI getArenaSetupGui() {
        return arenaSetupGui;
    }

    public ArenaPositionsGUI getArenaPositionsGui() {
        return arenaPositionsGui;
    }

    public ArenaGameplayLocationsGUI getArenaGameplayLocationsGui() {
        return arenaGameplayLocationsGui;
    }

    public ArenaOptionsGUI getArenaOptionsGui() {
        return arenaOptionsGui;
    }

    public AchievementManager getAchievementManager() {
        return achievementManager;
    }

    public AchievementGUI getAchievementGui() {
        return achievementGui;
    }

    public AchievementAdminGUI getAchievementAdminGui() {
        return achievementAdminGui;
    }

    public AchievementEditGUI getAchievementEditGui() {
        return achievementEditGui;
    }

    public SchematicGUI getSchematicGui() {
        return schematicGui;
    }

    public SchematicCategoryGUI getSchematicCategoryGui() {
        return schematicCategoryGui;
    }

    public VDAdminGUI getVdAdminGui() {
        return vdAdminGui;
    }

    public LanguageGUI getLanguageGui() {
        return languageGui;
    }

    public IconPickerGUI getIconPickerGui() {
        return iconPickerGui;
    }

    public SpecializationGUI getSpecializationGui() {
        return specializationGui;
    }

    public TrapGUI getTrapGui() {
        return trapGui;
    }

    public LeaderboardGUI getLeaderboardGui() {
        return leaderboardGui;
    }

    public EnchantGUI getEnchantGui() {
        return enchantGui;
    }

    public EnchantPricesGUI getEnchantPricesGui() {
        return enchantPricesGui;
    }

    public TowerConfigGUI getTowerConfigGui() {
        return towerConfigGui;
    }

    public DatabaseGUI getDatabaseGui() {
        return databaseGui;
    }

    public TrapManager getTrapManager() {
        return trapManager;
    }

    public ArenaPathsGUI getArenaPathsGui() {
        return arenaPathsGui;
    }

    public ArenaPathEditGUI getArenaPathEditGui() {
        return arenaPathEditGui;
    }

    public ArenaPathSpawnersGUI getArenaPathSpawnersGui() {
        return arenaPathSpawnersGui;
    }

    public ArenaRewardsGUI getArenaRewardsGui() {
        return arenaRewardsGui;
    }

    public ChatInputManager getChatInputManager() {
        return chatInputManager;
    }

    // ---- Gameplay system ----

    public ArenaGameplayService getArenaGameplayService()         { return arenaGameplayService; }
    public MobRegistryService getMobRegistryService()             { return mobRegistryService; }
    public MobSpawnService getMobSpawnService()                   { return mobSpawnService; }
    public WaveCategoryResolver getWaveCategoryResolver()         { return waveCategoryResolver; }
    public WaveDifficultyCalculator getWaveDifficultyCalculator() { return waveDifficultyCalculator; }
    public WaveComposer getWaveComposer()                         { return waveComposer; }

    // ---- Gameplay GUIs ----

    public GameplayMainGUI getGameplayMainGui()               { return gameplayMainGui; }
    public DifficultyGUI getDifficultyGui()                   { return difficultyGui; }
    public WaveScalingGUI getWaveScalingGui()                 { return waveScalingGui; }
    public WaveCategoriesGUI getWaveCategoriesGui()           { return waveCategoriesGui; }
    public WaveCategoryEditorGUI getWaveCategoryEditorGui()   { return waveCategoryEditorGui; }
    public MobPoolsGUI getMobPoolsGui()                       { return mobPoolsGui; }
    public MobPoolEditorGUI getMobPoolEditorGui()             { return mobPoolEditorGui; }
    public MobEntryEditorGUI getMobEntryEditorGui()           { return mobEntryEditorGui; }
    public MobSelectionListGUI getMobSelectionListGui()       { return mobSelectionListGui; }
    public WaveOverridesGUI getWaveOverridesGui()             { return waveOverridesGui; }

    public void fireProgress(Player player, Arena arena, VillageDefenseProgressType type, long amount, long total,
            String detail) {
        if (publicApi != null) {
            publicApi.fireProgress(player, arena, type, amount, total, detail);
        }
    }
}
