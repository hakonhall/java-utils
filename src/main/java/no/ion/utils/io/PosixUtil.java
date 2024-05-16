package no.ion.utils.io;

import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.UserPrincipal;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.time.Instant;
import java.util.Set;

import static no.ion.utils.exceptions.Exceptions.uncheckIO;

public class PosixUtil {
    static void setPermissions(FilePermissions permissions, PosixFileAttributeView view) {
        Set<PosixFilePermission> permissionsSet = permissions.toPosixFilePermissionSet();
        uncheckIO(() -> view.setPermissions(permissionsSet));
    }

    static void setLastModifiedTime(Instant time, PosixFileAttributeView view) {
        FileTime fileTime = FileTime.from(time);
        uncheckIO(() -> view.setTimes(fileTime, null, null));
    }

    static void setOwner(String owner, PosixFileAttributeView view, Path path) {
        UserPrincipalLookupService userPrincipalLookupService = path.getFileSystem().getUserPrincipalLookupService();
        UserPrincipal ownerPrincipal = uncheckIO(() -> userPrincipalLookupService.lookupPrincipalByName(owner));
        uncheckIO(() -> view.setOwner(ownerPrincipal));
    }

    static void setGroup(String group, PosixFileAttributeView view, Path path) {
        UserPrincipalLookupService userPrincipalLookupService = path.getFileSystem().getUserPrincipalLookupService();
        GroupPrincipal groupPrincipal = uncheckIO(() -> userPrincipalLookupService.lookupPrincipalByGroupName(group));
        uncheckIO(() -> view.setGroup(groupPrincipal));
    }
}
