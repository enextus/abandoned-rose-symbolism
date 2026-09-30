# Сборка и структура

## JDK без Maven

Windows: `build.bat`. Linux/macOS: `sh build.sh`. Результат — `abandoned-rose-symbolism.jar` в корне. Скрипты используют javac --release 17 и jar с Main-Class. Внешние зависимости не нужны.

## Maven

```sh
mvn clean package
java -jar target/abandoned-rose-symbolism-2.0.0.jar
```

Maven POM содержит compiler-plugin и jar-plugin. Maven не выполняет автономный RoseRenderingCheck автоматически: для него используйте test.bat/test.sh. Готовый JAR в корне и Maven JAR в target — разные файлы; запускайте именно пересобранный вариант.

## Файлы

- `src/main/java/org/example/`: приложение и совместимый launcher.
- `src/test/java/org/example/`: автономная проверка.
- `pom.xml`: альтернативная Maven-сборка.
- `build.*`, `run.*`, `test.*`: команды для Windows и POSIX.
- `abandoned-rose-symbolism.jar`: готовая сборка Java 17.
- `preview.png`: результат экспорта.
- `architekture.html`: автономный обзор, использует соседний preview.png.
- `wiki/`: подробная документация Markdown.

Архив поставки не содержит .git, .idea, старые out и временный target. История исходного репозитория в приложенном исходном архиве не переписывалась. Изменённые файлы можно перенести в свой checkout и оформить обычным commit.
