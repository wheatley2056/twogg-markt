package de.twogg.markt.core;

import com.google.gson.JsonElement;

/** Ein Angebot an der Verkaufstheke eines Stands: so viele Stück dieser Ware für so viel Geld. */
public final class Offer {
    public String id = "";
    /** Ware als serialisierter ItemStack (mit allen Daten, Anzahl 1). */
    public JsonElement item;
    /** Anzeigename beim Anlegen (für Listen, auch wenn das Item nicht lesbar ist). */
    public String name = "";
    /** Stück pro Kauf. */
    public int count = 1;
    /** Preis pro Kauf in Zehntel-Coins (vom Standbetreiber festgelegt, folgt nicht dem Kurs). */
    public long price = 10;
    /** Verkauft insgesamt (Käufe). */
    public long sold;
}
