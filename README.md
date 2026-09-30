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

## REDRAW — версия 2.1.0

Кнопка внизу окна меняет цвет, насыщенность, яркость, seed фактуры и стиль. Четыре стиля: VELVET, ENGRAVING, ART_NOUVEAU, FADED_INK. Следующий стиль отличается от предыдущего. Resize сохраняет результат. Начальное изображение и CLI-экспорт сохраняют исходную композицию.

**Обновление существующего проекта:** замените `src/main/java/org/example/RoseDrawing.java` целиком. Ваши исправленные тесты можно оставить. Пересоберите через `mvn clean package` и запускайте JAR из target. Готовый JAR в корне этого архива тоже обновлён; `run.bat` запускает его.
