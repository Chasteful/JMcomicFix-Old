package net.ccbluex.liquidbounce.injection.mixins.minecraft.client.texture;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.PlayerSkinTextureDownloader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;

@Environment(EnvType.CLIENT)
@Mixin(PlayerSkinTextureDownloader.class)
public class MixinPlayerSkinTextureDownloader {

    /**
     * @author KotlinModule
     * @reason Remove vanilla size check to allow non-64x64 skin textures (e.g. 128x128 from Netease servers).
     */
    @Overwrite
    private static NativeImage remapTexture(NativeImage image, String uri) {
        int width = image.getWidth();
        int height = image.getHeight();

        if ((width == 64 && (height == 32 || height == 64)) || (width == 128 && height == 128)) {
            boolean isOld = (width == 64 && height == 32);

            if (isOld) {
                NativeImage nativeImage = new NativeImage(64, 64, true);
                nativeImage.copyFrom(image);
                image.close();
                image = nativeImage;
                nativeImage.fillRect(0, 32, 64, 32, 0);
                nativeImage.copyRect(4, 16, 16, 32, 4, 4, true, false);
                nativeImage.copyRect(8, 16, 16, 32, 4, 4, true, false);
                nativeImage.copyRect(0, 20, 24, 32, 4, 12, true, false);
                nativeImage.copyRect(4, 20, 16, 32, 4, 12, true, false);
                nativeImage.copyRect(8, 20, 8, 32, 4, 12, true, false);
                nativeImage.copyRect(12, 20, 16, 32, 4, 12, true, false);
                nativeImage.copyRect(44, 16, -8, 32, 4, 4, true, false);
                nativeImage.copyRect(48, 16, -8, 32, 4, 4, true, false);
                nativeImage.copyRect(40, 20, 0, 32, 4, 12, true, false);
                nativeImage.copyRect(44, 20, -8, 32, 4, 12, true, false);
                nativeImage.copyRect(48, 20, -16, 32, 4, 12, true, false);
                nativeImage.copyRect(52, 20, -8, 32, 4, 12, true, false);
            }

            stripAlpha(image, 0, 0, 32, 16);
            if (isOld) stripColor(image, 32, 0, 64, 32);
            stripAlpha(image, 0, 16, 64, 32);
            stripAlpha(image, 16, 48, 48, 64);

            return image;
        }

        //Don't delete this.
        if (width == 128 && height == 128) {
            return image;
        }

        image.close();
        throw new IllegalStateException("Discarding incorrectly sized (" + width + "x" + height + ") skin texture from " + uri);
    }

    @Unique
    private static void stripColor(NativeImage image, int x1, int y1, int x2, int y2) {
        for (int i = x1; i < x2; i++) {
            for (int j = y1; j < y2; j++) {
                int k = image.getColorArgb(i, j);
                if ((k >> 24 & 0xFF) < 128) return;
            }
        }
        for (int i = x1; i < x2; i++) {
            for (int j = y1; j < y2; j++) {
                image.setColorArgb(i, j, image.getColorArgb(i, j) & 0xFFFFFF);
            }
        }
    }

    @Unique
    private static void stripAlpha(NativeImage image, int x1, int y1, int x2, int y2) {
        for (int i = x1; i < x2; i++) {
            for (int j = y1; j < y2; j++) {
                int c = image.getColorArgb(i, j);
                image.setColorArgb(i, j, (c & 0xFFFFFF) | 0xFF000000);
            }
        }
    }
}
