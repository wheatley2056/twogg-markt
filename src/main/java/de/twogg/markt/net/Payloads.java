package de.twogg.markt.net;

import de.twogg.markt.TwoggMarkt;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Zwei Pakete, Inhalt JSON ({@link Views}). Der Server entscheidet alles; der Client zeigt nur an und schickt Wünsche.
 */
public final class Payloads {
    private static final StreamCodec<ByteBuf, String> JSON = ByteBufCodecs.stringUtf8(262_144);

    private Payloads() {}

    /** S2C: ein Markt-Fenster öffnen oder aktualisieren ({@link Views.View}, Feld {@code screen}). */
    public record View(String json) implements CustomPacketPayload {
        public static final Type<View> TYPE = new Type<>(TwoggMarkt.id("view"));
        public static final StreamCodec<ByteBuf, View> CODEC = StreamCodec.composite(JSON, View::json, View::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** C2S: Wunsch aus einem Fenster ({@link Views.Action}). */
    public record Action(String json) implements CustomPacketPayload {
        public static final Type<Action> TYPE = new Type<>(TwoggMarkt.id("action"));
        public static final StreamCodec<ByteBuf, Action> CODEC = StreamCodec.composite(JSON, Action::json, Action::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
}
