import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

public class SoapUIReportGenerator {

    static class TestCase {
        String name;
        String status;
        double time;
        String failedStep;

        public TestCase(String name, String status, double time, String failedStep) {
            this.name = name;
            this.status = status;
            this.time = time;
            this.failedStep = failedStep;
        }
    }

    static class TestSuite {
        String name;
        double time;
        String status;
        List<TestCase> testCases = new ArrayList<>();

        public TestSuite(String name, double time, String status) {
            this.name = name;
            this.time = time;
            this.status = status;
        }
    }

    static class TestResults {
        List<TestSuite> testSuites = new ArrayList<>();
        int totalTests = 0;
        int totalPassed = 0;
        int totalFailed = 0;
        double totalTime = 0.0;
    }

    public static TestResults parseJUnitXml(File xmlFile) throws Exception {
        TestResults results = new TestResults();

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(xmlFile);
        doc.getDocumentElement().normalize();

        NodeList suiteNodes;

        // Handle both testsuite and testsuites root elements
        if (doc.getDocumentElement().getTagName().equals("testsuites")) {
            suiteNodes = doc.getElementsByTagName("testsuite");
        } else {
            suiteNodes = doc.getChildNodes();
        }

        for (int i = 0; i < suiteNodes.getLength(); i++) {
            if (suiteNodes.item(i) instanceof Element) {
                Element suiteElement = (Element) suiteNodes.item(i);

                if (!suiteElement.getTagName().equals("testsuite")) {
                    continue;
                }

                String nameVal = suiteElement.getAttribute("name");
                String testSuiteName = nameVal.substring(nameVal.indexOf(".") + 1);
                if (testSuiteName.isEmpty())
                    testSuiteName = "Unknown Suite";

                double suiteTime = parseDouble(suiteElement.getAttribute("time"));

                TestSuite testSuite = new TestSuite(testSuiteName, suiteTime, "PASSED");

                NodeList testCaseNodes = suiteElement.getElementsByTagName("testcase");

                for (int j = 0; j < testCaseNodes.getLength(); j++) {
                    Element testCaseElement = (Element) testCaseNodes.item(j);

                    String testCaseName = testCaseElement.getAttribute("name");
                    if (testCaseName.isEmpty())
                        testCaseName = "Unknown Test";

                    double testTime = parseDouble(testCaseElement.getAttribute("time"));

                    String status = "PASSED";
                    String failedStep = "";

                    // Check for failure
                    NodeList failures = testCaseElement.getElementsByTagName("failure");

                    if (failures.getLength() > 0) {
                        status = "FAILED";
                        testSuite.status = "FAILED";

                        String failureText = failures.item(0).getTextContent();

                        int begIdx = failureText.indexOf("<b>") + 3;
                        int endIdx = failureText.indexOf("Failed", begIdx);
                        failedStep = failureText.substring(begIdx, endIdx).trim();

                        results.totalFailed++;
                    } else {
                        results.totalPassed++;
                    }

                    testSuite.testCases.add(new TestCase(testCaseName, status, testTime, failedStep));
                    results.totalTests++;
                    results.totalTime += testTime;
                }

                results.testSuites.add(testSuite);
            }
        }

        return results;
    }

    public static TestResults mergeResults(List<TestResults> resultsList) {
        TestResults merged = new TestResults();

        for (TestResults results : resultsList) {
            merged.testSuites.addAll(results.testSuites);
            merged.totalTests += results.totalTests;
            merged.totalPassed += results.totalPassed;
            merged.totalFailed += results.totalFailed;
            merged.totalTime += results.totalTime;
        }

        return merged;
    }

    public static void generatePieChartImage(
            int passedCount,
            int failedCount,
            String outputFile) throws Exception {

        int size = 220;
        int padding = 10;

        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Transparent background
        g.setComposite(AlphaComposite.Clear);
        g.fillRect(0, 0, size, size);
        g.setComposite(AlphaComposite.SrcOver);

        int diameter = size - (padding * 2);
        int center = size / 2;
        int radius = diameter / 2;

        int total = passedCount + failedCount;
        double passedPercent = total > 0 ? (passedCount * 100.0) / total : 0;
        double failedPercent = total > 0 ? (failedCount * 100.0) / total : 0;

        // Angles
        double passedAngle = passedPercent * 360.0 / 100.0;

        int startAngle = 90;
        int passedSweep = (int) Math.round(-passedAngle);
        int failedSweep = -360 - passedSweep;

        // --- Draw slices ---
        g.setColor(new Color(0, 128, 0)); // passed
        g.fillArc(padding, padding, diameter, diameter, startAngle, passedSweep);

        g.setColor(Color.RED); // failed
        g.fillArc(padding, padding, diameter, diameter,
                startAngle + passedSweep, failedSweep);

        // --- Text settings ---
        g.setColor(Color.WHITE);
        g.setFont(new Font("Source Sans Pro", Font.BOLD, 13));

        // Passed text
        drawSliceText(
                g,
                center,
                center,
                radius * 0.65,
                startAngle + passedSweep / 2.0,
                passedCount,
                passedPercent);

        // Failed text
        drawSliceText(
                g,
                center,
                center,
                radius * 0.65,
                startAngle + passedSweep + failedSweep / 2.0,
                failedCount,
                failedPercent);

        g.dispose();
        ImageIO.write(image, "png", new File(outputFile));
    }

    private static void drawSliceText(
            Graphics2D g,
            int centerX,
            int centerY,
            double textRadius,
            double angleDeg,
            int count,
            double percent) {

        // Skip tiny slices (prevents unreadable overlap)
        if (percent < 5) {
            return;
        }

        double angleRad = Math.toRadians(angleDeg);

        int x = (int) (centerX + textRadius * Math.cos(angleRad));
        int y = (int) (centerY - textRadius * Math.sin(angleRad));

        String line1 = String.valueOf(count);
        String line2 = String.format("%.2f%%", percent);

        FontMetrics fm = g.getFontMetrics();

        int line1Width = fm.stringWidth(line1);
        int line2Width = fm.stringWidth(line2);

        int lineHeight = fm.getHeight();

        g.drawString(line1, x - line1Width / 2, y);
        g.drawString(line2, x - line2Width / 2, y + lineHeight);
    }

    public static void generateHtmlReport(TestResults results, String outputPath, String jobName) throws Exception {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String currentDate = dateFormat.format(new Date());

        StringBuilder html = new StringBuilder();

        html.append("<!doctype html>\n");
        html.append("<html>\n");
        html.append("<head>\n");
        html.append("    <meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\">\n");
        html.append("    <title>" + escapeHtml(jobName) + "</title>\n");
        html.append("</head>\n\n");
        html.append("<body style=\"margin:0; padding:0; font-family: Arial, Helvetica, sans-serif;\">\n");
        html.append("    <table width=\"85%\" align=\"center\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin:auto;\">\n");
        html.append("        <tr>\n");
        html.append("            <td>\n");
        html.append("                <hr style=\"border:0; border-top:1px solid #aaaaaa;\">\n");
        html.append("                <table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\">\n");
        html.append("                    <tr>\n");
        html.append("                        <td align=\"center\">\n");
        html.append("                            <h1 style=\"color:#1B3651; margin:0 0 8px 0;\">" + escapeHtml(jobName) + "</h1>\n");
        html.append("                        </td>\n");
        html.append("                    </tr>\n");
        html.append("                </table>\n");
        html.append("                <p style=\"text-align:center; margin:0 0 8px 0; font-size:14px;\">\n");
        html.append("                    Generated: " + currentDate + "\n");
        html.append("                </p>\n\n");

        html.append("                <hr style=\"border:0; border-top:1px solid #aaaaaa;\">\n\n");

        html.append("                <h2 style=\"color:#C63; margin:16px 0 8px 0;\">SUMMARY</h2>\n\n");

        html.append("                <table width=\"100%\" cellpadding=\"10\" cellspacing=\"0\">\n");
        html.append("                    <tr valign=\"top\">\n");
        html.append("                        <td width=\"260\" style=\"border:1px solid #dddddd; background-color:#f5f5f5;\">\n");
        html.append("                            <p style=\"margin:0 0 6px 0; font-size:14px;\">\n");
        html.append("                                <strong style=\"color:#1B3651;\">Total Tests:</strong> " + results.totalTests + "\n");
        html.append("                            </p>\n");
        html.append("                            <p style=\"margin:0 0 12px 0; font-size:14px;\">\n");
        html.append("                                <strong style=\"color:#1B3651;\">Total Time:</strong> " + String.format("%.3f", results.totalTime) + "s\n");
        html.append("                            </p>\n");

        generatePieChartImage(results.totalPassed, results.totalFailed, outputPath + File.separator + "piechart.png");

        html.append("                            <div style=\"text-align:center;\">\n");
        html.append("                                <img src=\"cid:piechart.png\" width=\"180\" alt=\"Resultant Pie Chart\" style=\"display:block; margin:auto;\">\n");
        html.append("                            </div>\n");
        html.append("                        </td>\n\n");

        html.append("                        <td width=\"16\">&nbsp;</td>\n\n");

        html.append("                        <td>\n");
        html.append("                            <table width=\"100%\" cellpadding=\"8\" cellspacing=\"0\" style=\"border-collapse:collapse; border:2px solid #242424; font-size:14px;\">\n");
        html.append("                                <tr>\n");
        html.append("                                    <th width=\"60%\" align=\"left\" style=\"border:1px solid #0D0000; background-color:#1B3651; color:#ffffff;\">\n");
        html.append("                                        Flow(s)\n");
        html.append("                                    </th>\n");
        html.append("                                    <th width=\"20%\" align=\"left\" style=\"border:1px solid #0D0000; background-color:#1B3651; color:#ffffff;\">\n");
        html.append("                                        Result\n");
        html.append("                                    </th>\n");
        html.append("                                    <th width=\"20%\" align=\"left\" style=\"border:1px solid #0D0000; background-color:#1B3651; color:#ffffff;\">\n");
        html.append("                                        Time(s)\n");
        html.append("                                    </th>\n");
        html.append("                                </tr>\n");



        for (TestSuite testSuite : results.testSuites) {
            String statusColor = testSuite.status.equals("PASSED") ? "#008000" : "#FF0000";
            String anchorId = testSuite.name.replaceAll(" ", "_");

            html.append("                                <tr>\n");
            html.append("                                    <td style=\"border:1px solid #0D0000;\">\n");
            html.append("                                        <a href=\"#" + anchorId + "\" style=\"color:#000000; text-decoration:none;\">" + escapeHtml(testSuite.name) + "</a>\n");
            html.append("                                    </td>\n");
            html.append("                                    <td bgcolor=\"" + statusColor + "\" style=\"border:1px solid #0D0000; color:#ffffff;\">\n");
            html.append("                                        " + testSuite.status + "\n");
            html.append("                                    </td>\n");
            html.append("                                    <td style=\"border:1px solid #0D0000; text-align:right;\">\n");
            html.append("                                        " + String.format("%.3f", testSuite.time) + "\n");
            html.append("                                    </td>\n");
            html.append("                                </tr>\n");
        }

        html.append("                            </table>\n");
        html.append("                        </td>\n");
        html.append("                    </tr>\n");
        html.append("                </table>\n\n");

        html.append("                <br>\n");
        html.append("                <hr style=\"border:0; border-top:1px solid #aaaaaa;\">\n");
        html.append("                <br>\n\n");

        html.append("                <h2 style=\"color:#C63; margin:0 0 8px 0;\">TEST CASES DETAILS</h2>\n");

        for (TestSuite testSuite : results.testSuites) {
            String anchorId = testSuite.name.replaceAll(" ", "_");

            html.append("                <h3 id=\"" + anchorId + "\" style=\"color:#C63; margin:12px 0 8px 0;\">\n");
            html.append("                    " + escapeHtml(testSuite.name) + "\n");
            html.append("                </h3>\n\n");

            html.append("                <table width=\"100%\" cellpadding=\"8\" cellspacing=\"0\" style=\"border-collapse:collapse; font-size:14px;\">\n");
            html.append("                    <tr>\n");
            html.append("                        <th width=\"60%\" align=\"left\" style=\"border:1px solid #0D0000; background-color:#1B3651; color:#ffffff;\">\n");
            html.append("                            TestCase\n");
            html.append("                        </th>\n");
            html.append("                        <th width=\"7%\" align=\"left\" style=\"border:1px solid #0D0000; background-color:#1B3651; color:#ffffff;\">\n");
            html.append("                            Result\n");
            html.append("                        </th>\n");
            html.append("                        <th width=\"5%\" align=\"left\" style=\"border:1px solid #0D0000; background-color:#1B3651; color:#ffffff;\">\n");
            html.append("                            Time(s)\n");
            html.append("                        </th>\n");
            if (testSuite.status.equals("FAILED")) {
                html.append("                        <th width=\"28%\" align=\"left\" style=\"border:1px solid #0D0000; background-color:#1B3651; color:#ffffff;\">\n");
                html.append("                            Failed Step\n");
                html.append("                        </th>\n");
            }
            html.append("                    </tr>\n");

            for (TestCase testCase : testSuite.testCases) {
                String statusColor = testCase.status.equals("PASSED") ? "#008000" : "#FF0000";

                html.append("                    <tr>\n");
                html.append("                        <td style=\"border:1px solid #0D0000;\">\n");
                html.append("                            " + escapeHtml(testCase.name) + "\n");
                html.append("                        </td>\n");
                html.append("                        <td bgcolor=\"" + statusColor + "\" style=\"border:1px solid #0D0000; color:#ffffff;\">" + testCase.status + "</td>\n");
                html.append("                        <td style=\"border:1px solid #0D0000; text-align:right;\">"
                        + String.format("%.3f", testCase.time) + "</td>\n");
                if (testSuite.status.equals("FAILED")) {
                    html.append("                        <td style=\"border:1px solid #0D0000;\">" + testCase.failedStep + "</td>\n");
                }
                html.append("                    </tr>\n");
            }

            html.append("                </table>\n");
            html.append("                <hr style=\"border:0; border-top:1px solid #aaaaaa;\">\n");
        }

        html.append("            </td>\n");
        html.append("        </tr>\n");
        html.append("    </table>\n");
        html.append("</body>\n\n");

        html.append("</html>\n");

        // Write to file
        try (FileWriter writer = new FileWriter(outputPath + File.separator + "summary-report.html")) {
            writer.write(html.toString());
        }

        System.out.println("HTML report generated: " + outputPath + File.separator + "summary-report.html");
    }

    private static double parseDouble(String value) {
        try {
            return value.isEmpty() ? 0.0 : Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private static String escapeHtml(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    public static void main(String[] args) {
        if (args.length < 1 || args.length > 3) {
            System.out.println(
                    "Usage: java SoapUIReportGenerator <junit_xml_file_or_directory> [output_html] [job_name_&_build]");
            System.out.println(
                    "Example: java SoapUIReportGenerator TEST-results.xml report.html \"My SoapUI Tests\"");
            System.out.println(
                    "Example: java SoapUIReportGenerator ./test-results/ report.html \"My SoapUI Tests\"");
            System.exit(1);
        }

        String inputPath = args[0];
        String outputPath = args.length > 1 ? args[1] : "";
        String jobName = args.length > 2 ? args[2] : "CHARTER-PT-SANITY_#n";

        try {
            List<File> xmlFiles = new ArrayList<>();
            File input = new File(inputPath);

            if (input.isDirectory()) {
                // Process all XML files in directory
                try (Stream<Path> paths = Files.walk(Paths.get(inputPath))) {
                    xmlFiles = paths
                            .filter(Files::isRegularFile)
                            .filter(p -> p.toString().endsWith(".xml"))
                            .filter(p -> p.getFileName().toString().startsWith("TEST-"))
                            .map(Path::toFile)
                            .collect(Collectors.toList());
                }
            } else if (inputPath.contains("*")) {
                // Handle wildcard pattern
                Path dir = Paths.get(inputPath).getParent();
                String pattern = Paths.get(inputPath).getFileName().toString();
                String regex = pattern.replace("*", ".*").replace("?", ".");

                try (Stream<Path> paths = Files.list(dir != null ? dir : Paths.get("."))) {
                    xmlFiles = paths
                            .filter(Files::isRegularFile)
                            .filter(p -> p.getFileName().toString().matches(regex))
                            .map(Path::toFile)
                            .collect(Collectors.toList());
                }
            } else {
                // Single file
                if (!input.exists()) {
                    System.err.println("Error: XML file not found: " + inputPath);
                    System.exit(1);
                }
                xmlFiles.add(input);
            }

            if (xmlFiles.isEmpty()) {
                System.err.println("Error: No XML files found matching: " + inputPath);
                System.exit(1);
            }

            System.out.println("Found " + xmlFiles.size() + " XML file(s)");

            // Parse all XML files
            List<TestResults> allResults = new ArrayList<>();
            for (File xmlFile : xmlFiles) {
                System.out.println("Parsing: " + xmlFile.getPath());
                TestResults results = parseJUnitXml(xmlFile);
                allResults.add(results);
            }

            // Merge results if multiple files
            TestResults finalResults;
            if (allResults.size() > 1) {
                System.out.println("Merging results from multiple files...");
                finalResults = mergeResults(allResults);
            } else {
                finalResults = allResults.get(0);
            }

            String htmlReportPath = outputPath + File.separator + "summary-report.html";
            System.out.println("Generating HTML report: " + htmlReportPath);
            generateHtmlReport(finalResults, outputPath, jobName);

            System.out.println("\nSummary:");
            System.out.println("  Total Tests: " + finalResults.totalTests);
            System.out.println("  Passed: " + finalResults.totalPassed);
            System.out.println("  Failed: " + finalResults.totalFailed);
            System.out.println("  Total Time: " + String.format("%.3f", finalResults.totalTime) + "s");

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}