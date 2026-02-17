import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

public class ExtractFailedAPIResponses {

    private static void parseFilesExtractResponses(Path dirPath, Path outputDirPath) throws IOException {

        AtomicBoolean outputDirCreated = new AtomicBoolean(false);

        try (Stream<Path> paths = Files.walk(dirPath)) {
            paths
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().endsWith("FAILED.txt"))
                .forEach(inputFile -> {
                    try {
                        String lastLine = readLastLine(inputFile);

                        if (looksLikeJson(lastLine)) {

                            // Create output directory only when needed (first valid response)
                            if (!outputDirCreated.get()) {
                                Files.createDirectories(outputDirPath);
                                outputDirCreated.set(true);
                            }

                            String inputFileName = inputFile.getFileName().toString();
                            String outputFileName = inputFileName.replaceFirst("\\.txt$", ".json");
                            Path outputFile = outputDirPath.resolve(outputFileName);

                            Files.write(
                                outputFile,
                                (lastLine + System.lineSeparator()).getBytes(StandardCharsets.UTF_8),
                                StandardOpenOption.CREATE,
                                StandardOpenOption.TRUNCATE_EXISTING
                            );
                        }

                    } catch (IOException e) {
                        System.err.println("Error processing file: " + inputFile);
                        e.printStackTrace();
                    }
                });
        }
    }

    private static String readLastLine(Path file) throws IOException {
        String lastLine = null;

        try (Stream<String> lines = Files.lines(file, StandardCharsets.UTF_8)) {
            for (String line : (Iterable<String>) lines::iterator) {
                lastLine = line;
            }
        }

        return lastLine;
    }

    private static boolean looksLikeJson(String line) {
        if (line == null) {
            return false;
        }

        String trimmed = line.trim();

        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            return false;
        }

        long openBraces = trimmed.chars().filter(ch -> ch == '{').count();
        long closeBraces = trimmed.chars().filter(ch -> ch == '}').count();

        return openBraces == closeBraces;
    }

    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println("Usage: java ExtractFailedAPIResponses <inputDir> <outputDir>");
            System.exit(1);
        }

        Path inputDir = Paths.get(args[0]);
        Path outputDir = Paths.get(args[1]);

        try {
            parseFilesExtractResponses(inputDir, outputDir);
            System.out.println("Extraction completed successfully.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
