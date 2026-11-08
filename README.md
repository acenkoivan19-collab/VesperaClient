> **Разработка переехала в `soglasenka99/vespera-src`** (`mods-src/vespera-client`).
> Этот репозиторий больше не собирается: автосборка отключена, релиз `dev-build` не обновляется.
> Все визуалы отсюда (Hit Marker, Totem Pop, Item Glow, Projectile Trails, Block Effects,
> Screen Effects, Render Quality, Item Physics, Auto Sprint и др.) уже перенесены в общий мод.

# Vespera Client — NanoVG ClickGUI / Fabric 1.21.11

ClickGUI в стиле Dark Soft UI. Вся отрисовка идёт через **NanoVG (LWJGL, GL3-бэкенд)**.
`DrawContext`, `BufferBuilder` и `Tessellator` Minecraft не используются совсем.
`Screen` нужен только для того, чтобы получать ввод (мышь и клавиатуру).

## Структура

```
src/main/java/dev/vespera/client/
├── VesperaClient.java          бинд Right Shift, тики модулей, автосохранение, выход
├── mixin/WindowMixin.java      хук «конец кадра» (перед swapBuffers)
├── module/
│   ├── Module.java             состояние + onEnable/onDisable/onTick
│   ├── HudModule.java          модуль, рисующий HUD (только когда включён)
│   ├── ModuleManager.java      реестр, get(Class), слушатели, диспетчер тиков/HUD
│   ├── StateListener.java
│   ├── ModuleConfig.java       config/vespera-client.json
│   └── impl/                   модули (образец HUD: WatermarkModule)
├── render/                     NanoVG, GL-стейт, примитивы, иконки, Brand (логотип)
└── ui/                         ClickGuiScreen, ModuleCard, SearchField, Toggle, Anim, Theme
```

## Связка тумблер ↔ модуль

```
клик по карточке ─► ClickGuiScreen (очередь кликов, hit-test по текущему кадру)
                └─► module.toggle() ─► setEnabled() ─► onEnable() / onDisable()
                                                   └─► StateListener'ы (сохранение конфига)
тик клиента ─► ModuleManager.tick()      ─► onTick()    только у включённых
кадр        ─► ModuleManager.renderHud() ─► renderHud() только у включённых HudModule
```

Тумблер в GUI своего состояния не хранит, а каждый кадр берёт его из `module.isEnabled()`.

**Чтобы фича реально выключалась**, её код должен жить внутри модуля:
- логика каждого тика — в `onTick()`;
- отрисовка — в `HudModule.renderHud()`;
- одноразовые изменения (настройки, подписки) — в `onEnable()`, а откат — в `onDisable()`;
- в миксинах первой строкой проверяй состояние: `if (!ModuleManager.get(MyModule.class).isEnabled()) return;`.

## Модули

| Раздел | Модуль | Как работает |
|---|---|---|
| Combat | Hit Color | вспышка экрана, когда урон получаешь ты (порт TerraHUD) |
| Combat | Target HUD | панель цели: последняя ударенная (6 сек, `AttackEntityCallback`) или под прицелом |
| Combat | Crosshair | свой прицел на NanoVG; ванильный скрыт `InGameHudMixin` |
| Visuals | Jump Circles | кольца при приземлении |
| Visuals | Trails | градиентный след за игроком |
| Visuals | China Hat | конус над головой (вид от 3-го лица) |
| Visuals | Block Overlay | обводка блока под прицелом, плавно перетекает |
| Visuals | Hand View | сдвиг/масштаб рук (`HeldItemRendererMixin`) |
| World | Time Changer | клиентское время суток (полночь) |
| World | Weather Changer | ясная погода на клиенте |
| Interface | Watermark | логотип + FPS |
| Interface | Keystrokes | WASD / LMB / RMB / пробел |
| Interface | Notifications | тосты при переключении модулей (подписка на `StateListener`) |
| Utility | Zoom | зум на **C** (`GameRendererMixin` меняет FOV) |
| Utility | FPS Boost | выключает тени и облака, при выключении возвращает твои настройки |
| Cosmetics | Halo | светящееся кольцо над головой (3-е лицо) |
| Appearance | Theme | фиолетовый акцент вместо бирюзового |

3D-эффекты (кольца, след, шляпа, нимб, обводка, вспышки, цифры урона) рисуются NanoVG
поверх кадра через `Projection` (камера + реальный FOV кадра). Они видны сквозь блоки,
а при включённом View Bobbing слегка «плавают».

Ещё заготовки (`stub(...)`): Custom Fog, Particles+, Hotbar, Screenshot+, Discord RPC,
Cape, Wings, Menu Blur, Custom Font. Им нужен ванильный рендер (модели, предметы, шейдеры)
или внешние библиотеки, поэтому их нельзя сделать только на NanoVG.

Миксины фич лежат в отдельном конфиге `vespera.features.mixins.json` (`required: false`,
`defaultRequire: 0`). Если какой-то инжект не применится, игра не упадёт, просто эта фича
не сработает. Ищи `vespera` в `latest.log`.

## Сборка и установка в лаунчер

1. Шрифты Inter положи в `src/main/resources/assets/vespera/fonts/` (см. `PUT_FONTS_HERE.txt`).
2. Запусти **`build.bat`** двойным кликом. Скрипт:
   - находит установленный JDK 21 (Temurin);
   - при первом запуске сам скачивает Gradle 9.2.1 в папку `.gradle-dist`;
   - собирает мод (первая сборка скачивает Minecraft и идёт несколько минут);
   - **кладёт готовый `vespera-client-1.0.0.jar` в `F:\usermods\1.21.11`** и удаляет
     оттуда старые версии мода. Лаунчер сам копирует моды из этой папки в игру.
3. Запусти 1.21.11 через лаунчер и нажми **Right Shift**.

После любого изменения кода снова запускай `build.bat`, и обновление сразу окажется в лаунчере.
Папку назначения можно поменять в `gradle.properties` (`launcher_mods_dir`).

Если сборка упала, скопируй текст ошибки и пришли Claude.

## Как это работает

- **Где рисуется.** С версии 1.21.6 ванильный GUI рендерится отложенно, в самом конце кадра.
  Если рисовать в `Screen#render`, HUD окажется поверх меню. Поэтому меню рисуется
  в `Window#swapBuffers` (HEAD), прямо в дефолтный framebuffer, у которого есть
  stencil-буфер, нужный NanoVG.
- **GL-стейт.** NanoVG меняет блендинг, трафарет, VAO, текстуры и pixel store.
  `GlStateBackup` сохраняет всё это перед кадром и потом возвращает назад.
  Иначе в следующем кадре сломается кэш `GlStateManager` Minecraft.
- **Адаптивный масштаб.** Панель 920×600 «логических» px масштабируется так, чтобы занимать
  не больше 92% окна. Меню влезает на любом разрешении и не зависит от GUI Scale игры.
- **Анимации.** Используется lerp с коэффициентом `1 − e^(−speed·dt)`, поэтому скорость
  одинаковая на 60 и на 1000 FPS. Анимируются hover карточек и пунктов, тумблеры,
  скользящая подсветка раздела, плавный скролл, фокус поиска, открытие и закрытие.
- **Поиск.** Работает по всем разделам сразу (название + описание). `Ctrl+F` ставит фокус,
  `Ctrl+Backspace` очищает, `Esc` сначала снимает фокус, второй `Esc` закрывает меню.

## Честные примечания

- **«Blur» у активного пункта** сделан приёмом NanoVG: полупрозрачный градиент, мягкое
  свечение и блик. Это выглядит как матовое стекло, но настоящего размытия фона нет.
  Для настоящего blur нужен отдельный проход: скопировать кадр в FBO и прогнать через
  gaussian-шейдер. Это можно добавить отдельно.
- **FPS.** NanoVG рисует всё меню за десятки draw call'ов, это доли миллисекунды.
  Итоговый FPS всё равно ограничен рендером мира Minecraft. Меню его почти не снижает,
  но и выше «1000+» не поднимет.
- **Маппинги.** Код написан под Yarn и новые типы ввода из 1.21.9+:
  `Click`, `KeyInput`, `CharInput`, `KeyBinding.Category`. Если IDE подсвечивает имя метода
  или класса, сверь его по своим маппингам. Если `Window#swapBuffers` не находится,
  перенеси инжект на `RenderSystem#flipFrame` (HEAD).


## Редактор HUD (свободная раскладка)

Открывается клавишей **H** или кнопкой «Редактор HUD» в шапке меню.

- ЛКМ — перетащить модуль. Магнит тянет к краям и центру экрана, линия подсвечивается.
  С зажатым Alt магнит выключен.
- Колесо над модулем меняет масштаб (0.5–2.0), Shift+колесо — прозрачность (0.1–1.0).
  Шаг 0.05, цифры показываются в тултипе.
- ПКМ сбрасывает модуль, «Сбросить всё» — все модули. Esc или «Готово» — выход с сохранением.
- Раскладка хранится в `config/vespera-client.json` → `layout`:
  `{"Watermark":{"x":0.006,"y":0.01,"scale":1.0,"alpha":1.0}}`.

**Как подключить новый HUD-модуль к редактору.** Ничего делать не нужно, если `layer()`
равен `LAYER_HUD` (или если `layoutable()` возвращает `true`). Модуль рисует себя от (0,0)
и вызывает `size(w, h)`. Позицию, масштаб и прозрачность ДО `renderHud` применяет
`ModuleManager` через `WorldDraw.applyLayout(vg, module, w, h)`.
Начальная позиция задаётся в `defaultX()` / `defaultY()`. Чтобы модуль было видно
в редакторе без данных (как Target HUD без цели), рисуй пример, когда `isEditing() == true`.

- [MENU_FIX.md](MENU_FIX.md): фикс ПКМ-панели, режимы визуалов, минимализм, новые HUD-модули.
