package org.warm4ik.lab;

import javax.swing.*;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.filechooser.FileSystemView;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.Arrays;

public class FileExplorerGUI extends JFrame {
    private JTree tree;
    private DefaultTreeModel treeModel;
    private final FileSystemView fileSystemView = FileSystemView.getFileSystemView();
    private final FileExplorer fileOps = new FileExplorer();

    public FileExplorerGUI() {
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
        Arrays.stream(roots).forEach(rootDrive -> {
            DefaultMutableTreeNode driveNode = new DefaultMutableTreeNode(rootDrive);
            root.add(driveNode);
            addDummyNode(driveNode);
        });

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
            public void treeCollapsed(TreeExpansionEvent event) { }
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
        createFolderItem.addActionListener(ev -> {
            String name = JOptionPane.showInputDialog(this, "Имя новой папки:");
            File newFolder = fileOps.createFolder(file, name);
            if (newFolder != null) {
                DefaultMutableTreeNode newNode = new DefaultMutableTreeNode(newFolder);
                node.add(newNode);
                treeModel.reload(node);
            } else {
                showErrorDialog("Ошибка создания папки");
            }
        });
        menu.add(createFolderItem);

        JMenuItem deleteItem = new JMenuItem("Удалить");
        deleteItem.setEnabled(!isRootDrive);
        deleteItem.addActionListener(ev -> {
            int confirm = JOptionPane.showConfirmDialog(
                    this,
                    "Удалить '" + file.getName() + "'?",
                    "Подтверждение",
                    JOptionPane.YES_NO_OPTION
            );
            if (confirm == JOptionPane.YES_OPTION) {
                if (fileOps.deleteFile(file)) {
                    DefaultMutableTreeNode parent = (DefaultMutableTreeNode) node.getParent();
                    if (parent != null) {
                        parent.remove(node);
                        treeModel.reload(parent);
                    }
                } else {
                    showErrorDialog("Ошибка удаления");
                }
            }
        });
        menu.add(deleteItem);

        JMenuItem renameItem = new JMenuItem("Переименовать");
        renameItem.setEnabled(!isRootDrive);
        renameItem.addActionListener(ev -> {
            String newName = JOptionPane.showInputDialog(this, "Новое имя:", file.getName());
            File renamed = fileOps.renameFile(file, newName);
            if (renamed != null) {
                node.setUserObject(renamed);
                treeModel.reload(node);
            } else {
                showErrorDialog("Ошибка переименования");
            }
        });
        menu.add(renameItem);

        JMenuItem copyItem = new JMenuItem("Копировать");
        copyItem.setEnabled(!isRootDrive);
        copyItem.addActionListener(ev -> fileOps.copyFile(file));
        menu.add(copyItem);

        JMenuItem cutItem = new JMenuItem("Вырезать");
        cutItem.setEnabled(!isRootDrive);
        cutItem.addActionListener(ev -> fileOps.cutFile(file));
        menu.add(cutItem);

        JMenuItem pasteItem = new JMenuItem("Вставить");
        pasteItem.setEnabled(file.isDirectory() && fileOps.hasClipboard());
        pasteItem.addActionListener(ev -> {
            File pasted = fileOps.pasteFile(file);
            if (pasted != null) {
                loadChildrenAsync(node, file);
            } else {
                showErrorDialog("Ошибка вставки");
            }
        });
        menu.add(pasteItem);

        JMenuItem infoItem = new JMenuItem("Информация");
        infoItem.setEnabled(!isRootDrive);
        infoItem.addActionListener(ev -> {
            String info = fileOps.getFileInfo(file);
            showInfoDialog(info, "Информация о файле/папке");
        });
        menu.add(infoItem);

        menu.show(tree, e.getX(), e.getY());
    }

    private void showInfoDialog(String message, String title) {
        JOptionPane.showMessageDialog(this, message, title, JOptionPane.INFORMATION_MESSAGE);
    }

    private void showErrorDialog(String message) {
        JOptionPane.showMessageDialog(this, message, "Ошибка", JOptionPane.ERROR_MESSAGE);
    }

    private DefaultMutableTreeNode findNodeByFile(File targetFile) {
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) treeModel.getRoot();
        return findNodeRecursive(root, targetFile);
    }

    private DefaultMutableTreeNode findNodeRecursive(DefaultMutableTreeNode node, File targetFile) {
        Object userObject = node.getUserObject();
        if (userObject instanceof File file && file.equals(targetFile)) {
            return node;
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
        SwingUtilities.invokeLater(FileExplorerGUI::new);
    }

    private class FileTreeCellRenderer extends DefaultTreeCellRenderer {
        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected,
                                                      boolean expanded, boolean leaf, int row, boolean hasFocus) {
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
