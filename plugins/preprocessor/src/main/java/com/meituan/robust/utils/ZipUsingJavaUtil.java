package com.meituan.robust.utils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;


/**
 * Created by muditmathur on 01/02/16.
 */

public class ZipUsingJavaUtil {
    /*
     * Zip function zip all files and folders
     */
    @SuppressWarnings("finally")
    public boolean zipFiles(String srcFolder, String destZipFile, boolean includeRootDirectory) {
        boolean result = false;
        try {
            /*
             * send to the zip procedure
             */
            zipFolder(srcFolder, destZipFile, includeRootDirectory);
            result = true;
        } catch (Exception e) {
            System.err.println(e);
        } finally {
            return result;
        }
    }

    /*
     * zip the folders
     */
    private void zipFolder(String srcFolder, String destZipFile, boolean includeRootDirectory) throws Exception {
        ZipOutputStream zip = null;
        FileOutputStream fileWriter = null;
        /*
         * create the output stream to zip file result
         */
        fileWriter = new FileOutputStream(destZipFile);
        zip = new ZipOutputStream(fileWriter);
        /*
         * add the folder to the zip
         */
        if (includeRootDirectory) {
            addFolderToZip("", srcFolder, zip);
        } else {
            File folder = new File(srcFolder);
            for (String fileName : folder.list()) {
                File file = new File(folder, fileName);
                if (file.isDirectory()) {
                    addFolderToZip("", srcFolder + "/" + fileName, zip);
                } else {
                    addFileToZip("", srcFolder + "/" + fileName, zip, false);
                }
            }
        }

        /*
         * close the zip objects
         */
        zip.flush();
        zip.close();
    }

    /*
     * recursively add files to the zip files
     */
    private void addFileToZip(String path, String srcFile, ZipOutputStream zip, boolean flag) throws Exception {
        /*
         * create the file object for inputs
         */
        File folder = new File(srcFile);

        /*
         * if the folder is empty add empty folder to the Zip file
         */
        if (flag == true) {
            zip.putNextEntry(new ZipEntry(path + "/" + folder.getName() + "/"));
            zip.closeEntry();
        } else { /*
         * if the current name is directory, recursively traverse it
         * to get the files
         */
            if (folder.isDirectory()) {
                /*
                 * if folder is not empty
                 */
                addFolderToZip(path, srcFile, zip);
            } else {
                /*
                 * write the file to the output
                 */
                byte[] buf = new byte[1024];
                int len;
                FileInputStream in = new FileInputStream(srcFile);
                if (path == null || path.trim().length() == 0) {
                    zip.putNextEntry(new ZipEntry(folder.getName()));
                } else {
                    zip.putNextEntry(new ZipEntry(path + "/" + folder.getName()));
                }
                while ((len = in.read(buf)) > 0) {
                    /*
                     * Write the Result
                     */
                    zip.write(buf, 0, len);
                }
                zip.closeEntry();
                in.close();

            }
        }
    }

    /*
     * add folder to the zip file
     */
    private void addFolderToZip(String path, String srcFolder, ZipOutputStream zip) throws Exception {
        File folder = new File(srcFolder);

        /*
         * check the empty folder
         */
        if (folder.list().length == 0) {
            addFileToZip(path, srcFolder, zip, true);
        } else {
            /*
             * list the files in the folder
             */
            for (String fileName : folder.list()) {
                if (path.equals("")) {
                    addFileToZip(folder.getName(), srcFolder + "/" + fileName, zip, false);
                } else {
                    addFileToZip(path + "/" + folder.getName(), srcFolder + "/" + fileName, zip, false);
                }
            }
        }
    }

    public void extractFolder(String zipFile, String extractFolder) {
        try {
            int BUFFER = 2048;
            File file = new File(zipFile);

            ZipFile zip = new ZipFile(file);
            String newPath = extractFolder;

            new File(newPath).mkdir();
            Enumeration zipFileEntries = zip.entries();

            // Process each entry
            while (zipFileEntries.hasMoreElements()) {
                // grab a zip file entry
                ZipEntry entry = (ZipEntry) zipFileEntries.nextElement();
                String currentEntry = entry.getName();

                File destFile = new File(newPath, currentEntry);
                //destFile = new File(newPath, destFile.getName());
                File destinationParent = destFile.getParentFile();

                // create the parent directory structure if needed
                destinationParent.mkdirs();

                if (!entry.isDirectory()) {
                    BufferedInputStream is = new BufferedInputStream(zip
                            .getInputStream(entry));
                    int currentByte;
                    // establish buffer for writing file
                    byte[] data = new byte[BUFFER];

                    // write the current file to disk
                    FileOutputStream fos = new FileOutputStream(destFile);
                    BufferedOutputStream dest = new BufferedOutputStream(fos,
                            BUFFER);

                    // read and write until last byte is encountered
                    while ((currentByte = is.read(data, 0, BUFFER)) != -1) {
                        dest.write(data, 0, currentByte);
                    }
                    dest.flush();
                    dest.close();
                    is.close();
                }


            }
        } catch (Exception e) {
            System.err.println(e);
        }

    }
}