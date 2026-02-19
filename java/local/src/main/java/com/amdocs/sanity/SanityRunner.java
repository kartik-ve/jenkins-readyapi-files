package com.amdocs.sanity;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class SanityRunner {
    public static void main(String[] args) {
        int argsForBasicSanity = 5;
        int argsForExtendedSanity = 11;
        if (args.length != argsForBasicSanity && args.length != argsForExtendedSanity) {
            System.err.println(
                    "Usage: java SanityRunner <input_junit_report_dir> <output_dir> <job_name> <input_api_responses_dir> <output_failed_responses_dir>");
            System.err.println(
                    "Usage: java SanityRunner <input_junit_report_dir> <output_dir> <job_name> <input_api_responses_dir> <output_failed_responses_dir> <input_log_file> <flows_separated_by_|> <project (OE/CO)> <DMP> <env> <tester_name>");
            System.exit(1);
        }

        String junitReportDir = args[0];
        String outputDir = args[1];
        String jobName = args[2];
        String apiRespDir = args[3];
        Path failedApiRespDir = Paths.get(args[4]);

        int exitCode = 0;

        try {
            ReadyAPIReportGenerator.ReportSummary summary = ReadyAPIReportGenerator.generateReport(
                    junitReportDir,
                    outputDir,
                    jobName,
                    apiRespDir,
                    System.out::println);

            System.out.println("\nSummary:");
            System.out.println("  Total Tests: " + summary.totalTests);
            System.out.println("  Passed: " + summary.totalPassed);
            System.out.println("  Failed: " + summary.totalFailed);
            System.out.println("  Total Time: " + String.format("%.3f", summary.totalTime) + "s");

            CopyFailedResponses.copy(Paths.get(apiRespDir), failedApiRespDir);
            System.out.println("Copied Failed Responses!");
        } catch (Exception e) {
            exitCode = 1;
            e.printStackTrace();
        }

        if (args.length == argsForExtendedSanity) {
            Path originalPath = Paths.get(args[6]);
            String originalFileName = originalPath.getFileName().toString();
            Path excelPath = Paths.get(outputDir, "Exceptions.xlsx");
            String[] flows = args[7].split("\\|");
            
            int project = -1;
            if (args[8].equalsIgnoreCase("OE")) {
                project = 1;
            } else if (args[8].equalsIgnoreCase("CO")) {
                project = 2;
            } else {
                System.err.println("Project must be OE or CO");
                System.exit(1);
            }

            String dmp = args[9];
            String env = args[10];
            String tester = args[11];

            try {
                for (String flow : flows) {
                    Path logFilePath = originalPath.resolveSibling(flow.toUpperCase() + originalFileName);
                    LogsToExcel.log(logFilePath, excelPath, flow, project, dmp, env, tester);
                }
                System.out.println("Logs processed and saved to Excel!");
            } catch (IOException e) {
                exitCode = 1;
                e.printStackTrace();
            }
        }

        System.exit(exitCode);
    }
}
