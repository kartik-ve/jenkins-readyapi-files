import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

public class CopyFailedResponses {

    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println("Usage: java CopyFailedResponses <sourceDir> <destinationDir>");
            System.exit(1);
        }

        Path sourceDir = Paths.get(args[0]);
        Path destinationDir = Paths.get(args[1]);

        if (!Files.isDirectory(sourceDir)) {
            System.err.println("Source path is not a directory: " + sourceDir);
            System.exit(1);
        }

        try {
            Files.walkFileTree(sourceDir, new SimpleFileVisitor<Path>() {

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                        throws IOException {

                    if (file.getFileName().toString().endsWith("~FAILED.txt")) {
                        Path relativePath = sourceDir.relativize(file);
                        Path targetFile = destinationDir.resolve(relativePath);

                        // Create parent directories ONLY when needed
                        Files.createDirectories(targetFile.getParent());

                        Files.copy(
                                file,
                                targetFile,
                                StandardCopyOption.REPLACE_EXISTING
                        );
                    }
                    return FileVisitResult.CONTINUE;
                }
            });

            System.out.println("Copied Failed Responses!");

        } catch (IOException e) {
            System.err.println("Error during copy:");
            e.printStackTrace();
        }
    }
}
