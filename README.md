# Abandoned Rose Symbolism — Rosa relicta

Java 17+ · Swing · Java2D · без runtime-зависимостей.

![Роза](preview.png)

## Запуск

Распакуйте архив. Windows: `run.bat`. Linux/macOS: `sh run.sh`.

```sh
java -jar abandoned-rose-symbolism.jar
java -jar abandoned-rose-symbolism.jar --export rose.png
```

Сборка: `build.bat` / `sh build.sh` (нужен JDK 17+).
Проверки: `test.bat` / `sh test.sh`.
Maven с JUnit 6.1.3: `mvn clean verify` (тесты + JAR в target).

[Архитектура HTML](architekture.html) · [Подробная Wiki](wiki/Home.md)

## BACK + REDRAW — версия 2.3.0

Внизу окна теперь две кнопки в одном Art Nouveau стиле: **BACK** слева и **REDRAW** справа. Каждый показанный вариант хранится в памяти текущего запуска. BACK возвращает точно предыдущее сохранённое превью. После BACK кнопка REDRAW работает как «вперёд» и восстанавливает уже сохранённый следующий вариант; когда достигнут самый новый вариант, REDRAW снова генерирует новую вариацию и добавляет её в историю.

Новая вариация меняет цвет, насыщенность, яркость, seed фактуры и стиль. Четыре стиля: VELVET, ENGRAVING, ART_NOUVEAU, FADED_INK. Следующий сгенерированный стиль отличается от предыдущего. Resize сохраняет выбранный результат. Начальное изображение и CLI-экспорт сохраняют исходную композицию.

**Обновление существующего проекта:** перенесите обновлённый `src/main/java/org/example/RoseDrawing.java`, ресурсы из `src/main/resources/ui/` и тесты из этого архива. Пересоберите через `mvn clean package` и запускайте JAR из target. Готовый JAR в корне этого архива тоже обновлён; `run.bat` запускает его.

## Покрытие JaCoCo

`mvn clean verify` запускает JUnit и проверяет минимум 90% строк и 90% ветвей. После сборки актуальный отчёт: `target/site/jacoco/index.html`. Подробности: [Wiki / Testing](wiki/Testing.md).

Art Nouveau assets лежат в `src/main/resources/ui/`; Maven и обновлённые `build.sh` / `build.bat` включают их в JAR автоматически.
