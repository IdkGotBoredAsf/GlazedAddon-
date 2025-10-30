package com.nnpg.glazed;

import com.nnpg.glazed.modules.esp.*;
import com.nnpg.glazed.modules.movement.*;
import com.nnpg.glazed.modules.main.*;
import com.nnpg.glazed.modules.pvp.*;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.orbit.EventHandler;

import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.MeteorClient;





public class GlazedAddon extends MeteorAddon {
    public static final Category CATEGORY = new Category("MAIN");
    public static final Category esp = new Category("DONUT BASE HUNTING");
    public static final Category pvp = new Category("PYRA-PVP");
    public static final Category movement = new Category("MOVEMENT");



    public static int MyScreenVERSION = 13;

    @Override
    public void onInitialize() {



        Modules.get().add(new SpawnerProtectV2()); //done
        Modules.get().add(new PearlThrow()); //done
        Modules.get().add(new RTPBaseFinder()); //done
        Modules.get().add(new AntiTrap()); //done
        Modules.get().add(new CoordSnapper()); //done
        Modules.get().add(new ElytraSwap()); //done
        Modules.get().add(new AHSniper()); //done
        Modules.get().add(new TunnelBaseFinder()); //done
        Modules.get().add(new ShulkerDropper()); //done
        Modules.get().add(new SpawnerDropper()); //done
        Modules.get().add(new AutoShulkerOrder()); // done
        Modules.get().add(new AutoMiner());
        Modules.get().add(new BlockFinder());
        Modules.get().add(new SeedCracker());
        Modules.get().add(new UpsideDownDeepslateESP());
        Modules.get().add(new LegitCrystalMacro());
        Modules.get().add(new WindMaceAuto());
        Modules.get().add(new TridentESP());
        Modules.get().add(new OneByOneHoles());
        Modules.get().add(new KelpESP());
        Modules.get().add(new ColumnESP());
        Modules.get().add(new DeepslateESP());
        Modules.get().add(new RotatedDeepslateESP());
        Modules.get().add(new VillagerESP());
        Modules.get().add(new HoleTunnelStairsESP());
        Modules.get().add(new CoveredHole());
        Modules.get().add(new AutoShulkerShellOrder());
        Modules.get().add(new RTPEndBaseFinder());
        Modules.get().add(new VineESP());
        Modules.get().add(new GlowBerryESP());
        Modules.get().add(new ChunkFinder());
        Modules.get().add(new LegitAnchorMacro());
        Modules.get().add(new AutoPotion());
        Modules.get().add(new SpawnerOrder());
        Modules.get().add(new FreecamV2());
        Modules.get().add(new RegionMap());
        Modules.get().add(new HoverTotem());


        // Register this class for events
        MeteorClient.EVENT_BUS.subscribe(this);
    }

    @EventHandler
    private void onGameJoined(GameJoinedEvent event) {
        MyScreen.checkVersionOnServerJoin();
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        MyScreen.resetSessionCheck();
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
        Modules.registerCategory(esp);
        Modules.registerCategory(pvp);
        Modules.registerCategory(movement);


        //mc.setScreen(new MyScreen(GuiThemes.get()));
    }

    @Override
    public String getPackage() {
        return "com.nnpg.glazed";
    }


}
