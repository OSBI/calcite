/*
// This software is subject to the terms of the Eclipse Public License v1.0
// Agreement, available at the following URL:
// http://www.eclipse.org/legal/epl-v10.html.
// You must accept the terms of that agreement to use this software.
//
// Copyright (C) 2012-2012 Pentaho
// All Rights Reserved.
*/
package org.apache.calcite.adapter.csv;


import org.apache.commons.vfs.*;
import org.apache.commons.vfs.provider.http.HttpFileObject;

import java.io.*;
import java.net.URL;

public class ApacheVfsVirtualFileHandler  {

    public InputStream readVirtualFile(String url)
        throws FileSystemException
    {
        // Treat catalogUrl as an Apache VFS (Virtual File System) URL.
        // VFS handles all of the usual protocols (http:, file:)
        // and then some.
        FileSystemManager fsManager = VFS.getManager();
        if (fsManager == null) {
        }

        // Workaround VFS bug.
        if (url.startsWith("file://localhost")) {
            url = url.substring("file://localhost".length());
        }
        if (url.startsWith("file:")) {
            url = url.substring("file:".length());
        }

        //work around for VFS bug not closing http sockets
        // (Mondrian-585)
        if (url.startsWith("http")) {
            try {
                return new URL(url).openStream();
            } catch (IOException e) {
            }
        }

        File userDir = new File("").getAbsoluteFile();
        FileObject file = fsManager.resolveFile(userDir, url);
        FileContent fileContent = null;
        try {
            // Because of VFS caching, make sure we refresh to get the latest
            // file content. This refresh may possibly solve the following
            // workaround for defect MONDRIAN-508, but cannot be tested, so we
            // will leave the work around for now.
            file.refresh();

            // Workaround to defect MONDRIAN-508. For HttpFileObjects, verifies
            // the URL of the file retrieved matches the URL passed in.  A VFS
            // cache bug can cause it to treat URLs with different parameters
            // as the same file (e.g. http://blah.com?param=A,
            // http://blah.com?param=B)
            if (file instanceof HttpFileObject
                && !file.getName().getURI().equals(url))
            {
                fsManager.getFilesCache().removeFile(
                    file.getFileSystem(),  file.getName());

                file = fsManager.resolveFile(userDir, url);
            }

            if (!file.isReadable()) {
            }

            fileContent = file.getContent();
        } finally {
            file.close();
        }

        if (fileContent == null) {
        }

        return fileContent.getInputStream();
    }
}

// End ApacheVfsVirtualFileHandler.java
