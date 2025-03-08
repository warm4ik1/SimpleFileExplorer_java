package org.warm4ik.lab;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

public class FileExplorer {
    private File clipboardFile = null;
    private File clipboardSourceDir = null;
    private boolean cutOperation = false;

    public boolean hasClipboard() {
        return clipboardFile != null;
    }

    public File createFolder(File parentDir, String folderName) {
        if (folderName == null || folderName.trim().isEmpty()) return null;
        File newFolder = new File(parentDir, folderName);
        return newFolder.mkdir() ? newFolder : null;
    }

    public boolean deleteFile(File file) {
        return deleteRecursive(file);
    }

    private boolean deleteRecursive(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        return file.delete();
    }

    public File renameFile(File file, String newName) {
        if (newName == null || newName.trim().isEmpty()) return null;
        File newFile = new File(file.getParent(), newName);
        return file.renameTo(newFile) ? newFile : null;
    }

    public void copyFile(File file) {
        clipboardFile = file;
        cutOperation = false;
    }

    public void cutFile(File file) {
        clipboardFile = file;
        clipboardSourceDir = file.getParentFile();
        cutOperation = true;
    }

    public File pasteFile(File targetDir) {
        if (clipboardFile == null) return null;
        File dest = new File(targetDir, clipboardFile.getName());
        try {
            if (cutOperation) {
                Files.move(clipboardFile.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
                clipboardFile = null;
                cutOperation = false;
                clipboardSourceDir = null;
            } else {
                dest = generateUniqueFile(targetDir, clipboardFile.getName());
                if (clipboardFile.isDirectory()) {
                    copyDirectory(clipboardFile.toPath(), dest.toPath());
                } else {
                    Files.copy(clipboardFile.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
            }
            return dest;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private File generateUniqueFile(File targetDir, String originalName) {
        File dest = new File(targetDir, originalName);
        if (!dest.exists()) {
            return dest;
        }
        String baseName = originalName;
        String extension = "";
        int dotIndex = originalName.lastIndexOf('.');
        if (dotIndex > 0) {
            baseName = originalName.substring(0, dotIndex);
            extension = originalName.substring(dotIndex);
        }
        int copyNum = 1;
        while (dest.exists()) {
            String newName = String.format("%s - Копия (%d)%s", baseName, copyNum, extension);
            dest = new File(targetDir, newName);
            copyNum++;
        }
        return dest;
    }


    private void copyDirectory(Path source, Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path targetDir = target.resolve(source.relativize(dir));
                Files.createDirectories(targetDir);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path targetFile = target.resolve(source.relativize(file));
                Files.copy(file, targetFile, StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    public long calculateSizeRecursive(File file) {
        if (file.isFile()) return file.length();
        long size = 0;
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                size += calculateSizeRecursive(child);
            }
        }
        return size;
    }

    public String getFileInfo(File file) {
        StringBuilder info = buildBaseFileInfo(file);
        long size = file.isFile() ? file.length() : calculateSizeRecursive(file);
        addSizeInfo(info, size);
        return info.toString();
    }

    private StringBuilder buildBaseFileInfo(File file) {
        StringBuilder info = new StringBuilder();
        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");
        info.append("1. Имя: ").append(file.getName()).append("\n");
        String extension = getFileExtension(file);
        info.append("2. Расширение: ").append(extension.isEmpty() ? "нет" : extension).append("\n");
        info.append("3. Путь: ").append(file.getAbsolutePath()).append("\n");
        info.append("5. Тип: ").append(getFileType(file, extension)).append("\n");
        appendDates(info, file, sdf);
        appendPermissions(info, file);
        appendOwner(info, file);
        appendAttributes(info, file);
        return info;
    }

    private void addSizeInfo(StringBuilder info, long bytes) {
        double mb = bytes / (1024.0 * 1024.0);
        info.append("4. Размер: ")
                .append(bytes).append(" байт (")
                .append(String.format("%.2f МБ", mb)).append(")").append("\n");
    }

    private String getFileExtension(File file) {
        if (file.isDirectory()) return "";
        String name = file.getName();
        int dotIndex = name.lastIndexOf('.');
        return (dotIndex > 0 && dotIndex < name.length() - 1) ? name.substring(dotIndex + 1) : "";
    }

    private String getFileType(File file, String extension) {
        if (file.isDirectory()) return "Папка";
        return switch (extension.toLowerCase()) {
            case "txt" -> "Текстовый файл";
            case "jpg", "jpeg", "png", "gif" -> "Изображение";
            case "mp3", "wav" -> "Аудиофайл";
            case "exe" -> "Исполняемый файл";
            case "zip", "rar" -> "Архив";
            default -> "Файл";
        };
    }

    private void appendDates(StringBuilder info, File file, SimpleDateFormat sdf) {
        try {
            BasicFileAttributes attrs = Files.readAttributes(file.toPath(), BasicFileAttributes.class);
            info.append("6. Дата создания: ")
                    .append(sdf.format(new Date(attrs.creationTime().toMillis()))).append("\n");
            info.append("7. Дата изменения: ")
                    .append(sdf.format(new Date(attrs.lastModifiedTime().toMillis()))).append("\n");
        } catch (IOException e) {
            info.append("6. Дата создания: недоступно\n");
            info.append("7. Дата изменения: недоступно\n");
        }
    }

    private void appendPermissions(StringBuilder info, File file) {
        info.append("8. Права: ")
                .append(file.canRead() ? "Чтение " : "")
                .append(file.canWrite() ? "Запись " : "")
                .append(file.canExecute() ? "Выполнение" : "").append("\n");
    }

    private void appendOwner(StringBuilder info, File file) {
        try {
            info.append("9. Владелец: ")
                    .append(Files.getOwner(file.toPath()).getName()).append("\n");
        } catch (IOException e) {
            info.append("9. Владелец: недоступно\n");
        }
    }

    private void appendAttributes(StringBuilder info, File file) {
        ArrayList<String> attrs = new ArrayList<>();
        if (file.isHidden()) attrs.add("Скрытый");
        if (!file.canWrite()) attrs.add("Только для чтения");
        try {
            DosFileAttributes dos = Files.readAttributes(file.toPath(), DosFileAttributes.class);
            if (dos.isArchive()) attrs.add("Архивный");
            if (dos.isSystem()) attrs.add("Системный");
        } catch (Exception ignored) { }
        info.append("10. Атрибуты: ").append(String.join(", ", attrs)).append("\n");
    }
}
