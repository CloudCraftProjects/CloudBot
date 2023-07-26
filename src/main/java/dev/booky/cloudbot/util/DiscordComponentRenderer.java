package dev.booky.cloudbot.util;

import io.papermc.paper.text.PaperComponents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.flattener.ComponentFlattener;
import net.kyori.adventure.text.flattener.FlattenerListener;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.util.List;

// this is loosely based off adventure's ANSI serializer and renderer library
public final class DiscordComponentRenderer implements FlattenerListener {

    private static final String RESET = "0";

    // discord's foreground colors are VERY limited, and formatting (e.g. bold/underlined)
    // doesn't work properly; so just use these 8 colors and ignore all other style stuff
    private static final List<ColorData> COLORS = List.of(
            new ColorData(0x073642, "30"),
            new ColorData(0xDC232F, "31"),
            new ColorData(0x85900, "32"),
            new ColorData(0xB58900, "33"),
            new ColorData(0x268BD2, "34"),
            new ColorData(0xD33682, "35"),
            new ColorData(0x2AA198, "36"),
            new ColorData(0xFFFFFF, "37"));

    private final StringBuilder builder = new StringBuilder();

    private DiscordComponentRenderer() {
    }

    public static String render(Component component) {
        return render(component, PaperComponents.flattener());
    }

    public static String render(Component component, ComponentFlattener flattener) {
        DiscordComponentRenderer renderer = new DiscordComponentRenderer();
        flattener.flatten(component, renderer);
        return renderer.builder.toString();
    }

    private void append(String id) {
        this.builder.append("\u001b[").append(id).append('m');
    }

    @Override
    public void pushStyle(@NonNull Style style) {
        TextColor color = style.color();
        if (color != null) {
            this.append(TextColor.nearestColorTo(COLORS, color).id());
        }
    }

    @Override
    public void component(@NonNull String text) {
        this.builder.append(text);
    }

    @Override
    public void popStyle(@NonNull Style style) {
        if (style.color() != null) {
            this.append(RESET);
        }
    }

    private record ColorData(int color, String id) implements TextColor {
        @Override
        public int value() {
            return this.color;
        }
    }
}
