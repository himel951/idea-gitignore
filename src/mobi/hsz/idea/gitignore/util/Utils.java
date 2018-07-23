/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2018 hsz Jakub Chrzanowski <jakub@hsz.mobi>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package mobi.hsz.idea.gitignore.util;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.EditorSettings;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.containers.ContainerUtil;
import mobi.hsz.idea.gitignore.IgnoreBundle;
import mobi.hsz.idea.gitignore.command.CreateFileCommandAction;
import mobi.hsz.idea.gitignore.file.type.IgnoreFileType;
import mobi.hsz.idea.gitignore.lang.IgnoreLanguage;
import mobi.hsz.idea.gitignore.psi.IgnoreFile;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.util.*;

import static com.intellij.ui.SimpleTextAttributes.REGULAR_ATTRIBUTES;

/**
 * {@link Utils} class that contains various methods.
 *
 * @author Jakub Chrzanowski <jakub@hsz.mobi>
 * @since 0.3.3
 */
public class Utils {
    /** Private constructor to prevent creating {@link Utils} instance. */
    private Utils() {
    }

    /**
     * Gets relative path of given @{link VirtualFile} and root directory.
     *
     * @param directory root directory
     * @param file      file to get it's path
     * @return relative path
     */
    @Nullable
    public static String getRelativePath(@NotNull VirtualFile directory, @NotNull VirtualFile file) {
        return VfsUtilCore.getRelativePath(file, directory, '/') + (file.isDirectory() ? '/' : "");
    }

    /**
     * Gets Ignore file for given {@link Project} root directory.
     *
     * @param project  current project
     * @param fileType current ignore file type
     * @return Ignore file
     */
    @Nullable
    public static PsiFile getIgnoreFile(@NotNull Project project, @NotNull IgnoreFileType fileType) throws Throwable {
        return getIgnoreFile(project, fileType, null, false);
    }

    /**
     * Gets Ignore file for given {@link Project} and root {@link PsiDirectory}.
     *
     * @param project   current project
     * @param fileType  current ignore file type
     * @param directory root directory
     * @return Ignore file
     */
    @Nullable
    public static PsiFile getIgnoreFile(@NotNull Project project, @NotNull IgnoreFileType fileType,
                                        @Nullable PsiDirectory directory) throws Throwable {
        return getIgnoreFile(project, fileType, directory, false);
    }

    /**
     * Gets Ignore file for given {@link Project} and root {@link PsiDirectory}.
     * If file is missing - creates new one.
     *
     * @param project         current project
     * @param fileType        current ignore file type
     * @param directory       root directory
     * @param createIfMissing create new file if missing
     * @return Ignore file
     */
    @Nullable
    public static PsiFile getIgnoreFile(@NotNull Project project, @NotNull IgnoreFileType fileType,
                                        @Nullable PsiDirectory directory, boolean createIfMissing) {
        if (directory == null) {
            directory = PsiManager.getInstance(project).findDirectory(project.getBaseDir());
        }

        assert directory != null;
        String filename = fileType.getIgnoreLanguage().getFilename();
        PsiFile file = directory.findFile(filename);
        VirtualFile virtualFile = file == null ? directory.getVirtualFile().findChild(filename) : file.getVirtualFile();

        if (file == null && virtualFile == null && createIfMissing) {
            file = new CreateFileCommandAction(project, directory, fileType).execute().getResultObject();
        }

        return file;
    }

    /**
     * Finds {@link PsiFile} for the given {@link VirtualFile} instance. If file is outside current project,
     * it's required to create new {@link PsiFile} manually.
     *
     * @param project     current project
     * @param virtualFile to handle
     * @return {@link PsiFile} instance
     */
    @Nullable
    public static PsiFile getPsiFile(@NotNull Project project, @NotNull VirtualFile virtualFile) {
        PsiFile psiFile = PsiManager.getInstance(project).findFile(virtualFile);

        if (psiFile == null) {
            FileViewProvider viewProvider = PsiManager.getInstance(project).findViewProvider(virtualFile);
            if (viewProvider != null) {
                IgnoreLanguage language = IgnoreBundle.obtainLanguage(virtualFile);
                if (language != null) {
                    psiFile = language.createFile(viewProvider);
                }
            }
        }

        return psiFile;
    }

    /**
     * Opens given file in editor.
     *
     * @param project current project
     * @param file    file to open
     */
    public static void openFile(@NotNull Project project, @NotNull PsiFile file) {
        openFile(project, file.getVirtualFile());
    }

    /**
     * Opens given file in editor.
     *
     * @param project current project
     * @param file    file to open
     */
    public static void openFile(@NotNull Project project, @NotNull VirtualFile file) {
        FileEditorManager.getInstance(project).openFile(file, true);
    }

    /**
     * Returns all Ignore files in given {@link Project} that can match current passed file.
     *
     * @param project current project
     * @param file    current file
     * @return collection of suitable Ignore files
     *
     * @throws ExternalFileException
     */
    public static List<VirtualFile> getSuitableIgnoreFiles(@NotNull Project project, @NotNull IgnoreFileType fileType,
                                                           @NotNull VirtualFile file)
            throws ExternalFileException {
        List<VirtualFile> files = ContainerUtil.newArrayList();
        if (file.getCanonicalPath() == null || project.getBaseDir() == null ||
                !VfsUtilCore.isAncestor(project.getBaseDir(), file, true)) {
            throw new ExternalFileException();
        }
        VirtualFile baseDir = project.getBaseDir();
        if (baseDir != null && !baseDir.equals(file)) {
            do {
                file = file.getParent();
                VirtualFile ignoreFile = file.findChild(fileType.getIgnoreLanguage().getFilename());
                ContainerUtil.addIfNotNull(files, ignoreFile);
            } while (!file.equals(project.getBaseDir()));
        }
        return files;
    }

    /**
     * Checks if given directory is a {@link IgnoreLanguage#getVcsDirectory()}.
     *
     * @param directory to check
     * @return given file is VCS directory
     */
    public static boolean isVcsDirectory(@NotNull VirtualFile directory) {
        if (!directory.isDirectory()) {
            return false;
        }
        for (IgnoreLanguage language : IgnoreBundle.VCS_LANGUAGES) {
            final String vcsName = language.getVcsDirectory();
            if (directory.getName().equals(vcsName) && IgnoreBundle.ENABLED_LANGUAGES.get(language.getFileType())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Searches for excluded roots in given {@link Project}.
     *
     * @param project current project
     * @return list of excluded roots
     */
    public static List<VirtualFile> getExcludedRoots(@NotNull Project project) {
        List<VirtualFile> roots = ContainerUtil.newArrayList();
        ModuleManager manager = ModuleManager.getInstance(project);
        for (Module module : manager.getModules()) {
            ModifiableRootModel model = ModuleRootManager.getInstance(module).getModifiableModel();
            if (model.isDisposed()) {
                continue;
            }
            Collections.addAll(roots, model.getExcludeRoots());
            model.dispose();
        }
        return roots;
    }

    /**
     * Gets list of words for given {@link String} excluding special characters.
     *
     * @param filter input string
     * @return list of words without special characters
     */
    public static List<String> getWords(@NotNull String filter) {
        List<String> words = ContainerUtil.newArrayList(filter.toLowerCase().split("\\W+"));
        words.removeAll(Arrays.asList(null, ""));
        return words;
    }

    /**
     * Returns Gitignore plugin information.
     *
     * @return {@link IdeaPluginDescriptor}
     */
    public static IdeaPluginDescriptor getPlugin() {
        return PluginManager.getPlugin(PluginId.getId(IgnoreBundle.PLUGIN_ID));
    }

    /**
     * Returns plugin major version.
     *
     * @return major version
     */
    public static String getMajorVersion() {
        return getVersion().split("\\.")[0];
    }

    /**
     * Returns plugin minor version.
     *
     * @return minor version
     */
    public static String getMinorVersion() {
        return StringUtil.join(getVersion().split("\\."), 0, 2, ".");
    }

    /**
     * Returns plugin version.
     *
     * @return version
     */
    public static String getVersion() {
        return getPlugin().getVersion();
    }

    /**
     * Checks if lists are equal.
     *
     * @param l1 first list
     * @param l2 second list
     * @return lists are equal
     */
    public static boolean equalLists(@NotNull List<?> l1, @NotNull List<?> l2) {
        return l1.size() == l2.size() && l1.containsAll(l2) && l2.containsAll(l1);
    }

    /**
     * Returns {@link IgnoreFileType} basing on the {@link VirtualFile} file.
     *
     * @param virtualFile current file
     * @return file type
     */
    public static IgnoreFileType getFileType(@Nullable VirtualFile virtualFile) {
        if (virtualFile != null) {
            FileType fileType = virtualFile.getFileType();
            if (fileType instanceof IgnoreFileType) {
                return (IgnoreFileType) fileType;
            }
        }
        return null;
    }

    /**
     * Checks if file is under given directory.
     *
     * @param file      file
     * @param directory directory
     * @return file is under directory
     */
    public static boolean isUnder(@NotNull VirtualFile file, @NotNull VirtualFile directory) {
        if (directory.equals(file)) {
            return true;
        }
        VirtualFile parent = file.getParent();
        while (parent != null) {
            if (directory.equals(parent)) {
                return true;
            }
            parent = parent.getParent();
        }
        return false;
    }

    /**
     * Checks if file is in project directory.
     *
     * @param file    file
     * @param project project
     * @return file is under directory
     */
    public static boolean isInProject(@NotNull final VirtualFile file, @NotNull final Project project) {
        return project.getBaseDir() != null && (isUnder(file, project.getBaseDir()) ||
                StringUtil.startsWith(file.getUrl(), "temp://"));
    }

    /**
     * Creates and configures template preview editor.
     *
     * @param document virtual editor document
     * @param project  current project
     * @return editor
     */
    @NotNull
    public static Editor createPreviewEditor(@NotNull Document document, @Nullable Project project, boolean isViewer) {
        EditorEx editor = (EditorEx) EditorFactory.getInstance().createEditor(document, project,
                IgnoreFileType.INSTANCE, isViewer);
        editor.setCaretEnabled(!isViewer);

        final EditorSettings settings = editor.getSettings();
        settings.setLineNumbersShown(false);
        settings.setAdditionalColumnsCount(1);
        settings.setAdditionalLinesCount(0);
        settings.setRightMarginShown(false);
        settings.setFoldingOutlineShown(false);
        settings.setLineMarkerAreaShown(false);
        settings.setIndentGuidesShown(false);
        settings.setVirtualSpace(false);
        settings.setWheelFontChangeEnabled(false);

        EditorColorsScheme colorsScheme = editor.getColorsScheme();
        colorsScheme.setColor(EditorColors.CARET_ROW_COLOR, null);

        return editor;
    }

    /**
     * Checks if specified plugin is enabled.
     *
     * @param id plugin id
     * @return plugin is enabled
     */
    private static boolean isPluginEnabled(@NotNull final String id) {
        IdeaPluginDescriptor p = PluginManager.getPlugin(PluginId.getId(id));
        return p instanceof IdeaPluginDescriptorImpl && p.isEnabled();
    }

    /**
     * Checks if Git plugin is enabled.
     *
     * @return Git plugin is enabled
     */
    public static boolean isGitPluginEnabled() {
        return isPluginEnabled("Git4Idea");
    }

    /**
     * Resolves user directory with the <code>user.home</code> property.
     *
     * @param path path with leading ~
     * @return resolved path
     */
    public static String resolveUserDir(@Nullable String path) {
        if (StringUtil.startsWithChar(path, '~')) {
            assert path != null;
            path = System.getProperty("user.home") + path.substring(1);
        }
        return path;
    }

    /**
     * Sorts {@link IgnoreFile} ascending using files path.
     * Uses passed argument by reference and additionally returns the same object.
     *
     * @param files {@link IgnoreFile} list
     * @return sorted list
     */
    public static List<IgnoreFile> ignoreFilesSort(final List<IgnoreFile> files) {
        ContainerUtil.sort(files, new Comparator<IgnoreFile>() {
            @Override
            public int compare(IgnoreFile file1, IgnoreFile file2) {
                return StringUtil.naturalCompare(file1.getVirtualFile().getPath(), file2.getVirtualFile().getPath());
            }
        });
        return files;
    }

    /**
     * Escapes character in the given {@link String}.
     * Method is copied from the {@link StringUtil} class to keep the backward compatibility with IDEA 12.x
     *
     * @param string    to parse
     * @param character to escape
     * @return escaped string
     */
    @NotNull
    @Contract(pure = true)
    public static String escapeChar(@NotNull final String string, final char character) {
        final StringBuilder buf = new StringBuilder(string);
        int idx = 0;
        while ((idx = StringUtil.indexOf(buf, character, idx)) >= 0) {
            buf.insert(idx, "\\");
            idx += 2;
        }
        return buf.toString();
    }

    /**
     * Trims leading character in the given {@link String}.
     * Method is copied from the {@link StringUtil} class to keep the backward compatibility with IDEA 12.x
     *
     * @param string    to parse
     * @param character to trim
     * @return trimmed string
     */
    @NotNull
    @Contract(pure = true)
    public static String trimLeading(@NotNull String string, final char character) {
        int index = 0;
        while (index < string.length() && string.charAt(index) == character) {
            index++;
        }
        return string.substring(index);
    }

    /**
     * Intersection method cloned from {@link ContainerUtil#intersection(Collection, Collection)} because of
     * NoSuchMethodError exception errors related to the some API changes.
     *
     * @param collection1 left
     * @param collection2 right
     * @return read-only collection consisting of elements from both collections
     */
    @NotNull
    @Contract(pure = true)
    public static <T> List<T> intersection(@NotNull Collection<? extends T> collection1,
                                           @NotNull Collection<? extends T> collection2) {
        List<T> result = new ArrayList<T>();
        for (T t : collection1) {
            if (collection2.contains(t)) {
                result.add(t);
            }
        }
        return result.isEmpty() ? ContainerUtil.<T>emptyList() : result;
    }

    /**
     * Method cloned from {@link ContainerUtil#notNullize(List)} because of NoSuchMethodError exception
     * errors related to the some API changes.
     *
     * @param list method to check
     * @param <T>  container type
     * @return not null container
     */
    @NotNull
    public static <T> List<T> notNullize(@Nullable List<T> list) {
        return list == null ? ContainerUtil.<T>newArrayList() : list;
    }

    /**
     * Method cloned from {@link ContainerUtil#getFirstItem(List)} because of NoSuchMethodError exception
     * errors related to the some API changes.
     *
     * @param items method to check
     * @param <T>   container type
     * @return not null container
     */
    public static <T> T getFirstItem(@Nullable List<T> items) {
        return items == null || items.isEmpty() ? null : items.get(0);
    }

    /**
     * Adds ColoredFragment to the node's presentation.
     *
     * @param data       node's presentation data
     * @param text       text to add
     * @param attributes custom {@link SimpleTextAttributes}
     */
    public static void addColoredText(@NotNull PresentationData data, @NotNull String text,
                                      @NotNull SimpleTextAttributes attributes) {
        if (data.getColoredText().isEmpty()) {
            data.addText(data.getPresentableText(), REGULAR_ATTRIBUTES);
        }
        data.addText(" " + text, attributes);
    }

    /**
     * Checks if we're currently inside event processing.
     *
     * @return event is processed
     */
    public static boolean isInsideEventProcessing() {
        return safeInvocation(
                null,
                "com.intellij.openapi.project.NoAccessDuringPsiEvents",
                "isInsideEventProcessing",
                false
        );
    }

    /**
     * Invokes UISettings#getScrollTabLayoutInEditor or returns true if method is not available (previous IDEs).
     *
     * @return scroll tab layout setting is in use
     */
    public static boolean getUISettingsScrollTabLayoutInEditor() {
        return safeInvocation(
                UISettings.getInstance(),
                "com.intellij.ide.ui.UISettings",
                "getScrollTabLayoutInEditor",
                true
        );
    }

    /**
     * Invokes safely any SDK method that may not be available in some IDE versions. If method is not resolved in
     * runtime, default value is returned.
     *
     * @param that         the object the underlying method is invoked from
     * @param className    class to invoke
     * @param methodName   method that we want to invoke
     * @param defaultValue default value to return
     * @param args         the arguments used for the method call
     * @param <T>          return type
     * @return result of invoked function or default value if method doesn't exist
     */
    @SuppressWarnings("unchecked")
    private static <T> T safeInvocation(@Nullable Object that, @NotNull String className, @NotNull String methodName,
                                        @Nullable T defaultValue, @Nullable Object... args) {
        try {
            return (T) Class.forName(className).getMethod(methodName).invoke(that, args);
        } catch (ClassNotFoundException ignored) {
        } catch (NoSuchMethodException ignored) {
        } catch (IllegalAccessException ignored) {
        } catch (InvocationTargetException ignored) {
        }
        return defaultValue;
    }
}
