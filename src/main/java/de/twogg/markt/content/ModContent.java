package de.twogg.markt.content;

import de.twogg.markt.TwoggMarkt;
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

    /** Verkaufstheke: Spieler stellen sie in ihren Stand, verkauft wird Ware aus Kisten im Stand. */
    public static final Block COUNTER = block("market_counter", ShopService::open, false, SoundType.WOOD, MapColor.WOOD, null);
    /** Standschild: Admins stellen es an den Stand; zeigt Preis und Status, dort wird gemietet/gekauft. */
    public static final Block STALL_SIGN = block("stall_sign", StallService::openSign, true, SoundType.WOOD, MapColor.COLOR_RED,
            Shapes.or(Block.box(7, 0, 7, 9, 10, 9), Block.box(1, 9, 6.5, 15, 16, 9.5)));
    /** Ankaufstelle: kauft Rohstoffe und Gebäck zum schwankenden Tagespreis. */
    public static final Block BUYER = block("buyer_station", BuyerService::open, true, SoundType.WOOD, MapColor.COLOR_BROWN, null);
    /** Auftragsbrett: Marktaufträge und Aufträge von Spielern. */
    public static final Block BOARD = block("job_board", BoardService::open, true, SoundType.WOOD, MapColor.WOOD,
            Shapes.or(Block.box(1, 0, 7, 3, 16, 9), Block.box(13, 0, 7, 15, 16, 9), Block.box(0, 4, 6.5, 16, 16, 9.5)));

    public static final Item COUNTER_ITEM = blockItem("market_counter", COUNTER, Rarity.COMMON);
    public static final Item STALL_SIGN_ITEM = blockItem("stall_sign", STALL_SIGN, Rarity.UNCOMMON);
    public static final Item BUYER_ITEM = blockItem("buyer_station", BUYER, Rarity.UNCOMMON);
    public static final Item BOARD_ITEM = blockItem("job_board", BOARD, Rarity.UNCOMMON);

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
                    })
                    .build());

    private static Block block(String name, BiConsumer<ServerPlayer, BlockPos> onUse, boolean fixed, SoundType sound,
                               MapColor color, VoxelShape shape) {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, TwoggMarkt.id(name));
        BlockBehaviour.Properties p = BlockBehaviour.Properties.of().setId(key).mapColor(color).sound(sound).noOcclusion();
        // Feste Admin-Blöcke wie das Bankterminal: Spieler können sie nicht abbauen (nur im Kreativmodus)
        p = fixed ? p.strength(-1.0f, 3_600_000f) : p.strength(2.0f, 3.0f);
        return Registry.register(BuiltInRegistries.BLOCK, key, new MarktBlock(p, onUse, shape));
    }

    private static Item blockItem(String name, Block block, Rarity rarity) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, TwoggMarkt.id(name));
        return Registry.register(BuiltInRegistries.ITEM, key,
                new BlockItem(block, new Item.Properties().setId(key).useBlockDescriptionPrefix().rarity(rarity)));
    }

    public static void init() {
    }
}
