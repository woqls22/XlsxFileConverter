package com.xlsx.demo.Service;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.xssf.usermodel.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.text.DecimalFormat;
import java.util.Calendar;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@Slf4j
public class XlsxService {
  @Value("${spring.servlet.multipart.location}")
  private String attach_path;
  @Value("${spring.zipfile.path}")
  private String zipFilePath;
  @Value("${spring.output.path}")
  private String output_path;

  public void convertXlsxFile(MultipartHttpServletRequest request, HttpServletRequest httpServletRequest, HttpServletResponse response) throws IOException, InvalidFormatException {
    File fileDir = new File(attach_path);
    if (!fileDir.exists()) {
      fileDir.mkdirs();
    }
    DecimalFormat decFormat = new DecimalFormat("###,###");
    MultipartFile templateMf = request.getFile("template");
    MultipartFile sourceMf = request.getFile("source");
    String bank = request.getParameter("bank");
    String account = request.getParameter("account");
    String accpountTo=request.getParameter("accountTo");
    String templateFilename = templateMf.getOriginalFilename();
    String sourceFilename1 = sourceMf.getOriginalFilename();
    templateFilename = attach_path + templateFilename.replaceAll(" ", "");
    sourceFilename1 = attach_path + sourceFilename1.replaceAll(" ", "");

    templateMf.transferTo(new File(templateFilename));
    sourceMf.transferTo(new File(sourceFilename1));

    FileInputStream source = new FileInputStream(sourceFilename1);
    XSSFWorkbook sourceWorkbook = new XSSFWorkbook(source);
    XSSFSheet sheet = sourceWorkbook.getSheetAt(0);
    int rows = sheet.getPhysicalNumberOfRows(); // ?????? ????????? ??? ??????
    //source file??? row?????? ??????
    for (int rowIdx = 1; rowIdx <= rows; rowIdx++) {
      XSSFRow row = sheet.getRow(rowIdx);
      FileInputStream template = new FileInputStream(templateFilename);
      XSSFWorkbook templateWorkbook = new XSSFWorkbook(template);
      XSSFCellStyle cellStyle = templateWorkbook.createCellStyle();
      XSSFDataFormat format = templateWorkbook.createDataFormat();
      cellStyle.setDataFormat(format.getFormat("#,##0_);(#,##0)"));
      if (row != null) { // row??? ??? ????????? ??????
        XSSFCell firstItem = row.getCell(1);
        XSSFCell secondItem = row.getCell(3);
        String firstItemVal = firstItem.getStringCellValue();
        int secondItemVal = (int) (secondItem.getNumericCellValue());
        System.out.println("Date : " + firstItemVal + ", Cost : " + secondItemVal);

        XSSFSheet sheet1 = templateWorkbook.getSheetAt(1);
        sheet1.getRow(3).getCell(1).setCellValue(convertDateStringToDate(firstItemVal));
        sheet1.getRow(3).getCell(2).setCellFormula("B4");
        XSSFRow templateRow = sheet1.getRow(1);
        int rowCount = 0;
        int sheet1Rows = sheet1.getPhysicalNumberOfRows();
        String nFormula = "TEXT($B$4,\"MM??? DD??? \")&\""+bank+" "+account+"--->"+accpountTo+"\"";
        boolean isUpdated = false;
        for (rowCount = 6; rowCount <= sheet1Rows; rowCount++) {
          templateRow = sheet1.getRow(rowCount);
          if (templateRow.getCell(4).getCellType() == XSSFCell.CELL_TYPE_BLANK) {
            System.out.println("break at rowCount : "+rowCount);
            break;
          }else if (templateRow.getCell(4).getCellType() == XSSFCell.CELL_TYPE_FORMULA) {
            templateRow.getCell(4).setCellFormula(templateRow.getCell(4).getCellFormula());
          }
          if (templateRow.getCell(13).getCellType() == XSSFCell.CELL_TYPE_FORMULA) {
            System.out.println(nFormula);
            templateRow.getCell(13).setCellFormula(nFormula);
          }
          if (templateRow.getCell(9).getCellType() == XSSFCell.CELL_TYPE_NUMERIC) {
            System.out.println(secondItemVal+", "+templateRow.getCell(9).getNumericCellValue());
            templateRow.getCell(9).setCellValue(decFormat.format(secondItemVal));
          }
          if (templateRow.getCell(9).getCellType() == XSSFCell.CELL_TYPE_FORMULA) {
            templateRow.getCell(9).setCellType(XSSFCell.CELL_TYPE_NUMERIC);
            templateRow.getCell(9).setCellValue(decFormat.format(secondItemVal));
          }
        }
//        XSSFCell cell1 = templateRow.createCell(4);
//        XSSFCell cell2 = templateRow.createCell(9);
//        XSSFCell cell3 = templateRow.createCell(13);
//        cell1.setCellType(Cell.CELL_TYPE_STRING);
//        cell2.setCellType(Cell.CELL_TYPE_NUMERIC);
//        cell3.setCellType(Cell.CELL_TYPE_STRING);
//        cell1.setCellFormula("TEXT($B$4,\"MM??? DD???\")");
////        cell2.setCellValue(decFormat.format(secondItemVal));
//        cell2.setCellStyle(cellStyle);
//        cell3.setCellFormula(nFormula);


        File outputDir = new File(attach_path + output_path);
        if (!outputDir.exists()) {
          outputDir.mkdirs();
        }
        String year = firstItemVal.split("\\.")[0];
        String month = firstItemVal.split("\\.")[1];
        String day = firstItemVal.split("\\.")[2];
        FileOutputStream fileOut = new FileOutputStream(outputDir + "/Copy of ????????? ????????????" +month+"???"+day+"???" + ".xlsx");
        templateWorkbook.write(fileOut);
        fileOut.close();
      }
    }
    makeZipFromDir();
    File outputDir = new File(attach_path+output_path);
    deleteFilesRecursively(outputDir);
    File downloadFile = new File(attach_path+"output.zip");
    long fileLength = downloadFile.length();
    response.setHeader("Content-Disposition", "attachment; filename=output.zip;");
    response.setHeader("Content-Transfer-Encoding", "binary");
    response.setHeader("Content-Type", "application/zip");
    response.setHeader("Content-Length", "" + fileLength);
    response.setHeader("Pragma", "no-cache;");
    response.setHeader("Expires", "-1;");
    try (
        FileInputStream fis = new FileInputStream(attach_path+"output.zip");
        OutputStream out = response.getOutputStream();
    ) {
      int readCount = 0;
      byte[] buffer = new byte[1024];
      while ((readCount = fis.read(buffer)) != -1) {
        out.write(buffer, 0, readCount);
      }
    } catch (Exception ex) {
      System.out.println(attach_path+"output.zip");
      throw new RuntimeException("Err] output.zip - xlsx File Download Error");
    }
  }
  public void zipFileDownload(HttpServletRequest request, HttpServletResponse response) throws IOException {
    response.setContentType("application/zip");
    response.setHeader("Content-Disposition", "attachment; filename=\"" + "output.zip" + "\";");
    System.out.println("ZipFile Download..");
    FileInputStream fis = new FileInputStream(attach_path+"output.zip");
    BufferedInputStream bis = new BufferedInputStream(fis);
    ServletOutputStream so = response.getOutputStream();
    BufferedOutputStream bos = new BufferedOutputStream(so);

    byte[] data = new byte[2048];
    int input = 0;

    while ((input = bis.read(data)) != -1) {
      bos.write(data, 0, input);
      bos.flush();
    }

    if (bos != null)
      bos.close();
    if (bis != null)
      bis.close();
    if (so != null)
      so.close();
    if (fis != null)
      fis.close();
  }
  public String convertDateStr(String item) {
    String[] split = item.split("\\.");
    String year = split[0];
    String month = split[1];
    String day = split[2];
    StringBuilder stringBuilder = new StringBuilder();
    StringBuilder append = stringBuilder.append(month).append("???").append(" ").append(day).append("???");
    return append.toString();
  }

  public String convertDateWithSlash(String item) {
    String[] split = item.split("\\.");
    String year = split[0];
    String month = split[1];
    String day = split[2];
    StringBuilder stringBuilder = new StringBuilder();
    StringBuilder append = stringBuilder.append(month).append("/").append(day).append("/").append(year);
    return append.toString();
  }

  public Calendar convertDateStringToDate(String item) {
    String[] split = item.split("\\.");
    String year = split[0];
    String month = split[1];
    String day = split[2];
    Calendar date = Calendar.getInstance();
    date.set(Integer.parseInt(year), Integer.parseInt(month) - 1, Integer.parseInt(day));
    return date;
  }
  static boolean deleteFilesRecursively(File rootFile) {
    File[] allFiles = rootFile.listFiles();
    if (allFiles != null) {
      for (File file : allFiles) {
        deleteFilesRecursively(file);
      }
    }
    System.out.println("Remove file: " + rootFile.getPath());
    return rootFile.delete();
  }
  public void makeZipFromDir() throws IOException {
//   Macbook Env
  String dir = zipFilePath;
    String zipName="output.zip";
    File directory = new File(dir + File.separator);
    if (!directory.isDirectory()) {
      new IOException(dir + ": ????????? ??????");
    }
    File zipFile = new File(directory.getParent(), zipName);
    ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile));
    System.out.println(zipFile.getAbsolutePath() + "?????????..");
    File[] fileList = directory.listFiles();
    byte[] buf = new byte[1024];
    FileInputStream in = null;
    for (File file : fileList) {
      System.out.println(file.getAbsolutePath() + " : ??????????????? ?????????");
      in = new FileInputStream(file.getAbsoluteFile());
      zos.putNextEntry(new ZipEntry(file.getName()));
      int len;
      while ((len = in.read(buf)) > 0) {
        zos.write(buf, 0, len);
      }
      zos.closeEntry();
      in.close();
    }
    System.out.println(zipFile.getAbsolutePath() + "????????????");
    zos.close();
  }

}
