# Сборка и структура

## JDK без Maven

Windows: `build.bat`. Linux/macOS: `sh build.sh`. Результат — `abandoned-rose-symbolism.jar` в корне. Скрипты используют javac --release 17 и jar с Main-Class. Внешние зависимости не нужны.

## Maven

```sh
mvn clean package
java -jar target/abandoned-rose-symbolism-3.0.0.jar
```

Maven POM содержит compiler-plugin, surefire-plugin, jar-plugin и JaCoCo. Команда `mvn clean verify` запускает четыре тестовых класса (80 запусков) и проверяет порог покрытия. JUnit 6.1.3 используется только для тестов. Скрипты test.bat/test.sh запускают mvn clean verify с JUnit и JaCoCo. Готовый JAR в корне и Maven JAR в target — разные файлы; запускайте именно пересобранный вариант.

## Файлы

- `src/main/java/org/example/`: `RoseDrawing` (API, UI, CLI), `RoseRenderer` (конвейер), `RoseGeometry` + `RoseMesh` (3D-модель), `RoseLighting` (тени, AO, свет), `RosePalette` (цвета), `RoseRasterizer` (растеризация и стили), `RoseBackdrop` (Java2D-слои), `RoseMath`, совместимый launcher `RoseDrawing_2`.
- `src/test/java/org/example/`: `RoseDrawingTest`, `RoseSwingSmokeTest`, `RoseRendererTest`, `RoseInteractionTest`, вспомогательный `SwingTestSupport` и автономная `RoseRenderingCheck`.
- `examples/`: экспорт BUD/FULL с росой, ракурсы, сравнение «до/после».
- `pom.xml`: альтернативная Maven-сборка.
- `build.*`, `run.*`, `test.*`: команды для Windows и POSIX.
- `abandoned-rose-symbolism.jar`: готовая сборка Java 17.
- `preview.png`: результат экспорта.
- `architekture.html`: автономный обзор, использует соседний preview.png.
- `wiki/`: подробная документация Markdown.

Архив поставки не содержит .git, .idea, старые out и временный target. История исходного репозитория в приложенном исходном архиве не переписывалась. Изменённые файлы можно перенести в свой checkout и оформить обычным commit.
