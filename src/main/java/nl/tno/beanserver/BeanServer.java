package nl.tno.beanserver;

import io.javalin.Javalin;
import io.javalin.http.UploadedFile;
import io.javalin.http.staticfiles.Location;
import io.javalin.util.FileUtil;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import nl.tno.beancompiler.BeanCompiler;
import nl.tno.beangenerator.BeanGenerator;
import nl.tno.beangenerator.BeanGeneratorProperties;

/**
 * @author bergtwvd
 */
public class BeanServer {

  final String WORKDIR = "work/";
  final String MIMDIR = "mim/";
  final String FOMSDIR = "foms/";
  final String REFFOMSDIR = "reffoms/";
  final String SOURCEDIR = "source/";
  final String ZIPDIR = "zips/";
  final long SESSION_AGE = 10 * 60 * 1000; // millis
  final int PORT = 7000;

  static BeanServer beanServer;

  public static void main(String[] args) throws Exception {
    // set logging properties before geeting a Logger
    System.setProperty("java.util.logging.config.file", "logging.properties");

    hello();

    Logger.getLogger(BeanServer.class.getName()).log(Level.INFO, "Starting Server ...");

    // start serving
    beanServer = new BeanServer();
    beanServer.serve();
  }

  private static void hello() {
    // From: https://patorjk.com/software/taag/#p=moreopts&f=Small&t=TNO%20Bean%20Server
    String hello =
        """

                  _____ _  _  ___    ___                  ___
                 |_   _| \\| |/ _ \\  | _ ) ___ __ _ _ _   / __| ___ _ ___ _____ _ _
                   | | | .` | (_) | | _ \\/ -_) _` | ' \\  \\__ \\/ -_) '_\\ V / -_) '_|
                   |_| |_|\\_|\\___/  |___/\\___\\__,_|_||_| |___/\\___|_|  \\_/\\___|_|
                """;

    // Below two strings are replaced in the Docker build pipeline
    Logger.getLogger(BeanServer.class.getName())
        .log(Level.INFO, "Starting Bean Server version 0.0.0-git");
    Logger.getLogger(BeanServer.class.getName()).log(Level.INFO, "Build date UNKNOWN_BUILD_DATE");
    Logger.getLogger(BeanServer.class.getName()).log(Level.INFO, hello);
  }

  void deleteSubDirectories(File dir, long minAge) {
    long currentTimeMillis = System.currentTimeMillis();

    File[] allContents = dir.listFiles();
    if (allContents != null) {
      for (File file : allContents) {
        if (currentTimeMillis - file.lastModified() >= minAge) {
          deleteDirectory(file);
        }
      }
    }
  }

  void deleteDirectory(File dir) {
    deleteSubDirectories(dir, 0);
    dir.delete();
  }

  void zipDirectory(File dirToZip, String resultFileName) throws IOException {
    try (FileOutputStream fos = new FileOutputStream(resultFileName);
        ZipOutputStream zipOut = new ZipOutputStream(fos)) {
      zipFile(dirToZip, dirToZip.getName(), zipOut);
    }
  }

  void zipFile(File fileToZip, String fileName, ZipOutputStream zipOut) throws IOException {
    if (!fileToZip.isHidden()) {
      if (fileToZip.isDirectory()) {
        if (fileName.endsWith("/")) {
          zipOut.putNextEntry(new ZipEntry(fileName));
          zipOut.closeEntry();
        } else {
          zipOut.putNextEntry(new ZipEntry(fileName + "/"));
          zipOut.closeEntry();
        }
        File[] children = fileToZip.listFiles();
        for (File childFile : children) {
          zipFile(childFile, fileName + "/" + childFile.getName(), zipOut);
        }
      } else {
        try (FileInputStream fis = new FileInputStream(fileToZip)) {
          ZipEntry zipEntry = new ZipEntry(fileName);
          zipOut.putNextEntry(zipEntry);
          byte[] bytes = new byte[1024];
          int length;
          while ((length = fis.read(bytes)) >= 0) {
            zipOut.write(bytes, 0, length);
          }
        }
      }
    }
  }

  public void serve() {

    // init session count
    AtomicInteger session = new AtomicInteger(0);

    // create working folder
    new File(WORKDIR).mkdir();

    Javalin app =
        Javalin.create(
                config -> {
                  config.staticFiles.add("/public");
                  config.staticFiles.add(WORKDIR, Location.EXTERNAL);
                })
            .start(PORT);

    Logger.getLogger(BeanServer.class.getName())
        .log(Level.INFO, "Listening on port {0}", Integer.toString(PORT));

    app.post(
        "/upload",
        ctx -> {
          Logger.getLogger(BeanServer.class.getName()).log(Level.INFO, "Processing request ...");

          // clear aged data first
          this.deleteSubDirectories(new File(WORKDIR), SESSION_AGE);

          // setup session directory names
          int index = session.incrementAndGet();
          String sessionDir = "session" + index + "/";

          while (new File(WORKDIR + sessionDir).exists()) {
            index = session.incrementAndGet();
            sessionDir = "session" + index + "/";
          }

          String mimDir = WORKDIR + sessionDir + MIMDIR;
          String fomsDir = WORKDIR + sessionDir + FOMSDIR;
          String refFomsDir = WORKDIR + sessionDir + REFFOMSDIR;
          String sourceDir = WORKDIR + sessionDir + SOURCEDIR;
          String zipDir = WORKDIR + sessionDir + ZIPDIR;

          // process property settings
          BeanGeneratorProperties properties = new BeanGeneratorProperties();
          properties.setUseFQclassName(ctx.formParam("useFQnames") != null);
          properties.setUseList(ctx.formParam("useList") != null);
          properties.setUseUnboxedType(ctx.formParam("useUnboxedType") != null);
          properties.setUsePublicModifier(ctx.formParam("usePublicModifier") != null);
          properties.setUseJsonExport(ctx.formParam("useJsonExport") != null);
          properties.setGroupId(ctx.formParam("groupId"));

          boolean createJar = ctx.formParam("createJar") != null;

          String regexList = ctx.formParam("regexList");
          if (regexList != null) {
            String[] regexArray = regexList.split(",");
            for (String element : regexArray) {
              String[] regexEntry = element.split("=");
              if (regexEntry.length == 2) {
                properties.getDefaultPackageNames().put(regexEntry[0].trim(), regexEntry[1].trim());
              }
            }
          }

          // get the uploaded files
          for (UploadedFile uf : ctx.uploadedFiles("foms")) {
            if (uf.size() != 0) {
              FileUtil.streamToFile(uf.content(), fomsDir + uf.filename());
            }
          }

          for (UploadedFile uf : ctx.uploadedFiles("reffoms")) {
            if (uf.size() != 0) {
              FileUtil.streamToFile(uf.content(), refFomsDir + uf.filename());
            }
          }

          UploadedFile uf = ctx.uploadedFile("mim");
          if (uf != null) {
            if (uf.size() != 0) {
              FileUtil.streamToFile(uf.content(), mimDir + uf.filename());
            }
          }

          // process the uploaded files
          File[] fomFiles = new File(fomsDir).listFiles();
          File[] mimFiles = new File(mimDir).listFiles();
          File[] refFomFiles = new File(refFomsDir).listFiles();

          fomFiles = fomFiles == null ? new File[0] : fomFiles;
          mimFiles = mimFiles == null ? new File[0] : mimFiles;
          refFomFiles = refFomFiles == null ? new File[0] : refFomFiles;

          URL[] modules = new URL[fomFiles.length + refFomFiles.length + 1];
          boolean[] selectors = new boolean[fomFiles.length + refFomFiles.length + 1];

          for (int i = 0; i < fomFiles.length; i++) {
            modules[i] = fomFiles[i].toURI().toURL();
            selectors[i] = true;
          }

          for (int i = 0; i < refFomFiles.length; i++) {
            modules[fomFiles.length + i] = refFomFiles[i].toURI().toURL();
            selectors[fomFiles.length + i] = false;
          }

          if (mimFiles.length == 0) {
            modules[modules.length - 1] = BeanServer.class.getResource("/foms/HLAstandardMIM.xml");
            selectors[modules.length - 1] = false;
          } else {
            modules[modules.length - 1] = mimFiles[0].toURI().toURL();
            selectors[modules.length - 1] = true;
          }

          if (createJar) {
            try {
              /** Compile code and return JAR. */
              BeanCompiler bc = new BeanCompiler(properties);
              bc.expand(modules, null, selectors);

              // create a JAR
              // ensure directory exists
              new File(zipDir).mkdirs();
              String resultFileName =
                  "result-"
                      + new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss").format(new Date())
                      + "-"
                      + new Random().nextInt((1 << 31) - 1)
                      + ".jar";
              bc.createJar(new File(zipDir + resultFileName));

              ctx.redirect(sessionDir + ZIPDIR + resultFileName);
            } catch (Exception ex) {
              Logger.getLogger(BeanServer.class.getName()).log(Level.SEVERE, null, ex);
              ctx.result(ex.getMessage());
            }
          } else {
            try {
              /** Generate code and return ZIP. */

              // create the Bean Generator and generate the source code
              BeanGenerator bg =
                  new BeanGenerator(properties) {
                    @Override
                    protected void beforeOutput() throws Exception {}

                    @Override
                    protected void outputClass(
                        String fqOmtName,
                        String packageName,
                        String className,
                        StringBuilder sourceCode)
                        throws Exception {
                      this.createFile(
                          sourceDir
                              + packageName.replace(PKG_SEPARATOR, DIR_SEPARATOR)
                              + DIR_SEPARATOR
                              + className
                              + JAVA_FILE_SUFFIX,
                          sourceCode);
                    }

                    @Override
                    protected void outputPackage(
                        String packageName, String infoName, StringBuilder sourceCode)
                        throws Exception {
                      this.createFile(
                          sourceDir
                              + packageName.replace(PKG_SEPARATOR, DIR_SEPARATOR)
                              + DIR_SEPARATOR
                              + infoName
                              + JAVA_FILE_SUFFIX,
                          sourceCode);
                    }

                    @Override
                    protected void outputJson(String infoName, String jsonString) throws Exception {
                      this.createFile(
                          sourceDir + DIR_SEPARATOR + infoName + JSON_FILE_SUFFIX,
                          new StringBuilder(jsonString),
                          false);
                    }
                  };

              bg.expand(modules, null, selectors);

              // zip and return results
              if (new File(sourceDir).exists()) {
                new File(zipDir).mkdir();
                String resultFileName =
                    "result-"
                        + new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss").format(new Date())
                        + "-"
                        + new Random().nextInt((1 << 31) - 1)
                        + ".zip";
                this.zipDirectory(new File(sourceDir), zipDir + resultFileName);

                ctx.redirect(sessionDir + ZIPDIR + resultFileName);
              } else {
                Logger.getLogger(BeanServer.class.getName())
                    .log(Level.WARNING, "Dir {0} does not exist, and no data to return", sourceDir);
                ctx.result("No data to return");
              }
              // To return data as stream:
              // InputStream targetStream = new FileInputStream(new File(zipDir + resultFileName));
              // ctx.result(targetStream);
            } catch (Exception ex) {
              Logger.getLogger(BeanServer.class.getName()).log(Level.SEVERE, null, ex);
              ctx.result(ex.getMessage());
            }
          }

          Logger.getLogger(BeanServer.class.getName()).log(Level.INFO, "Processed request.");
        });
  }
}
