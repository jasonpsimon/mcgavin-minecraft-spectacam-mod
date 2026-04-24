package net.reseraph.spectacam.util;

//? if >=26 {
/*import net.minecraft.network.chat.Component;*/
//?} else if (<1.19) {
/*import net.minecraft.text.LiteralText;*/
/*import net.minecraft.text.Text;*/
//?} else {
import net.minecraft.text.Text;
//?}

/**
 * Version-agnostic helpers for building chat-component text.
 *
 * Yarn (≤1.21.x) uses {@code net.minecraft.text.Text}; mojmaps (≥26.x)
 * uses {@code net.minecraft.network.chat.Component}. Pre-1.19 yarn lacked
 * the {@code Text.literal} factory and needed {@code new LiteralText(s)}.
 * Stonecutter picks the right branch; callers just do
 * {@code SpectaCamText.lit("...")}.
 */
public final class SpectaCamText {
    private SpectaCamText() {}

    //? if >=26 {
    /*public static Component lit(String s) {
        return Component.literal(s);
    }*/
    //?} else if (<1.19) {
    /*public static Text lit(String s) {
        return new LiteralText(s);
    }*/
    //?} else {
    public static Text lit(String s) {
        return Text.literal(s);
    }
    //?}
}
