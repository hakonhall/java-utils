package no.ion.io;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import com.google.common.jimfs.PathType;

import java.nio.file.FileSystem;

import static com.google.common.jimfs.Feature.FILE_CHANNEL;
import static com.google.common.jimfs.Feature.LINKS;
import static com.google.common.jimfs.Feature.SECURE_DIRECTORY_STREAM;
import static com.google.common.jimfs.Feature.SYMBOLIC_LINKS;

public class TestFileSystem {
    public static FileSystem create() {
        // Copies from Configuration.unix(), except setAttributesView "basic" -> "posix"
        Configuration posixConfiguration = Configuration.builder(PathType.unix())
                                           .setRoots("/")
                                           .setWorkingDirectory("/work")
                                           .setAttributeViews("unix")
                                           .setSupportedFeatures(LINKS, SYMBOLIC_LINKS, SECURE_DIRECTORY_STREAM, FILE_CHANNEL)
                                           .build();

        return Jimfs.newFileSystem(posixConfiguration);
    }
}
