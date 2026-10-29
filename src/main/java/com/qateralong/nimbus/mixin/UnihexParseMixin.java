package com.qateralong.nimbus.mixin;

import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import java.io.IOException;
import java.io.InputStream;
import net.minecraft.client.gui.font.providers.UnihexProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(UnihexProvider.class)
public abstract class UnihexParseMixin {
    @Inject(method = "readFromStream", at = @At("HEAD"), cancellable = true)
    private static void nimbus$parse(InputStream stream, UnihexProvider.ReaderOutput output, CallbackInfo ci) throws IOException {
        byte[] data = stream.readAllBytes();
        ByteArrayList digits = new ByteArrayList(128);
        int length = data.length;
        int position = 0;
        for (int line = 0; ; line++) {
            int colon = position;
            while (colon < length && data[colon] != ':') {
                colon++;
            }
            boolean found = colon < length;
            int size = colon - position;
            if (size == 0 && !found) {
                break;
            }
            if (!found || size < 4 || size > 6) {
                throw new IllegalArgumentException("Invalid entry at line " + line + ": expected 4, 5 or 6 hex digits followed by a colon");
            }
            int codepoint = 0;
            for (int i = position; i < colon; i++) {
                codepoint = codepoint << 4 | nimbus$hex(line, data[i]);
            }
            int start = colon + 1;
            int end = -1;
            for (int width = 32; width <= 128; width += 32) {
                int candidate = start + width;
                if (candidate >= length || data[candidate] == '\n') {
                    end = Math.min(candidate, length);
                    break;
                }
            }
            if (end < 0 || end - start != 32 && end - start != 64 && end - start != 96 && end - start != 128) {
                end = start;
                while (end < length && data[end] != '\n') {
                    end++;
                }
            }
            digits.clear();
            digits.addElements(0, data, start, end - start);
            UnihexProvider.LineData glyph = switch (end - start) {
                case 32 -> UnihexProvider.ByteContents.read(line, digits);
                case 64 -> UnihexProvider.ShortContents.read(line, digits);
                case 96 -> UnihexProvider.IntContents.read24(line, digits);
                case 128 -> UnihexProvider.IntContents.read32(line, digits);
                default -> throw new IllegalArgumentException("Invalid entry at line " + line + ": expected hex number describing (8,16,24,32) x 16 bitmap, followed by a new line");
            };
            output.accept(codepoint, glyph);
            position = end < length ? end + 1 : length;
        }
        ci.cancel();
    }

    @Unique
    private static int nimbus$hex(int line, byte value) {
        if (value >= '0' && value <= '9') {
            return value - '0';
        }
        if (value >= 'A' && value <= 'F') {
            return value - 'A' + 10;
        }
        if (value >= 'a' && value <= 'f') {
            return value - 'a' + 10;
        }
        throw new IllegalArgumentException("Invalid entry at line " + line + ": expected hex digit, got " + (char) value);
    }
}
