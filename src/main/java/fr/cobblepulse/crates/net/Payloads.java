package fr.cobblepulse.crates.net;

import fr.cobblepulse.crates.Crates;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Deux paquets generiques (JSON) entre les ecrans (apercu, roulette, editeur, Voeu ADN) et le serveur. */
public final class Payloads {
    private static final StreamCodec<ByteBuf, String> BIG_STRING = ByteBufCodecs.stringUtf8(1 << 22);

    private Payloads() {}

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> makeType(String path) {
        return new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Crates.MOD_ID, path));
    }

    /** Client -> serveur : action et arguments JSON. */
    public record Action(String action, String json) implements CustomPacketPayload {
        public static final Type<Action> TYPE = makeType("action");
        public static final StreamCodec<ByteBuf, Action> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Action::action, BIG_STRING, Action::json, Action::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Serveur -> client : donnees. */
    public record Data(String kind, String json) implements CustomPacketPayload {
        public static final Type<Data> TYPE = makeType("data");
        public static final StreamCodec<ByteBuf, Data> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Data::kind, BIG_STRING, Data::json, Data::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
