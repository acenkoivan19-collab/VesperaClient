# Фаза 1 — мировые визуалы TerraHUD → Vespera

Все модули рисуются через NanoVG и `WorldDraw`/`Projection`. Геометрия, тайминги и цвета
по умолчанию взяты 1:1 из `WorldRings.java` / `TerraHudClient.java` / `RingTexture.java`.
Новые модули по умолчанию **выключены**: состояние хранится только в `ModuleConfig`.

## Модули

| Модуль Vespera | Порт из TerraHUD | Что выключить в TerraHUD |
|---|---|---|
| Jump Circles | WorldRings «кольца» + tickMovement (спавн на отрыве, 3 волны / 90 мс, 900 мс, 0.3→1.6, 4 стиля) | Кольца прыжка |
| Trails | tickTrails + «Шлейф» (>2.5 б/с, каждый 2-й тик, ≤20 точек, 1200 мс; точки / лента / оба) | Следы |
| Swarm | «Рой» (кольцо / сфера Фибоначчи / спираль / волна, гаснет за 3 с простоя) | Рой (Swarm) |
| Wings | drawWingsFor (2 слоя перьев, взмах 0.006 / 0.009 / 0.014) | Крылья и нимб |
| Halo | drawHaloFor (билборд-кольцо 0.32 на росте +0.55, 3 искры) | Крылья и нимб |
| China Hat | drawHatOn (10 граней, рост +0.28, R = SIZE/20, H = 0.25 + SIZE·0.02, спин 1.5 рад/с) | Шляпа |
| Hitboxes | «Хитбоксы» (рамка / заливка 45, луч взгляда 2.5, цвета игрок / кристалл / прочие) | Хитбоксы |
| Hit Color | hurtTint (экран 110·hurtTime/maxHurtTime, #FF3030) | Hit color |
| Hit Burst | onAttack + drawHitBurst (420 мс, 7 стилей, комбо 3 с) | Хит-партиклы |
| Target ESP | drawTargetEsp / drawEntityBox (рамка + имя + дистанция, damp 0.45) | ESP-рамка |
| Waypoints | drawWaypoints (ромб, луч < 128 блоков, прижим к краю, ≤ 8) | Метки |

Ещё у восьми старых модулей Vespera, которые дублируют TerraHUD, теперь стоит префикс
`[Дубль TerraHUD: …]`: Crosshair, Target HUD, Keystrokes, Watermark, Контур блока,
Время мира, FPS Boost, Модель в руках.

## Не читы

- **Hitboxes** и **Target ESP** в режиме 1 рисуются только для сущностей в прямой видимости.
  `EntityScan` раз в 3 тика делает рейкаст от глаз игрока (`canSee`), сущность за блоком
  не рисуется. Режим 0 (цель в прицеле) видим по определению.
- **Крылья, нимб и шляпа** на себе видны всегда, на чужих игроках только в прямой видимости.
  Иначе косметика сквозь стену выдаёт, где стоит игрок.
- **Кольца, след, рой, свои метки** видны сквозь блоки: это косметика.

## Новые файлы

```
util/MotionTracker.java      скорость и последнее движение (порт tickMovement)
util/EntityScan.java         сущности рядом (24 бл., ≤12+12, раз в 3 тика) + прямая видимость
util/CosmeticTargets.java    на ком рисовать косметику
module/impl/SwarmModule.java
module/impl/WingsModule.java
module/impl/HitboxesModule.java
module/impl/HitBurstModule.java
module/impl/TargetEspModule.java
module/impl/WaypointsModule.java
```

## Изменённые файлы

```
render/Projection.java       + pixelsPerBlock(), projectClamped()
render/WorldDraw.java        + disc, polarRing, quad, quadGradient, beam
module/impl/JumpCirclesModule.java, TrailsModule.java, HaloModule.java,
  ChinaHatModule.java, HitColorModule.java      переписаны под геометрию TerraHUD
module/impl/ZoomModule.java  проверку isEnabled() перенёс в миксин (первой строкой)
mixin/feature/GameRendererMixin.java  первая строка — проверка isEnabled() модуля Zoom
module/ModuleManager.java    регистрация новых модулей, заглушка Wings заменена модулем
VesperaClient.java           тики MotionTracker/EntityScan, удар → HitBurst
gradle.properties            loom 1.13.4, yarn build.6, fabric-api 0.141.6, loader 0.20.1
8 модулей                    префикс [Дубль TerraHUD: …] в описании
```

## Что удалить

Удалять ничего не нужно: файлы заменяются целиком. Старый Hit Color (вспышка вокруг
ударенной сущности) удалён: на его место встал порт TerraHUD. В TerraHUD код в этой фазе
не трогаем, только выключи тумблеры из таблицы выше.

## Метки

Модуль Waypoints **читает** `config/terrahud.json` (поле `waypoints`) и ничего не пишет.
Добавлять и удалять метки пока можно только в TerraHUD. Редактор меток в меню Vespera
появится в одной из следующих фаз.

## Чеклист самопроверки

- [ ] `build.bat` проходит без ошибок, в `latest.log` нет `vespera … failed` или `crashed`.
- [ ] В TerraHUD выключены: Кольца прыжка, Следы, Рой, Крылья и нимб, Шляпа, Хитбоксы,
      Hit color, Хит-партиклы, ESP-рамка, Метки.
- [ ] Свежий конфиг (удалить `config/vespera-client.json`): все 11 новых модулей выключены.
- [ ] Jump Circles: прыжок даёт 3 расходящихся кольца, стили 0–3 меняются через `STYLE`.
- [ ] Trails: бег по земле оставляет пятна, стоя на месте следов нет.
- [ ] Swarm: кружит при движении и гаснет примерно через 3 с после остановки.
- [ ] Wings / Halo / China Hat: видны на себе в F5. На другом игроке пропадают,
      когда он уходит за стену.
- [ ] Hitboxes: моб за блоком без рамки, вышел из-за угла — рамка и луч появились.
- [ ] Target ESP: в режиме 0 рамка только на цели в прицеле, в режиме 1 сквозь стены не рисуется.
- [ ] Hit Color: получил урон — красная вспышка экрана, гаснет вместе с анимацией урона.
- [ ] Hit Burst: удар даёт вспышку в точке попадания, стиль 6 показывает ×комбо.
- [ ] Waypoints: метки из TerraHUD видны с дистанцией, вне кадра прижаты к краю.
      Если поменять метку в TerraHUD, она обновится за ~2 с.
- [ ] Каждый тумблер: выключил — эффект пропал в тот же кадр; перезапуск сохраняет состояние.
- [ ] Zoom (C) по-прежнему работает, 3D-эффекты при зуме не разъезжаются.
