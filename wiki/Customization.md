# Настройка изображения

Без правки кода доступны: REDRAW (новый стиль, сорт-цвет, насыщенность, экспозиция, seed), Bloom, Petal layers, Morning dew, ракурс (мышь, стрелки, Turntable) и все флаги CLI (`--style`, `--hue`, `--seed`, `--size`, `--yaw`, `--pitch`, `--bloom`, `--layers`, `--dew`). После ручных правок исходников пересоберите JAR (`build.*`).

| Что изменить | Где |
|---|---|
| Число лепестков | `RoseGeometry.petalCount` (`6 × layers + 5`) |
| Форма лепестков по стадиям | `RoseGeometry.petalShape` — `phi0`, `bend`, `lip`, `length`, `halfWidth`, `roll` |
| Волнистость края | `petalShape`: `ruffle`, `ruffleFreq` |
| Наклон цветка к зрителю | `tilt` в `RoseGeometry.build` |
| Стебель | `RoseGeometry.stemCurve` (контрольные точки), радиус в `addStem` |
| Листья | два вызова `addLeaf` в `build`: узел на стебле, направление, размер |
| Опавшие лепестки | `spots` в `addFallenPetals` (x, z, масштаб) |
| Цвета сортов | таблица `STOPS` в `RosePalette` (линейный RGB) |
| Цвет листьев, стебля, шипов | `RosePalette.albedo` |
| Свет | `KEY_VIEW`, `RIM_VIEW`, `KEY_COLOR`, `RIM_COLOR` в `RoseLighting` |
| Материалы (обхват, просвет, отлив, блик) | таблица `MATERIAL` в `RoseLighting` |
| Небо для AO | `RoseLighting.sky` |
| Композиция и масштаб | `CENTER_X/Y`, `ZOOM`, `ELEVATION` в `RoseRenderer` |
| Сглаживание | `RoseRenderer.supersamplingFor` |
| Фон, арка, нимб, фактура, подпись | `RoseBackdrop` |
| Штриховка гравюры | `RoseRasterizer.engraving`, плотность — `frame.hatch` в `RoseRenderer` |

## Пример: тёмная «чёрная» роза

В `RosePalette.STOPS` уменьшите первую строку (crimson) примерно вдвое, оставив зелёный и синий каналы очень малыми относительно красного: насыщенный тёмный красный должен оставаться красным после тональной компрессии.

## Пример: крупный экспорт

```sh
java -jar abandoned-rose-symbolism.jar --export big.png --size 4000
```

Начиная с 1250 px используется самая плотная сетка; память растёт примерно пропорционально площади (полосы растеризатора ограничивают буферы, но итоговое изображение и фон — полноразмерные).

## Программно

```java
var look = new RoseDrawing.Appearance(1858, .62f, 1, 1, RoseDrawing.Style.ENGRAVING);
var flower = new RoseDrawing.FlowerOptions(RoseDrawing.BloomStage.FULL, 7, true);
var image = RoseDrawing.renderScene(1600, 1600, look, flower, new RoseDrawing.View(40, 10));
```

## История и случайные варианты

BACK/REDRAW/FORWARD работают как в 2.4/2.5: история хранит `RoseState(Appearance, FlowerOptions)`, REDRAW после BACK отбрасывает ветку вперёд. Ракурс (`View`) в историю не входит, как и размер окна: REDRAW сохраняет текущий поворот, двойной щелчок или Home возвращают исходный.

Новый вариант REDRAW: hue сдвигается на 0.12–0.88 круга (то есть другой сорт), saturation 0.55–1.20, exposure 0.85–1.25, стиль — один из трёх других.
