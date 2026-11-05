package dev.vespera.client.cosmetic;

import dev.vespera.client.render.Voxel;

/**
 * Воксельные модели косметики Vespera (собственный дизайн, без чужих логотипов и персонажей).
 * Все размеры — в пикселях скина (1 px = 1/16 блока), система тела:
 * X — вправо, Y — вверх от ног, Z — назад (спина игрока = +2).
 */
public final class CosmeticModels {

    private CosmeticModels() {}

    // ================================================================ КРЫЛЬЯ

    public static final String[] WING_NAMES = {"Кристальные", "Праздничные", "Ангельские", "Тёмные перья"};
    public static final String[] WING_HINTS = {
            "Голубые ледяные осколки, с которых сыплются искорки",
            "Розовые веточки с подарками и звёздочками, пушистый центр",
            "Небольшие белые крылья из мягких перьев",
            "Большие чёрные крылья с серыми прожилками"};

    /**
     * @param kind  стиль (индекс WING_NAMES)
     * @param fold  угол отведения назад, радианы (взмах)
     * @param lift  подъём крыла вверх, радианы
     * @param time  секунды (для анимации частиц)
     * @param tint  цвет, к которому подмешивается модель (0 — без подмешивания)
     */
    public static void wings(Voxel v, int kind, double fold, double lift, double time, int tint, float tintK) {
        for (int s = -1; s <= 1; s += 2) {
            v.push();
            v.translate(1.5 * s, 21.5, 2.6);
            v.rotY(-s * fold);
            v.rotZ(s * lift);
            switch (kind) {
                case 1 -> festiveWing(v, s, time, tint, tintK);
                case 2 -> spriteWing(v, s, ANGEL, ANGEL_ROOT, 1.35, tint, tintK);
                case 3 -> spriteWing(v, s, DARK, DARK_ROOT, 1.1, tint, tintK);
                default -> {
                    spriteWing(v, s, CRYSTAL, CRYSTAL_ROOT, 1.25, tint, tintK);
                    crystalSparks(v, s, time, tint, tintK);
                }
            }
            v.pop();
        }
        if (kind == 1) { // пушистый центр между крыльями
            v.box(-1.8, 18.6, 2.2, 1.8, 22.0, 4.6, t(0xFFFFFFFF, tint, tintK * 0.3f));
            v.box(-1.0, 19.4, 4.5, 1.0, 21.2, 5.0, t(0xFFFFC8E6, tint, tintK));
        }
    }

    // ---- пиксельные крылья (генерируются один раз)
    private static final int[][] CRYSTAL, ANGEL, DARK;
    private static final int CRYSTAL_ROOT, ANGEL_ROOT, DARK_ROOT;

    static {
        int[] r = new int[1];
        CRYSTAL = genWing(18, 1, 9, 7, 4, 9, 3,
                new int[]{0xFF1B5FB8, 0xFFE6FAFF, 0xFF9EE4FF, 0xFF4FB8F5, 0xFF2E86E0}, true, r);
        CRYSTAL_ROOT = r[0];
        ANGEL = genWing(14, 1, 7, 5, 4, 8, 2,
                new int[]{0xFFC4CCD8, 0xFFFFFFFF, 0xFFF2F5F9, 0xFFE4E9F0, 0xFFCDD5E0}, false, r);
        ANGEL_ROOT = r[0];
        DARK = genWing(24, 1, 13, 9, 5, 14, 3,
                new int[]{0xFF0B0B0E, 0xFF3A3D46, 0xFF24262D, 0xFF17181D, 0xFF4C505B}, false, r);
        DARK_ROOT = r[0];
    }

    /**
     * Пиксельное крыло (правое: от плеча x = 0 наружу). Передний край поднимается от плеча
     * к кончику, перья к краю длиннее, на кончике снова короче; низ — «зубчики» перьев.
     * Цвета: pal = {контур, светлые кроющие, средние, маховые, прожилки}.
     * @param rootRow выход: номер строки, где y = 0 (уровень плеча)
     */
    private static int[][] genWing(int w, double rootTop, double peak, double tipTop, double lenRoot, double lenTip,
                                   int fw, int[] pal, boolean shine, int[] rootRow) {
        double[] top = new double[w], bot = new double[w];
        double maxT = -1e9, minB = 1e9;
        for (int x = 0; x < w; x++) {
            double t = x / (double) (w - 1);
            double tp = rootTop + (peak - rootTop) * Math.sin(Math.min(1, t / 0.85) * Math.PI / 2)
                    - Math.pow(Math.max(0, (t - 0.85) / 0.15), 2) * (peak - tipTop);
            double ln = lenRoot + (lenTip - lenRoot) * Math.sin(Math.min(1, t / 0.8) * Math.PI / 2)
                    - Math.max(0, (t - 0.8) / 0.2) * (lenTip * 0.45);
            double u = ((x % fw) + 0.5) / fw * 2 - 1;
            ln -= Math.pow(Math.abs(u), 1.6) * 2.6;
            top[x] = tp;
            bot[x] = tp - ln;
            maxT = Math.max(maxT, tp);
            minB = Math.min(minB, bot[x]);
        }
        int yTop = (int) Math.ceil(maxT) + 1, yBot = (int) Math.floor(minB) - 1;
        int rows = yTop - yBot + 1;
        int[][] g = new int[rows][w];
        for (int r = 0; r < rows; r++) {
            int y = yTop - r;
            for (int x = 0; x < w; x++) {
                if (!in(top, bot, x, y)) continue;
                boolean edge = !(in(top, bot, x - 1, y) && in(top, bot, x + 1, y)
                        && in(top, bot, x, y + 1) && in(top, bot, x, y - 1));
                double d = top[x] - y;
                int c;
                if (edge) c = pal[0];
                else if (d < 2) c = pal[1];
                else if (d < 4) c = pal[2];
                else if (x % fw == 0) c = pal[4];
                else c = pal[3];
                if (shine && !edge && (x + r) % 7 == 0 && d < 5) c = 0xFFFFFFFF;
                g[r][x] = c;
            }
        }
        rootRow[0] = yTop;
        return g;
    }

    private static boolean in(double[] top, double[] bot, int x, int y) {
        return x >= 0 && x < top.length && y >= bot[x] && y <= top[x];
    }

    /** Нарисовать пиксельное крыло: строка rootRow — на уровне плеча. */
    private static void spriteWing(Voxel v, int s, int[][] grid, int rootRow, double px, int tint, float tk) {
        int[][] g = grid;
        if (tk > 0f) {
            g = new int[grid.length][];
            for (int r = 0; r < grid.length; r++) {
                g[r] = grid[r].clone();
                for (int c = 0; c < g[r].length; c++) if (g[r][c] != 0) g[r][c] = Voxel.tint(g[r][c], tint, tk);
            }
        }
        v.push();
        v.translate(0, -(grid.length - 1 - rootRow) * px, 0);
        v.pixels(g, px, 1, s);
        v.pop();
    }

    /** Искорки, сыплющиеся с кристальных крыльев. */
    private static void crystalSparks(Voxel v, int s, double time, int tint, float tk) {
        for (int i = 0; i < 10; i++) {
            double fall = (time * (3.5 + (i % 3)) + i * 2.9) % 10.0;
            double x = 4 + (i * 1.71 % 16.0);
            int a = (int) (220 * (1 - fall / 10.0));
            if (a < 10) continue;
            int col = (a << 24) | (t(0xFFA6ECFF, tint, tk) & 0xFFFFFF);
            v.boxS(s, x, -2 - fall, 0.1, x + 0.8, -1.2 - fall, 0.9, col);
        }
    }

    /** Розовые веточки с подарками и звёздочками. */
    private static void festiveWing(Voxel v, int s, double time, int tint, float tk) {
        int[] gifts = {0xFFFF8FC8, 0xFF8FF0D2, 0xFFFFE08A, 0xFFA9C8FF, 0xFFFFB38A};
        for (int b = 0; b < 5; b++) {
            double ang = 0.95 - b * 0.42;                       // от верхней ветки к нижней
            double len = 9 + (b % 2) * 3.5;
            v.push();
            v.rotZ(s * ang);
            v.boxS(s, 0, -0.7, 0, len, 0.7, 1.2, t(0xFFF5A3D4, tint, tk));
            v.boxS(s, 0, 0.3, -0.05, len, 0.7, 1.25, t(0xFFFFC8E8, tint, tk));
            // звёздочка посередине ветки
            v.boxS(s, len * 0.55 - 0.6, 0.4, -0.1, len * 0.55 + 0.6, 1.6, 1.0, t(0xFFFFD54A, tint, tk * 0.4f));
            // подарок на конце, слегка покачивается
            v.translate(s * len, 0, 0.45);
            v.rotZ(Math.sin(time * 2.0 + b) * 0.12);
            int g = t(gifts[b % gifts.length], tint, tk * 0.4f);
            v.box(-1.6, -3.4, -1.6, 1.6, -0.2, 1.6, g);
            v.box(-0.4, -3.5, -1.65, 0.4, -0.1, 1.65, 0xFFFFFFFF);  // ленточка
            v.box(-1.65, -2.2, -0.4, 1.65, -1.4, 0.4, 0xFFFFFFFF);
            v.box(-0.9, -0.2, -0.3, 0.9, 0.6, 0.3, 0xFFFFFFFF);    // бантик
            v.pop();
        }
    }

    // ================================================================ РЮКЗАКИ

    public static final String[] BACKPACK_NAMES = {"Лягушонок", "Кожаный", "Молния", "Красный", "Vespera"};
    public static final String[] BACKPACK_HINTS = {
            "Зелёный лягушонок с глазками на крышке и улыбкой",
            "Коричневый кожаный рюкзак с ремешками и золотыми пряжками",
            "Чёрный спортивный рюкзак с белой молнией",
            "Красный рюкзак с чёрным поясом и белой молнией-застёжкой",
            "Фирменный тёмно-фиолетовый рюкзак Vespera со знаком V"};

    private static final int[][][] BP_FACE = {
            Voxel.grid(new String[]{"gggggggg", "gggggggg", "gpggggpg", "ggkggkgg", "gggkkggg", "gLLLLLLg", "gLLLLLLg", "gLLLLLLg", "ggLLLLgg", "gggggggg"},
                    'g', 0xFF39C98A, 'L', 0xFF86E6BE, 'k', 0xFF1E7A50, 'p', 0xFFFF9BB0),
            Voxel.grid(new String[]{"dddddddd", "dlddddld", "dgddddgd", "bsbllbsb", "bslbblsb", "bsbllbsb", "bgbbbbgb", "bsblbbsb", "bsbbblsb", "bbbbbbbb"},
                    'd', 0xFF6E4426, 'l', 0xFFA0703F, 'g', 0xFFE0B14A, 'b', 0xFF8A5A33, 's', 0xFF4E2F19),
            Voxel.grid(new String[]{"kkkkkkkk", "kkkkwwkk", "kkkwwkkk", "kkwwwwkk", "kkkkwwkk", "kkkwwkkk", "kkwwkkkk", "KKKKKKKK", "KwKKKKwK", "KKKKKKKK"},
                    'k', 0xFF15161A, 'K', 0xFF25272E, 'w', 0xFFF2F2F2),
            Voxel.grid(new String[]{"rrrrrrrr", "rwwwwwwr", "rrrsrrrr", "rrrrrrrr", "RRRRRRRR", "kkkkkkkk", "rrrrrrrr", "rrrrrrrr", "rRrrrrRr", "RRRRRRRR"},
                    'r', 0xFFC8202A, 'R', 0xFFA3161F, 'k', 0xFF141414, 'w', 0xFFEDEDED, 's', 0xFFBDBDBD),
            Voxel.grid(new String[]{"VVVVVVVV", "VVVVVVVV", "vavvvvav", "vAavvaAv", "vvavvavv", "vvAaaAvv", "vvvaavvv", "vvvAAvvv", "vvvvvvvv", "vsvvvvsv"},
                    'v', 0xFF2A1E3F, 'V', 0xFF362752, 'a', 0xFF9B7BFF, 'A', 0xFFC7B5FF, 's', 0xFFC9CED8)
    };
    /** Основной цвет корпуса и цвет боков/крышки для каждого рюкзака. */
    private static final int[][] BP_BODY = {
            {0xFF39C98A, 0xFF2FB078}, {0xFF8A5A33, 0xFF6E4426}, {0xFF15161A, 0xFF25272E},
            {0xFFC8202A, 0xFFA3161F}, {0xFF2A1E3F, 0xFF362752}};

    public static void backpack(Voxel v, int kind, int tint, float tk) {
        int k = Math.max(0, Math.min(BP_FACE.length - 1, kind));
        int body = t(BP_BODY[k][0], tint, tk), side = t(BP_BODY[k][1], tint, tk);
        v.box(-4, 12.5, 2, 4, 22.5, 6, body);                         // корпус
        v.box(-4.2, 21.0, 1.9, 4.2, 22.8, 6.2, side);                  // крышка
        for (int s = -1; s <= 1; s += 2) v.boxS(s, 4.0, 13.2, 2.8, 4.6, 18.0, 5.4, side); // боковые карманы
        int[][] face = BP_FACE[k];
        if (tk > 0f) face = tinted(face, tint, tk);
        v.push().translate(-4, 12.5, 6.0).pixels(face, 1, 0.3, 1).pop(); // лицевая сторона
        if (k == 0) { // лягушонок: глазки на крышке
            int g = t(0xFF39C98A, tint, tk);
            for (int s = -1; s <= 1; s += 2) {
                v.boxS(s, 1.0, 22.4, 3.0, 3.6, 25.0, 5.6, g);
                v.boxS(s, 1.4, 22.8, 5.5, 3.2, 24.6, 5.75, 0xFFF4FFF8);
                v.boxS(s, 1.9, 23.2, 5.7, 2.7, 24.2, 5.85, 0xFF1A1A1A);
            }
        }
        if (k == 4) v.box(-1.2, 22.7, 3.4, 1.2, 23.6, 4.4, 0xFFC9CED8); // ручка Vespera
    }

    private static int[][] tinted(int[][] g, int tint, float tk) {
        int[][] o = new int[g.length][];
        for (int r = 0; r < g.length; r++) {
            o[r] = g[r].clone();
            for (int c = 0; c < o[r].length; c++) if (o[r][c] != 0) o[r][c] = Voxel.tint(o[r][c], tint, tk);
        }
        return o;
    }

    // ================================================================ ПИТОМЦЫ

    public static final String[] PET_NAMES = {"Пингвин", "Мишка-табличка", "Рыжий кот", "Призрачок"};
    public static final String[] PET_HINTS = {
            "Маленький пингвин с жёлтым клювом, машет крылышками",
            "Табличка-мордочка медвежонка на палочке, покачивается",
            "Рыжий котик с зелёными глазами, виляет хвостом",
            "Полупрозрачный белый призрачок, плавно парит"};

    private static final int[][] PENGUIN_FACE = Voxel.grid(new String[]{
            "nnnnnn", "nkwwkn", "nwyywn", "nwwwwn", "pwwwwp", "nwwwwn", "nwwwwn", "nnwwnn", "nnnnnn"},
            'n', 0xFF1F2240, 'k', 0xFF111111, 'w', 0xFFF4F6FA, 'y', 0xFFFFC233, 'p', 0xFFFFB3C6);
    private static final int[][] BEAR_FACE = Voxel.grid(new String[]{
            "bbbbbbbb", "bkbbbbkb", "bbllllbb", "pblnnlbp", "bbllllbb", "bbbbbbbb"},
            'b', 0xFFA8774E, 'k', 0xFF2B1B12, 'l', 0xFFD9B28A, 'n', 0xFF5A3A24, 'p', 0xFFF29AA8);
    private static final int[][] CAT_FACE = Voxel.grid(new String[]{
            "ooooooo", "ogkokgo", "ooooooo", "oocpcoo", "occccco", "ooooooo"},
            'o', 0xFFF29A3E, 'g', 0xFF5BD16E, 'k', 0xFF161616, 'c', 0xFFFFE2BF, 'p', 0xFFE37A8A);
    private static final int[][] GHOST_FACE = Voxel.grid(new String[]{
            "wwwwww", "wwwwww", "wkwwkw", "wkwwkw", "pwwwwp", "wwkkww", "wwwwww", "wwwwww"},
            'w', 0xD8F2F6FF, 'k', 0xFF1C1C24, 'p', 0xFFFFB3C6);

    /** Питомец смотрит вперёд (−Z). Ноги/низ — у Y = 0. Морды — пиксельные «таблички» спереди. */
    public static void pet(Voxel v, int kind, double time, int tint, float tk) {
        switch (kind) {
            case 1 -> { // мишка-табличка
                v.push().rotZ(Math.sin(time * 1.6) * 0.08);
                v.box(-0.4, -2, -0.3, 0.4, 4.2, 0.3, 0xFF6B4A2E);
                int br = t(0xFFA8774E, tint, tk);
                v.box(-4, 4, -0.5, 4, 10, 0.5, br);
                for (int s = -1; s <= 1; s += 2) {
                    v.boxS(s, 2.4, 9.2, -0.45, 4.6, 11.6, 0.45, br);
                    v.boxS(s, 2.9, 9.7, -0.6, 4.1, 11.1, -0.45, 0xFFF29AA8);
                }
                v.push().translate(-4, 4, -0.75).pixels(face(BEAR_FACE, tint, tk), 1, 0.25, 1).pop();
                v.pop();
            }
            case 2 -> { // рыжий кот
                int o = t(0xFFF29A3E, tint, tk), cr = 0xFFFFE2BF;
                v.box(-2.5, 0, -1.5, 2.5, 5, 3.5, o);
                v.box(-1.6, 0.5, -1.7, 1.6, 4.2, -1.5, cr);
                v.box(-3, 5, -3, 3, 10, 2, o);
                for (int s = -1; s <= 1; s += 2) {
                    v.boxS(s, 1.4, 10, -2, 3.0, 11.8, 0, o);
                    v.boxS(s, 1.8, 10.2, -2.15, 2.6, 11.3, -2.0, 0xFFF7A1A8);
                }
                v.push().translate(-3, 5, -3.25).pixels(face(CAT_FACE, tint, tk), 6 / 7.0, 0.25, 1).pop();
                v.push().translate(0, 2, 3.5).rotZ(Math.sin(time * 3) * 0.35).rotX(-0.5)
                        .box(-0.6, 0, -0.6, 0.6, 6.5, 0.6, o).pop();                 // хвост
            }
            case 3 -> { // призрачок
                int w = (0xD8 << 24) | (t(0xFFF2F6FF, tint, tk) & 0xFFFFFF);
                v.box(-3, 2, -3, 3, 9, 3, w);
                v.box(-2.4, 9, -2.4, 2.4, 10, 2.4, w);
                for (int i = 0; i < 3; i++) {
                    double wob = Math.sin(time * 4 + i * 2) * 0.5;
                    v.box(-3 + i * 2, 0.5 + wob, -3, -1 + i * 2, 2, 3, w);
                }
                v.push().translate(-3, 2, -3.25).pixels(face(GHOST_FACE, tint, tk), 1, 0.25, 1).pop();
            }
            default -> { // пингвин
                int nv = t(0xFF1F2240, tint, tk), y = 0xFFFFC233;
                v.box(-3, 0, -2.5, 3, 9, 2.5, nv);
                v.push().translate(-3, 0, -2.75).pixels(face(PENGUIN_FACE, tint, tk), 1, 0.25, 1).pop();
                for (int s = -1; s <= 1; s += 2) {
                    v.boxS(s, 0.5, -0.6, -3.4, 2.4, 0, 0, y);                       // лапки
                    v.push().translate(s * 3, 6.6, 0).rotZ(s * (0.15 + 0.25 * (0.5 + 0.5 * Math.sin(time * 5))))
                            .boxS(s, 0, -4.6, -1.5, 0.8, 0, 1.5, nv).pop();          // крылышки
                }
                v.box(-0.8, 6.0, -3.6, 0.8, 6.8, -2.75, y);                         // клюв
            }
        }
    }

    private static int[][] face(int[][] g, int tint, float tk) {
        return tk > 0f ? tinted(g, tint, tk) : g;
    }

    private static int t(int argb, int tint, float k) {
        return k <= 0f ? argb : Voxel.tint(argb, tint, k);
    }
}
