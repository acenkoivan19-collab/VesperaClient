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
                case 2 -> featherWing(v, s, 6, 1.7, 1.5, 0.11,
                        new double[]{9.5, 6.5, 3.6}, new int[]{0xFFE6EBF2, 0xFFF3F6FA, 0xFFFFFFFF}, 0xFFD3DAE4, tint, tintK);
                case 3 -> featherWing(v, s, 9, 2.3, 2.3, 0.13,
                        new double[]{16, 10.5, 5.5}, new int[]{0xFF16171C, 0xFF22242B, 0xFF30333B}, 0xFF5B606B, tint, tintK);
                default -> crystalWing(v, s, time, tint, tintK);
            }
            v.pop();
        }
        if (kind == 1) { // пушистый центр между крыльями
            v.box(-1.8, 18.6, 2.2, 1.8, 22.0, 4.6, t(0xFFFFFFFF, tint, tintK * 0.3f));
            v.box(-1.0, 19.4, 4.5, 1.0, 21.2, 5.0, t(0xFFFFC8E6, tint, tintK));
        }
    }

    /** Перья рядами: R рядов × K перьев, верх изогнут дугой, нижние ряды короче. */
    private static void featherWing(Voxel v, int s, int k, double fw, double rise, double rise2,
                                    double[] len, int[] rowColor, int streak, int tint, float tk) {
        for (int r = 0; r < len.length; r++) {
            double z0 = (len.length - 1 - r) * 0.55;
            for (int i = 0; i < k; i++) {
                double x0 = i * fw, x1 = x0 + fw * 0.96;
                double top = 1 + i * rise - i * i * rise * rise2;
                double l = Math.max(2.2, len[r] - i * (len[r] * 0.07));
                int col = rowColor[r];
                if (r == 0 && i % 3 == 2) col = streak;               // прожилки на длинных перьях
                v.boxS(s, x0, top - l, z0, x1, top, z0 + 0.7, t(col, tint, tk));
            }
        }
    }

    /** Ледяные осколки веером + падающие искорки. */
    private static void crystalWing(Voxel v, int s, double time, int tint, float tk) {
        int[] grad = {0xFFC9F4FF, 0xFF9FE2FF, 0xFF6FCBFF, 0xFF45ABFF, 0xFF2D8CF0, 0xFF1F6FD6};
        for (int i = 0; i < 6; i++) {
            v.push();
            v.translate(s * (i * 2.2), 1.5 + i * 1.3 - i * i * 0.12, 0.3);
            v.rotZ(s * (0.18 + i * 0.07));
            double l = 7.5 - i * 0.55;
            v.boxS(s, 0, -l, 0, 1.7, 0, 1.1, t(grad[i], tint, tk));
            v.boxS(s, 0.25, -1.2, -0.15, 1.45, 0.6, 1.25, t(0xFFE8FBFF, tint, tk * 0.5f)); // светлая «шапка»
            v.pop();
        }
        // искорки: каждая падает со своей скоростью и тает
        for (int i = 0; i < 10; i++) {
            double fall = (time * (4.5 + (i % 3)) + i * 2.9) % 12.0;
            double x = 1 + (i * 1.37 % 11.0);
            int a = (int) (200 * (1 - fall / 12.0));
            if (a < 10) continue;
            int col = (a << 24) | (t(0xFFA6ECFF, tint, tk) & 0xFFFFFF);
            v.boxS(s, x, -1 - fall, 0.2, x + 0.8, -0.2 - fall, 1.0, col);
        }
    }

    /** Розовые веточки с подарками и звёздочками. */
    private static void festiveWing(Voxel v, int s, double time, int tint, float tk) {
        int[] gifts = {0xFFFF8FC8, 0xFF8FF0D2, 0xFFFFE08A, 0xFFA9C8FF, 0xFFFFB38A};
        for (int b = 0; b < 5; b++) {
            double ang = 0.95 - b * 0.42;                       // от верхней ветки к нижней
            double len = 8 + (b % 2) * 3;
            v.push();
            v.rotZ(s * ang);
            v.boxS(s, 0, -0.45, 0, len, 0.45, 0.9, t(0xFFF5A3D4, tint, tk));
            // звёздочка посередине ветки
            v.boxS(s, len * 0.55 - 0.6, 0.4, -0.1, len * 0.55 + 0.6, 1.6, 1.0, t(0xFFFFD54A, tint, tk * 0.4f));
            // подарок на конце, слегка покачивается
            v.translate(s * len, 0, 0.45);
            v.rotZ(Math.sin(time * 2.0 + b) * 0.12);
            int g = t(gifts[b % gifts.length], tint, tk * 0.4f);
            v.box(-1.2, -2.6, -1.2, 1.2, -0.2, 1.2, g);
            v.box(-0.3, -2.7, -1.25, 0.3, -0.1, 1.25, 0xFFFFFFFF);  // ленточка
            v.box(-1.25, -1.7, -0.3, 1.25, -1.1, 0.3, 0xFFFFFFFF);
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

    public static void backpack(Voxel v, int kind, int tint, float tk) {
        double x0 = -4, x1 = 4, y0 = 12.5, y1 = 22, z0 = 2, z1 = 6;
        switch (kind) {
            case 1 -> { // кожаный
                v.box(x0, y0, z0, x1, y1, z1, t(0xFF8A5A33, tint, tk));
                v.box(-4.2, 18.8, 1.8, 4.2, 22.4, 6.3, t(0xFF6E4426, tint, tk));
                for (int s = -1; s <= 1; s += 2) {
                    v.boxS(s, 1.6, 13.0, 6.0, 2.6, 19.4, 6.3, t(0xFF4E2F19, tint, tk));
                    v.boxS(s, 1.35, 14.0, 6.2, 2.85, 15.3, 6.5, 0xFFE0B14A);   // пряжки
                    v.boxS(s, 4.0, 13.2, 3.0, 4.6, 17.2, 5.6, t(0xFF7A4D2B, tint, tk)); // боковые карманы
                }
            }
            case 2 -> { // молния
                v.box(x0, y0, z0, x1, y1, z1, t(0xFF15161A, tint, tk));
                v.box(-4.1, 12.4, 1.9, 4.1, 15.0, 6.1, t(0xFF25272E, tint, tk));
                int w = 0xFFF2F2F2;
                v.box(0.2, 19.6, 6.0, 2.0, 21.0, 6.3, w);
                v.box(-0.6, 18.2, 6.0, 1.2, 19.8, 6.3, w);
                v.box(-1.4, 17.4, 6.0, 1.6, 18.4, 6.3, w);
                v.box(-0.4, 15.8, 6.0, 1.0, 17.6, 6.3, w);
                v.box(-1.2, 14.6, 6.0, 0.2, 16.0, 6.3, w);
                for (int s = -1; s <= 1; s += 2) v.boxS(s, 4.0, 13.0, 4.6, 4.3, 21.0, 5.2, 0xFFBFC4CC); // светоотражатели
            }
            case 3 -> { // красный
                v.box(x0, y0, z0, x1, y1, z1, t(0xFFC8202A, tint, tk));
                v.box(-4.1, 16.0, 1.9, 4.1, 17.0, 6.25, 0xFF141414);
                v.box(-3.2, 20.2, 6.0, 3.2, 20.6, 6.2, 0xFFEDEDED);
                v.box(-0.4, 19.6, 6.0, 0.4, 20.4, 6.35, 0xFFBDBDBD);
                for (int s = -1; s <= 1; s += 2) v.boxS(s, 4.0, 13.0, 3.0, 4.7, 16.0, 5.5, 0xFF1C1C1C);
            }
            case 4 -> { // vespera
                v.box(x0, y0, z0, x1, y1, z1, t(0xFF2A1E3F, tint, tk));
                v.box(-4.1, 20.5, 1.9, 4.1, 22.1, 6.1, t(0xFF362752, tint, tk));
                v.box(-1.2, 22.0, 3.4, 1.2, 23.0, 4.4, 0xFFC9CED8);               // ручка
                int acc = t(0xFF9B7BFF, tint, tk * 0.5f);
                v.push().translate(-1.3, 18.8, 6.05).rotZ(0.38).box(-0.5, -4.2, 0, 0.5, 0, 0.3, acc).pop();
                v.push().translate(1.3, 18.8, 6.05).rotZ(-0.38).box(-0.5, -4.2, 0, 0.5, 0, 0.3, acc).pop();
                for (int s = -1; s <= 1; s += 2) v.boxS(s, 2.4, 13.4, 6.0, 3.4, 14.4, 6.3, 0xFFC9CED8);
            }
            default -> { // лягушонок
                int g = t(0xFF39C98A, tint, tk);
                v.box(x0, y0, z0, x1, y1, z1, g);
                v.box(-2.6, 13.4, 6.0, 2.6, 18.2, 6.25, t(0xFF86E6BE, tint, tk));   // животик
                for (int s = -1; s <= 1; s += 2) {
                    v.boxS(s, 1.0, 21.6, 3.0, 3.6, 24.4, 5.6, g);                   // глаза-бугорки
                    v.boxS(s, 1.4, 22.0, 5.5, 3.2, 24.0, 5.75, 0xFFF4FFF8);
                    v.boxS(s, 1.9, 22.4, 5.7, 2.7, 23.4, 5.85, 0xFF1A1A1A);
                    v.boxS(s, 2.6, 19.0, 6.0, 3.6, 19.8, 6.2, 0xFFFF9BB0);          // щёчки
                }
                v.box(-1.6, 19.2, 6.0, 1.6, 19.7, 6.2, 0xFF1E7A50);                 // улыбка
                v.box(-2.0, 19.6, 6.0, -1.5, 20.2, 6.2, 0xFF1E7A50);
                v.box(1.5, 19.6, 6.0, 2.0, 20.2, 6.2, 0xFF1E7A50);
            }
        }
    }

    // ================================================================ ПИТОМЦЫ

    public static final String[] PET_NAMES = {"Пингвин", "Мишка-табличка", "Рыжий кот", "Призрачок"};
    public static final String[] PET_HINTS = {
            "Маленький пингвин с жёлтым клювом, машет крылышками",
            "Табличка-мордочка медвежонка на палочке, покачивается",
            "Рыжий котик с зелёными глазами, виляет хвостом",
            "Полупрозрачный белый призрачок, плавно парит"};

    /** Питомец смотрит вперёд (−Z). Ноги/низ — у Y = 0. */
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
                    v.boxS(s, 1.6, 7.6, -0.65, 2.6, 8.6, -0.5, 0xFF2B1B12);     // глаза
                    v.boxS(s, 2.6, 6.2, -0.62, 3.6, 6.9, -0.5, 0xFFF4A3B0);     // щёчки
                }
                v.box(-1.6, 5.4, -0.62, 1.6, 7.3, -0.5, 0xFFD9B28A);             // мордочка
                v.box(-0.6, 6.4, -0.75, 0.6, 7.1, -0.6, 0xFF5A3A24);             // нос
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
                    v.boxS(s, 0.9, 7.4, -3.15, 2.1, 8.6, -3.0, 0xFF5BD16E);
                    v.boxS(s, 1.3, 7.4, -3.25, 1.7, 8.6, -3.15, 0xFF161616);
                }
                v.box(-1.3, 5.6, -3.2, 1.3, 7.2, -3.0, cr);
                v.box(-0.4, 6.8, -3.35, 0.4, 7.3, -3.2, 0xFFE37A8A);
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
                for (int s = -1; s <= 1; s += 2) {
                    v.boxS(s, 0.8, 5.6, -3.15, 1.8, 7.6, -3.0, 0xFF1C1C24);
                    v.boxS(s, 1.8, 4.6, -3.12, 2.8, 5.2, -3.0, 0xFFFFB3C6);
                }
                v.box(-0.6, 4.2, -3.15, 0.6, 4.8, -3.0, 0xFF1C1C24);
            }
            default -> { // пингвин
                int nv = t(0xFF1F2240, tint, tk), y = 0xFFFFC233;
                v.box(-3, 0, -2.5, 3, 8.5, 2.5, nv);
                v.box(-2.2, 0.5, -2.75, 2.2, 6.6, -2.5, 0xFFF4F6FA);
                for (int s = -1; s <= 1; s += 2) {
                    v.boxS(s, 0.7, 6.4, -2.75, 1.7, 7.4, -2.5, 0xFFFFFFFF);
                    v.boxS(s, 1.0, 6.5, -2.9, 1.5, 7.2, -2.75, 0xFF111111);
                    v.boxS(s, 0.5, -0.6, -3.4, 2.4, 0, 0, y);                       // лапки
                    v.push().translate(s * 3, 6.4, 0).rotZ(s * (0.15 + 0.25 * (0.5 + 0.5 * Math.sin(time * 5))))
                            .boxS(s, 0, -4.6, -1.5, 0.8, 0, 1.5, nv).pop();          // крылышки
                }
                v.box(-0.8, 5.0, -3.6, 0.8, 5.8, -2.5, y);                          // клюв
            }
        }
    }

    private static int t(int argb, int tint, float k) {
        return k <= 0f ? argb : Voxel.tint(argb, tint, k);
    }
}
