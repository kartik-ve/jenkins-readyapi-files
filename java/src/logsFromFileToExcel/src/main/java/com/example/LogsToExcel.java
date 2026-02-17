package com.example;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Scanner;
import java.util.ArrayList;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

public class LogsToExcel {

    public static void main(String[] args) {
        if (args.length != 7 || !args[2].trim().matches("[1-7]")
                || !(args[3].equalsIgnoreCase("CO") || args[3].equalsIgnoreCase("OE"))) {
            System.out.println(
                    "Usage: java LogsToExcel <input_log_file> <excel_workbook_path> <flow_number (1-7)> <project (OE/CO)> <DMP> <env> <tester_name>");
            return;
        }

        ArrayList<String> exceptions;
        try (BufferedReader br = new BufferedReader(new FileReader(args[0]), 32 * 1024);
                Scanner scanner = new Scanner(System.in);) {
            exceptions = findErrors(br);
            Path excelPath = Paths.get(args[1].trim(), "Exceptions.xlsx");
            String excelFile = excelPath.toString();
            int flow = Integer.parseInt(args[2]);
            int project = args[3].equalsIgnoreCase("OE") ? 1 : 2;
            String DMP = args[4].trim();
            String env = args[5].trim();
            String tester = args[6].trim();
            createExcel(exceptions, excelFile, flow, project, DMP, env, tester);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
    }

    private static ArrayList<String> findErrors(BufferedReader br) throws IOException {
        ArrayList<String> exceptions = new ArrayList<>();

        int openTags = 0;
        String line;
        StringBuilder exception = new StringBuilder();
        while ((line = br.readLine()) != null) {
            int len = line.trim().length();
            if (len == 0 && openTags == 0) {
                continue;
            }
            if (len >= 13 && len <= 15) {
                break;
            }

            openTags += calculateOpenTags(line);
            if (openTags == 0) {
                exceptions.add(exception.toString().trim());
                exception.setLength(0);
            } else {
                exception.append(line);
            }
        }

        return exceptions;
    }

    private static int calculateOpenTags(String line) {
        int open = 0;
        for (byte b : line.getBytes()) {
            if (b == '<') {
                open++;
            } else if (b == '>') {
                open--;
            }
        }

        return open;
    }

    private static void createExcel(ArrayList<String> exceptions, String excelFile,
            int flow, int project, String DMP, String ENV, String TESTER) throws Exception {

        File file = new File(excelFile);
        Workbook workbook;

        if (file.exists()) {
            try (FileInputStream fis = new FileInputStream(file)) {
                workbook = WorkbookFactory.create(fis);
            }
        } else {
            workbook = new XSSFWorkbook();
        }

        String sheetName;
        switch (flow) {
            case 1:
                sheetName = project == 1 ? "NC - OE" : "NC - CO";
                break;
            case 2:
                sheetName = project == 1 ? "COS - OE" : "COS - CO";
                break;
            case 3:
                sheetName = project == 1 ? "CE - OE" : "CE - CO";
                break;
            case 4:
                sheetName = project == 1 ? "RP - OE" : "RP - CO";
                break;
            case 5:
                sheetName = project == 1 ? "Move - OE" : "Move - CO";
                break;
            case 6:
                sheetName = project == 1 ? "Bulk - OE" : "Bulk - CO";
                break;
            case 7:
                sheetName = project == 1 ? "SU - OE" : "SU - CO";
                break;
            default:
                workbook.close();
                throw new IllegalArgumentException("Invalid flow selection");
        }

        Sheet sheet = workbook.createSheet(sheetName);

        /* ================= HEADER STYLE ================= */

        XSSFCellStyle headerStyle = (XSSFCellStyle) workbook.createCellStyle();
        headerStyle.setFillForegroundColor(
                new XSSFColor(new java.awt.Color(233, 113, 50), null)); // #E97132
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        headerStyle.setAlignment(HorizontalAlignment.CENTER);

        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerStyle.setFont(headerFont);

        /* ================= CENTER STYLE (Non-exception columns) ================= */

        CellStyle centerStyle = workbook.createCellStyle();
        centerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        centerStyle.setAlignment(HorizontalAlignment.CENTER);

        /* ================= WRAP STYLE (Exception column) ================= */

        CellStyle wrapStyle = workbook.createCellStyle();
        wrapStyle.setWrapText(true);
        wrapStyle.setVerticalAlignment(VerticalAlignment.TOP);

        /* ================= CREATE HEADER ================= */

        Row header = sheet.createRow(0);

        String[] headers = { "S.No.", "Exception", "DMP", "ENV", "Tester" };

        for (int i = 0; i < headers.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        /* ================= DATA ROWS ================= */

        int rowNum = 1;
        for (String exception : exceptions) {
            Row row = sheet.createRow(rowNum);

            Cell c0 = row.createCell(0);
            c0.setCellValue(rowNum);
            c0.setCellStyle(centerStyle);

            Cell exceptionCell = row.createCell(1);
            exceptionCell.setCellValue(exception.trim());
            exceptionCell.setCellStyle(wrapStyle);

            Cell c2 = row.createCell(2);
            c2.setCellValue(DMP);
            c2.setCellStyle(centerStyle);

            Cell c3 = row.createCell(3);
            c3.setCellValue(ENV);
            c3.setCellStyle(centerStyle);

            Cell c4 = row.createCell(4);
            c4.setCellValue(TESTER);
            c4.setCellStyle(centerStyle);

            row.setHeightInPoints(90);

            rowNum++;
        }

        /* ================= COLUMN WIDTH ================= */

        sheet.setColumnWidth(1, 100 * 256);

        for (int i = 0; i < 5; i++) {
            if (i != 1) {
                sheet.autoSizeColumn(i);
            }
        }

        /* ================= WRITE FILE ================= */

        try (FileOutputStream fos = new FileOutputStream(file)) {
            workbook.write(fos);
        }

        workbook.close();
    }
}