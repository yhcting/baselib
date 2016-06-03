/******************************************************************************
 * Copyright (C) 2016
 * Younghyung Cho. <yhcting77@gmail.com>
 * All rights reserved.
 *
 * This file is part of free.yhc.baselib
 *
 * This program is licensed under the FreeBSD license
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * The views and conclusions contained in the software and documentation
 * are those of the authors and should not be interpreted as representing
 * official policies, either expressed or implied, of the FreeBSD Project.
 *****************************************************************************/

package free.yhc.baselib.util;

import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import free.yhc.baselib.Logger;

public class FileUtil {
    private static final boolean DBG = Logger.DBG_DEFAULT;
    private static final Logger P = Logger.create(FileUtil.class, Logger.LOGLV_DEFAULT);

    private static final int DEFAULT_FILE_BUFFER_SIZE = 16 * 1024;

    // Characters that is not allowed as filename in Android.
    private static final char[] sNoPathNameChars = new char[] {
            '/', '?', '"', '\'', '`', ':', ';', '*', '|', '\\', '<', '>'
    };

    ///////////////////////////////////////////////////////////////////////////
    //
    //
    //
    ///////////////////////////////////////////////////////////////////////////
    /**
     *  Characters those are not allowed as pathname, are replaced with '~' characdter.
     *
     * @param str pathname string
     * @return Escaped string.
     */
    @NotNull
    public static String
    pathNameEscapeString(@NotNull String str) {
        // Most Unix (including Linux) allows all 8bit-character as file name
        //   except for ('/' and 'null').
        // But android shell doesn't allows some characters.
        // So, we need to handle those...
        for (char c : sNoPathNameChars)
            str = str.replace(c, '~');
        return str;
    }

    ///////////////////////////////////////////////////////////////////////////
    //
    //
    //
    ///////////////////////////////////////////////////////////////////////////
    private static void
    zipDir(@NotNull ZipOutputStream zos,
           @NotNull String directory,
           @NotNull String dirPathName)
           throws IOException {
        File zipDir = new File(directory);

        // get a listing of the directory content
        String[] dirList = zipDir.list();
        byte[] buf = new byte[DEFAULT_FILE_BUFFER_SIZE];
        // loop through dirList, and zip the files
        for (String aDirList : dirList) {
            File f = new File(zipDir, aDirList);
            if (f.isDirectory()) {
                String filePath = f.getPath();
                zipDir(zos, filePath, dirPathName + f.getName() + "/");
                continue;
            }
            try (FileInputStream fis = new FileInputStream(f)) {
                ZipEntry anEntry = new ZipEntry(dirPathName + f.getName());
                zos.putNextEntry(anEntry);
                int br; // bytes read
                while (0 < (br = fis.read(buf)))
                    zos.write(buf, 0, br);
            }
        }
    }

    private static void
    zipFile(@NotNull ZipOutputStream zos, @NotNull String fsrc)
            throws IOException {
        File file = new File(fsrc);
        try (FileInputStream fis = new FileInputStream(file)) {
            zip(zos, fis, file.getName());
        }
    }

    public static void
    zip(@NotNull ZipOutputStream zos,
        @NotNull String fsrc)
            throws IOException {
        File f = new File(fsrc);
        if (f.isDirectory())
            zipDir(zos, fsrc, "");
        else if (f.isFile())
            zipFile(zos, fsrc);
        else
            throw new IOException();
    }

    public static void
    zip(@NotNull ZipOutputStream zos,
        @NotNull InputStream is,
        @NotNull String entryName)
            throws IOException {
        ZipEntry ze = new ZipEntry(entryName);
        byte[] buf = new byte[DEFAULT_FILE_BUFFER_SIZE];
        zos.putNextEntry(ze);
        int br; // bytes read
        while (0 < (br = is.read(buf)))
            zos.write(buf, 0, br);
    }

    public static void
    zip(@NotNull String zipFilePath,
        @NotNull String srcFilePath)
            throws IOException {
        ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFilePath));
        zos.setLevel(8);
        try {
            zip(zos, srcFilePath);
        } finally {
            zos.close();
        }
    }

    public static void
    unzip(@NotNull OutputStream os, @NotNull ZipInputStream zis)
            throws IOException {
        while (null != zis.getNextEntry()) {
            int br;
            byte buf[] = new byte[DEFAULT_FILE_BUFFER_SIZE];
            while (0 < (br = zis.read(buf)))
                os.write(buf, 0, br);
        }
    }

    public static void
    unzip(@NotNull String outDir, @NotNull ZipInputStream zis)
            throws IOException {
        ZipEntry ze;
        while (null != (ze = zis.getNextEntry())) {
            File f = new File(outDir, ze.getName());
            //create directories if required.
            // return value is ignored intentionally
            //noinspection ResultOfMethodCallIgnored
            f.getParentFile().mkdirs();

            //if the entry is directory, leave it. Otherwise extract it.
            if (!ze.isDirectory()) {
                int br;
                byte buf[] = new byte[DEFAULT_FILE_BUFFER_SIZE];
                try (FileOutputStream fos = new FileOutputStream(f)) {
                    while (0 < (br = zis.read(buf)))
                        fos.write(buf, 0, br);
                }
            }
        }
    }

    public static void
    unzip(@NotNull String outDir, @NotNull String file)
            throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(file))) {
            unzip(outDir, zis);
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    //
    //
    //
    ///////////////////////////////////////////////////////////////////////////
    /**
     * Remove files recursively excluding files/directories in {@code skips}
     *
     * @param f file or directory
     * @param skips Absolute file path string that SHOULD NOT be excluded.
     *              If directory is in it, all sub files are also excluded from recursive removing.
     * @return true if all target files are removed successfully otherwise false.
     */
    public static boolean
    removeFileRecursive(@NotNull File f, @NotNull HashSet<String> skips) {
        if (skips.contains(f.getAbsolutePath()))
            return true;
        if (!f.exists())
            return true;
        if (f.isDirectory()) {
            for (File c : f.listFiles()) {
                if (!removeFileRecursive(c, skips))
                    return false;
            }
        }
        return f.delete();
    }

    public static boolean
    removeFileRecursive(@NotNull File f, @NotNull File[] skips) {
        HashSet<String> skipSets = new HashSet<>();
        for (File skf : skips)
            skipSets.add(skf.getAbsolutePath());
        return removeFileRecursive(f, skipSets);
    }

    public static boolean
    removeFileRecursive(@NotNull File f, @NotNull File skip) {
        return removeFileRecursive(f, new File[] { skip });
    }

    /**
     * @return 'false' if deleting one of files fails, and execution is stopped in the middle.
     */
    public static boolean
    removeFileRecursive(@NotNull File f) {
        return removeFileRecursive(f, new File[0]);
    }

    public static boolean
    cleanDirectory(@NotNull File d, @NotNull HashSet<String> skips) {
        File[] files = d.listFiles();
        // d is NOT directory or something wrong... exception...
        if (null == files)
            return false;

        for (File c : d.listFiles()) {
            if (!removeFileRecursive(c, skips))
                return false;
        }
        return true;
    }

    public static boolean
    cleanDirectory(@NotNull File d, @NotNull File[] skips) {
        HashSet<String> skipSets = new HashSet<>();
        for (File skf : skips)
            skipSets.add(skf.getAbsolutePath());
        return cleanDirectory(d, skipSets);
    }

    public static boolean
    cleanDirectory(@NotNull File d) {
        return cleanDirectory(d, new File[0]);
    }

    public static void
    addFilesRecursive(LinkedList<File> l, File f) throws FileNotFoundException {
        if (!f.exists())
            throw new FileNotFoundException();

        if (f.isDirectory()) {
            for (File c : f.listFiles())
                addFilesRecursive(l, c);
        } else
            l.add(f);
    }

    ///////////////////////////////////////////////////////////////////////////
    //
    //
    //
    ///////////////////////////////////////////////////////////////////////////
    /**
     *
     * @param file Text file.
     * @return 'null' if fails. And value for reading non-text files, is not defined.
     */
    @NotNull
    public static String
    readTextFile(@NotNull File file)
            throws IOException {
        StringBuilder fileData = new StringBuilder(DEFAULT_FILE_BUFFER_SIZE);
        BufferedReader reader = new BufferedReader(new FileReader(file));
        char[] buf = new char[DEFAULT_FILE_BUFFER_SIZE];
        int bytes;
        while(-1 != (bytes = reader.read(buf)))
            fileData.append(buf, 0, bytes);
        reader.close();
        return fileData.toString();
    }

    public static void
    writeTextFile(@NotNull File file, @NotNull String text)
            throws IOException {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(file))) {
            bw.write(text);
            bw.flush();
        }
    }
}
