package mobi.hsz.idea.gitignore.reference;

import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VFileProperty;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSet;
import mobi.hsz.idea.gitignore.psi.GitignoreEntry;
import mobi.hsz.idea.gitignore.util.Glob;
import mobi.hsz.idea.gitignore.util.Utils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

public class GitignoreReferenceSet extends FileReferenceSet {
    public GitignoreReferenceSet(@NotNull GitignoreEntry element) {
        super(element);
    }

    @Override
    public FileReference createFileReference(TextRange range, int index, String text) {
        return new GitignoreReference(this, range, index, text);
    }

    @Override
    public boolean isEndingSlashNotAllowed() {
        return false;
    }

    @NotNull
    @Override
    public Collection<PsiFileSystemItem> computeDefaultContexts() {
        PsiFile containingFile = getElement().getContainingFile();
        PsiDirectory containingDirectory = containingFile.getParent();
        return containingDirectory != null ? Collections.<PsiFileSystemItem>singletonList(containingDirectory) : super.computeDefaultContexts();
    }

    @Nullable
    public FileReference getLastReference() {
        FileReference lastReference = super.getLastReference();
        if (lastReference != null && lastReference.getCanonicalText().endsWith(getSeparatorString())) {
            return this.myReferences != null && this.myReferences.length > 1 ? this.myReferences[this.myReferences.length - 2] : null;
        }
        return lastReference;
    }

    @Override
    public boolean couldBeConvertedTo(boolean relative) {
        return false;
    }

    @Override
    protected void reparse() {
        String str = StringUtil.trimEnd(getPathString(), getSeparatorString());
        final List<FileReference> referencesList = new ArrayList<FileReference>();

        String separatorString = getSeparatorString(); // separator's length can be more then 1 char
        int sepLen = separatorString.length();
        int currentSlash = -sepLen;
        int startInElement = getStartInElement();

        // skip white space
        while (currentSlash + sepLen < str.length() && Character.isWhitespace(str.charAt(currentSlash + sepLen))) {
            currentSlash++;
        }

        if (currentSlash + sepLen + sepLen < str.length() &&
                str.substring(currentSlash + sepLen, currentSlash + sepLen + sepLen).equals(separatorString)) {
            currentSlash += sepLen;
        }
        int index = 0;

        if (str.equals(separatorString)) {
            final FileReference fileReference =
                    createFileReference(new TextRange(startInElement, startInElement + sepLen), index++, separatorString);
            referencesList.add(fileReference);
        }

        while (true) {
            final int nextSlash = str.indexOf(separatorString, currentSlash + sepLen);
            final String subreferenceText = nextSlash > 0 ? str.substring(0, nextSlash) : str.substring(0);
            final FileReference ref = createFileReference(
                    new TextRange(startInElement, startInElement + (nextSlash > 0 ? nextSlash : str.length())),
                    index++,
                    subreferenceText);
            referencesList.add(ref);
            if ((currentSlash = nextSlash) < 0) {
                break;
            }
        }

        myReferences = referencesList.toArray(new FileReference[referencesList.size()]);
    }

    private class GitignoreReference extends FileReference {
        public GitignoreReference(@NotNull FileReferenceSet fileReferenceSet, TextRange range, int index, String text) {
            super(fileReferenceSet, range, index, text);
        }

        @Override
        protected void innerResolveInContext(@NotNull String text, @NotNull PsiFileSystemItem context, Collection<ResolveResult> result, boolean caseSensitive) {
            super.innerResolveInContext(text, context, result, caseSensitive);
            VirtualFile contextVirtualFile = context.getVirtualFile();
            if (contextVirtualFile != null) {
                Pattern pattern = Glob.createPattern(getCanonicalText());
                if (pattern != null) {
                    PsiDirectory parent = getElement().getContainingFile().getParent();
                    if (parent != null) {
                        VirtualFile root = parent.getVirtualFile();
                        walk(result, pattern, root, contextVirtualFile);
                    }
                }
            }
        }

        private void walk(Collection<ResolveResult> result, Pattern pattern, VirtualFile root, VirtualFile directory) {
            PsiManager manager = getElement().getManager();
            for (VirtualFile file : directory.getChildren()) {
                String name = Utils.getRelativePath(root, file);
                if (pattern.matcher(name).matches()) {
                    PsiFileSystemItem psiFileSystemItem = getPsiFileSystemItem(manager, file);
                    if (psiFileSystemItem != null) {
                        result.add(new PsiElementResolveResult(psiFileSystemItem));
                    }
                }

                if (file.isDirectory() && !file.is(VFileProperty.SYMLINK)) {
                    walk(result, pattern, root, file);
                }
            }
        }

        private PsiFileSystemItem getPsiFileSystemItem(PsiManager manager, @NotNull VirtualFile file) {
            return file.isDirectory() ? manager.findDirectory(file) : manager.findFile(file);
        }
    }
}