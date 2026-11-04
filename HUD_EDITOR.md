# Шаг: чистка + Редактор HUD

## 1. Удалена вкладка TerraHUD
- удалён `ui/TerraHudPanel.java`;
- из `module/Category.java` убран `TERRAHUD`, из `render/Icons.java` — его иконка,
  из `module/Module.java` — проверка;
- из `ui/ClickGuiScreen.java` убраны навигация, счётчик, заголовок, сетка, поиск, клики,
  скролл и клавиши раздела;
- `compat/TerraBridge.java` **оставлен**, его никто не вызывает. Конфиг TerraHUD не трогаем,
  TerraHUD рисует своё как раньше.

## 2. Удалены визуалы, которые не дотягивают по оформлению
| Модуль | Почему |
|---|---|
| ArrayList | плоские прямоугольники с полоской. Новый — шаг 3 (таблетки, blur-подложка) |
| Damage Particles | голые строки цифр без оформления |
| Ambience | плоская заливка всего экрана, выглядит как грязный фильтр |
| Target ESP | плоские тёмные плашки-подписи (стиль TerraHUD) + дублирует ESP самого TerraHUD |
| Waypoints | те же плоские плашки + дублирует метки, которые TerraHUD и так рисует |

Записи этих модулей в `vespera-client.json` просто игнорируются.
`ModuleConfig` при первом запуске теперь включает только Watermark.

## 3. Редактор HUD
Новые файлы:
- `ui/HudEditorScreen.java` — экран редактора.

Изменённые файлы:
- `module/HudModule.java` — `layoutable()`, `defaultX/Y()`, `size()`, `isEditing()`;
- `module/ModuleConfig.java` — секция `layout` (x, y, scale, alpha), `layout()`,
  `resetLayout()`, `markDirty()`;
- `render/WorldDraw.java` — `applyLayout(vg, module, w, h)` и `layoutRect(...)`;
- `module/ModuleManager.java` — оборачивает `renderHud` layoutable-модулей в
  `nvgSave` → `applyLayout` → `renderHud` → `nvgRestore`, плюс `hudModules()`;
- `render/NvgRenderer.java` — рисует HUD под редактором, `hudScale()`;
- `ui/ClickGuiScreen.java` — кнопка «Редактор HUD» в шапке (поиск сузился до 230);
- `VesperaClient.java` + lang — бинд **H**;
- Watermark, Keystrokes, Target HUD, Notifications — рисуют от (0,0), сообщают `size()`,
  в редакторе показывают превью.

**Модули-порты Фазы 1.** Кольца, крылья, шляпа, рой, хитбоксы и прочие эффекты в мире
привязаны к точкам МИРА, а не экрана. Двигать их по экрану бессмысленно, поэтому для
них `layoutable() == false`. Из Фазы 1 к экрану привязан только Hit Color, но это
вспышка на весь экран, его тоже не двигаем. Если позже появятся экранные порты TerraHUD
(Фаза 2: FPS, координаты, броня…), они попадут в редактор автоматически.

## Чеклист
- [ ] Игра запускается, меню открывается на Right Shift, C — зум.
- [ ] H (или кнопка в шапке): экран затемнён, включённые HUD-модули в неоновых рамках
      с подписями.
- [ ] Перетащил Watermark, Keystrokes и Target HUD (у него в редакторе превью на тебе).
      У края и в центре модуль прилипает, линия подсвечивается. С Alt не прилипает.
- [ ] Колесо меняет размер, Shift+колесо — прозрачность, рядом тултип с цифрами.
- [ ] ПКМ сбрасывает модуль, «Сбросить всё» — все модули.
- [ ] Esc или «Готово» — выход. Из меню возвращает в меню, по H — в игру.
- [ ] Перезапуск: всё на местах, тот же размер и прозрачность (`layout` в
      `vespera-client.json`).
- [ ] Тумблеры модулей работают, в `latest.log` нет ошибок `vespera`.
