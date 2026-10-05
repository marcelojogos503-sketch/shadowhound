package com.shadowhound;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** Constroi um circulo de ritual: chao de sangue e pedra negra, 6 velas e 6 jaulas de barras de ferro. */
public class RitualSite {

    /** surface = primeira posicao livre acima do chao (resultado de getHeightmapPos). */
    public static void build(ServerLevel level, BlockPos surface) {
        int cx = surface.getX();
        int cz = surface.getZ();
        int y = surface.getY() - 1;
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState black = Blocks.BLACKSTONE.defaultBlockState();
        BlockState blood = Blocks.NETHER_WART_BLOCK.defaultBlockState();

        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                double d = Math.sqrt((double) (dx * dx + dz * dz));
                if (d > 4.5D) continue;
                BlockPos g = new BlockPos(cx + dx, y, cz + dz);
                BlockState floor;
                if (d < 2.5D) floor = black;
                else if (d >= 3.5D || dx == 0 || dz == 0) floor = blood;
                else floor = black;
                level.setBlock(g, floor, 3);
                for (int h = 1; h <= 3; h++) {
                    level.setBlock(g.above(h), air, 3);
                }
            }
        }

        for (int i = 0; i < 6; i++) {
            double a = Math.toRadians(i * 60.0D);
            int px = cx + (int) Math.round(Math.cos(a) * 4.0D);
            int pz = cz + (int) Math.round(Math.sin(a) * 4.0D);
            level.setBlock(new BlockPos(px, y + 1, pz),
                    Blocks.RED_CANDLE.defaultBlockState().setValue(BlockStateProperties.LIT, true), 3);

            int bx = cx + (int) Math.round(Math.cos(a) * 7.0D);
            int bz = cz + (int) Math.round(Math.sin(a) * 7.0D);
            level.setBlock(new BlockPos(bx, y, bz), black, 3);
            for (int h = 1; h <= 3; h++) {
                level.setBlock(new BlockPos(bx, y + h, bz), Blocks.IRON_BARS.defaultBlockState(), 3);
            }
        }
    }
}
