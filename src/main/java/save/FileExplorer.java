package save;

import javax.swing.*;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.filechooser.FileSystemView;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.DosFileAttributes;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;

public class FileExplorer extends JFrame {
    private JTree tree;
    private DefaultTreeModel treeModel;
    private final FileSystemView fileSystemView = FileSystemView.getFileSystemView();
    private File clipboardFile = null;
    private File clipboardSourceDir = null;
    private boolean cutOperation = false;

    public FileExplorer() {
        super("Проводник");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1920, 1080);
        initComponents();
        setLocationRelativeTo(null);
        setVisible(true);
    }

    private void initComponents() {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Компьютер");
        File[] roots = File.listRoots();
        for (File rootDrive : roots) {
            DefaultMutableTreeNode driveNode = new DefaultMutableTreeNode(rootDrive);
            root.add(driveNode);
            addDummyNode(driveNode);
        }

        treeModel = new DefaultTreeModel(root);
        tree = new JTree(treeModel);
        tree.setLargeModel(true);
        tree.setShowsRootHandles(true);
        tree.setCellRenderer(new FileTreeCellRenderer());

        tree.addTreeExpansionListener(new TreeExpansionListener() {
            @Override
            public void treeExpanded(TreeExpansionEvent event) {
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) event.getPath().getLastPathComponent();
                Object userObject = node.getUserObject();
                if (userObject instanceof File file) {
                    if (node.getChildCount() == 1 && ((DefaultMutableTreeNode) node.getChildAt(0)).getUserObject() == null) {
                        loadChildrenAsync(node, file);
                    }
                }
            }

            @Override
            public void treeCollapsed(TreeExpansionEvent event) {
            }
        });

        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    TreePath path = tree.getPathForLocation(e.getX(), e.getY());
                    if (path != null) {
                        tree.setSelectionPath(path);
                        showContextMenu(e, path);
                    }
                }
            }
        });

        add(new JScrollPane(tree));
    }

    private void addDummyNode(DefaultMutableTreeNode node) {
        node.add(new DefaultMutableTreeNode(null));
    }

    private void loadChildrenAsync(DefaultMutableTreeNode parentNode, File parentFile) {
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                parentNode.removeAllChildren();
                File[] children = parentFile.listFiles();
                if (children != null) {
                    Arrays.stream(children)
                            .filter(f -> !f.isHidden())
                            .forEach(child -> {
                                DefaultMutableTreeNode childNode = new DefaultMutableTreeNode(child);
                                if (child.isDirectory()) {
                                    addDummyNode(childNode);
                                }
                                parentNode.add(childNode);
                            });
                }
                return null;
            }

            @Override
            protected void done() {
                treeModel.reload(parentNode);
            }
        }.execute();
    }

    private void showContextMenu(MouseEvent e, TreePath path) {
        JPopupMenu menu = new JPopupMenu();
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
        Object userObject = node.getUserObject();

        if (!(userObject instanceof File file)) return;
        boolean isRootDrive = Arrays.asList(File.listRoots()).contains(file);

        JMenuItem createFolderItem = new JMenuItem("Создать папку");
        createFolderItem.setEnabled(!isRootDrive && file.isDirectory());
        createFolderItem.addActionListener(ev -> createFolder(file, node));
        menu.add(createFolderItem);

        JMenuItem deleteItem = new JMenuItem("Удалить");
        deleteItem.setEnabled(!isRootDrive);
        deleteItem.addActionListener(ev -> deleteFile(file, node));
        menu.add(deleteItem);

        JMenuItem renameItem = new JMenuItem("Переименовать");
        renameItem.setEnabled(!isRootDrive);
        renameItem.addActionListener(ev -> renameFile(file, node));
        menu.add(renameItem);

        JMenuItem copyItem = new JMenuItem("Копировать");
        copyItem.setEnabled(!isRootDrive);
        copyItem.addActionListener(ev -> copyFile(file));
        menu.add(copyItem);

        JMenuItem cutItem = new JMenuItem("Вырезать");
        cutItem.setEnabled(!isRootDrive);
        cutItem.addActionListener(ev -> cutFile(file));
        menu.add(cutItem);

        JMenuItem pasteItem = new JMenuItem("Вставить");
        pasteItem.setEnabled(file.isDirectory() && clipboardFile != null);
        pasteItem.addActionListener(ev -> pasteFile(file, node));
        menu.add(pasteItem);

        JMenuItem infoItem = new JMenuItem("Информация");
        infoItem.setEnabled(!isRootDrive);
        infoItem.addActionListener(ev -> showFileInfo(file));
        menu.add(infoItem);

        menu.show(tree, e.getX(), e.getY());
    }

    private void showFileInfo(File file) {
        StringBuilder info = buildBaseFileInfo(file);

        if (file.isFile()) {
            addSizeInfo(info, file.length());
            showInfoDialog(info.toString(), "Информация о файле");
        } else {
            new SwingWorker<Long, Void>() {
                @Override
                protected Long doInBackground() {
                    return calculateSizeRecursive(file);
                }

                @Override
                protected void done() {
                    try {
                        addSizeInfo(info, get());
                        showInfoDialog(info.toString(), "Информация о папке");
                    } catch (Exception ex) {
                        showErrorDialog("Ошибка расчета размера");
                    }
                }
            }.execute();
        }
    }

    private void addSizeInfo(StringBuilder info, long bytes) {
        double mb = bytes / (1024.0 * 1024.0);
        info.append("4. Размер: ")
                .append(bytes).append(" байт (")
                .append(String.format("%.2f МБ)", mb)).append("\n");
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

    private String getFileExtension(File file) {
        if (file.isDirectory()) return "";
        String name = file.getName();
        int dotIndex = name.lastIndexOf('.');
        return (dotIndex > 0 && dotIndex < name.length() - 1) ?
                name.substring(dotIndex + 1) : "";
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
                    .append(sdf.format(new Date(attrs.creationTime().toMillis()))).append("\n")
                    .append("7. Дата изменения: ")
                    .append(sdf.format(new Date(attrs.lastModifiedTime().toMillis()))).append("\n");
        } catch (IOException e) {
            info.append("6. Дата создания: недоступно\n")
                    .append("7. Дата изменения: недоступно\n");
        }
    }

    private void appendPermissions(StringBuilder info, File file) {
        info.append("8. Права: ")
                .append(file.canRead() ? "Чтение " : "")
                .append(file.canWrite() ? "Запись " : "")
                .append(file.canExecute() ? "Выполнение" : "")
                .append("\n");
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
        } catch (Exception ignored) {
        }

        info.append("10. Атрибуты: ").append(String.join(", ", attrs)).append("\n");
    }

    private void showInfoDialog(String message, String title) {
        JOptionPane.showMessageDialog(this, message, title, JOptionPane.INFORMATION_MESSAGE);
    }

    private void showErrorDialog(String message) {
        JOptionPane.showMessageDialog(this, message, "Ошибка", JOptionPane.ERROR_MESSAGE);
    }

    private void createFolder(File parentDir, DefaultMutableTreeNode parentNode) {
        String name = JOptionPane.showInputDialog(this, "Имя новой папки:");
        if (name == null || name.trim().isEmpty()) return;

        File newFolder = new File(parentDir, name);
        if (newFolder.mkdir()) {
            DefaultMutableTreeNode newNode = new DefaultMutableTreeNode(newFolder);
            parentNode.add(newNode);
            treeModel.reload(parentNode);
        } else {
            showErrorDialog("Ошибка создания папки");
        }
    }

    private void deleteFile(File file, DefaultMutableTreeNode node) {
        int confirm = JOptionPane.showConfirmDialog(
                this,
                "Удалить '" + file.getName() + "'?",
                "Подтверждение",
                JOptionPane.YES_NO_OPTION
        );

        if (confirm == JOptionPane.YES_OPTION) {
            if (deleteRecursive(file)) {
                DefaultMutableTreeNode parent = (DefaultMutableTreeNode) node.getParent();
                parent.remove(node);
                treeModel.reload(parent);
            } else {
                showErrorDialog("Ошибка удаления");
            }
        }
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

    private void renameFile(File oldFile, DefaultMutableTreeNode node) {
        String newName = JOptionPane.showInputDialog(this, "Новое имя:", oldFile.getName());
        if (newName == null || newName.trim().isEmpty()) return;

        File newFile = new File(oldFile.getParent(), newName);
        if (oldFile.renameTo(newFile)) {
            node.setUserObject(newFile);
            treeModel.reload(node);
        } else {
            showErrorDialog("Ошибка переименования");
        }
    }

    private void copyFile(File file) {
        clipboardFile = file;
        cutOperation = false;
    }

    private void cutFile(File file) {
        clipboardFile = file;
        clipboardSourceDir = file.getParentFile();
        cutOperation = true;
    }

    private void pasteFile(File targetDir, DefaultMutableTreeNode targetNode) {
        if (clipboardFile == null) return;

        File dest = new File(targetDir, clipboardFile.getName());

        if (!cutOperation && dest.exists()) {
            String baseName = clipboardFile.getName();
            String extension = "";
            int dotIndex = baseName.lastIndexOf('.');

            if (dotIndex > 0) {
                extension = baseName.substring(dotIndex);
                baseName = baseName.substring(0, dotIndex);
            }

            int copyNum = 1;
            while (dest.exists()) {
                String newName = String.format("%s - Копия (%d)%s", baseName, copyNum, extension);
                dest = new File(targetDir, newName);
                copyNum++;
            }
        }

        try {
            if (clipboardFile.isDirectory()) {
                copyDirectory(clipboardFile.toPath(), dest.toPath());
            } else {
                Files.copy(clipboardFile.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }

            if (cutOperation) {
                deleteRecursive(clipboardFile);
                cutOperation = false;
                refreshNode(clipboardSourceDir);
                clipboardSourceDir = null;
            }

            loadChildrenAsync(targetNode, targetDir);
        } catch (IOException ex) {
            showErrorDialog("Ошибка вставки: " + ex.getMessage());
        }
    }

    private void copyDirectory(Path source, Path target) throws IOException {
        Files.walk(source).forEach(s -> {
            try {
                Path d = target.resolve(source.relativize(s));
                if (Files.isDirectory(s)) {
                    Files.createDirectories(d);
                } else {
                    Files.copy(s, d, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        });
    }

    private long calculateSizeRecursive(File file) {
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

    private void refreshNode(File file) {
        DefaultMutableTreeNode node = findNodeByFile(file);
        if (node != null) {
            loadChildrenAsync(node, file);
        }
    }

    private DefaultMutableTreeNode findNodeByFile(File targetFile) {
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) treeModel.getRoot();
        return findNodeRecursive(root, targetFile);
    }

    private DefaultMutableTreeNode findNodeRecursive(DefaultMutableTreeNode node, File targetFile) {
        Object userObject = node.getUserObject();
        if (userObject instanceof File nodeFile) {
            if (nodeFile.equals(targetFile)) {
                return node;
            }
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) node.getChildAt(i);
            DefaultMutableTreeNode found = findNodeRecursive(child, targetFile);
            if (found != null) {
                return found;
            }
        }

        return null;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(FileExplorer::new);
    }

    private class FileTreeCellRenderer extends DefaultTreeCellRenderer {
        @Override
        public Component getTreeCellRendererComponent(
                JTree tree, Object value, boolean selected,
                boolean expanded, boolean leaf, int row, boolean hasFocus
        ) {
            super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
            Object userObject = node.getUserObject();

            if (userObject instanceof File file) {
                setText(fileSystemView.getSystemDisplayName(file));
                setIcon(fileSystemView.getSystemIcon(file));

                if (Arrays.asList(File.listRoots()).contains(file)) {
                    setForeground(Color.GRAY);
                }
            }
            return this;
        }
    }
}