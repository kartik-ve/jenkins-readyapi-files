import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;

public class ReplaceDumpFileSetting {

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.out.println("Usage: java ReplaceDumpFileSetting <xml-file>");
            return;
        }

        Path xmlPath = Path.of(args[0]);
        String content = Files.readString(xmlPath, StandardCharsets.UTF_8);

        String regex =
            "<con:setting\\s+id=\"com\\.eviware\\.soapui\\.impl\\.support\\.AbstractHttpRequest@dump-file\">.*?</con:setting>";

        String replacement =
            "<con:setting id=\"com.eviware.soapui.impl.support.AbstractHttpRequest@dump-file\"/>";

        String updatedContent = content.replaceAll(regex, replacement);
        Files.writeString(xmlPath, updatedContent, StandardCharsets.UTF_8);
    }
}