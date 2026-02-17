package com.example;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.Scanner;
import java.util.ArrayList;
import org.apache.poi.ss.usermodel.*;

public class LogsToExcel {

    public static void main(String[] args) {
        if (args.length != 7 || !args[2].trim().matches("[1-7]")
                || !(args[3].equalsIgnoreCase("CO") || args[3].equalsIgnoreCase("OE"))) {
            System.out.println(
                    "Usage: java LogsToExcel <input_log_file> <input_excel_workbook> <flow_number (1-7)> <project (OE/CO)> <DMP> <env> <tester_name>");
            return;
        }

        ArrayList<String> exceptions;
        try (BufferedReader br = new BufferedReader(new FileReader(args[0]), 32 * 1024);
                Scanner scanner = new Scanner(System.in);) {
            exceptions = findErrors(br);
            int flow = Integer.parseInt(args[2]);
            int project = args[3].equalsIgnoreCase("OE") ? 1 : 2;
            String DMP = args[4].trim();
            String env = args[5].trim();
            String tester = args[6].trim();
            addToExcel(exceptions, args[1], flow, project, DMP, env, tester);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
    }

    private static ArrayList<String> findErrors(BufferedReader br) throws IOException {
        ArrayList<String> exceptions = new ArrayList<>();

        String line;
        StringBuilder exception = new StringBuilder();
        while ((line = br.readLine()) != null) {
            if (line.trim().length() == 0) {
                if (exception.length() > 0) {
                    exceptions.add(exception.toString().trim());
                    exception.setLength(0);
                }
            } else if (line.trim().length() < 15) {
                break;
            } else {
                exception.append(line);
            }
        }

        return exceptions;
    }

    private static void addToExcel(ArrayList<String> exceptions, String excelFile, int flow, int project, String DMP,
            String ENV,
            String TESTER) throws Exception {

        FileInputStream fis = new FileInputStream(excelFile);
        Workbook workbook = WorkbookFactory.create(fis);

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
                throw new IllegalArgumentException("Invalid flow selection");
        }

        Sheet sheet = workbook.getSheet(sheetName);

        DataFormatter formatter = new DataFormatter();

        String newLine = System.lineSeparator();
        for (String exception : exceptions) {
            boolean exists = false;
            if (exception.contains("GROUP ID = ")) {
                String id = exception.split("GROUP ID = ")[1].split(" ")[0];
                for (Row row : sheet) {
                    Cell cell = row.getCell(1, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    if (cell != null) {
                        String value = formatter.formatCellValue(cell);
                        if (value.contains(id)) {
                            exists = true;
                            break;
                        }
                    }
                }
            } else {
                String[] lines = exception.split(newLine);
                String identifier;
                if (lines[1].trim().length() > 0) {
                    if (lines[1].contains("Exception")) {
                        identifier = lines[1];
                    } else {
                        identifier = lines[1].split("line")[0].trim();
                    }
                } else {
                    identifier = lines[2];
                }

                for (Row row : sheet) {
                    Cell cell = row.getCell(1, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    if (cell != null) {
                        String value = formatter.formatCellValue(cell);
                        if (value.contains(identifier)) {
                            exists = true;
                            break;
                        }
                    }
                }
            }

            if (exists) {
                continue;
            }

            boolean inserted = false;
            for (Row row : sheet) {
                if (row.getRowNum() == 0) {
                    continue;
                }

                boolean isEmpty = true;
                for (int c = 0; c <= 5; c++) {
                    Cell cell = row.getCell(c, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    if (cell != null) {
                        isEmpty = false;
                        break;
                    }
                }

                if (isEmpty) {
                    insertRow(sheet, row, exception, DMP, ENV, TESTER);
                    inserted = true;
                    break;
                }
            }

            if (!inserted) {
                int newRowNum = sheet.getLastRowNum() + 1;
                insertRow(sheet, sheet.createRow(newRowNum), exception, DMP, ENV, TESTER);
            }
        }

        // outer: for (String exception : exceptions) {
        // String id = exception.split("GROUP ID = ")[1].split(" ")[0];
        // for (Row row : sheet) {
        // Cell cell = row.getCell(1, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        // if (cell != null) {
        // String value = formatter.formatCellValue(cell);
        // if (value.contains(id)) {
        // continue outer;
        // }
        // }
        // }

        // boolean inserted = false;
        // for (Row row : sheet) {
        // if (row.getRowNum() == 0)
        // continue;

        // boolean isEmpty = true;
        // for (int c = 0; c <= 5; c++) {
        // Cell cell = row.getCell(c, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        // if (cell != null) {
        // isEmpty = false;
        // break;
        // }
        // }

        // if (isEmpty) {
        // insertRow(sheet, row, exception, DMP, ENV, TESTER);
        // inserted = true;
        // break;
        // }
        // }

        // if (!inserted) {
        // int newRowNum = sheet.getLastRowNum() + 1;
        // insertRow(sheet, sheet.createRow(newRowNum), exception, DMP, ENV, TESTER);
        // }
        // }

        // String newLine = System.lineSeparator();
        // outer: for (String exception : excelExceptions.get(1)) {
        // String[] lines = exception.split(newLine);
        // String identifier;
        // if (lines[1].trim().length() > 0) {
        // if (lines[1].contains("Exception")) {
        // identifier = lines[1];
        // } else {
        // identifier = lines[1].split("line")[0].trim();
        // }
        // } else {
        // identifier = lines[2];
        // }

        // for (Row row : sheet) {
        // Cell cell = row.getCell(1, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        // if (cell != null) {
        // String value = formatter.formatCellValue(cell);
        // if (value.contains(identifier)) {
        // continue outer;
        // }
        // }
        // }

        // boolean inserted = false;
        // for (Row row : sheet) {
        // if (row.getRowNum() == 0) {
        // continue;
        // }

        // boolean isEmpty = true;
        // for (int c = 0; c <= 5; c++) {
        // Cell cell = row.getCell(c, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        // if (cell != null) {
        // isEmpty = false;
        // break;
        // }
        // }

        // if (isEmpty) {
        // insertRow(sheet, row, exception, DMP, ENV, TESTER);
        // inserted = true;
        // break;
        // }
        // }

        // if (!inserted) {
        // int newRowNum = sheet.getLastRowNum() + 1;
        // insertRow(sheet, sheet.createRow(newRowNum), exception, DMP, ENV, TESTER);
        // }
        // }

        FileOutputStream fos = new FileOutputStream(excelFile);
        workbook.write(fos);

        fos.close();
        workbook.close();
        fis.close();
    }

    private static void insertRow(Sheet sheet, Row row, String exception, String DMP, String ENV, String TESTER) {
        Row templateRow = sheet.getRow(1);
        copyRowFormatting(templateRow, row);

        row.getCell(0).setCellValue(row.getRowNum());
        row.getCell(1).setCellValue(exception.trim());
        row.getCell(3).setCellValue(DMP);
        row.getCell(4).setCellValue(ENV);
        row.getCell(5).setCellValue(TESTER);
    }

    private static void copyRowFormatting(Row sourceRow, Row targetRow) {
        if (sourceRow == null || targetRow == null)
            return;

        targetRow.setHeight(sourceRow.getHeight());

        for (int c = 0; c <= 5; c++) {
            Cell sourceCell = sourceRow.getCell(c, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            Cell targetCell = targetRow.getCell(c);

            if (targetCell == null) {
                targetCell = targetRow.createCell(c);
            }

            if (sourceCell != null) {
                targetCell.setCellStyle(sourceCell.getCellStyle());
            }
        }
    }
}