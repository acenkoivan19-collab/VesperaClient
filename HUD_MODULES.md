# Шаг: нативные HUD-модули + ПКМ-настройки

## 1. Новые HUD-модули (по умолчанию ВЫКЛЮЧЕНЫ)

Все модули — `HudModule` слоя HUD, то есть `layoutable() == true`. Позиция, масштаб
и прозрачность применяются общим `WorldDraw.applyLayout`, своего кода раскладки у них нет.
Оформление общее (`render/HudStyle`), шрифты Inter, цифры табличные (`Draw.textTabular`).

| Модуль | Файл `module/hud/` | Данные | Свои настройки |
|---|---|---|---|
| FPS & Ping | FpsPingModule | `getCurrentFps()`, `PlayerListEntry.getLatency()` (SP в одиночке) | — |
| Coordinates | CoordsModule | позиция игрока | дробные, пересчёт Незер/Мир |
| CPS | CpsModule | свой счётчик: опрос кнопок мыши GLFW каждый кадр, окно 1 с | показывать ПКМ |
| Clock | ClockModule | `LocalTime` | секунды |
| Session | SessionModule | `util/SessionTracker`: от входа в мир, сброс при выходе в меню | — |
| RAM | RamModule | `Runtime` | процент |
| Sprint Status | SprintStatusModule | элитры / полёт / плаваю / крадусь / бег / иду / стою | — |
| Direction | DirectionModule | yaw → лента компаса С/СВ/В… | стили «Лента» / «Компакт» |
| Server | ServerModule | `getCurrentServerEntry()` / одиночная игра | адрес |
| Reach | ReachModule | глаза → точка `crosshairTarget` | — |
| Saturation | SaturationModule | `HungerManager`: голод и насыщение полосами | — |
| Cooldowns | CooldownsModule | `ItemCooldownManager` (перл, хорус, заряд ветра, рог): круговой прогресс + секунды | — |
| Pickup Log | PickupModule | свой трекер инвентаря раз в тик, TTL 6 с, пока открыт экран — не считает | — |
| Effects | EffectsModule | иконки `textures/mob_effect`, кольцо прогресса, мигает < 10 с | стили «Список» / «Иконки», названия |
| Armor | ArmorModule | 4 слота + рука, иконки `textures/item`, полоса прочности | стили «Колонка» / «Ряд», рука, проценты |
| Music | MusicModule | `gameDir/terra-nowplaying.json` не чаще раза в 500 мс и только при изменении; обложка base64 → NanoVG | обложка |

«Цель» уже есть: это **Target HUD** (имя, ХП с плавной полосой, броня, дистанция по
`crosshairTarget` и последней ударенной сущности). Второй такой модуль я не делал,
Target HUD просто переведён на общий стиль и настройки.

Иконки предметов и эффектов (`render/ItemIcons`) берутся PNG-файлами прямо из ресурсов
игры (с учётом ресурспаков) и рисуются NanoVG с NEAREST-фильтром, без DrawContext.
У предметов с 3D-моделью (щит, сундук) плоской текстуры нет, для них рисуется запасной
значок.

## 2. ПКМ-панель настроек

ПКМ по карточке в меню (Right Shift) или по модулю в Редакторе HUD (H) открывает
панель `ui/ModuleSettingsPanel`:
- **Масштаб** (0.5–2.0) и **Прозрачность** (10–100 %) — та же раскладка, что в редакторе;
- **Стиль**: Soft / Glass / Минимал / Контур, у части модулей свои 2 варианта;
- **Цвет**: «T» — цвет темы, палитра из 9 цветов и полоса оттенков для своего цвета;
- тумблеры **Фон**, **Тень**, **Анимация** и свои подопции модуля;
- кнопки **Сбросить позицию** и **Сбросить настройки**.

Панель закрывается по Esc, клику мимо или повторному ПКМ по тому же модулю.
Всё применяется сразу: модули читают настройки каждый кадр.

### Где хранится (`config/vespera-client.json`)
```json
"modules": { "CPS": { "enabled": true, "style": 1, "color": "#FF4C8DFF",
                      "background": true, "shadow": true, "animation": true,
                      "icon": true, "labels": true, "right": true } },
"layout":  { "CPS": { "x": 0.01, "y": 0.26, "scale": 1.25, "alpha": 0.8 } }
```
**Масштаб и прозрачность лежат в `layout`, а не в `modules.<имя>`.** Их уже пишет
Редактор HUD. Если хранить их ещё и в `modules`, появятся два источника правды,
которые разъедутся. Панель и редактор правят один и тот же объект.
Старый формат `"modules": {"Имя": true}` читается без потерь и при следующем
сохранении переписывается в новый.

## Новые файлы
```
module/setting/Setting, BoolSetting, ChoiceSetting, ColorSetting, NumberSetting
module/hud/InfoHudModule + 16 модулей из таблицы
render/HudStyle.java, render/ItemIcons.java
util/SessionTracker.java
ui/ModuleSettingsPanel.java
```
## Изменённые файлы
```
module/Module.java          настройки модуля (setting(), settings())
module/HudModule.java       общие настройки: style/color/background/shadow/animation, styles(), step()
module/ModuleConfig.java    modules.<имя> = {enabled + настройки}, чтение старого формата,
                            resetLayout() сбрасывает только позицию, resetLayoutFull() — всё
module/ModuleManager.java   регистрация 16 модулей
render/Draw.java            textTabular(), tabularWidth(), progressRing()
render/Icons.java           значки HUD (Glyph)
render/NvgRenderer.java     context(), очистка иконок при выходе
ui/ClickGuiScreen.java      ПКМ по карточке → панель; Esc/скролл сначала в панель
ui/HudEditorScreen.java     ПКМ → панель (раньше был сброс), «Сбросить всё» — полный сброс
module/impl/Watermark, TargetHud, Keystrokes, Notifications — общий стиль, цвет, анимация
VesperaClient.java          SessionTracker.tick
```
Удалено в этом шаге: ничего.

## Чеклист
- [ ] Игра запускается, меню на Right Shift, редактор на H, зум на C.
- [ ] Каждый из 16 новых модулей включается в меню и сразу рисуется (Effects, Cooldowns,
      Pickup, Music без данных видны в редакторе как превью).
- [ ] В редакторе новые модули тащатся, масштабируются колесом, прозрачность — Shift+колесо.
- [ ] ПКМ по карточке и по модулю в редакторе открывает панель. Стиль, цвет, тумблеры
      и слайдеры применяются в тот же кадр. Esc, клик мимо и повторный ПКМ закрывают её.
- [ ] «Сбросить позицию» возвращает модуль на место по умолчанию, масштаб сохраняется.
- [ ] Перезапуск: настройки (`modules.<имя>`) и раскладка (`layout`) на месте.
- [ ] Старые модули (кольца, крылья, Zoom…) и их состояния не сломались.
- [ ] В `latest.log` нет ошибок `vespera`.
