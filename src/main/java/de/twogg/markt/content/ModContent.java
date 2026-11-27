package de.twogg.markt.content;

import de.twogg.markt.TwoggMarkt;
import de.twogg.markt.server.ActivityService;
import de.twogg.markt.server.BoardService;
import de.twogg.markt.server.BuyerService;
import de.twogg.markt.server.ShopService;
import de.twogg.markt.server.StallService;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.function.BiConsumer;

/** Alle Blöcke und Items des Markts. */
public final class ModContent {
    private ModContent() {}

    // Formen (Kollision und Umriss) folgen den Modellen aus tools/modelle.py, angegeben für die Blickrichtung Nord
    // (Vorderseite = Norden). Teile über dem Block (Markise, Dächer, Hausgiebel) bleiben außen vor: Minecraft prüft
    // Klicks nur im eigenen Blockfeld.

    /** Verkaufstheke: Spieler stellen sie in ihren Stand, verkauft wird Ware aus Kisten im Stand. */
    public static final Block COUNTER = block("market_counter", ShopService::open, false, SoundType.WOOD, MapColor.WOOD,
            Shapes.or(Block.box(0, 0, 1.5, 16, 13, 16), Block.box(2, 13, 2, 15, 16, 8),
                    Block.box(0.5, 13, 14.5, 1.5, 16, 15.5), Block.box(14.5, 13, 14.5, 15.5, 16, 15.5)));
    /** Standschild: Admins stellen es an den Stand; zeigt Preis und Status, dort wird gemietet/gekauft. */
    public static final Block STALL_SIGN = block("stall_sign", StallService::openSign, true, SoundType.WOOD, MapColor.COLOR_RED,
            Shapes.or(Block.box(7, 0, 7, 9, 5.5, 9), Block.box(4, 0, 7, 12, 1, 9), Block.box(7, 0, 4, 9, 1, 12),
                    Block.box(0.5, 5.5, 6.5, 15.5, 13, 9.5), Block.box(0, 13, 6, 16, 15, 10), Block.box(11.5, 15, 7, 14, 16, 9.5)));
    /** Ankaufstelle: kauft Rohstoffe und Gebäck zum schwankenden Tagespreis. */
    public static final Block BUYER = block("buyer_station", BuyerService::open, true, SoundType.WOOD, MapColor.COLOR_BROWN,
            Shapes.or(Block.box(0, 0, 1, 16, 12, 16), Block.box(0.5, 12, 2.5, 16, 16, 9), Block.box(1, 12, 9.5, 6, 16, 13.5),
                    Block.box(0.5, 12, 14.5, 15.5, 16, 16)));
    /** Auftragsbrett: Marktaufträge und Aufträge von Spielern. */
    // Zwei Blöcke hoch: unten das Brett, darüber ein unsichtbarer Aufsatz für die obere Hälfte (sonst nur unten klickbar).
    public static final Block BOARD = register("job_board", true, SoundType.WOOD, MapColor.WOOD,
            p -> new BoardBlock(p, BoardService::open,
                    Shapes.or(Block.box(0, 0, 7, 2, 16, 9), Block.box(14, 0, 7, 16, 16, 9), Block.box(2, 6, 6.5, 14, 16, 9.5)),
                    () -> ModContent.BOARD_TOP));
    /** Oberer Teil des Auftragsbretts (unsichtbar, ohne Item): Pfosten, Brett, Leiste mit Haken und Dach. */
    public static final Block BOARD_TOP = register("job_board_top", true, SoundType.WOOD, MapColor.WOOD,
            p -> new BoardTopBlock(p.noLootTable(),
                    Shapes.or(Block.box(0, 0, 7, 2, 13, 9), Block.box(14, 0, 7, 16, 13, 9), Block.box(2, 0, 6.5, 14, 8, 9.5),
                            Block.box(0, 7.5, 5.5, 16, 12, 7), Block.box(1, 13, 7, 15, 14, 9), Block.box(0, 13, 2, 16, 15.5, 14)),
                    () -> ModContent.BOARD));

    /** Losbude: Lose und Wichteln während des Events, Ziehung und Verteilung bei der Siegerehrung. */
    public static final Block LOTTERY = block("lottery_booth", ActivityService::openLottery, true, SoundType.WOOD, MapColor.COLOR_RED,
            Shapes.or(Block.box(0, 0, 1.5, 16, 13, 16), Block.box(2, 13, 8.5, 14, 16, 12.5), Block.box(10.5, 13, 3, 14.5, 16, 7),
                    Block.box(0.5, 13, 2, 1.5, 16, 3), Block.box(14.5, 13, 2, 15.5, 16, 3),
                    Block.box(0.5, 13, 14, 1.5, 16, 15), Block.box(14.5, 13, 14, 15.5, 16, 15)));
    /** Adventskalender (Holzhäuschen an der Wand, Rückseite nach Süden): 1.–24. Dezember ein Türchen pro Spieler und Tag. */
    public static final Block ADVENT = block("advent_calendar", ActivityService::openAdvent, true, SoundType.WOOD, MapColor.COLOR_GREEN,
            Block.box(0, 0, 11, 16, 16, 16));
    /** Verstecktes Geschenk für die Geschenkesuche. */
    public static final Block GIFT = block("hidden_gift", ActivityService::openGift, true, SoundType.WOOL, MapColor.COLOR_RED,
            Shapes.or(Block.box(3.5, 0, 3.5, 12.5, 8.5, 12.5), Block.box(4.5, 8.5, 4.5, 11.5, 10.5, 11.5),
                    Block.box(11.5, 0, 0.5, 15.5, 3.5, 4.5)));

    public static final Item COUNTER_ITEM = blockItem("market_counter", COUNTER, Rarity.COMMON);
    public static final Item STALL_SIGN_ITEM = blockItem("stall_sign", STALL_SIGN, Rarity.UNCOMMON);
    public static final Item BUYER_ITEM = blockItem("buyer_station", BUYER, Rarity.UNCOMMON);
    public static final Item BOARD_ITEM = blockItem("job_board", BOARD, Rarity.UNCOMMON);
    public static final Item LOTTERY_ITEM = blockItem("lottery_booth", LOTTERY, Rarity.UNCOMMON);
    public static final Item ADVENT_ITEM = blockItem("advent_calendar", ADVENT, Rarity.UNCOMMON);
    public static final Item GIFT_ITEM = blockItem("hidden_gift", GIFT, Rarity.UNCOMMON);


    public static final ResourceKey<Item> PLANNER_KEY = ResourceKey.create(Registries.ITEM, TwoggMarkt.id("market_planner"));
    public static final Item PLANNER = Registry.register(BuiltInRegistries.ITEM, PLANNER_KEY,
            new PlannerItem(new Item.Properties().setId(PLANNER_KEY).stacksTo(1).rarity(Rarity.EPIC)));

    public static final CreativeModeTab TAB = Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, TwoggMarkt.id("main"),
            FabricCreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.twogg_markt"))
                    .icon(() -> new ItemStack(COUNTER_ITEM))
                    .displayItems((params, out) -> {
                        out.accept(PLANNER);
                        out.accept(STALL_SIGN_ITEM);
                        out.accept(COUNTER_ITEM);
                        out.accept(BUYER_ITEM);
                        out.accept(BOARD_ITEM);
                        out.accept(LOTTERY_ITEM);
                        out.accept(ADVENT_ITEM);
                        out.accept(GIFT_ITEM);
                        if (de.twogg.markt.content.guide.GuideBook.ITEM != null) out.accept(de.twogg.markt.content.guide.GuideBook.ITEM);
                    })
                    .build());

    private static Block block(String name, BiConsumer<ServerPlayer, BlockPos> onUse, boolean fixed, SoundType sound,
                               MapColor color, VoxelShape shape) {
        return register(name, fixed, sound, color, p -> new MarktBlock(p, onUse, shape));
    }

    private static Block register(String name, boolean fixed, SoundType sound, MapColor color,
                                  java.util.function.Function<BlockBehaviour.Properties, Block> factory) {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, TwoggMarkt.id(name));
        BlockBehaviour.Properties p = BlockBehaviour.Properties.of().setId(key).mapColor(color).sound(sound).noOcclusion();
        // Feste Admin-Blöcke wie das Bankterminal: Spieler können sie nicht abbauen (nur im Kreativmodus)
        p = fixed ? p.strength(-1.0f, 3_600_000f) : p.strength(2.0f, 3.0f);
        return Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(p));
    }

    private static Item blockItem(String name, Block block, Rarity rarity) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, TwoggMarkt.id(name));
        return Registry.register(BuiltInRegistries.ITEM, key,
                new BlockItem(block, new Item.Properties().setId(key).useBlockDescriptionPrefix().rarity(rarity)));
    }

    public static void init() {
    }
}
